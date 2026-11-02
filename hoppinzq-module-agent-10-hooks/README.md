# hoppinzq-module-agent-10-hooks（钩子系统）

> 说明：下方"历史实现"章节保留了重构前的代码片段（`AnthropicClient`、两参 `setSessionManager`、
> `ToolDefinition` 静态技能字段等），仅作演进对照；当前 API 以本页末尾「能力化架构」与源码为准。


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
| `capability/skill/` | 技能加载（load_skill 两层注入 + /skills 命令 + 技能描述提示） |
| `capability/hooks/` | 钩子示例（危险命令拦截 / 审计日志 / 长输出告警 / 每轮统计） |
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
mvn -pl hoppinzq-module-agent-10-hooks compile

# 2) 运行入口类（IDE 里直接运行，或在根目录）
mvn -pl hoppinzq-module-agent-10-hooks exec:java -Dexec.mainClass=com.hoppinzq.agent.AgentHooks
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
