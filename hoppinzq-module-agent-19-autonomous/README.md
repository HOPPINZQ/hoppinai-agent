# hoppinzq-module-agent-19-autonomous（自主认领）

> 说明：下方"历史实现"章节保留了重构前的代码片段（`AnthropicClient`、两参 `setSessionManager`、
> `ToolDefinition` 静态技能字段等），仅作演进对照；当前 API 以本页末尾「能力化架构」与源码为准。


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

---

## 能力化架构（2026-10 重构）

本工程的内核与特性已彻底分离：**内核不感知任何具体能力**，特性以能力包形式插拔。

### 内核（25 个模块逐字节一致）

| 目录 | 职责 |
|---|---|
| `client/` | 协议中立模型 + 全能 Provider（OpenAI / Anthropic，均支持 stream、扩展思考 think、多模态） |
| `session/` | 会话持久化（`.sessions/*.json`）、消息转换、token 统计、thinking 签名回填 |
| `base/ZQAgent` | 主循环 + 能力注册（`agent.capability(...)`）+ 四类钩子分发 + 命令收集 |
| `command/AgentCommandHandler` | 内置命令（`/stats` `/usage` `/exit`）+ 能力注册的斜杠命令 |
| `capability/{Capability,AgentHook,AgentCommand}` | 能力契约（工具 / 钩子 / 命令 / 提示片段 / 生命周期） |

内核一致性由 `bin/check-kernel-sync.sh` 校验（以 06-compact 为权威源）。

### 本模块的能力包

| 目录 | 职责 |
|---|---|
| `capability/subagent/` | 子智能体（sub_agent：独立上下文 + token 用量回传） |
| `capability/task/` | 任务板（create/list/get/claim/complete） |
| `capability/cron/` | 定时调度（命中后由能力在宿主上下文中执行任务） |
| `capability/teams/` | 团队协作（spawn_teammate / send_message / check_inbox + 邮箱轮询注入） |
| `capability/protocol/` | 团队协议状态机（shutdown / plan approval，带 requestId 回执） |
| `capability/autonomous/` | teammate 空闲自动轮询任务板并认领 |
入口类只负责装配：复制能力目录 + 一行注册。

```java
XxxCapability cap = new XxxCapability(...);
agent.capability(cap);
agent.setSystemPrompt(basePrompt + "

" + String.join("
", cap.promptFragments()));
```

### 快速开始

```bash
# 1) 编译（工程根目录）
mvn -pl hoppinzq-module-agent-19-autonomous compile

# 2) 运行入口类（IDE 里直接运行，或在根目录）
mvn -pl hoppinzq-module-agent-19-autonomous exec:java -Dexec.mainClass=com.hoppinzq.agent.AgentAutonomous
```

环境变量：`DEEPSEEK_API_KEY`（必填)；协议与模型在 `constant/AIConstants` 中配置
（`PROVIDER=openai|anthropic`、`OPENAI_BASE_URL`、`ANTHROPIC_BASE_URL`、`MODEL`、`THINK_ENABLED`）。

### 最近变更（与本模块相关）

- **能力化重构**：特性从入口类覆写 / 静态字段迁入 `capability/<feature>/`，入口类只做装配
- **内核解耦**：`/skills` 与 `SkillLoader`、`SubAgentSessionResult` 与子智能体运行逻辑已移出内核；
  内核 `setSessionManager` 为单参、`AgentCommandHandler` 不再持有技能类型
- **协议层全能版**：流式 `stream()`、扩展思考 think（含 Anthropic thinking 块签名回传）、多模态内容块
- **bash 工具加固**：120s 超时强杀、stderr 合并读取（防管道死锁）、危险命令黑名单、
  5 万字符截断、读写统一 UTF-8、glob 结果 200 条上限
- **上下文压缩修复**：snip 触发/目标分离（迟滞）、本轮工具结果整批保留、压缩后保留当前用户请求
