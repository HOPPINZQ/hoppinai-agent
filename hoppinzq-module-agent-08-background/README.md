# Agent08（后台任务智能体）

## 项目简介

Agent08 在 Agent07 的基础上引入了**后台任务执行系统（Background Tasks）**。通过守护线程池异步执行耗时命令（npm install、pytest、docker build 等），让智能体能够并行处理多个任务，不阻塞主对话流程。任务完成后通过通知队列自动注入结果到对话中。

## 核心特性

- **后台任务执行**：守护线程异步执行耗时命令，立即返回 task_id，不阻塞主线程
- **通知队列机制**：任务完成后将结果放入 `ConcurrentLinkedQueue`，每次 LLM 调用前自动排空并注入
- **线程安全**：`ConcurrentHashMap` 存储任务、`ConcurrentLinkedQueue` 通知队列、`AtomicInteger` ID 生成
- **超时控制**：后台任务最多运行 300 秒，超时自动强制终止
- **自动清理**：保留最近 100 个任务，自动移除已完成的旧任务
- **优雅关闭**：`shutdown()` 优雅关闭线程池，等待 5 秒后强制终止
- **16 个工具**：继承 Agent07 的 14 个工具 + 2 个后台任务工具（background_run、check_background）
- **DAG 任务管理**：继承 Agent07 的 TaskManager，持久化 DAG 任务图到 `.tasks/` 目录
- **三层压缩策略**：继承 Agent07 的 microCompact → autoCompact → 手动 compact
- **待办事项管理**：继承 Agent07 的 TodoManager，3 回合未更新自动催办
- **技能加载系统**：继承 Agent07 的 SkillLoader 两层注入架构

## 实现原理

### 静态字段桥接模式

Agent08 通过 `public static` 字段将依赖注入到 ToolDefinition 的静态工具定义中：

```java
public class Agent08 extends ZQAgent {
    public static BackgroundManager backgroundManager;
    public static TaskManager taskManager;
    public static ContextCompactor compactor;
    public static boolean manualCompactRequested = false;
    public static TodoManager todoManager;
    public static SkillLoader skillLoader;
    private int roundsSinceTodo = 0;
    private long lastTodoVersion = 0;
}
```

### BackgroundManager 核心架构

```
Main thread                     Background threads
+-----------------+             +------------------+
| agent loop      |             | task A executes  |
| ...             |             | ...              |
| [LLM call] <---+------------- | enqueue(result)  |
|  ^drain queue   |             +------------------+
|  ↓inject        |             +------------------+
|  notifications  |             | task B executes  |
|                 |             | ...              |
+-----------------+             +------------------+

Timeline:
Agent ----[spawn A]----[spawn B]----[other work]----
             |              |
             v              v
          [A runs]      [B runs]        (parallel)
             |              |
             +-- notification queue --> [results injected before next LLM call]
```

### BackgroundManager 数据结构

```java
public class BackgroundManager {
    private final Map<String, BackgroundTaskInput> tasks;              // ConcurrentHashMap
    private final Queue<BackgroundNotification> notificationQueue;     // ConcurrentLinkedQueue
    private final ExecutorService executor;                            // CachedThreadPool(daemon)
    private final AtomicInteger taskIdCounter;                         // 8位十六进制ID
}
```

### BackgroundTaskInput 数据模型

```java
public class BackgroundTaskInput {
    private String taskId;       // 8位十六进制，如 "00000001"
    private String command;      // 执行的命令
    private String status;       // running / completed / timeout / error
    private String result;       // 命令输出（截断至50000字符）
    private long startTime;      // 任务开始时间
}
```

### BackgroundManager 核心方法

**runInBackground** — 启动后台任务，立即返回：

```java
public String runInBackground(String command) {
    String taskId = generateTaskId();
    BackgroundTaskInput task = new BackgroundTaskInput();
    task.setTaskId(taskId);
    task.setCommand(command);
    task.setStatus("running");
    task.setStartTime(System.currentTimeMillis());
    tasks.put(taskId, task);
    executor.submit(() -> executeTask(task));
    return "后台任务 " + taskId + " 已启动: " + command;
}
```

