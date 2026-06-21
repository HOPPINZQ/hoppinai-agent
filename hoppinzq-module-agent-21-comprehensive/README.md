# hoppinzq-module-agent-comprehensive（综合集成）

对应 Python 教程 `s20_comprehensive`。1:1 复刻 Python s20，把前面所有模块的机制塞进同一个 agent loop，演示"机制很多，循环一个"。

## 集成清单

| 子系统 | 来源 | 工具数 |
|---|---|---|
| 基础工具（bash/read_file/write_file/edit_file/list_files/content_search） | module-02 | 6 |
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
| 基础 | bash, read_file, write_file, edit_file, list_files, content_search |
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
| 建一份检查仓库的 todo，然后列出所有 Java 文件 | create_task + list_files 串联 |
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
