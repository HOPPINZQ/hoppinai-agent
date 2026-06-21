# hoppinzq-module-agent-protocols（团队协议）

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
