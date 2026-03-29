# sweb: The Web Application (Web应用智能体)

`s01 > s02 > s03 > s04 > s05 > s06 > s07 > s08 > s09 > s10 > s11 > s12 > [ sweb ]`

> *"A standalone agent that serves itself"* -- 一个独立自举的 Web 智能体。

## 问题

前 14 个模块的智能体都是 CLI 应用——用户在终端输入、终端输出。当需要 Web 前端接入时，CLI 架构完全无法满足需求。更关键的是，随着工具数量从 1 个增长到 16 个，会话管理、上下文压缩、后台任务、待办事项、子代理委派等需求接踵而至，这些都需要 WebZQAgent 拥有独立的实现能力，而非继续依赖 ZQAgent 基类。

## 解决方案

```
                        用户请求
                     ┌────┴────┐
                     ▼         ▼
              /agent/runTask  /agent/streamChat
              (同步, 完整)    (SSE, 实时流)
                     │         │
                     └────┬────┘
                          ▼
                   AgentChatController
                   ┌──────────────┐
                   │  WebZQAgent   │  ← 完全独立, 不继承 ZQAgent
                   │  (独立智能体)  │
                   └──────┬───────┘
                          │
            ┌─────────────┼─────────────┐
            │             │             │
            ▼             ▼             ▼
     BackgroundManager  TaskManager  ContextCompactor
     (异步+通知队列)   (文件+依赖图)  (三层压缩)
            │             │             │
            ▼             ▼             ▼
     TodoManager     .tasks/        .transcripts/
     (20项上限)    task_{id}.json  transcript_{ts}.jsonl
            │
            ▼
     SkillLoader
     (文件系统/JAR)
            │
            ▼
     16 个 ToolDefinition ──► Tools.java (实现)
            │
            ▼
     REACT_ENABLE
     ├─ true:  正则解析 Thought/Action/Observation
     └─ false: SDK 解析 tool_use block
            │
            ▼
     ┌──────┴──────┐
     ▼             ▼
  MySQL (持久化)  SSE (JSON事件流)
```

WebZQAgent 是项目中唯一一个**完全独立**的智能体——不继承 ZQAgent、不组合 ZQAgent，拥有自己的消息循环、工具调用链、上下文压缩策略和会话管理。它集成了 16 个工具、5 个管理器、ReAct/标准双模式，通过 Spring Boot 暴露为 Web 服务。

## 工作原理

### 1. 独立智能体 -- WebZQAgent

WebZQAgent 不依赖 ZQAgent，拥有完整的消息循环和工具调用机制：

```java
public class WebZQAgent {
    private final AnthropicClient client;
    private final String model;
    private final List<ToolDefinition> tools = new ArrayList<>();
    private final ConcurrentHashMap<String, List<MessageParam>> sessionMessages = new ConcurrentHashMap<>();

    private static BackgroundManager backgroundManager;
    private static TaskManager taskManager;
    private static ContextCompactor contextCompactor;
    private static TodoManager todoManager;
    private static SkillLoader skillLoader;

    public String runTask(String sessionId, String task) {
        List<MessageParam> messages = sessionMessages.computeIfAbsent(sessionId, k -> new ArrayList<>());
        // 添加用户消息
        // 循环: chatMessage → 解析 → invokeTool → 压缩 → 继续
    }
}
```

**代码解释**:
- `sessionMessages`: ConcurrentHashMap 实现多会话隔离，每个 sessionId 对应独立的消息历史
- 5 个 static 管理器: 在 AgentChatController 构造时初始化，WebZQAgent 共享使用
- `runTask()`: 同步执行完整循环，适用于单次请求场景
- `chatMessage()`: 根据 REACT_ENABLE 决定是否发送 tools 给 API

### 2. 双模式系统提示词 -- buildSystemPrompt()

AgentChatController 构建两种完全不同的系统提示词：

```java
private String buildSystemPrompt() {
    if (REACT_ENABLE) {
        // ReAct 模式: 强制 Thought/Action/Action Input/Observation 格式
        // 包含所有工具的名称、描述、参数 Schema
        // 追加技能描述列表
        // 追加工作环境信息
    } else {
        // 标准模式: 结构化 Markdown
        // 能力分类 (文件操作/命令执行/任务管理/后台任务/技能系统)
        // 工作指南 (步骤拆解/确认/持久化)
        // 最佳实践 (错误处理/进度汇报/技能使用)
        // 技能描述列表
    }
}
```

