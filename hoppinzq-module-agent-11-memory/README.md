# hoppinzq-module-agent-11-memory（持久记忆）

> 说明：下方"历史实现"章节保留了重构前的代码片段（`AnthropicClient`、两参 `setSessionManager`、
> `ToolDefinition` 静态技能字段等），仅作演进对照；当前 API 以本页末尾「能力化架构」与源码为准。


对应 Python 教程 `s09_memory`。跨会话记忆系统，让 agent 在重启后仍记得上次对话中的事实。

## 核心机制

```
每轮 LLM 调用前               每轮工具循环后
       │                              │
       ▼                              ▼
MemorySelector             MemoryExtractor
读 MEMORY.md 索引           从对话中抽事实
→ 调 LLM 选相关项           → 调 LLM 输出 TYPE|name|tags|body
→ 读 .memory/*.md 正文      → 写入 .memory/{type}_{name}.md
→ 注入 systemPrompt         → 更新 MEMORY.md 索引
                             → 文件数 ≥10 触发 consolidate
```

## 文件格式

`.memory/user_prefers_vim.md`：

```
---
type: user
name: prefers_vim
tags: [editor, vim]
created: 2026-06-19T10:15:30Z
---

用户偏好使用 vim 编辑器。
```

`MEMORY.md`：

```
# Memory Index

- [user] prefers_vim #editor #vim — 用户偏好使用 vim 编辑器
- [feedback] no_emoji #style — 不要在回复中使用 emoji
```

## 四类记忆

| type | 含义 |
|---|---|
| `user` | 用户偏好/身份 |
| `feedback` | 用户纠正过的错误做法 |
| `project` | 项目级约定（目录、技术栈、CI 等） |
| `reference` | 外部链接/路径 |

## 新增文件

- `tool/memory/MemoryRecord.java` — 数据类 + `toIndexLine()`
- `tool/memory/MemoryStore.java` — 读写文件、维护索引、YAML 解析、`askLlm` 公共方法
- `tool/memory/MemorySelector.java` — 每轮选记忆（调 1 次 LLM）
- `tool/memory/MemoryExtractor.java` — 每轮抽记忆（调 1 次 LLM）

## ZQAgent 本地副本修改点

1. 新增 `memoryStore / memorySelector / memoryExtractor` 三字段
2. `chatMessage` 改签为 `chatMessage(params, effectiveSystem)`，每轮允许传不同 system prompt
3. 每轮 while 入口：`selectAndFormat(userInput)` 追加到 systemPrompt 形成 `effectiveSystem`
4. 每轮工具循环退出后：`extractAndSave`，写盘后 `consolidateIfNeeded`

## 配置 & 运行

```bash
mvn -pl hoppinzq-module-agent-memory -am compile
mvn -pl hoppinzq-module-agent-memory exec:java -Dexec.mainClass=com.hoppinzq.agent.AgentMemory
```

## 场景测试

1. 第一次运行：
   - 输入 "我喜欢用 vim 编辑器"
   - 检查 `.memory/user_prefers_vim.md` 与 `MEMORY.md` 是否生成
2. 重启程序后：
   - 输入 "我喜欢用什么编辑器？"
   - 应看到 `[memory] 已注入相关记忆`，agent 从记忆读出 vim 回答
3. 持续交互 10+ 次：
   - 触发 `[memory] 文件数已到 N，建议 consolidate`

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
| `capability/memory/` | 持久记忆（检索后注入提示） |
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
mvn -pl hoppinzq-module-agent-11-memory compile

# 2) 运行入口类（IDE 里直接运行，或在根目录）
mvn -pl hoppinzq-module-agent-11-memory exec:java -Dexec.mainClass=com.hoppinzq.agent.AgentMemory
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