**executeTask** — 后台线程执行命令：

- 根据 OS 选择 `cmd.exe /c` 或 `bash -c`
- 工作目录设置为 `AIConstants.ROOT`
- 分别捕获 stdout 和 stderr
- `process.waitFor(300, TimeUnit.SECONDS)` 超时控制
- 超时调用 `process.destroyForcibly()`
- 完成后调用 `enqueueNotification(task)` 放入通知队列

**injectBackgroundNotifications** — 静态方法，注入通知到对话：

```java
public static void injectBackgroundNotifications(
        List<MessageParam> messageParams, BackgroundManager backgroundManager) {
    List<BackgroundNotification> notifications = backgroundManager.drainNotifications();
    if (!notifications.isEmpty()) {
        String notifText = notifications.stream()
            .map(n -> String.format("[后台任务:%s] %s: %s",
                    n.getTaskId(), getStatusChinese(n.getStatus()), n.getResult()))
            .collect(Collectors.joining("\n"));
        messageParams.add(MessageParam.builder()
            .role(MessageParam.Role.USER)
            .content("<后台任务结果>\n" + notifText + "\n</后台任务结果>")
            .build());
        messageParams.add(MessageParam.builder()
            .role(MessageParam.Role.ASSISTANT)
            .content("已记录后台任务结果。")
            .build());
    }
}
```

### chatMessage 覆写 — 通知注入 + 三层压缩

```java
@Override
protected Message chatMessage(List<MessageParam> messageParams) {
    injectBackgroundNotifications(messageParams, backgroundManager);
    List<MessageParam> compactedParams = compactor.microCompact(new ArrayList<>(messageParams));
    if (ContextCompactor.estimateTokens(compactedParams) > TOKEN_THRESHOLD) {
        compactedParams = compactor.autoCompact(compactedParams);
        this.messageParams.clear();
        this.messageParams.addAll(compactedParams);
    }
    return super.chatMessage(compactedParams);
}
```

### onToolExecution 覆写 — 待办催办 + 三层压缩

```java
@Override
protected void onToolExecution(List<ContentBlockParam> toolResults) {
    // 待办催办：超过3回合未更新
    if (roundsSinceTodo >= 3) {
        toolResults.add(ContentBlockParam.ofText(TextBlockParam.builder()
            .text("<reminder>您已经 N 个回合没有更新待办事项列表了。</reminder>")
            .build()));
    }
    // Layer 1: 微压缩
    compactor.microCompact(messageParams);
    // Layer 2: 自动压缩
    if (ContextCompactor.estimateTokens(messageParams) > TOKEN_THRESHOLD) { ... }
    // Layer 3: 手动压缩
    if (manualCompactRequested) { ... }
}
```

## 工具清单（16 个）

| 类别 | 工具名 | 说明 |
|------|--------|------|
| 文件操作 | read_file | 读取文件内容 |
| 文件操作 | write_file | 写入文件内容 |
| 文件操作 | edit_file | 编辑文件（字符串替换） |
| 文件操作 | list_files | 列出目录文件 |
| 代码搜索 | content_search | ripgrep 正则搜索 |
| 命令执行 | bash | 阻塞式 Shell 命令（cmd/powershell/bash） |
| 命令执行 | **background_run** | **后台执行命令，立即返回 task_id** |
| 命令执行 | **check_background** | **检查后台任务状态** |
| 任务管理 | task_create | 创建 DAG 任务 |
| 任务管理 | task_update | 更新任务状态和依赖 |
| 任务管理 | task_list | 列出所有任务 |
| 任务管理 | task_get | 获取任务详情 |
| 待办事项 | todo | 管理待办列表 |
| 高级功能 | sub_agent | 委托子智能体 |
| 高级功能 | load_skill | 加载技能 |
| 高级功能 | compact | 手动触发压缩 |

## 使用方法

### 环境要求

- Java 17+
- Maven 3.6+
- Anthropic API Key 或兼容的 API 服务

### 编译运行

