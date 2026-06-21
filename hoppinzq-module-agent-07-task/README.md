# Agent07（任务系统智能体）

## 项目简介

Agent07 在 Agent06 的基础上引入了**任务管理系统（Task System）**。通过 DAG（有向无环图）结构的任务图，将任务持久化到磁盘并建立依赖关系，使智能体能够管理复杂的多步骤工作流，识别可并行任务，自动解析依赖。

## 核心特性

- **DAG 任务图**：基于 `blockedBy`/`blocks` 的有向无环依赖图，支持顺序执行与并行执行
- **磁盘持久化**：任务保存到 `.tasks/` 目录，每个任务一个 `task_{id}.json` 文件，压缩和重启后不丢失
- **自动依赖解析**：任务标记为 `completed` 时，自动从其他任务的 `blockedBy` 列表中移除该 ID，解锁后续任务
- **双向依赖维护**：通过 `addBlocks` 添加后置任务时，自动更新被阻塞任务的 `blockedBy` 列表
- **三态状态管理**：pending → in_progress → completed，`getReadyTasks()` 可查询无阻塞的可执行任务
- **14 个工具**：继承 Agent06 的 10 个工具 + 4 个任务管理工具（task_create、task_update、task_list、task_get）
- **三层压缩策略**：继承 Agent06 的 microCompact → autoCompact → 手动 compact
- **待办事项管理**：继承 Agent05 的 TodoManager，3 回合未更新自动催办
- **技能加载系统**：继承 Agent05 的 SkillLoader 两层注入架构

## 实现原理

### 静态字段桥接模式

Agent07 通过 `public static` 字段将依赖注入到 ToolDefinition 的静态工具定义中：

```java
public class Agent07 extends ZQAgent {
    public static TaskManager taskManager;
    public static ContextCompactor compactor;
    public static boolean manualCompactRequested = false;
    public static TodoManager todoManager;
    public static SkillLoader skillLoader;
```

构造函数中通过 `Agent07.taskManager = taskManager` 将实例赋值给静态字段，ToolDefinition 中的 `Agent07.taskManager` 引用即可访问。

### 任务图结构

```
.tasks/
  task_1.json  {"id":1, "status":"completed"}
  task_2.json  {"id":2, "blockedBy":[1], "status":"pending"}
  task_3.json  {"id":3, "blockedBy":[1], "status":"pending"}
  task_4.json  {"id":4, "blockedBy":[2,3], "status":"pending"}

DAG 依赖图:
     task_1 (completed)
        ↓
     task_2 ←→ task_4
     task_3 ←┘
```

### TaskInput 数据类

任务数据存储在 `tool/schema/TaskInput.java`，包含 7 个字段：

```java
public class TaskInput {
    private int id;
    private String subject;
    private String description;
    private String status;         // pending, in_progress, completed
    private List<Integer> blockedBy; // 被哪些任务阻塞
    private List<Integer> blocks;    // 阻塞哪些任务
    private String owner;
}
```

显示格式：`[状态标记] #id: subject (blocked by: [...])`，其中 pending→`[ ]`，in_progress→`[>]`，completed→`[x]`。

### TaskManager 核心方法

TaskManager 位于 `tool/task/TaskManager.java`，核心职责：

- **createTask(subject, description)**：自增 ID 创建任务，默认 `pending` 状态，保存为 `task_{id}.json`
- **updateTask(taskId, status, addBlockedBy, addBlocks)**：更新状态和依赖，完成时调用 `clearDependency()`
- **clearDependency(completedId)**：遍历 `.tasks/` 目录，从所有任务的 `blockedBy` 中移除已完成任务的 ID
- **双向同步**：`addBlocks` 时自动更新被阻塞任务的 `blockedBy`，保证图一致性
- **getReadyTasks()**：查询 `pending` 且 `blockedBy` 为空的任务列表，用于并行执行
- **findMaxId()**：启动时扫描目录获取最大 ID，实现重启后 ID 连续

### 四个任务工具

| 工具名 | 说明 | 必填参数 |
|--------|------|----------|
| task_create | 创建新任务 | subject |
| task_update | 更新状态/依赖 | taskId |
| task_list | 列出所有任务 | 无 |
| task_get | 获取任务详情 | taskId |

### 三层压缩（继承 Agent06）

