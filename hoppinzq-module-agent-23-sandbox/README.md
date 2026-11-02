# hoppinzq-module-agent-23-sandbox（沙箱）

> 说明：下方"历史实现"章节保留了重构前的代码片段（`AnthropicClient`、两参 `setSessionManager`、
> `ToolDefinition` 静态技能字段等），仅作演进对照；当前 API 以本页末尾「能力化架构」与源码为准。


s23 安全沙箱示例（最小独立模块）。基于 s01-baseloop 模式。

## 四层防护

bash 命令执行时按以下顺序逐层拦截：

| 层级 | 类 | 启用条件 | 作用 |
|------|-----|---------|------|
| L1 | `StaticAnalyzer` | 总是 | 黑名单 regex 拦截高危命令（rm -rf /、mkfs、fork bomb、curl|sh、dd if=、shutdown...） |
| L2 | `DirectoryJail` | 总是 | 工作目录监禁，拒绝 `..` / 绝对路径越界 |
| L3 | `OsSandbox` | 仅 Linux，需 `bwrap` | `bwrap --unshare-all` 包裹执行 |
| L4 | `ContainerIsolation` | 需 docker daemon | `docker run --network=none --read-only` 隔离 |
| 降级 | `SandboxRunner.runWithTimeout` | — | 带超时的 ProcessBuilder |

## 运行

```bash
mvn compile
mvn exec:java -Dexec.mainClass="com.hoppinzq.agent.Agent23"
```

## 验证

1. 让 agent 执行 `rm -rf /tmp/test` → L1 拦截
2. 让 agent 执行 `ls ../../etc` → L2 拦截
3. 让 agent 执行 `echo hello` → 通过 L1+L2，降级到带超时 ProcessBuilder
4. Linux 环境下：L3 bwrap 包装（需 `apt install bubblewrap`）
5. 有 docker 环境下：L4 容器隔离（手动开启 `enableContainer`）

## 配置

`SandboxRunner` 通过 builder 控制层级：

```java
SandboxRunner sandbox = new SandboxRunner.Builder()
        .workDir(Path.of("."))
        .enableOsSandbox(System.getProperty("os.name").toLowerCase().contains("linux"))
        .enableContainer(false)  // 默认关闭，手动开启
        .build();
```

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
| `capability/sandbox/` | 沙箱与路径越界防护 |
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
mvn -pl hoppinzq-module-agent-23-sandbox compile

# 2) 运行入口类（IDE 里直接运行，或在根目录）
mvn -pl hoppinzq-module-agent-23-sandbox exec:java -Dexec.mainClass=com.hoppinzq.agent.Agent23
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
