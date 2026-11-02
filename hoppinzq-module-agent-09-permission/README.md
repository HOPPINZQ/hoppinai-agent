# hoppinzq-module-agent-09-permission（权限控制）

> 说明：下方"历史实现"章节保留了重构前的代码片段（`AnthropicClient`、两参 `setSessionManager`、
> `ToolDefinition` 静态技能字段等），仅作演进对照；当前 API 以本页末尾「能力化架构」与源码为准。


对应 Python 教程 `s03_permission`。在 module-02 工具循环的基础上，插入**三重权限闸门**，所有工具执行前必须先过闸门。

## 核心机制

```
工具调用请求
   │
   ├─ 闸门 1: denyList（危险命令黑名单）        → 命中 → DENY
   │
   ├─ 闸门 2: permissionRules（路径越界等规则）  → 命中 → DENY
   │
   └─ 闸门 3: askUser（破坏性命令交互式确认）    → y/N → ALLOW/DENY
   │
   全部通过 → 执行工具
```

| 闸门 | 命中示例 | 行为 |
|---|---|---|
| denyList | `rm -rf /`、`sudo xxx`、`format C:`、`del /f /s /q C:\` | 直接拒绝 |
| rules | `write_file` 路径越出 ROOT | 直接拒绝 |
| askUser | `rm xxx`、`del xxx`、`git push --force` | `y/N` 确认 |

## 新增文件

- `tool/permission/Decision.java` — 决策类型 + 拒绝原因
- `tool/permission/PermissionRules.java` — 静态黑名单 + 路径越界规则
- `tool/permission/PermissionChecker.java` — 三闸门流水线

## ZQAgent 本地副本修改点

1. 新增 `permissionChecker` 字段 + setter
2. 工具执行前调用 `checkPermission(toolName, input)`：
   - `DENY` → 构造 `isError=true` 的 ToolResult 把拒绝原因回灌给模型
   - `ASK` 阶段已在 `PermissionChecker.askUser` 内通过 Scanner 完成确认

## 配置 & 运行

编辑 `src/main/java/com/hoppinzq/agent/constant/AIConstants.java`，填好 `API_KEY` / `BASE_URL` / `MODEL`。

```bash
mvn -pl hoppinzq-module-agent-permission -am compile
mvn -pl hoppinzq-module-agent-permission exec:java -Dexec.mainClass=com.hoppinzq.agent.AgentPermission
```

## 场景测试

| Prompt | 期望 |
|---|---|
| 请执行 `rm -rf /tmp/x` | denyList 拦截 |
| 请执行 `sudo apt update` | denyList 拦截 |
| 把内容写到 `../escape.txt` | 路径越界规则拦截 |
| 用 del 删除 old.log | 弹出 `[权限确认]` 提示 |
| 读 README.md | 正常执行 |

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
| `capability/permission/` | 工具调用拦截（beforeToolCall，危险命令防护） |
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
mvn -pl hoppinzq-module-agent-09-permission compile

# 2) 运行入口类（IDE 里直接运行，或在根目录）
mvn -pl hoppinzq-module-agent-09-permission exec:java -Dexec.mainClass=com.hoppinzq.agent.AgentPermission
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
