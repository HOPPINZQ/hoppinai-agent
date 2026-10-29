package com.hoppinzq.agent.capability.teams;

import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.capability.AgentHook;
import com.hoppinzq.agent.capability.Capability;
import com.hoppinzq.agent.capability.AgentCommand;
import com.hoppinzq.agent.client.LlmMessage;
import com.hoppinzq.agent.client.LlmProvider;
import com.hoppinzq.agent.constant.AIConstants;
import com.hoppinzq.agent.tool.ToolDefinition;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.hoppinzq.agent.capability.teams.TeamsConfig.LEAD_NAME;
import static com.hoppinzq.agent.constant.AIConstants.OBJECT_MAPPER;

/**
 * agent teams 能力（可整体复制的能力包）。
 *
 * <p>向内核提供：
 * <ul>
 *   <li>3 个工具：{@code spawn_teammate} / {@code send_message} / {@code check_inbox}</li>
 *   <li>1 个钩子：{@code beforeLlmCall} 里轮询 lead 邮箱，把 teammate 回执注入本轮上下文
 *       （原内核 ZQAgent 每轮 pollInbox 的职责移入能力，内核不再感知消息总线）</li>
 *   <li>生命周期：{@link #onStart} 建邮箱目录并起消息总线，{@link #onStop} 打断仍在跑的 teammate 线程</li>
 *   <li>系统提示片段：lead 的团队协作用法</li>
 * </ul>
 *
 * <p>每个 teammate 是一个守护线程，有自己的对话上下文与工具子集，与 lead 之间
 * 唯一的通信通道是文件邮箱（{@code <ROOT>/} + {@link TeamsConfig#MAILBOX_DIR}）。
 *
 * <p>对内核的依赖仅限：Capability 契约、client（协议中立模型）、AIConstants.ROOT。
 * 拔除本目录并删除注册行即可完全移除该能力。
 *
 * @author hoppinzq
 */
public class TeamsCapability implements Capability {

    /** lead 侧复用的 provider（spawn 时传给 teammate） */
    private final LlmProvider provider;
    /** 模型名 */
    private final String model;
    /** teammate 可用的工具子集（由装配方给定，通常 bash/read_file/write_file/send_message） */
    private final List<ToolDefinition> teammateTools;

    /** 消息总线（文件邮箱，无状态，onStart 时建目录即"起总线"） */
    private final MessageBus bus = new MessageBus();

    /** onStart 捕获的宿主 Agent（保留引用，便于后续扩展 lead 侧主动干活） */
    private ZQAgent agent;

    /** 已 spawn 的 teammate 线程；onStop 时统一打断 */
    private final List<Thread> teammateThreads = new CopyOnWriteArrayList<>();

    /** 可选空闲回调（autonomous 能力注入；为 null 时 teammate 靠 inbox 驱动） */
    private TeammateIdleHook idleHook;

    /**
     * 注入 teammate 空闲回调（可选）。装配方在注册 autonomous 能力时调用一次即可。
     */
    public TeamsCapability idleHook(TeammateIdleHook idleHook) {
        this.idleHook = idleHook;
        return this;
    }

    public TeamsCapability(LlmProvider provider, String model, List<ToolDefinition> teammateTools) {
        this.provider = provider;
        this.model = model;
        this.teammateTools = teammateTools;
    }

    @Override
    public String name() {
        return "teams";
    }

    @Override
    public void onStart(ZQAgent agent) {
        this.agent = agent;
        try {
            Files.createDirectories(MessageBus.inboxPath(LEAD_NAME).getParent());
        } catch (Exception e) {
            System.err.println("[能力 teams] 创建邮箱目录失败: " + e.getMessage());
        }
        System.out.println("[能力 teams] 消息总线已就绪（邮箱目录 "
                + MessageBus.inboxPath(LEAD_NAME).getParent() + "）");
    }

    @Override
    public void onStop() {
        for (Thread t : teammateThreads) {
            if (t.isAlive()) {
                t.interrupt();
            }
        }
    }

