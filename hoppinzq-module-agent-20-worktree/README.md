# hoppinzq-module-agent-20-worktree（Worktree 隔离）

> 说明：下方"历史实现"章节保留了重构前的代码片段（`AnthropicClient`、两参 `setSessionManager`、
> `ToolDefinition` 静态技能字段等），仅作演进对照；当前 API 以本页末尾「能力化架构」与源码为准。


对应 Python 教程 `s18_worktree_isolation`。在 autonomous 模块的基础上，加入 git worktree 隔离机制：每个 task 可绑定一个 git worktree，teammate 认领 task 时自动切换到 worktree 目录执行文件/bashi 操作，互不干扰。

## 核心机制

```
   lead: create_task("重构用户模块") ──> task 1
          create_worktree("refactor-user", taskId=1)
                 │
                 │ git worktree add .worktrees/refactor-user wt/refactor-user
                 ▼
          ┌─────────────────────────────────────┐
          │  .worktrees/refactor-user/           │
          │  （完整 git 工作区副本）              │
          └─────────────────────────────────────┘
                 │
   alice 自主认领 task 1
                 │
                 │ WorktreeContext.set("refactor-user")
                 ▼
          bash / read_file / write_file / edit_file
          所有操作自动定位到 worktree 目录
```

路径解析链：`Tool → WorktreeContext.effectiveRoot() → ROOT/.worktrees/{name}`

| 组件 | 职责 |
|---|---|
| WorktreeManager | create / remove / keep / list worktree，底层 ProcessBuilder 跑 git 命令 |
| WorktreeContext | ThreadLocal 持当前 teammate 的 worktree 名称 |
| GitRunner | git 子进程包装，处理 Windows/Linux 路径差异 |

## 新增文件

- `tool/worktree/WorktreeManager.java` — create(name, taskId) / bindTask(taskId, name) / remove(name, discard) / keep(name)
- `tool/worktree/WorktreeContext.java` — ThreadLocal cwd + effectiveRoot() 静态方法
- `tool/worktree/GitRunner.java` — 包装 `git worktree add/remove` + `git branch -D`
- `tool/schema/CreateWorktreeInput.java` / `RemoveWorktreeInput.java` / `KeepWorktreeInput.java`

## 修改点

- `tool/Tools.java`：所有文件/bashi 操作在解析路径时先调用 `WorktreeContext.effectiveRoot()` 替代 `ROOT`
- `tool/ToolDefinition.java`：新增 `CreateWorktreeDefinition` / `RemoveWorktreeDefinition` / `KeepWorktreeDefinition`
- `tool/task/TaskManager.java`：新增 `setWorktree` / `getWorktree` 方法（ConcurrentHashMap 内存存储）
- `tool/autonomous/AutoClaimer.java`：认领后读取 `task.worktree`，设置 `WorktreeContext`

## 工具

| 工具 | 用途 |
|---|---|
| create_worktree(name, taskId?) | 创建 git worktree（分支 wt/name → .worktrees/name） |
| remove_worktree(name, discard=true) | 移除 worktree（discard=true 同时删分支） |
| keep_worktree(name) | 标记 worktree 为 keep（不自动清理） |

加上 autonomous 的 5 个 + protocols 的 3 个 + teams 的 3 个 + cron 的 3 个 + 基础 6 个，共 23 个。

## 配置 & 运行

编辑 `src/main/java/com/hoppinzq/agent/constant/AIConstants.java`，填好 `API_KEY` / `BASE_URL` / `MODEL`。

```bash
mvn -pl hoppinzq-module-agent-worktree -am compile -Dmaven.compiler.fork=true
mvn -pl hoppinzq-module-agent-worktree exec:java -Dexec.mainClass=com.hoppinzq.agent.AgentWorktree
```

> `-Dmaven.compiler.fork=true` 规避本机 slf4j jar 锁问题。必须在 git 仓库内运行。

## 场景测试

| Prompt | 期望 |
|---|---|
| 创建 2 个 task，分别 create_worktree 绑定，spawn alice 和 bob，观察他们在隔离目录里干活 | alice 和 bob 各自在 .worktrees/ 下操作，互不干扰 |
| keep_worktree(name) 标记保留 | 任务完成后 worktree 不自动清理 |
| remove_worktree(name, discard=false) | 仅从 git worktree 索引移除，目录和分支保留 |

## 产出物检查

- `.worktrees/{name}/` 目录是否生成
- `git worktree list` 是否可见
- alice 创建的文件在 `alice-*` worktree 下，不影响主仓库
- 进程退出后 `.worktrees/` 目录仍保留（除非显式 remove）

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
| `capability/task/` | 任务板（create/list/get/claim/complete） |
| `capability/cron/` | 定时调度（命中后由能力在宿主上下文中执行任务） |
| `capability/teams/` | 团队协作（spawn_teammate / send_message / check_inbox + 邮箱轮询注入） |
| `capability/protocol/` | 团队协议状态机（shutdown / plan approval，带 requestId 回执） |
| `capability/autonomous/` | teammate 空闲自动轮询任务板并认领 |
| `capability/worktree/` | git worktree 隔离（create/keep/remove，可绑定任务） |
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
mvn -pl hoppinzq-module-agent-20-worktree compile

# 2) 运行入口类（IDE 里直接运行，或在根目录）
mvn -pl hoppinzq-module-agent-20-worktree exec:java -Dexec.mainClass=com.hoppinzq.agent.AgentWorktree
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
