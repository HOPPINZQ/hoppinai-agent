# hoppinzq-module-agent-worktree（工作区隔离）

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
