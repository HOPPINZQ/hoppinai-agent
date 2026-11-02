# hoppinzq-module-agent-12-errorrecovery（错误恢复）

> 说明：下方"历史实现"章节保留了重构前的代码片段（`AnthropicClient`、两参 `setSessionManager`、
> `ToolDefinition` 静态技能字段等），仅作演进对照；当前 API 以本页末尾「能力化架构」与源码为准。


对应 Python 教程 `s11_error_recovery`。包装 LLM 调用，三条恢复路径让 agent 在异常下继续运行。

## 三条恢复路径

| 触发条件 | 恢复动作 |
|---|---|
| `stop_reason == max_tokens`（输出被截断） | 升级 maxTokens 8K → 64K，再让模型续写 |
| `prompt_too_long`（上下文溢出） | 触发 `ReactiveCompactor.compact`：保留首条 user + 最近 6 条，中间摘要替代 |
| 429 / 529 / 网络抖动 | 指数退避 `min(500×2^attempt, 32000) + jitter`；连续 3 次 529 切到 fallback model |

## 新增文件

```
tool/recovery/
├── RecoveryState.java     — attempt / consecutive529 / maxTokens / model / fallbackModel
├── ErrorClassifier.java   — 异常 → PROMPT_TOO_LONG / RATE_LIMIT / OVERLOAD / TRANSIENT / FATAL
├── ReactiveCompactor.java — 紧急压缩 messageParams（首条 + 最近 6 + 摘要）
└── RetryWrapper.java      — call(paramBuilder, state, history)，调度三条路径
```

## ZQAgent 本地副本修改点

1. 新增 `retryWrapper` / `recoveryState` / `fallbackModel` 三字段
2. `chatMessage` 内部判断：有 `retryWrapper` 时走 `RetryWrapper.call(...)`；
   paramBuilder 接收 `RecoveryState`，从中取当前 model / maxTokens
3. 懒初始化 RecoveryState（首次调用时按 model / fallbackModel 构造）

## 配置 & 运行

```bash
mvn -pl hoppinzq-module-agent-errorrecovery -am compile
mvn -pl hoppinzq-module-agent-errorrecovery exec:java -Dexec.mainClass=com.hoppinzq.agent.AgentErrorRecovery
```

## 场景测试

### 1. 触发输出截断
把 `AIConstants.MAX_TOKENS` 临时改到很小（如 64），问一个需要长回复的问题：
```
你: 请详细解释 Java 的 ConcurrentHashMap 实现原理
[recovery] 输出截断 (max_tokens)，当前上限 8000
[recovery] 升级 maxTokens → 64000 并续写
```

### 2. 触发上下文压缩
进行 10+ 轮长对话，下一轮命中 `prompt_too_long`：
```
[recovery] attempt=1, action=PROMPT_TOO_LONG, err=...
[reactive compact] 保留首条 + 最近 6 条，省略 N 条
```

### 3. 触发退避（断网 / 错 BASE_URL）
临时改 `AIConstants.BASE_URL` 到一个无效地址：
```
[recovery] attempt=1, action=TRANSIENT, err=Connection refused
[recovery] 退避 537ms（base=500, jitter=37）
[recovery] attempt=2, action=TRANSIENT, err=...
[recovery] 退避 1024ms（base=1000, jitter=24）
...
```

### 4. 连续 529 切 fallback
设置 `FALLBACK_MODEL` 为非 null 后，模拟连续 529：
```
[recovery] 连续 529，切到 fallback 模型 xxx
```

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
| `capability/recovery/` | 错误分类与重试（上下文溢出触发紧急压缩） |
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
mvn -pl hoppinzq-module-agent-12-errorrecovery compile

# 2) 运行入口类（IDE 里直接运行，或在根目录）
mvn -pl hoppinzq-module-agent-12-errorrecovery exec:java -Dexec.mainClass=com.hoppinzq.agent.AgentErrorRecovery
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
