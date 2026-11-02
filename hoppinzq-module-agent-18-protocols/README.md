# hoppinzq-module-agent-18-protocols（团队协议状态机）

> 说明：下方"历史实现"章节保留了重构前的代码片段（`AnthropicClient`、两参 `setSessionManager`、
> `ToolDefinition` 静态技能字段等），仅作演进对照；当前 API 以本页末尾「能力化架构」与源码为准。


对应 Python 教程 `s16_team_protocols`。在 teams 模块的基础上，加入 request/response 协议状态机：shutdown 和 plan_approval 两种正式协议，让 lead 能优雅地关停 teammate、审批 teammate 提交的方案。

## 核心机制

```
   lead ──request_shutdown(alice)─────────> "shutdown_request" ──> alice 邮箱
                                                                      │
                                                                      ▼
                                                 alice 回合收到 shutdown_request
                                                         │
                                                         ├── 回 "shutdown_response"
                                                         └── 线程结束

   alice ──send_message(type=plan_approval_request)──> "plan_approval_request" ──> lead 邮箱
                                                                                      │
                                                                                      ▼
                           lead pollInbox → ProtocolDispatcher.handle()
                                  │
                                  ├── 打印 plan 内容，提示用户审批
                                  └── review_plan(requestId, approved=true/false)
                                              │
                                              ▼
                          "plan_approval_response" ──> alice 邮箱
```

两种协议：

| 协议 | 发起方 | Request | Response | 效果 |
|---|---|---|---|---|
| shutdown | lead | shutdown_request | shutdown_response | teammate 退出 |
| plan_approval | teammate | plan_approval_request | plan_approval_response | lead 审批后 teammate 继续 |

## 新增文件

- `tool/protocol/ProtocolState.java` — 协议状态 POJO（requestId / type / sender / target / status / payload / createdAt）
- `tool/protocol/ProtocolRegistry.java` — 协议注册表（newRequestId / put / getPending / matchResponse）
- `tool/protocol/ProtocolDispatcher.java` — 按消息 type 分发（shutdown / plan_approval / message），返回 DispatchOutcome
- `tool/protocol/DispatchOutcome.java` — 分发结果（continue / shutdown / plan_review_needed）
- `tool/schema/ShutdownRequestInput.java` / `RequestPlanInput.java` / `ReviewPlanInput.java`

## 修改点

- `tool/Tools.java`：新增 `LEAD_REGISTRY` 静态字段 + setter；新增 `requestShutdown` / `requestPlan` / `reviewPlan` 方法
- `tool/ToolDefinition.java`：新增 `RequestShutdownDefinition` / `RequestPlanDefinition` / `ReviewPlanDefinition`
- `base/ZQAgent.java`：新增 `protocolRegistry` 字段；`pollInbox()` 经 `ProtocolDispatcher.handle()` 分发
- `tool/bus/TeammateRunner.java`：idle 循环（等待 inbox 来消息或 shutdown），不再用 10 轮硬上限

## 工具

| 工具 | 用途 |
|---|---|
| request_shutdown(teammate) | 向 teammate 发 shutdown_request |
| request_plan(teammate, plan?) | 请求 teammate 提交方案 |
| review_plan(requestId, approved, comment?) | 审批 teammate 方案（通过/驳回） |

加上 teams 的 6 个（spawn_teammate / send_message / check_inbox）+ cron 的 3 个 + 基础工具的 6 个，共 18 个。

## 配置 & 运行

编辑 `src/main/java/com/hoppinzq/agent/constant/AIConstants.java`，填好 `API_KEY` / `BASE_URL` / `MODEL`。

```bash
mvn -pl hoppinzq-module-agent-protocols -am compile -Dmaven.compiler.fork=true
mvn -pl hoppinzq-module-agent-protocols exec:java -Dexec.mainClass=com.hoppinzq.agent.AgentProtocols
```

> `-Dmaven.compiler.fork=true` 规避本机 slf4j jar 锁问题。

## 场景测试

| Prompt | 期望 |
|---|---|
| Spawn alice，让她做事，然后 request_shutdown | alice 干活 → 收到 shutdown → 回 response → 线程退出 |
| Spawn bob 做重构，让他先提交 plan，然后 review approve | bob 发 plan_approval_request → lead 看到后 review_plan(approved=true) → bob 收到通过后继续 |
| review_plan 驳回 | approved=false → bob 收到驳回，可选择修正后重发或退出 |

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
| `capability/cron/` | 定时调度（命中后由能力在宿主上下文中执行任务） |
| `capability/teams/` | 团队协作（spawn_teammate / send_message / check_inbox + 邮箱轮询注入） |
| `capability/protocol/` | 团队协议状态机（shutdown / plan approval，带 requestId 回执） |
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
mvn -pl hoppinzq-module-agent-18-protocols compile

# 2) 运行入口类（IDE 里直接运行，或在根目录）
mvn -pl hoppinzq-module-agent-18-protocols exec:java -Dexec.mainClass=com.hoppinzq.agent.AgentProtocols
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