**代码解释**:
- ReAct 模式提示词以工具 Schema 为主，LLM 据此构造 Action Input JSON
- 标准模式提示词以 Markdown 格式组织，包含能力分类和工作指南
- 两种模式都包含技能描述，由 `skillLoader.getDescriptions()` 动态生成

### 3. SSE 流式处理 -- processAgentStream()

核心递归流处理方法，同时处理标准 tool_use 和 ReAct 两种模式：

```java
private Flux<String> processAgentStream(String sessionId, List<MessageParam> messages) {
    return Flux.create(sink -> {
        // 注入后台通知
        injectBackgroundNotifications(messages, sink);

        client.messages().stream(messageBuilder.build())
            .subscribe(event -> {
                switch (event.type()) {
                    case CONTENT_BLOCK_START:
                        if (event.contentBlockStart().contentBlock().isToolUse()) {
                            isToolUse = true;
                            // 记录工具名和 tool_id
                        }
                        break;

                    case CONTENT_BLOCK_DELTA:
                        if (isToolUse) {
                            inputJsonBuilder.append(delta.partialJson());
                        } else {
                            // 累积文本
                            fullTextResponse.append(delta.text());
                            sink.next(toJson("content_block_delta", delta.text()));
                        }
                        break;

                    case CONTENT_BLOCK_STOP:
                        if (isToolUse) {
                            String result = invokeTool(toolName, inputJsonBuilder.toString());
                            sink.next(toJson("tool_status", "success", toolName, ...));
                            // 构建助手+工具结果消息, 递归调用
                        }
                        break;

                    case MESSAGE_STOP:
                        // ReAct 模式: 检查 "Action:" 文本
                        if (REACT_ENABLE && fullTextResponse.toString().contains("Action:")) {
                            // 正则解析 Action + Action Input
                            String result = invokeTool(toolName, inputJson);
                            sink.next(toJson("tool_status", "success", toolName, ...));
                            // 注入 Observation, 递归调用
                        } else {
                            sink.next("end");
                        }
                        break;
                }
            });
    });
}
```

**代码解释**:
- `contentBlockStart` → 检测 toolUse 标记，区分文本块和工具调用块
- `contentBlockDelta` → 文本块累积文本并发送 SSE 事件，工具调用块累积 inputJson
- `contentBlockStop` → 标准模式下执行工具并递归
- `messageStop` → ReAct 模式下检查 Action 文本并执行，无 Action 则结束
- 两种模式的工具结果都通过 `tool_status` JSON 事件推送
- `injectBackgroundNotifications()` 在每次 LLM 调用前排空通知队列

### 4. 三层上下文压缩 -- ContextCompactor

```java
public class ContextCompactor {
    // Layer 1: 微压缩 -- 每次工具调用后
    private void microCompact(List<MessageParam> messages) {
        int toolResultCount = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            // 从后往前计数工具结果
            if (toolResultCount >= KEEP_RECENT) {
                // 替换为 "[已执行: toolName]"
                messages.set(i, replacedMessage);
            }
        }
    }

    // Layer 2: 自动压缩 -- token 超过阈值
    private void autoCompact(List<MessageParam> messages, String sessionId) {
        if (estimateTokens(messages) > TOKEN_THRESHOLD) {
            String transcriptPath = saveTranscript(messages); // JSONL 归档
            String summary = generateSummary(messages);       // LLM 总结
            // 用摘要替换原始消息
        }
    }

    // Layer 3: 手动压缩 -- compact 工具触发
    public void requestCompact() {
        manualCompactRequested = true;
    }

    // LLM 总结生成
    private String generateSummary(List<MessageParam> messages) {
        String serialized = serializeMessages(messages, 80000);
        // 构建中文提示词: "请总结这段对话以保持上下文连贯性"
        // 调用 LLM, maxTokens=2000
    }
}
```

**代码解释**:
- Layer 1 `microCompact`: 保留最近 10 个工具结果，更早的替换为摘要文本
- Layer 2 `autoCompact`: 序列化消息检查 token 估算值，超阈值时保存归档并调用 LLM 生成摘要
- Layer 3 `requestCompact`: 设置标志位，在 WebZQAgent 的循环中触发 Layer 2
- `saveTranscript`: 写入 `{ROOT}/.transcripts/{sessionId}/transcript_{timestamp}.jsonl`
- `generateSummary`: 80000 字符序列化上限，2000 token 摘要

