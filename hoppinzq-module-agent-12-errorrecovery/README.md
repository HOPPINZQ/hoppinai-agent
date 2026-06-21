# hoppinzq-module-agent-errorrecovery（错误恢复）

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
