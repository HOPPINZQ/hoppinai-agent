# hoppinzq-module-agent-15-sysprompt（动态系统提示）

> 说明：下方"历史实现"章节保留了重构前的代码片段（`AnthropicClient`、两参 `setSessionManager`、
> `ToolDefinition` 静态技能字段等），仅作演进对照；当前 API 以本页末尾「能力化架构」与源码为准。


对应 Python 教程 `s10_system_prompt`。系统提示词不再写死在 `buildSystemPrompt()` 里，
改由 `PromptAssembler` 在每轮 LLM 调用前按 `AgentContext` 动态装配。

## 4 个 section

| section | 触发条件 |
|---|---|
| `identity` | 永远挂 |
| `tools` | 当前 tools 列表非空 |
| `workspace` | 永远挂 |
| `memory` | **仅当 MEMORY.md 存在时挂**（读取索引摘要） |

context hash 相同时走缓存，避免重复渲染。

## 新增文件

- `tool/prompt/AgentContext.java` — workspace / os / tools / memoryEnabled / userPrompt
- `tool/prompt/PromptAssembler.java` — Section 注册表 + `get(ctx)` 带 hash 缓存

## ZQAgent 本地副本修改点

1. **去掉** `setSystemPrompt` 字段 —— 不再一次性赋值
2. 新增 `promptAssembler` 字段
3. 每轮 while 入口：`buildContext(userInput)` 检测 MEMORY.md 是否存在 + 取当前工具名
4. `chatMessage(params, systemPrompt)` 接受动态 system

## 配置 & 运行

```bash
mvn -pl hoppinzq-module-agent-sysprompt -am compile
mvn -pl hoppinzq-module-agent-sysprompt exec:java -Dexec.mainClass=com.hoppinzq.agent.AgentSysPrompt
```

## 场景测试

```text
1. 启动 → 默认无 MEMORY.md → 系统提示只含 identity + tools + workspace
2. 让 agent 写一个 MEMORY.md（write_file 工具）
3. 下一轮 agent 调用 → system 提示自动追加 memory 段（含 MEMORY.md 摘要）
4. 用 bash 删掉 MEMORY.md → 再下一轮 → memory 段消失
```

也可手工创建：

```bash
echo "# Memory Index" > hoppinzq-module-agent-sysprompt/MEMORY.md
```

观察下一轮 agent 的系统提示是否自动挂上 memory 段。

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
| `capability/prompt/` | 动态系统提示装配（identity / tools / workspace / memory 四段，MEMORY.md 实时开关） |
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
mvn -pl hoppinzq-module-agent-15-sysprompt compile

# 2) 运行入口类（IDE 里直接运行，或在根目录）
mvn -pl hoppinzq-module-agent-15-sysprompt exec:java -Dexec.mainClass=com.hoppinzq.agent.AgentSysPrompt
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
