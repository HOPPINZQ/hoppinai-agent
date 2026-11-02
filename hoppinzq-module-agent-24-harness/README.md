# hoppinzq-module-agent-24-harness（Harness 集成宿主）

> 说明：下方"历史实现"章节保留了重构前的代码片段（`AnthropicClient`、两参 `setSessionManager`、
> `ToolDefinition` 静态技能字段等），仅作演进对照；当前 API 以本页末尾「能力化架构」与源码为准。


对应 Python 教程 `s15_integrated_harness`。**多种机制，一个循环** —— 不引入新机制，而是把前面各章的组件接到同一个 agent loop 上，展示它们从哪里进入模型循环、产生的事件又如何回到同一段对话。

![System Architecture](img/system-architecture.svg)

## 循环结构

```
用户输入
  → UserPromptSubmit hooks
  → cron 队列注入（定时 prompt 当作本轮输入）
  → background 通知注入（已完成的后台任务结果）
  → context compact
  → memory + skills + MCP 状态组装 system prompt
  → LLM
  → 有 tool_use block?
      否 → Stop hooks → 回到等待输入
      是 → PreToolUse hooks + permission
          → TOOL_HANDLERS / MCP handlers / background dispatch
          → PostToolUse hooks
          → tool_result 追加回 messages
          → 下一轮
```

循环本身没有变化：调用模型 → 检查响应里有没有 `tool_use` → 执行工具 → 把结果追加回 `messages`。是否继续工具轮，由响应中有没有实际的 `tool_use` 决定。

## 组件在循环中的位置

| 位置 | 组件 | 作用 |
|------|------|------|
| 用户输入后 | `UserPromptSubmit` hooks | 记录、注入、审计用户输入 |
| LLM 前 | cron 队列 | 把定时触发的 prompt 注入 `messages` |
| LLM 前 | background 通知 | 后台任务完成后以「后台任务结果」注入 |
| LLM 前 | compaction pipeline | 先压大输出，再裁历史，再压旧 tool_result，必要时摘要 |
| LLM 前 | system prompt 组装 | 身份 + 工具说明 + skills 目录 + MCP 状态 |
| 工具执行前 | `PreToolUse` hooks + permission | 拦截危险命令、写越界 |
| 工具分发 | 工具池 | 内置工具 + 动态注册的 MCP 工具 |
| 工具执行 | background dispatch | `bash_background` 放入线程池，主循环先返回占位结果 |
| 工具执行后 | `PostToolUse` hooks | 大输出告警、日志等后处理 |
| 返回循环 | tool_result | 每个 `tool_use` 对应一个 `tool_result`，再回到下一轮 |
| 本轮无 tool_use | `Stop` hooks | 统计、清理、审计 |

## 工具总览（29 个）

| 分组 | 数量 | 工具名 |
|---|---|---|
| 基础 | 6 | bash, read_file, write_file, edit_file, list_files, content_search |
| cron | 3 | schedule_cron, list_crons, cancel_cron |
| background | 2 | bash_background, check_background |
| teams | 3 | spawn_teammate, send_message, check_inbox |
| protocol | 3 | request_shutdown, request_plan, review_plan |
| task（任务图） | 5 | create_task, list_tasks, get_task, claim_task, complete_task |
| worktree | 3 | create_worktree, remove_worktree, keep_worktree |
| mock MCP | 5 | connect_mcp, mcp__docs__search, mcp__docs__get_version, mcp__deploy__status, mcp__deploy__run |

## 后台任务路径（相对 module-21 的增量）

```
bash_background(command)
  → BackgroundManager.runInBackground 放进线程池
  → 立即返回占位结果（task_id），主循环不等命令结束
  → 命令结束产生 BackgroundNotification
  → 下一次 LLM 调用前，ZQAgent.chatMessage 调 injectBackgroundNotifications
  → 结果作为「后台任务结果」user 消息追加进 messages
```

实现位置：

- `tool/background/BackgroundManager.java` —— 任务注册、状态查询、通知队列
- `tool/Tools.java` —— `runBashBackground` / `checkBackground` 处理器（静态注入位 `setBackgroundManager`）
- `base/ZQAgent.java` —— `chatMessage` 里的单一注入点（`backgroundManager` 字段）

## 两层计划

- `todo_write`：当前会话内的轻量计划，保存在内存中
- task graph：跨会话、可依赖、可认领的任务文件，写入 `.tasks/task_*.json`

两者目标相近但实现不同：`todo_write` 整表替换当前会话清单；task record 有稳定 ID 和单条生命周期更新。

## 两种 delegation

- `spawn_teammate`：启动 teammate 线程，独立 `messages[]`，通过共享 mailbox 与 lead 通信
- lead 每轮结束 poll 自己的 inbox，按协议消息类型路由

## 配置 & 运行

编辑 `src/main/java/com/hoppinzq/agent/constant/AIConstants.java`，填好 `API_KEY` / `BASE_URL` / `MODEL`。

```bash
# 编译
mvn -pl hoppinzq-module-agent-24-harness -am compile

# 运行
mvn -pl hoppinzq-module-agent-24-harness exec:java -Dexec.mainClass=com.hoppinzq.agent.AgentHarness
```

## 试一下

1. `建一份检查仓库的 todo，然后列出所有 Java 文件`
2. `connect docs MCP server 然后搜索 agent loop`（验 mock MCP：下一轮工具池里出现 `mcp__docs__search`）
3. `创建两个任务，分别 create_worktree 绑定，spawn alice 和 bob 自主干活`
4. `3 分钟后提醒我开会`（cron one-shot）
5. `后台运行 mvn compile，同时继续读 README`（验 background：占位返回 → 完成通知注入）

观察重点：

- 工具调用前后是否经过 hooks / permission
- `connect_mcp` 后下一轮是否出现 MCP 工具
- `bash_background` 是否立即返回 task_id 占位结果，而不是阻塞
- 到点是否自动提醒开会

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
| `capability/skill/` | 技能加载（load_skill 两层注入 + /skills 命令 + 技能描述提示） |
入口类只负责装配：复制能力目录 + 一行注册。

```java
XxxCapability cap = new XxxCapability(...);
agent.capability(cap);
agent.setSystemPrompt(basePrompt + "

" + String.join("
", cap.promptFragments()));
```

> **注意：本模块是累积式综合演示**，特性仍是单体组合装配（`tool/` + `Tools.setXxx` 静态绑定），
> 尚未全量拆分为 `capability/<feature>/`。内核部分已与 06 权威源同步。

### 快速开始

```bash
# 1) 编译（工程根目录）
mvn -pl hoppinzq-module-agent-24-harness compile

# 2) 运行入口类（IDE 里直接运行，或在根目录）
mvn -pl hoppinzq-module-agent-24-harness exec:java -Dexec.mainClass=com.hoppinzq.agent.AgentHarness
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