### 5. 后台任务通知注入 -- BackgroundManager

```java
public class BackgroundManager {
    private final ExecutorService executor;
    private final ConcurrentHashMap<String, BackgroundTask> tasks;
    private final ConcurrentLinkedQueue<BackgroundNotification> notificationQueue;

    public void runInBackground(String command) {
        String taskId = generateTaskId(); // 8位十六进制原子计数器
        executor.submit(() -> {
            // 执行命令, 记录状态和结果
            notificationQueue.offer(new BackgroundNotification(taskId, status, command, result));
        });
    }

    // WebZQAgent 在每次 LLM 调用前排空通知
    public void injectBackgroundNotifications(List<MessageParam> messages, FluxSink<String> sink) {
        BackgroundNotification notification;
        while ((notification = notificationQueue.poll()) != null) {
            // 注入为用户消息 + 助手消息对
            messages.add(userMessage("后台任务通知: " + notification));
            messages.add(assistantMessage("收到, 已记录后台任务状态更新。"));
            sink.next(toJson("tool_status", notification));
        }
    }
}
```

**代码解释**:
- `generateTaskId`: `String.format("%08x", counter.incrementAndGet() & 0xFFFFFFFFL)` 生成 8 位十六进制 ID
- `notificationQueue`: ConcurrentLinkedQueue 线程安全队列
- `injectBackgroundNotifications`: 在每次 LLM 调用前调用，排空队列并注入为消息对
- `cleanupOldTasks`: 超过 100 条时清理非运行状态的任务
- Agent 无需轮询，通知自然出现在对话流中

### 6. 文件任务管理 -- TaskManager

```java
public class TaskManager {
    private final File tasksDir; // {ROOT}/.tasks/{sessionId}/
    private int nextTaskId = 1;

    public String createTask(String sessionId, String title, String description,
                             List<String> blockedBy) {
        String taskId = "task_" + (nextTaskId++);
        Task task = new Task(taskId, title, description, "pending", blockedBy);
        // 保存为 task_{id}.json
        // 更新依赖图 (blockedBy → blocks 反向映射)
        return taskId;
    }

    public void updateTask(String sessionId, String taskId, String status) {
        // 更新状态: pending → in_progress → completed
        // 完成时 clearDependency: 递归清理依赖链
    }
}
```

**代码解释**:
- 文件存储: `{ROOT}/.tasks/{sessionId}/task_{id}.json`
- 依赖图: `blockedBy`（前置依赖）和 `blocks`（后续阻塞）双向映射
- `clearDependency`: 任务完成时递归清理依赖链，解除被阻塞任务
- 自动清理: 删除会话时清理对应任务目录

### 7. 工具调用 HTML 注释持久化

```java
private String appendToolCallToResponse(String toolName, String toolId,
                                         String status, String input, String result) {
    Map<String, Object> toolCallMap = new LinkedHashMap<>();
    toolCallMap.put("type", "tool_call");
    toolCallMap.put("tool_id", toolId);
    toolCallMap.put("tool_name", toolName);
    toolCallMap.put("status", status);
    toolCallMap.put("tool_input", input);
    toolCallMap.put("tool_result", result);
    return "\n<!--TOOL:" + JSON.toJSONString(toolCallMap) + "-->\n";
}
```

**代码解释**:
- 工具调用信息嵌入 HTML 注释 `<!--TOOL:...-->` 中
- 保存在 assistant 消息的文本字段里，DB 中可完整追溯工具执行历史
- 前端可选择渲染或隐藏这些注释

### 8. 技能加载 -- SkillLoader

```java
public class SkillLoader {
    public SkillLoader() {
        loadSkills();
    }

    private void loadSkills() {
        if (isFileSystemAvailable()) {
            loadSkillsFromFileSystem();
        } else {
            loadSkillsFromJar();
        }
    }

    private String parseSkillContent(String rawContent, String filePath) {
        // 提取 YAML front-matter (---...---)
        // 解析 name, description
        // 包装为 <skill name="..." description="...">body</skill>
    }
}
```

