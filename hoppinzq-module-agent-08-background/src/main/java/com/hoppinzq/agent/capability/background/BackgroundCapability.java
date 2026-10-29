package com.hoppinzq.agent.capability.background;

import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.capability.AgentCommand;
import com.hoppinzq.agent.capability.AgentHook;
import com.hoppinzq.agent.capability.Capability;
import com.hoppinzq.agent.client.LlmMessage;
import com.hoppinzq.agent.tool.ToolDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.hoppinzq.agent.tool.ToolDefinition.createInputSchema;
import static com.hoppinzq.agent.tool.ToolDefinition.createProperty;

/**
 * 后台任务能力（可整体复制的能力包）。
 *
 * <p>向内核提供：
 * <ul>
 *   <li>2 个工具：{@code background_run}（后台起命令，立即返回 task_id）、
 *       {@code check_background}（查单个任务状态或列出全部任务）</li>
 *   <li>1 个钩子：{@code beforeLlmCall} —— 每次 LLM 调用前排空通知队列，
 *       把已完成任务的结果以 {@code <task_notification>} XML 注入上下文
 *       （替代原先入口类在主循环里手动调用的静态回调模式）</li>
 *   <li>1 个命令：{@code /background}（控制台直接查看全部后台任务）</li>
 *   <li>系统提示片段：何时使用后台任务</li>
 * </ul>
 *
 * <p>生命周期：线程池在构造时创建、{@link #onStop()} 关闭 ——
 * 替代原先 main 的 {@code finally { backgroundManager.shutdown(); }}。
 *
 * <p>对内核的依赖仅限：Capability 契约、client（消息模型）、tool/ToolDefinition、
 * AIConstants.ROOT（后台进程的工作目录）。
 *
 * @author hoppinzq
 */
public class BackgroundCapability implements Capability {

    /** 能力自持的后台任务管理器（线程池 + 任务表 + 通知队列） */
    private final BackgroundManager backgroundManager = new BackgroundManager();

    /** onStart 时捕获的宿主 Agent（当前仅用于诊断，预留后续挂载点扩展） */
    private ZQAgent agent;

    @Override
    public String name() {
        return "background";
    }

    @Override
    public void onStart(ZQAgent agent) {
        this.agent = agent;
    }

    @Override
    public void onStop() {
        // Agent 关闭时回收后台线程：等待已提交任务收尾，超时强制中断
        backgroundManager.shutdown();
    }

    // ============================== 工具 ==============================

    @Override
    public List<ToolDefinition> tools() {
        ToolDefinition run = new ToolDefinition(
                "background_run",
                "在后台线程中执行命令。立即返回task_id，不阻塞主线程。适用于耗时命令如 npm install、pytest、docker build等。",
                createInputSchema(
                        Map.of("command", createProperty("string", "要在后台执行的 bash 命令。")),
                        List.of("command")
                ),
                BackgroundRunInput.class,
                this::invokeRun);

        ToolDefinition check = new ToolDefinition(
                "check_background",
                "检查后台任务状态。省略task_id参数可列出所有任务。",
                createInputSchema(
                        Map.of("taskId", createProperty("string", "要检查的任务ID（可选）")),
                        List.of()
                ),
                CheckBackgroundInput.class,
                this::invokeCheck);
        return List.of(run, check);
    }

    /** background_run 执行体：与 manager 同包直接调用，不再经过任何静态字段 */
    private String invokeRun(BackgroundRunInput input) {
        try {
            return backgroundManager.runInBackground(input.getCommand());
        } catch (Exception e) {
            return "启动后台任务失败: " + e.getMessage();
        }
    }

    /** check_background 执行体：带 taskId 查单个任务，否则列全部 */
    private String invokeCheck(CheckBackgroundInput input) {
        try {
            String taskId = input.getTaskId();
            return taskId != null && !taskId.isEmpty()
                    ? backgroundManager.checkStatus(taskId)
                    : backgroundManager.listAllTasks();
        } catch (Exception e) {
            return "检查后台任务失败: " + e.getMessage();
        }
    }

    // ============================== 钩子 ==============================

    @Override
    public List<AgentHook> hooks() {
        return List.of(new AgentHook() {
            @Override
            public List<LlmMessage> beforeLlmCall(List<LlmMessage> messages) {
                // 每次 LLM 调用前注入已完成的后台任务结果；
                // 无通知时原样返回入参（内核约定：同一引用 = 不修改上下文）
                String xml = backgroundManager.drainNotificationsAsXml();
                if (xml == null) {
                    return messages;
                }
                List<LlmMessage> next = new ArrayList<>(messages);
                next.add(LlmMessage.user(xml));
                return next;
            }
        });
    }

    // ============================== 命令 ==============================

    @Override
    public List<AgentCommand> commands() {
        return List.of(new AgentCommand() {
            @Override
            public String name() {
                return "/background";
            }

            @Override
            public String description() {
                return "查看全部后台任务状态";
            }

            @Override
            public void execute() {
                System.out.println(backgroundManager.listAllTasks());
            }
        });
    }

    // ============================== 提示词片段 ==============================

    @Override
    public List<String> promptFragments() {
        return List.of("""
                ## 后台任务

                - 预计耗时超过 5 秒的命令（npm install、pytest、docker build、mvn、git clone 等）
                  用 background_run 执行：立即返回 task_id，不阻塞对话
                - 后台任务完成后，结果会以 <task_notification> 自动注入到对话中，无需轮询
                - 需要主动查看时用 check_background：带 taskId 查单个任务，省略则列出全部""",
                "当前运行中的后台任务数: " + backgroundManager.getRunningTaskCount());
    }

    /** 启动横幅用的配置摘要 */
    public String description() {
        return "后台执行超时: " + BackgroundConfig.RUN_TIMEOUT_SECONDS + "s, "
                + "结果截断: " + BackgroundConfig.MAX_RESULT_CHARS + " chars, "
                + "保留任务数: " + BackgroundConfig.MAX_RETAINED_TASKS;
    }
}