    // ============================== 工具 ==============================

    @Override
    public List<ToolDefinition> tools() {
        ToolDefinition spawnTeammate = new ToolDefinition(
                "spawn_teammate",
                "启动一个 teammate agent，让它在独立的守护线程里自主完成一项任务。\n" +
                        "teammate 拥有自己的对话上下文和专属工具子集，和 lead 之间通过文件邮箱异步通信。\n" +
                        "spawn 后立即返回，不要等待 teammate 结束；下一轮对话会自动收到它的回执，也可用 check_inbox 主动查看。",
                ToolDefinition.createInputSchema(
                        Map.of(
                                "name", ToolDefinition.createProperty("string", "teammate 的名字（也是其邮箱名），例如 'alice'。"),
                                "role", ToolDefinition.createProperty("string", "teammate 的角色描述，例如 '后端开发'。"),
                                "prompt", ToolDefinition.createProperty("string", "派给 teammate 的初始任务说明，应足够具体。")
                        ),
                        List.of("name", "role", "prompt")
                ),
                SpawnTeammateInput.class,
                this::invokeSpawnTeammate);

        ToolDefinition sendMessage = new ToolDefinition(
                "send_message",
                "向某个 teammate（或 lead）的邮箱发送一条消息。lead 调用时 from 自动填 'lead'，" +
                        "teammate 在自己的线程里调用时 from 自动填其名字。",
                ToolDefinition.createInputSchema(
                        Map.of(
                                "to", ToolDefinition.createProperty("string", "收件人名字（teammate 名字或 'lead'）。"),
                                "content", ToolDefinition.createProperty("string", "消息内容。"),
                                "type", ToolDefinition.createProperty("string", "消息类型，默认 message（协议类类型由协议能力使用）。")
                        ),
                        List.of("to", "content")
                ),
                SendMessageInput.class,
                this::invokeSendMessage);

        ToolDefinition checkInbox = new ToolDefinition(
                "check_inbox",
                "读取 lead 自己的收件箱，返回所有未读消息（读取即消费，同一条消息不会重复出现）。",
                ToolDefinition.createInputSchema(Map.of(), List.of()),
                Map.class,
                this::invokeCheckInbox);

        return List.of(spawnTeammate, sendMessage, checkInbox);
    }

    /** spawn_teammate 执行体：起一个 teammate 守护线程，立即返回（原 Tools 静态回调方案收编为能力私有方法） */
    private String invokeSpawnTeammate(SpawnTeammateInput in) {
        if (in.getName() == null || in.getName().isBlank()) {
            return "错误：teammate 名字不能为空";
        }
        if (in.getPrompt() == null || in.getPrompt().isBlank()) {
            return "错误：prompt 不能为空";
        }
        try {
            List<ToolDefinition> tools = new ArrayList<>(teammateTools);
            Thread thread = TeammateSpawner.spawn(provider, model, in.getName(),
                    in.getRole() == null ? "assistant" : in.getRole(),
                    in.getPrompt(), bus, tools, idleHook);
            teammateThreads.add(thread);
            return "已 spawn teammate " + in.getName() + "（最多 "
                    + TeamsConfig.TEAMMATE_MAX_ROUNDS + " 轮，结束后通过邮箱回执）";
        } catch (Exception e) {
            return "spawn_teammate 错误: " + e.getMessage();
        }
    }

    /** send_message 执行体：from 由 TeammateContext 区分（lead 或 teammate 线程） */
    private String invokeSendMessage(SendMessageInput in) {
        if (in.getTo() == null || in.getTo().isBlank()) {
            return "错误：to 不能为空";
        }
        String from = TeammateContext.current();
        if (from == null || from.isBlank()) {
            from = LEAD_NAME;
        }
        try {
            bus.send(from, in.getTo(), in.getContent() == null ? "" : in.getContent(),
                    in.getType() == null ? "message" : in.getType());
            return "已发送给 " + in.getTo();
        } catch (Exception e) {
            return "send_message 错误: " + e.getMessage();
        }
    }

