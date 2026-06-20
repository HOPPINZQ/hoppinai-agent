# hoppinzq-module-agent-teams（agent 团队）

对应 Python 教程 `s15_agent_teams`。在 cron 模块的基础上（保留 cron 工具链，保持自包含），
新增 **lead / teammate 多 agent 协作**：lead 可以 spawn 一批 teammate，每个 teammate 在自己的守护线程里跑独立的 agent 循环，
彼此通过文件邮箱（`<ROOT>/.mailboxes/*.jsonl`）异步通信。

## 核心机制

```
                          ┌─────────────────────────────────────┐
   user ──> lead agent    │  spawn_teammate(alice, role, prompt)│
            (主线程)      └───────────────┬─────────────────────┘
                                            │ daemon thread
                                            ▼
                          ┌─────────────────────────────────────┐
                          │ teammate "alice" (独立 messageParams)│
                          │  工具子集: bash/read/write/send_msg  │
                          └───────────────┬─────────────────────┘
                                            │ send_message(to=lead)
                                            ▼
   <ROOT>/.mailboxes/lead.jsonl  <──append──  teammate
                  │
                  │ readInbox (读即消费，清空文件)
                  ▼
   lead 下一轮主循环 pollInbox() 自动注入成 user 消息
```

| 邮箱 | 文件 | 语义 |
|---|---|---|
| lead | `<ROOT>/.mailboxes/lead.jsonl` | 所有 teammate 的回执都追加到这里 |
| alice | `<ROOT>/.mailboxes/alice.jsonl` | lead 主动发给 alice 的消息 |
| 读即消费 | `readInbox(name)` 一次读完然后清空文件 | 同一条消息不会被消费两次 |

## 新增文件

- `tool/bus/MailboxMessage.java` — 邮箱消息 POJO（from / to / content / type / ts）
- `tool/bus/MessageBus.java` — 基于文件的消息总线，静态 `send` / `readInbox` / `inboxPath`
- `tool/bus/TeammateRunner.java` — teammate 自己的 agent 主循环（10 轮硬上限，独立 messageParams）
- `tool/bus/TeammateSpawner.java` — 守护线程工厂，spawn 完立即给 lead 发一条 `started`
- `tool/schema/SpawnTeammateInput.java` — spawn_teammate 入参
- `tool/schema/SendMessageInput.java` — send_message 入参（from 由 ThreadLocal 决定）
- `AgentTeams.java` — 模块入口

## 修改文件

- `base/ZQAgent.java`（本地副本）：新增 `messageBus / teammateClient / teammateTools / leadName` 字段；
  每轮主循环结束后调用 `pollInbox()`，把 teammate 的消息注入下一轮对话
- `tool/ToolDefinition.java`（本地副本）：新增 `SpawnTeammateDefinition / SendMessageDefinition / CheckInboxDefinition`
- `tool/Tools.java`（本地副本）：新增 `LEAD_CLIENT / LEAD_MODEL / LEAD_BUS / LEAD_TEAMMATE_TOOLS` 静态字段 + setter；
  `ThreadLocal<String> CURRENT_TEAMMATE_NAME` 区分 send_message 的发送者；
  新增 `spawnTeammate / sendMessage / checkInbox` 三个方法
- `constant/AIConstants.java`（本地副本）：`MODULE_NAME` 改为 `hoppinzq-module-agent-teams`

## 工具

| 工具 | 入参 | 说明 |
|---|---|---|
| spawn_teammate | name, role, prompt | 启动 teammate 守护线程，立即返回 |
| send_message | to, content | 发消息到某人邮箱；from 由 ThreadLocal 决定（lead 主线程=lead，teammate 线程=其名字） |
| check_inbox | （无） | 读取并清空 lead 邮箱，返回未读消息 pretty JSON |

teammate 工具子集（4 个）：bash / read_file / write_file / send_message。

## 配置 & 运行

编辑 `src/main/java/com/hoppinzq/agent/constant/AIConstants.java`，填好 `API_KEY` / `BASE_URL` / `MODEL`。

```bash
mvn -pl hoppinzq-module-agent-teams -am compile
mvn -pl hoppinzq-module-agent-teams exec:java -Dexec.mainClass=com.hoppinzq.agent.AgentTeams
```

## 场景测试

| Prompt | 期望 |
|---|---|
| Spawn alice 作为后端开发，让她创建 schema.sql | alice 在守护线程里跑，给 lead 发 `started`，干完后再发一条 `alice finished: ...` |
| 检查 inbox 看 alice 的结果 | check_inbox 返回 alice 的回执；或下一轮主循环自动 pollInbox 注入 |
| 让 alice 用 send_message 把建表 SQL 直接发给我 | alice 调 send_message(to=lead)，lead 邮箱收到 SQL |
| 同时 spawn alice 和 bob | 两条守护线程并行，各自有自己的 messageParams 与邮箱 |

注意：alice 跑在自己的 daemon 线程里，与 lead 完全隔离，唯一的通信通道是
`<ROOT>/.mailboxes/{name}.jsonl`。teammate 最多跑 10 轮 LLM 调用就会自动收尾，
避免 runaway。

@hoppinzq