Agent07 重写了 `chatMessage()` 和 `onToolExecution()`，实现与 Agent06 相同的三层压缩策略：

- **chatMessage()**：microCompact → estimateTokens 检查 → autoCompact → 状态同步
- **onToolExecution()**：Todo 催办 + microCompact + autoCompact 检查 + manualCompactRequested 检查

## 使用方法

### 编译运行

```bash
cd hoppinzq-module-agent-07
mvn clean compile
mvn exec:java -Dexec.mainClass="com.hoppinzq.agent.Agent07"
```

### 交互示例

**创建并行任务：**
```
你: 创建重构任务板：parse -> transform & emit (并行) -> test
AI: 创建 4 个任务并建立依赖关系
    task_create(subject="Parse code")          → id=1
    task_create(subject="Transform code")      → id=2
    task_create(subject="Emit code")           → id=3
    task_create(subject="Test code")           → id=4
    task_update(taskId=2, addBlockedBy=[1])
    task_update(taskId=3, addBlockedBy=[1])
    task_update(taskId=4, addBlockedBy=[2,3])
```

**完成并解锁：**
```
你: 完成任务 1，然后列出所有任务
AI: task_update(taskId=1, status="completed")
    → 自动解除 task_2 和 task_3 的阻塞
    task_list()
    [x] #1: Parse code
    [ ] #2: Transform code    ← 已解锁
    [ ] #3: Emit code         ← 已解锁
    [ ] #4: Test code (blocked by: [2, 3])
```

## 项目结构

```
hoppinzq-module-agent-07/
├── src/main/java/com/hoppinzq/agent/
│   ├── Agent07.java                    # 主智能体，继承 ZQAgent
│   ├── base/
│   │   ├── ZQAgent.java                # 基类
│   │   └── SubAgent.java               # 子智能体工具
│   ├── constant/
│   │   └── AIConstants.java            # 常量配置
│   └── tool/
│       ├── ToolDefinition.java          # 工具定义（14 个静态工具）
│       ├── Tools.java                   # 工具实现
│       ├── compact/
│       │   └── ContextCompactor.java    # 三层上下文压缩
│       ├── manager/
│       │   └── TodoManager.java         # 待办事项管理
│       ├── skill/
│       │   └── SkillLoader.java         # 技能加载
│       ├── task/
│       │   └── TaskManager.java         # DAG 任务管理器
│       ├── schema/
│       │   ├── TaskInput.java           # 任务数据类
│       │   ├── TaskCreateInput.java     # 任务创建输入
│       │   ├── TaskUpdateInput.java     # 任务更新输入
│       │   ├── TaskListInput.java       # 任务列表输入
│       │   ├── TaskGetInput.java        # 任务详情输入
│       │   └── ...                      # 其他 Schema 类
│       └── util/
│           └── FileExclusionHelper.java
├── .tasks/                              # 任务存储（运行时创建）
├── pom.xml
├── README.md
└── s7.md
```

## 技术栈

- **语言**：Java 17
- **构建工具**：Maven
- **AI SDK**：Anthropic Java SDK
- **JSON 处理**：Jackson
- **文件存储**：本地文件系统（`.tasks/` 目录）

## 设计亮点

1. **DAG 依赖图**：通过 `blockedBy`/`blocks` 双向边构建有向无环图，支持顺序和并行任务编排
2. **磁盘持久化**：每个任务独立 JSON 文件，重启和上下文压缩后不丢失
3. **自动依赖解析**：`clearDependency()` 在任务完成时自动解锁后续任务
4. **双向一致性**：`addBlocks` 时自动同步被阻塞任务的 `blockedBy`，无需手动维护
5. **ID 连续性**：`findMaxId()` 扫描已有文件，确保重启后 ID 不冲突
6. **增量式架构**：在 Agent06 基础上仅新增 TaskManager 和 4 个工具，保持向后兼容

## 扩展开发

### 添加新的任务状态

在 `TaskInput` 中扩展 `status` 字段值（如 `cancelled`、`failed`），并在 `TaskManager.isValidStatus()` 中注册：

```java
private boolean isValidStatus(String status) {
    return "pending".equals(status) || "in_progress".equals(status)
        || "completed".equals(status) || "cancelled".equals(status);
}
```

### 添加任务优先级

在 `TaskInput` 中添加 `priority` 字段，修改 `getReadyTasks()` 按优先级排序返回。