**代码解释**:
- 双路径: 文件系统开发环境 vs JAR 生产环境
- YAML front-matter: `---` 包裹的元数据，提取 name 和 description
- XML 包装: `<skill>` 标签包裹技能正文，自然融入系统提示词
- `getDescriptions()`: 提取所有技能的 description 属性，用于提示词

## 工具数量表

| 工具名 | 类型 | 输入参数 | 说明 |
|--------|------|----------|------|
| bash | Function | command, type | 执行 Shell 命令（自动检测 OS） |
| read_file | Function | path | 读取文件内容 |
| write_file | Function | path, content | 写入文件 |
| edit_file | Function | path, oldStr, newStr | 编辑文件（字符串替换） |
| list_files | Function | path, fileType | 列出目录文件 |
| content_search | Function | pattern, path, fileType, caseSensitive | ripgrep 搜索（50结果上限） |
| sub_agent | Function | task | 子代理（3工具隔离执行） |
| todo | Function | action, items | 待办事项管理（增删改查） |
| load_skill | Function | name | 加载指定技能 |
| compact | Function | (无) | 触发上下文压缩 |
| task_create | Function | title, description, blockedBy | 创建任务 |
| task_update | Function | taskId, status | 更新任务状态 |
| task_list | Function | (无) | 列出所有任务 |
| task_get | Function | taskId | 获取任务详情 |
| background_run | Function | command | 后台异步执行命令 |
| check_background | Function | taskId | 查询后台任务状态 |

**总计：16 个工具**

## 相对于 s01 的变更

| 组件 | s01 (Agent Loop) | sweb (Web Application) | 变更说明 |
|------|-----------------|----------------------|----------|
| 架构模式 | 组合 ZQAgent | **完全独立 WebZQAgent** | 不继承也不组合 |
| 消息循环 | ZQAgent.run() | **WebZQAgent.runTask()** | 独立实现 |
| 工具数量 | 1 (bash) | **16** | 文件/搜索/任务/后台/技能/压缩 |
| 模式支持 | 标准 tool_use | **ReAct + 标准** | REACT_ENABLE 双模式 |
| 通信方式 | 终端 I/O | **HTTP REST + SSE** | Web 化 |
| 会话管理 | 单会话 | **ConcurrentHashMap 多会话** | 线程安全会话隔离 |
| 上下文管理 | 无 | **三层压缩** | 微压缩+自动+手动 |
| 持久化 | 无 | **MySQL (MyBatis-Plus)** | 会话+消息两层存储 |
| 后台任务 | 无 | **BackgroundManager** | 异步执行+通知注入 |
| 任务管理 | 无 | **TaskManager** | 文件存储+依赖图 |
| 待办事项 | 无 | **TodoManager** | 20项上限+单活跃约束 |
| 子代理 | 无 | **SubAgent** | 3工具隔离执行 |
| 技能系统 | 无 | **SkillLoader** | YAML+XML+双路径 |
| 业务层 | 无 | **Buff 交易层** | Retrofit+定时同步 |

## 试一试

```bash
# 编译项目
mvn clean install

# 运行 Web 应用
mvn spring-boot:run

# 应用启动后 (端口 8099)
```

试试这些 API 调用:

1. **同步任务**:
   ```bash
   curl -X POST http://localhost:8099/agent/runTask \
     -H "X-Session-Id: test-session" \
     -H "Content-Type: application/json" \
     -d '{"task": "查看当前目录有哪些文件"}'
   ```

2. **SSE 流式对话**:
   ```bash
   curl -X POST http://localhost:8099/agent/streamChat \
     -H "X-Session-Id: test-session" \
     -H "Content-Type: application/json" \
     -d '{"message": "帮我创建一个 hello.txt 并写入 Hello World", "stream": true}'
   ```

3. **查询聊天历史**:
   ```bash
   curl http://localhost:8099/api/chat/sessions
   ```

4. **触发后台任务**:
   ```
   在对话中让 Agent 执行: "后台运行一个耗时命令, 然后继续和我对话"
   Agent 会调用 background_run, 后续对话中自动收到完成通知
   ```

5. **触发上下文压缩**:
   ```
   进行长对话 (超过 TOKEN_THRESHOLD), Agent 会自动压缩上下文
   或直接让 Agent 调用 compact 工具手动触发
   ```
