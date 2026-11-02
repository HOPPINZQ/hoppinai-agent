# hoppinzq-module-agent-21-comprehensive（综合集成）

> 说明：下方"历史实现"章节保留了重构前的代码片段（`AnthropicClient`、两参 `setSessionManager`、
> `ToolDefinition` 静态技能字段等），仅作演进对照；当前 API 以本页末尾「能力化架构」与源码为准。


对应 Python 教程 `s20_comprehensive`。1:1 复刻 Python s20，把前面所有模块的机制塞进同一个 agent loop，演示"机制很多，循环一个"。

## 集成清单

| 子系统 | 来源 | 工具数 |
|---|---|---|
| 基础工具（bash/read_file/write_file/edit_file/glob/content_search） | module-02 | 6 |
| cron 定时调度 | s14 | 3 |
| teams + mailbox | s15 | 3 |
| protocol（shutdown/plan） | s16 | 3 |
| task 任务板 | s17 | 5 |
| worktree 隔离 | s18 | 3 |
| mock MCP（docs/deploy） | s19 风格 | 1 + 4 mcp__* |
| hooks（PreToolUse/PostToolUse/Stop） | Batch 1 | 0（基础设施） |
| permission（三重闸门） | Batch 1 | 0（基础设施） |
| memory（MemoryStore + Selector + Extractor） | Batch 1 | 0（基础设施） |
| sysprompt assembler（PromptAssembler） | Batch 1 | 0（基础设施） |
| skill loading（SkillLoader） | module-07 | 0（基础设施） |
| context compaction（ContextCompactor） | module-07 | 0（基础设施） |
| error recovery（RetryWrapper） | Batch 1 | 0（基础设施） |
| **合计** | | **27** |

## 循环结构

```
   user input
       │
       ▼
   ┌──────────────────────────────────────────┐
   │ 1. compact pipeline (ContextCompactor)    │
   │ 2. assemble system prompt（skill+memory） │
   │ 3. consume cron queue → 注入提醒          │
   │ 4. RetryWrapper.call → LLM 调用          │
   │ 5. PreToolUse hooks                      │
   │ 6. 工具执行（permission 三重闸门）         │
   │ 7. PostToolUse hooks                     │
   │ 8. Stop hooks                            │
   │ 9. memory extractor                      │
   │ 10. pollInbox（teammate 消息）            │
   └──────────────────────────────────────────┘
       │
       ▼
   下一轮
```

## mock MCP 服务器

| Server | 工具 | 权限标注 |
|---|---|---|
| docs | search(query) / get_version() | readOnly |
| deploy | status() / run(env) | readOnly / destructive |

工具名含前缀 `mcp__{server}__{tool}`，名称归一化（下划线 → 连字符，小写化），避免与内置工具冲突。

连接方式：`connect_mcp("docs")` / `connect_mcp("deploy")`，可同时连接多个。

## 工具总览

| 分组 | 工具名 |
|---|---|
| 基础 | bash, read_file, write_file, edit_file, glob, content_search |
| cron | schedule_cron, list_crons, cancel_cron |
| teams | spawn_teammate, send_message, check_inbox |
| protocol | request_shutdown, request_plan, review_plan |
| task | create_task, list_tasks, get_task, claim_task, complete_task |
| worktree | create_worktree, remove_worktree, keep_worktree |
| mock MCP | connect_mcp, mcp__docs__search, mcp__docs__get_version, mcp__deploy__status, mcp__deploy__run |

## 配置 & 运行

编辑 `src/main/java/com/hoppinzq/agent/constant/AIConstants.java`，填好 `API_KEY` / `BASE_URL` / `MODEL`。

```bash
# 编译
mvn -pl hoppinzq-module-agent-comprehensive -am compile -Dmaven.compiler.fork=true

# 运行
mvn -pl hoppinzq-module-agent-comprehensive exec:java -Dexec.mainClass=com.hoppinzq.agent.AgentComprehensive
```

> `-Dmaven.compiler.fork=true` 规避本机 slf4j jar 锁问题。

## 场景测试

| Prompt | 期望 |
|---|---|
| 建一份检查仓库的 todo，然后列出所有 Java 文件 | create_task + glob 串联 |
| connect docs MCP server 然后搜索 agent loop | connect_mcp("docs") → mcp__docs__search("agent loop") 返回 mock 结果 |
| 创建两个任务，分别 create_worktree 绑定，spawn alice 和 bob 自主干活 | task + worktree + teammate 全链路 |
| 3 分钟后提醒我开会（one-shot cron） | schedule_cron 一次性定时 |
| 后台运行 mvn compile，同时继续读 README | spawn_teammate 后台执行编译 |

## s19_mcp_plugin vs Java 13-mcp 对比

| 维度 | Python s19_mcp_plugin | Java 13-mcp |
|---|---|---|
| 角色 | 纯客户端（mock） | 纯客户端（真实 SDK） |
| 传输 | mock 子进程（实际不调用） | STDIO + SSE + Streamable HTTP |
| MCP SDK | 无（手写 mock） | `io.modelcontextprotocol.sdk:mcp:0.10.0` |
| 工具发现 | `MCPClient.register()` 硬编码 | `listTools()` 真实 JSON-RPC |
| 工具执行 | `MCPClient.call_tool()` mock | `callTool()` 真实 |
| Resources | 否 | 是（listResources/readResource） |
| Prompts | 否 | 是（listPrompts） |
| Sampling/Logging | 否 | capability 声明（未完整实现） |
| 同步/异步 | 仅同步 | 同步 + 异步双客户端 |
| 工具名前缀 | `mcp__{server}__{tool}` + 名称归一化 | 直接用原名（潜在冲突） |
| 配置来源 | 代码内 `MOCK_SERVERS` 字典 | 外部 `resources/mcp.json`（自动检测传输类型） |
| 环境变量注入 | 否 | 是（STDIO 模式 `env` 字段） |
| 自定义 headers | 否 | 是（HTTP/SSE） |
| 动态工具池 | `connect_mcp` 后重建 | 启动时一次性加载 |
| 权限标注 | `(readOnly)`/`(destructive)` 在 description 里 | 否 |

**结论**：
- Java 13-mcp 在**协议完整度、传输广度、生产可用性**上完胜（真实 SDK + 三种传输 + 配置外置）
- Python s19 在**教学清晰度**上更优：mock 让概念聚焦，`mcp__{server}__{tool}` 前缀避免命名冲突是值得 Java 端借鉴的安全实践
- Java 13-mcp 可改进点：① 加 `mcp__{server}__{tool}` 前缀 + 名称归一化 ② 支持运行时 `connect_mcp` 动态发现新工具 ③ description 加权限标注

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
mvn -pl hoppinzq-module-agent-21-comprehensive compile

# 2) 运行入口类（IDE 里直接运行，或在根目录）
mvn -pl hoppinzq-module-agent-21-comprehensive exec:java -Dexec.mainClass=com.hoppinzq.agent.AgentComprehensive
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
