# hoppinzq-module-agent-cron（定时调度）

对应 Python 教程 `s14_cron_scheduler`。在 module-02 工具循环的基础上，加入 5 字段 cron 表达式驱动的定时调度，把"触发"和"执行"解耦。

## 核心机制

```
   ┌──────────────────┐     每秒轮询当前时间
   │  cron 调度线程    │     命中表达式 → cronQueue.add(prompt)
   └──────────────────┘            │ lastFire 防同分钟重复
                                   ▼
                              ┌────────────┐
                              │ cronQueue  │
                              └────────────┘
                                   │
   ┌──────────────────┐  LLM 调用前  │
   │  agent 主循环     │ ◀───────────┘ consumeQueue() 返回 prompt
   └──────────────────┘  非 null 则当 user 消息注入 history
```

| 角色 | 频率 | 职责 |
|---|---|---|
| 调度线程 | 1s | 扫描所有任务，命中 cron + 距上次 ≥ 60s 则入队 |
| 队列处理线程 | 200ms | 预留扩展位（主循环主动 consumeQueue） |
| 主循环 | 每轮 | LLM 调用前 consumeQueue；非空 prompt 作为 user 消息注入 |

## cron 表达式

5 字段：`minute hour day-of-month month day-of-week`

- `*`：任意值
- `*/N`：每 N 单位
- `1,3,5`：列表
- `1-5`：范围
- DOM 与 DOW 任一命中即触发（与 Python s14 行为一致）

示例：

| 表达式 | 含义 |
|---|---|
| `*/2 * * * *` | 每 2 分钟 |
| `30 9 * * *` | 每天 9:30 |
| `0 9 * * 1` | 每周一 9:00 |
| `0 0 1 * *` | 每月 1 号 0:00 |

## 新增文件

- `tool/cron/CronJob.java` — 任务定义（id / cron / prompt / recurring / durable / lastFire / nextFire）
- `tool/cron/CronExpression.java` — 5 字段解析 + matches(epochMillis) + nextFireAfter(from)
- `tool/cron/CronScheduler.java` — 两条 daemon 线程，schedule/cancel/list/consumeQueue API
- `tool/cron/CronStore.java` — `.scheduled_tasks.json` 读写（Jackson）
- `tool/schema/CronScheduleInput.java` / `CronCancelInput.java`

## ZQAgent 本地副本修改点

1. 新增 `cronScheduler` 字段 + setter
2. `run()`：每轮 user input 之前先 `markIdle()` + `consumeQueue()`；非空 prompt 作为 cron 触发的用户消息注入
3. LLM 调用 + 工具执行期间 `markBusy()`，结束 `markIdle()`
4. 防同分钟重复触发由 `CronScheduler.tick()` 内的 `lastFire` 检查完成

## 工具

| 工具 | 用途 |
|---|---|
| schedule_cron(cron, prompt, recurring=true, durable=false) | 注册定时任务 |
| list_crons() | 列出所有任务 |
| cancel_cron(id) | 按 id 取消 |

加上 module-02 的 6 个基础工具（bash / read_file / write_file / edit_file / glob / content_search），共 9 个。

## 配置 & 运行

编辑 `src/main/java/com/hoppinzq/agent/constant/AIConstants.java`，填好 `API_KEY` / `BASE_URL` / `MODEL`。

```bash
mvn -pl hoppinzq-module-agent-cron -am compile -Dmaven.compiler.fork=true
mvn -pl hoppinzq-module-agent-cron exec:java -Dexec.mainClass=com.hoppinzq.agent.AgentCron
```

> `-Dmaven.compiler.fork=true` 规避本机 slf4j jar 锁问题。

## 场景测试

| Prompt | 期望 |
|---|---|
| 每 2 分钟打印当前日期 | schedule_cron('*/2 * * * *', '...')，2 分钟后注入提示 |
| 1 分钟后提醒我检查构建状态（one-shot） | schedule_cron('... * * * *', recurring=false) |
| 列出所有 cron 任务 | list_crons() 返回 JSON |
| 取消任务 abc12345 | cancel_cron('abc12345') |
| 进程退出再重启 → durable=true 的任务是否自动恢复 | 是 |

## 产出物检查

- `<ROOT>/.scheduled_tasks.json` 是否生成（durable=true 的任务）
- 重启后 durable 任务是否恢复并仍按 cron 触发

@hoppinzq