    /** check_inbox 执行体：读并清空 lead 的邮箱 */
    private String invokeCheckInbox(Map input) {
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(bus.readInbox(LEAD_NAME));
        } catch (Exception e) {
            return "check_inbox 错误: " + e.getMessage();
        }
    }

    // ============================== 钩子 ==============================

    @Override
    public List<AgentHook> hooks() {
        return List.of(new AgentHook() {
            @Override
            public List<LlmMessage> beforeLlmCall(List<LlmMessage> messages) {
                // 每次 LLM 调用前消费 lead 邮箱：teammate 的回执以 user 消息形式并入本轮上下文。
                // 返回新列表 = 内核会用它替换当前上下文（含持久化），无需内核感知邮箱存在。
                List<MailboxMessage> inbox = bus.readInbox(LEAD_NAME);
                if (inbox.isEmpty()) {
                    return messages;
                }
                List<LlmMessage> merged = new ArrayList<>(messages);
                for (MailboxMessage msg : inbox) {
                    if ("shutdown_request".equals(msg.getType())) {
                        // teams 模块不处理协议消息，原样透出内容
                        merged.add(LlmMessage.user("[" + msg.getFrom() + "] " + msg.getContent()));
                    } else {
                        merged.add(LlmMessage.user("[" + msg.getFrom() + "] " + msg.getContent()));
                    }
                }
                return merged;
            }
        });
    }

    // ============================== 命令 ==============================

    @Override
    public List<AgentCommand> commands() {
        return List.of(new AgentCommand() {
            @Override
            public String name() {
                return "/team";
            }

            @Override
            public String description() {
                return "查看当前团队的 teammate 线程状态";
            }

            @Override
            public void execute() {
                printTeam();
            }
        });
    }

    /** /team：列出已 spawn 的 teammate 线程及其存活状态 */
    private void printTeam() {
        if (teammateThreads.isEmpty()) {
            System.out.println("[teams] 还没有 spawn 任何 teammate");
            return;
        }
        System.out.println("========== 团队 ==========");
        for (Thread t : teammateThreads) {
            System.out.printf("  %-20s %s%n", t.getName(), t.isAlive() ? "运行中" : "已结束");
        }
    }

    // ============================== 提示词与描述 ==============================

    @Override
    public List<String> promptFragments() {
        return List.of(
                "## 团队协作（teams）\n" +
                "你是 lead agent（团队负责人），可以派 teammate 去并行干活：\n" +
                "- spawn_teammate(name, role, prompt)：启动一个 teammate，它在独立的守护线程里跑自己的 agent 循环。" +
                "spawn 立即返回，不要等待。\n" +
                "- 每个 teammate 用完最多 " + TeamsConfig.TEAMMATE_MAX_ROUNDS + " 轮 LLM 调用就会自动结束，并通过文件邮箱给你回执。\n" +
                "- check_inbox()：读取你的邮箱。每轮对话我都会自动 poll 邮箱，所以即使你不主动 check_inbox，" +
                "teammate 的消息也会出现在下一轮对话里。\n" +
                "- send_message(to, content)：主动给某个 teammate 发消息。\n" +
                "- 邮箱机制：每个角色在 " + TeamsConfig.MAILBOX_DIR + "/ 下有一个 {name}.jsonl 收件箱，读即消费。\n" +
                "典型流程：spawn_teammate 派任务 → 继续做别的事 → 下一轮自动收到 teammate 的回执。");
    }

    /** 启动横幅用的配置摘要 */
    public String description() {
        return "agent teams：teammate 最多 " + TeamsConfig.TEAMMATE_MAX_ROUNDS + " 轮/任务，采样温度 "
                + TeamsConfig.TEMPERATURE + "，邮箱目录 " + AIConstants.ROOT
                + java.io.File.separator + TeamsConfig.MAILBOX_DIR;
    }
}
