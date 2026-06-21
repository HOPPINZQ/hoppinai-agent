# hoppinzq-module-agent-hooks（钩子系统）

对应 Python 教程 `s04_hooks`。把 permission 模块硬编码的检查改成可注册的钩子。

## 4 个事件点

| 事件 | 触发时机 | 非 null 返回值含义 |
|---|---|---|
| `USER_PROMPT_SUBMIT` | 读到用户输入后、LLM 调用前 | 用返回值替换原 prompt |
| `PRE_TOOL_USE` | 工具执行前 | 跳过执行，返回值作为 ToolResult 回灌 |
| `POST_TOOL_USE` | 工具执行后 | 仅副作用（不影响主流程） |
| `STOP` | 本轮无工具调用、循环即将退出时 | 仅副作用 |

## 新增文件

```
tool/hook/
├── HookEvent.java        — 4 事件枚举
├── HookContext.java      — 携带 userInput/toolName/toolInput/toolResult
├── HookCallback.java     — @FunctionalInterface apply(ctx) -> String|null
├── HookRegistry.java     — Map<event, list<callback>>，register + trigger
├── PermissionHook.java   — PreToolUse：拦截 rm -rf /、sudo、format、del C:
├── LogHook.java          — 4 事件全挂：审计日志
├── LargeOutputHook.java  — PostToolUse：>4000 字符警告
└── SummaryHook.java      — PostToolUse+Stop：本轮工具调用统计
```

## ZQAgent 本地副本修改点

1. 新增 `hookRegistry` 字段 + setter
2. 4 个事件点依次调用 `trigger(event, ctx)`：
   - UserPromptSubmit：非 null → 改写 prompt
   - PreToolUse：非 null → `blockedByHook=true`，跳过 `invokeTool`，把返回值作为 ToolResult
   - PostToolUse：仅触发副作用
   - Stop：每轮外层 while 末尾触发

## 配置 & 运行

```bash
mvn -pl hoppinzq-module-agent-hooks -am compile
mvn -pl hoppinzq-module-agent-hooks exec:java -Dexec.mainClass=com.hoppinzq.agent.AgentHooks
```

## 场景测试

| Prompt | 期望 |
|---|---|
| 执行 `rm -rf /tmp/x` | `[hook 阻断]` 由 PermissionHook 触发 |
| 列出当前目录文件 | LogHook 打印 pre/post 审计；SummaryHook 末尾输出本轮次数 |
| 读取一个大文件 | LargeOutputHook 在 >4000 字符时打印警告 |

@hoppinzq
