# hoppinzq-module-agent-autonomous（自主 agent 群）

对应 Python 教程 `s17_autonomous_agents`。在 protocols 模块的基础上，加入任务板（TaskManager）+ IdlePoller + AutoClaimer，实现 teammate 自主轮询认领任务的 WORK → IDLE 两阶段循环。

## 核心机制

```
                   ┌───────────────────────────────────────┐
                   │  TaskManager（任务板）                  │
                   │  create / list / get / claim / complete │
                   │  + blockedBy 依赖图 + scanUnclaimed     │
                   └───────────────────────────────────────┘
                                       ▲
                                       │ claim_task(自主)
   ┌──────────────────┐    ┌─────────────────────────────┐
   │  WORK (最多10轮)  │───>│  IDLE (IdlePoller, 60s/5s)  │
   │  LLM + tools     │<───│  先看 inbox(可能有 shutdown)  │
   │  stop_reason≠    │    │  再看任务板有无未认领任务      │
   │  tool_use → IDLE │    │  有 → claim → 回 WORK        │
   └──────────────────┘    └─────────────────────────────┘
```

WORK/IDLE 两阶段：

| 阶段 | 行为 | 退出条件 |
|---|---|---|
| WORK | LLM 调用 + 工具执行（最多 10 轮） | stop_reason 非 tool_use 或轮数耗尽 |
| IDLE | 每 5s 轮询 inbox + 任务板（最长 60s） | 有新任务 → WORK；shutdown → 退出；超时 → 退出 |

## 新增/抄入文件

- `tool/task/TaskManager.java` — 从 module-07 抄入（含 create/list/get/claim/complete + blockedBy 依赖图 + scanUnclaimedTasks）
- `tool/autonomous/IdlePoller.java` — IDLE 阶段轮询器：先查 inbox（可能含 shutdown），再查任务板
- `tool/autonomous/AutoClaimer.java` — 包装 claim_task，认领成功则切换 teammate 的当前任务上下文
- `tool/schema/TaskCreateInput.java` / `TaskListInput.java` / `TaskGetInput.java` / `TaskClaimInput.java` / `TaskUpdateInput.java`

## 修改点

- `tool/Tools.java`：新增 `TASK_MANAGER` 静态字段 + setter；新增 `createTask` / `listTasks` / `getTask` / `claimTask` / `completeTask` 方法
- `tool/ToolDefinition.java`：新增 `CreateTaskDefinition` / `ListTasksDefinition` / `GetTaskDefinition` / `ClaimTaskDefinition` / `CompleteTaskDefinition`
- `tool/bus/TeammateRunner.java`：改成 WORK → IDLE 两阶段循环，替代 protocols 的简单 idle
- `tool/bus/TeammateSpawner.java`：spawn 时注入 TaskManager 引用

## 工具

| 工具 | 用途 |
|---|---|
| create_task(subject, description) | 创建新任务 |
| list_tasks() | 列出所有任务（含状态和依赖） |
| get_task(taskId) | 获取任务详情 |
| claim_task(taskId) | 认领任务（仅 teammate 可调用） |
| complete_task(taskId) | 标记任务完成 |

加上 protocols 的 3 个 + teams 的 3 个 + cron 的 3 个 + 基础 6 个，共 20 个。

## 配置 & 运行

编辑 `src/main/java/com/hoppinzq/agent/constant/AIConstants.java`，填好 `API_KEY` / `BASE_URL` / `MODEL`。

```bash
mvn -pl hoppinzq-module-agent-autonomous -am compile -Dmaven.compiler.fork=true
mvn -pl hoppinzq-module-agent-autonomous exec:java -Dexec.mainClass=com.hoppinzq.agent.AgentAutonomous
```

> `-Dmaven.compiler.fork=true` 规避本机 slf4j jar 锁问题。

## 场景测试

| Prompt | 期望 |
|---|---|
| 创建 3 个任务，然后 spawn alice 和 bob，看他们自动认领并干活 | alice 和 bob 各自 IDLE 轮询 → 发现未认领任务 → claim → WORK，互不冲突 |
| 创建带依赖的任务（blockedBy） | task2 blockedBy task1 → task2 在 task1 完成前不会被 scanUnclaimed 扫出 |
| complete_task 后释放依赖 | task1 complete → scanUnclaimed 扫出 task2 → 下一个空闲 teammate 认领 |

@hoppinzq
