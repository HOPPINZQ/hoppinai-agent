# hoppinzq-module-agent-22-multimodal（多模态）

> 说明：下方"历史实现"章节保留了重构前的代码片段（`AnthropicClient`、两参 `setSessionManager`、
> `ToolDefinition` 静态技能字段等），仅作演进对照；当前 API 以本页末尾「能力化架构」与源码为准。


s22 多模态输入示例（最小独立模块）。基于 s01-baseloop 模式。

## 特性

- **MessageContent 多类型消息管道**：text / image / file
- **read_file 自动 base64**：探测 `image/*` MIME 时自动转 base64 image block
- **screenshot 工具**：通过 `Robot.createScreenCapture` 抓屏并返回 PNG base64
- **vision 适配**：根据 `AIConstants.SUPPORTS_VISION` 决定是发送图片 block 还是降级为文本描述

## 运行

```bash
mvn compile
mvn exec:java -Dexec.mainClass="com.hoppinzq.agent.Agent22"
```

## 验证

1. 工作目录放一张 `test.png`
2. 输入 `帮我读取 test.png`
3. 若 `SUPPORTS_VISION=true`：read_file 返回 image block，agent 看到 base64 图片
4. 若 `SUPPORTS_VISION=false`（默认）：降级为文本描述 `[图片: image/png, 286KB]`
5. 输入 `截个屏` → screenshot 工具抓屏并返回 base64 PNG

## 注意

- `Robot.createScreenCapture` 在无头服务器（headless）上会抛 `AWTException`，已 try-catch 返回友好错误。
- 默认 `MODEL=deepseek-chat`，不支持 vision，故 `SUPPORTS_VISION=false`。

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
| `capability/multimodal/` | 多模态输入（图片 / 文件内容块） |
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
mvn -pl hoppinzq-module-agent-22-multimodal compile

# 2) 运行入口类（IDE 里直接运行，或在根目录）
mvn -pl hoppinzq-module-agent-22-multimodal exec:java -Dexec.mainClass=com.hoppinzq.agent.Agent22
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
