package com.hoppinzq.agent.capability.todo;

import com.hoppinzq.agent.capability.AgentCommand;
import com.hoppinzq.agent.capability.AgentHook;
import com.hoppinzq.agent.capability.Capability;
import com.hoppinzq.agent.client.LlmMessage;
import com.hoppinzq.agent.tool.ToolDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 待办事项能力（可整体复制的特性能力包）。
 *
 * <p>向内核提供：
 * <ul>
 *   <li>1 个工具：{@code todo}（LLM 增量更新任务列表）</li>
 *   <li>1 个钩子：{@code beforeLlmCall}（连续多回合未更新列表时注入一次提醒 user 消息，
 *       承接原入口类覆写 {@code onToolExecution} 的提醒逻辑——旧内核从未调用该回调，
 *       能力化后改挂在真正会触发的内核钩子上）</li>
 *   <li>1 个命令：{@code /todos}（本地查看当前任务列表，不发给 LLM）</li>
 *   <li>系统提示片段：任务管理策略</li>
 * </ul>
 *
 * <p>与旧实现的差异（为什么）：原先 {@code TodoDefinition} 是 {@code ToolDefinition} 的静态字段，
 * 依赖 {@code Agent03.todoManager} 静态注入才能拿到执行体；现在能力自持 manager 实例，
 * 工具定义改为 {@link #tools()} 返回的实例，入口类不再需要任何 todo 接线。
 * 拔除 {@code capability/todo/} 目录并删除装配处一行注册即可完全移除该能力。
 *
 * <p>对内核的依赖仅限：Capability 契约、client（LlmMessage）、tool/ToolDefinition。
 *
 * @author hoppinzq
 */
public class TodoCapability implements Capability {

    /** 能力自持的管理器实例（替代原 Agent03.todoManager 静态注入） */
    private final TodoManager todoManager = new TodoManager();

    /** 上次观测到的待办版本号，用于判断“最近是否有更新” */
    private long lastTodoVersion = 0;
    /** 距上次待办更新已经历的 LLM 调用回合数 */
    private int roundsSinceTodo = 0;

    @Override
    public String name() {
        return "todo";
    }

    // ============================== 工具 ==============================

    @Override
    public List<ToolDefinition> tools() {
        // 原 ToolDefinition.TodoDefinition 静态字段整体迁入：能力自持 manager，
        // 执行体直接方法引用，无需任何静态回调钩子
        ToolDefinition todo = new ToolDefinition(
                "todo",
                "管理待办事项列表。使用此工具可以添加、更新或完成任务。支持任务状态追踪，包括待处理、进行中和已完成三种状态。",
                ToolDefinition.createInputSchema(
                        Map.of("todos",
                                Map.of(
                                        "type", "array",
                                        "description", "待办事项列表",
                                        "items", Map.of(
                                                "type", "object",
                                                "properties", Map.of(
                                                        "id", Map.of("type", "string", "description", "任务的唯一标识符"),
                                                        "content", Map.of("type", "string", "description", "待办事项的内容描述"),
                                                        "status", Map.of(
                                                                "type", "string",
                                                                "enum", List.of("pending", "in_progress", "completed"),
                                                                "description", "任务状态：pending-待处理, in_progress-进行中, completed-已完成"
                                                        )
                                                ),
                                                "required", List.of("id", "content", "status")
                                        )
                                )
                        ),
                        List.of("todos")
                ),
                TodoInput.class,
                todoManager::updateTodos);
        return List.of(todo);
    }

    // ============================== 钩子 ==============================

    @Override
    public List<AgentHook> hooks() {
        return List.of(new AgentHook() {
            @Override
            public List<LlmMessage> beforeLlmCall(List<LlmMessage> messages) {
                return maybeRemind(messages);
            }
        });
    }

    /**
     * 连续 {@link TodoConfig#REMINDER_ROUNDS} 个回合没有更新待办列表时，
     * 在本次请求的上下文末尾追加一条提醒 user 消息。
     * <p>原样返回入参 = 本轮不注入（内核不会改动上下文）；返回新列表 = 内核替换当前上下文。
     */
    private List<LlmMessage> maybeRemind(List<LlmMessage> messages) {
        long currentVersion = todoManager.getVersion();
        if (currentVersion > lastTodoVersion) {
            // 本轮有更新：重置计数
            roundsSinceTodo = 0;
            lastTodoVersion = currentVersion;
            return messages;
        }
        roundsSinceTodo++;
        if (roundsSinceTodo < TodoConfig.REMINDER_ROUNDS) {
            return messages;
        }
        String reminder = String.format("""
                <reminder>
                您已经 %d 个回合没有更新待办事项列表了。请更新列表以反映当前进度。

                建议操作：
                1. 检查当前任务状态
                2. 更新已完成的任务
                3. 添加新的任务（如果需要）
                4. 调整任务优先级
                </reminder>
                """, roundsSinceTodo);
        // 提醒后重新计数：每个阈值周期只提醒一次，避免每轮刷屏
        roundsSinceTodo = 0;
        List<LlmMessage> next = new ArrayList<>(messages);
        next.add(LlmMessage.user(reminder));
        return next;
    }

    // ============================== 命令 ==============================

    @Override
    public List<AgentCommand> commands() {
        return List.of(new AgentCommand() {
            @Override
            public String name() {
                return "/todos";
            }

            @Override
            public String description() {
                return "查看当前待办事项列表";
            }

            @Override
            public void execute() {
                System.out.println("========== 当前待办事项 ==========");
                System.out.println(todoManager.render());
            }
        });
    }

    // ============================== 提示词与描述 ==============================

    @Override
    public List<String> promptFragments() {
        return List.of(
                "# 任务管理策略（todo 能力）\n" +
                "1. 当开始新任务时，立即使用 todo 工具创建相关任务\n" +
                "2. 任务进行中时，定期更新任务状态（pending / in_progress / completed）\n" +
                "3. 任务完成后，立即标记为已完成\n" +
                "4. 最多 " + TodoConfig.MAX_TODOS + " 个待办事项，同时只能有一个进行中\n" +
                "5. 如果连续 " + TodoConfig.REMINDER_ROUNDS + " 个回合没有更新任务列表，系统会提醒您");
    }

    /** 启动横幅用的配置摘要 */
    public String description() {
        return "待办能力：列表上限 = " + TodoConfig.MAX_TODOS
                + ", 同时进行中上限 = 1, 提醒阈值 = " + TodoConfig.REMINDER_ROUNDS + " 回合";
    }
}