```bash
cd hoppinzq-module-agent-08
mvn clean compile
mvn exec:java -Dexec.mainClass="com.hoppinzq.agent.Agent08"
```

### 交互示例

**并行执行后台任务**：

```
你: 在后台运行 "sleep 5 && echo done"，然后创建一个文件
AI: 工具: background_run(command="sleep 5 && echo done")
    结果: 后台任务 00000001 已启动
    工具: write_file(path="test.txt", content="Hello")
    结果: 已写入 5 字节
    [5秒后，下次LLM调用时自动注入]
    [后台任务:00000001] 已完成: done
```

**多个后台任务并行**：

```
你: 启动3个后台任务："sleep 2"、"sleep 4"、"sleep 6"
AI: background_run("sleep 2") → 00000001
    background_run("sleep 4") → 00000002
    background_run("sleep 6") → 00000003
    [2秒后] [后台任务:00000001] 已完成
    [4秒后] [后台任务:00000002] 已完成
    [6秒后] [后台任务:00000003] 已完成
```

## 项目结构

```
src/main/java/com/hoppinzq/agent/
├── Agent08.java                          # 主智能体，继承 ZQAgent
├── base/
│   ├── ZQAgent.java                      # 基类（核心循环）
│   └── SubAgent.java                     # 子智能体（静态工具类）
├── constant/
│   └── AIConstants.java                  # API配置、常量
├── tool/
│   ├── ToolDefinition.java               # 16个工具定义
│   ├── Tools.java                        # 工具实现
│   ├── background/
│   │   └── BackgroundManager.java        # 后台任务管理器
│   ├── compact/
│   │   └── ContextCompactor.java         # 三层压缩器
│   ├── manager/
│   │   └── TodoManager.java              # 待办事项管理器
│   ├── schema/
│   │   ├── BackgroundRunInput.java       # background_run 输入
│   │   ├── BackgroundTaskInput.java      # 后台任务数据模型
│   │   ├── CheckBackgroundInput.java     # check_background 输入
│   │   ├── TaskCreateInput.java          # 任务创建输入
│   │   ├── TaskUpdateInput.java          # 任务更新输入
│   │   ├── TaskListInput.java            # 任务列表输入
│   │   ├── TaskGetInput.java             # 任务详情输入
│   │   ├── CompactInput.java             # 压缩输入
│   │   └── ...                           # 其他工具输入
│   ├── skill/
│   │   └── SkillLoader.java              # 技能加载器
│   ├── task/
│   │   └── TaskManager.java              # DAG 任务管理器
│   └── util/
│       └── FileExclusionHelper.java      # 文件排除助手
```

## 技术栈

| 技术 | 用途 |
|------|------|
| Java 17+ | 运行时环境 |
| Anthropic Java SDK | LLM API 调用 |
| Jackson | JSON 序列化/反序列化 |
| Lombok | 简化 POJO |
| SLF4J | 日志框架 |
| ripgrep | 代码搜索引擎 |
| CachedThreadPool | 后台任务线程池 |

## 设计亮点

- **非阻塞架构**：后台任务通过守护线程执行，主线程立即返回，实现真正的并行
- **通知队列模式**：生产者-消费者模式，后台线程生产通知，主线程消费注入，解耦执行与通知
- **OS 自适应**：自动检测操作系统，Windows 使用 `cmd.exe`，Linux/Mac 使用 `bash`
- **资源保护**：300 秒超时 + 100 任务上限自动清理 + 守护线程随 JVM 退出
- **状态同步机制**：`chatMessage` 和 `onToolExecution` 双重压缩检查，压缩后手动同步 `messageParams` 引用

## 扩展开发

- **自定义超时时间**：修改 `BackgroundManager.executeTask()` 中的 `waitFor(300, TimeUnit.SECONDS)`
- **任务优先级**：将 `CachedThreadPool` 替换为 `PriorityBlockingQueue` 的线程池
- **任务结果持久化**：在 `enqueueNotification` 中将结果写入磁盘
- **进度回调**：在 `executeTask` 中添加进度监控逻辑
