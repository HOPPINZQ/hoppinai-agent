# AI智能体 - Module Web (Web应用 + ReAct智能体)

基于 Spring Boot 的全功能 AI 智能体 Web 应用。核心是一个**完全独立**的 `WebZQAgent`（不继承 ZQAgent），集成 16 个工具、5 个管理器、ReAct/标准双模式、SSE 实时流式通信、MySQL 持久化，以及 Buff 交易数据业务层。

## 项目简介

Agent-Web 是整个项目中最完整的模块——它既是 CLI 智能体的"集大成者"，也是面向 Web 的生产级应用。与 Agent01~08（继承 ZQAgent）和 Agent13/14（组合 ZQAgent）不同，**WebZQAgent 是完全独立的**，拥有自己的消息循环、工具调用、上下文压缩和会话管理机制。

模块提供三大能力：

- **同步任务执行**：`POST /agent/runTask` — 单次请求完成整个工具调用循环
- **SSE 流式对话**：`POST /agent/streamChat` — 实时推送 LLM 文本、工具状态、任务完成事件
- **聊天历史管理**：`REST /api/chat` — 会话和消息的 CRUD 持久化

同时集成了 Buff（buff.163.com）交易数据业务层，提供饰品价格查询、历史曲线、自动同步等功能。

## 核心特性

- **独立智能体架构**：`WebZQAgent` 不继承也不组合 ZQAgent，拥有完整的消息循环和工具调用链
- **REACT/标准双模式**：`REACT_ENABLE` 开关控制两条执行路径——ReAct 纯文本解析 vs 标准 tool_use 结构化响应
- **16 个内置工具**：文件操作（read/write/edit/list/search）、命令执行（bash）、子代理（sub_agent）、任务管理（create/update/list/get）、后台任务（background_run/check_background）、待办事项（todo）、技能加载（load_skill）、上下文压缩（compact）
- **三层上下文压缩**：Layer 1 微压缩（替换旧工具结果为摘要）、Layer 2 自动压缩（LLM 总结对话历史）、Layer 3 手动压缩（Agent 触发）
- **后台任务系统**：异步执行长耗时命令，通知队列自动注入到 LLM 对话流
- **文件任务管理**：基于文件的任务系统，支持依赖图（blockedBy/blocks），完成自动清理
- **子代理委派**：创建独立子 Agent，仅携带 read/write/edit 三个工具，隔离执行
- **技能系统**：YAML 前置元数据的 SKILL.md 文件，支持文件系统和 JAR 双路径加载
- **SSE 流式协议**：JSON 事件格式推送 content_block_delta、tool_status、task_completed、end
- **MySQL 持久化**：MyBatis-Plus 实现，会话（chat_session）和消息（chat_message）两层存储
- **Buff 交易层**：Retrofit + OkHttp + RxJava2 客户端，支持重试、Cookie 认证、定时同步

## 实现原理

### 架构总览

```
                     Spring Boot (port 8099)
                     ┌─────────────────────────────────────────┐
                     │                                         │
  POST /agent/runTask│          AgentChatController            │POST /agent/streamChat
  (同步, 完整循环)    │         ┌──────────────────┐           │(SSE, 实时流式)
                     │         │   WebZQAgent      │           │
                     │         │   (独立智能体)     │           │
                     │         └────────┬─────────┘           │
                     │                  │                      │
                     │    ┌─────────────┼─────────────┐        │
                     │    │             │             │        │
                     │    ▼             ▼             ▼        │
                     │ BackgroundManager  TaskManager  ContextCompactor
                     │    │             │             │        │
                     │    ▼             ▼             ▼        │
                     │ TodoManager    文件存储     JSONL归档   │
                     │    │                                       │
                     │    ▼                                       │
                     │ SkillLoader                                    │
                     │    │                                       │
                     │    ▼                                       │
                     │ 16 个 ToolDefinition                       │
                     │    │                                       │
                     │    ▼                                       │
                     │ Tools.java (工具实现)                       │
                     │    │                                       │
                     │    ▼                                       │
                     │ REACT_ENABLE                               │
                     │  ├─ true:  正则解析 Thought/Action/Observation│
                     │  └─ false: SDK 解析 tool_use block          │
                     │                                         │
                     └─────────────────────────────────────────┘
                                          │
                     ┌────────────────────┼────────────────────┐
                     ▼                    ▼                    ▼
              MySQL (MyBatis-Plus)   Buff API (Retrofit)   SSE (Flux<String>)
              chat_session           buff.163.com          JSON 事件流
              chat_message           饰品/价格/订单
```

### WebZQAgent 消息循环

```
runTask(task)
  │
  ├─ 1. 创建 sessionMessages 列表
  │
  ├─ 2. chatMessage() → 调用 LLM API
  │     ├─ REACT_ENABLE=true  → 不发送 tools, LLM 输出纯文本
  │     └─ REACT_ENABLE=false → 发送 tools, LLM 输出 tool_use
  │
  ├─ 3. 解析响应
  │     ├─ ReAct 模式:
  │     │   ├─ 检测 "Action:" → 正则提取工具名 + JSON 参数
  │     │   ├─ invokeTool() → 执行工具
  │     │   ├─ 注入 "Observation: result" 为用户消息
  │     │   └─ 回到步骤 2
  │     │
  │     └─ 标准模式:
  │         ├─ 检测 ToolUseBlock → SDK 自动解析
  │         ├─ invokeTool() → 执行工具
  │         ├─ 追加 ToolResultBlockParam
  │         └─ 回到步骤 2
  │
  ├─ 4. 上下文压缩检查
  │     ├─ 微压缩: 替换 KEEP_RECENT=10 之前的旧工具结果
  │     ├─ 自动压缩: token > TOKEN_THRESHOLD → LLM 总结
  │     └─ 手动压缩: compact 工具触发
  │
  ├─ 5. 后台通知注入
  │     └─ injectBackgroundNotifications() → 排空通知队列
  │
  └─ 6. 无工具调用 → 返回最终结果
```

### SSE 流式处理 (processAgentStream)

```
streamChat(sessionId, userMessage)
  │
  ├─ 1. 创建会话, 保存用户消息到 DB
  │
  ├─ 2. processAgentStream() [递归]
  │     │
  │     ├─ contentBlockStart
  │     │   └─ 检测 toolUse → 标记工具调用模式
  │     │
  │     ├─ contentBlockDelta
  │     │   ├─ 文本块 → 累积到 fullTextResponse
  │     │   └─ inputJsonDelta → 累积工具参数 JSON
  │     │
  │     ├─ contentBlockStop
  │     │   ├─ 有工具调用:
  │     │   │   ├─ invokeTool() → 执行
  │     │   │   ├─ 发送 tool_status JSON 事件
  │     │   │   ├─ 构建 assistant + toolResult 消息
  │     │   │   └─ 递归调用 processAgentStream()
  │     │   │
  │     │   └─ 无工具调用 → 发送 "end" 事件
  │     │
  │     ├─ messageStop (ReAct 模式)
  │     │   ├─ 检测 "Action:" → 正则解析
  │     │   ├─ invokeTool() → 执行
  │     │   ├─ 发送 tool_status JSON 事件
  │     │   ├─ 注入 Observation 为用户消息
  │     │   └─ 递归调用 processAgentStream()
  │     │
  │     └─ 无 Action → 发送 "end" 事件, 持久化
  │
  └─ 3. persistAssistantResponse() → 保存到 DB
```

### SSE 事件格式

| 事件类型 | 数据格式 | 说明 |
|---------|---------|------|
| `content_block_delta` | `{"type":"content_block_delta","text":"..."}` | LLM 文本增量 |
| `tool_status` | `{"type":"tool_status","status":"calling/success/error","tool_name":"...","tool_input":{},"tool_result":"..."}` | 工具调用状态 |
| `task_completed` | `{"type":"task_completed","result":"..."}` | 任务完成 |
| `end` | `"end"` | 对话结束 |

### 三层上下文压缩

```
消息历史 ──────────────────────────────────────────►
│                                                 │
├─ Layer 1: 微压缩 (microCompact)                  │
│   条件: 每次工具调用后                            │
│   动作: 替换 KEEP_RECENT=10 之前的工具结果为      │
│         "[已执行: toolName]"                     │
│                                                 │
├─ Layer 2: 自动压缩 (autoCompact)                 │
│   条件: 序列化后 token > TOKEN_THRESHOLD(20000)  │
│   动作:                                          │
│     1. saveTranscript() → 保存 JSONL 归档        │
│     2. generateSummary() → LLM 总结 (2000 tokens)│
│     3. 用摘要替换原始消息                         │
│                                                 │
└─ Layer 3: 手动压缩 (compact 工具)                │
    条件: Agent 主动调用 compact 工具               │
    动作: 设置 manualCompactRequested 标志          │
          下次循环触发 Layer 2
```

### 核心组件

| 组件 | 类 | 职责 |
|------|----|------|
| 智能体 | `WebZQAgent` | 独立智能体，消息循环、工具调用、压缩、会话管理 |
| 控制器 | `AgentChatController` | SSE 流式处理、工具注册（16个）、系统提示词构建 |
| 历史控制器 | `ChatHistoryController` | REST API: 会话和消息 CRUD |
| 后台管理 | `BackgroundManager` | 异步执行长命令、通知队列、100条上限清理 |
| 任务管理 | `TaskManager` | 文件存储任务、依赖图、自动清理 |
| 上下文压缩 | `ContextCompactor` | 三层压缩、JSONL 归档、LLM 总结 |
| 待办管理 | `TodoManager` | 20项上限、单活跃约束、版本跟踪、[ ]/[>]/[x] 渲染 |
| 技能加载 | `SkillLoader` | 文件系统/JAR 双路径、YAML 前置元数据、XML 包装 |
| 子代理 | `SubAgent` | 创建子 WebZQAgent（仅3工具）、隔离执行 |
| 会话上下文 | `SessionContextHolder` | InheritableThreadLocal 绑定 sessionId |
| 工具定义 | `ToolDefinition` | 16个工具的静态字段、输入Schema、反射调用 |
| 工具实现 | `Tools` | 所有工具的具体执行逻辑 |
| 常量配置 | `AIConstants` | API/模型/路径/REACT_ENABLE/TOKEN阈值等 |

## 使用方法

### 环境要求

- Java 17+
- Maven 3.6+
- MySQL 数据库
- ripgrep（用于 content_search 工具）
- Anthropic 兼容 API 密钥（当前使用 DeepSeek 代理）

### 配置

编辑 `src/main/java/com/hoppinzq/agent/constant/AIConstants.java`：

```java
public static final String BASE_URL = "https://api.deepseek.com/anthropic";
public static final String API_KEY = "your-api-key-here";
public static final String MODEL = "deepseek-chat";
public static final Boolean REACT_ENABLE = true;
public static final int TOKEN_THRESHOLD = 20000;
public static final int KEEP_RECENT = 10;
```

编辑 `src/main/resources/application.yml`：

```yaml
server:
  port: 8099

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/your_db?useSSL=false&serverTimezone=Asia/Shanghai
    username: your_username
    password: your_password
```

### 编译运行

```bash
# 编译项目
mvn clean install

# 运行应用
mvn spring-boot:run
```

### API 接口

#### 同步任务执行

```bash
POST /agent/runTask
Header: X-Session-Id: your-session-id
Body: {"task": "查看当前目录并读取 README.md"}
```

#### SSE 流式对话

```bash
POST /agent/streamChat
Header: X-Session-Id: your-session-id
Body: {"message": "帮我创建一个 Python 脚本", "stream": true}
```

#### 聊天历史管理

```bash
# 获取会话列表
GET /api/chat/sessions

# 创建会话
POST /api/chat/sessions

# 获取会话消息
GET /api/chat/sessions/{sessionId}/messages

# 删除会话（级联删除消息）
DELETE /api/chat/sessions/{sessionId}
```

## 项目结构

```
hoppinzq-module-agent-web/
├── src/main/java/
│   ├── com/hoppinzq/
│   │   ├── HoppinaiTempReActApplication.java     # @SpringBootApplication + @EnableAsync + @EnableScheduling
│   │   ├── agent/
│   │   │   ├── base/
│   │   │   │   ├── WebZQAgent.java               # 独立智能体（消息循环、工具调用、压缩）
│   │   │   │   └── SubAgent.java                 # 子代理（3工具隔离执行）
│   │   │   ├── tool/
│   │   │   │   ├── Tools.java                    # 16个工具实现
│   │   │   │   ├── ToolDefinition.java           # 工具定义（16个静态字段）
│   │   │   │   ├── manager/
│   │   │   │   │   ├── BackgroundManager.java    # 后台任务管理（异步+通知队列）
│   │   │   │   │   ├── TaskManager.java          # 文件任务管理（依赖图）
│   │   │   │   │   ├── ContextCompactor.java     # 三层上下文压缩
│   │   │   │   │   └── TodoManager.java          # 待办事项（20项上限）
│   │   │   │   ├── skill/
│   │   │   │   │   └── SkillLoader.java          # 技能加载（文件系统/JAR双路径）
│   │   │   │   ├── schema/                       # 工具输入参数类
│   │   │   │   └── util/
│   │   │   │       └── FileExclusionHelper.java  # 文件排除辅助
│   │   │   ├── context/
│   │   │   │   └── SessionContextHolder.java     # InheritableThreadLocal 会话绑定
│   │   │   ├── constant/
│   │   │   │   └── AIConstants.java              # 常量配置
│   │   │   ├── entity/
│   │   │   │   ├── ChatSession.java              # 会话实体（@TableName("chat_session")）
│   │   │   │   └── ChatMessage.java              # 消息实体（@TableName("chat_message")）
│   │   │   ├── mapper/
│   │   │   │   ├── ChatSessionMapper.java        # 会话 Mapper（BaseMapper）
│   │   │   │   └── ChatMessageMapper.java        # 消息 Mapper（BaseMapper）
│   │   │   └── service/
│   │   │       ├── ChatSessionService.java       # 会话 CRUD
│   │   │       └── ChatMessageService.java       # 消息 CRUD
│   │   ├── controller/
│   │   │   ├── AgentChatController.java          # 智能体控制器（SSE流式+同步）
│   │   │   └── ChatHistoryController.java        # 聊天历史 REST API
│   │   ├── config/
│   │   │   ├── CorsConfig.java                  # CORS 全开放
│   │   │   ├── AsyncConfig.java                 # 双线程池配置
│   │   │   └── MybatisPlusConfig.java           # 分页插件
│   │   └── wybuff/                              # Buff 交易数据层
│   │       ├── BuffApi.java                     # Retrofit API 接口
│   │       ├── BuffService.java                 # 服务层（重试+Cookie认证）
│   │       ├── AuthenticationInterceptor.java   # OkHttp Cookie 拦截器
│   │       └── task/
│   │           └── BuffDataSyncTask.java         # 定时同步（每12小时）
│   └── com/config/                              # （备用配置目录）
├── src/main/resources/
│   ├── application.yml                          # Spring Boot 配置
│   └── skills/
│       └── git/
│           └── SKILL.md                         # Git 工作流技能
└── pom.xml
```

## 技术栈

| 技术 | 用途 |
|------|------|
| Java 17 | 编程语言 |
| Spring Boot | Web 框架、依赖注入、定时任务、异步 |
| Anthropic Java SDK | LLM API 客户端（通过 DeepSeek 代理） |
| MyBatis-Plus | ORM 框架（MySQL 持久化） |
| MySQL | 会话和消息存储 |
| Retrofit + OkHttp + RxJava2 | Buff API 客户端 |
| Project Reactor (Flux) | SSE 流式响应 |
| Jackson | JSON 序列化/反序列化 |
| Lombok | 减少样板代码 |
| SLF4J | 日志框架 |
| ripgrep | 文件内容搜索 |

## 设计亮点

1. **完全独立的智能体**：WebZQAgent 不继承也不组合 ZQAgent，拥有自己的完整实现，避免了继承层级带来的耦合问题
2. **单方法双模式流处理**：`processAgentStream()` 在同一个递归方法中同时处理标准 tool_use 和 ReAct 文本解析，通过 contentBlockStart 检测和 messageStop 正则匹配分别捕获两种模式
3. **后台通知自动注入**：`BackgroundManager` 的通知队列在每次 LLM 调用前由 `injectBackgroundNotifications()` 排空，以用户消息+助手消息对的形式注入对话流，Agent 无需轮询即可感知后台任务状态
4. **三层渐进式压缩**：从轻量微压缩（替换旧结果）到中量自动压缩（LLM 总结）再到手动压缩，平衡了上下文保留和 token 消耗
5. **工具调用 HTML 注释持久化**：`appendToolCallToResponse()` 将工具调用信息嵌入 `<!--TOOL:...-->` HTML 注释，保存在 assistant 文本中，DB 中的消息记录保留了完整的工具执行历史
6. **文件任务依赖图**：TaskManager 通过 blockedBy/blocks 字段构建任务依赖关系，完成时自动清理依赖链，支持复杂工作流编排
7. **技能系统 YAML 前置元数据**：SKILL.md 使用 YAML front-matter 声明名称和描述，SkillLoader 提取后包装为 `<skill>` XML 标签，自然融入系统提示词
8. **双路径技能加载**：支持文件系统目录和 JAR 包内两种加载路径，确保开发环境和生产打包环境都能正确加载技能

## 扩展开发

### 添加自定义工具

在 `ToolDefinition.java` 中添加静态字段，然后在 `AgentChatController` 构造函数中注册：

```java
// 1. 在 ToolDefinition 中添加静态字段
public static ToolDefinition MyToolDefinition = new ToolDefinition(
    "my_tool",
    "工具描述",
    createInputSchema(
        Map.of("param1", createProperty("string", "参数描述")),
        List.of("param1")
    ),
    MyToolInput.class,
    Tools::myToolMethod
);

// 2. 在 AgentChatController 构造函数中注册
tools.add(ToolDefinition.MyToolDefinition);
webZQAgent.registerTool(ToolDefinition.MyToolDefinition);
```

### 添加自定义技能

在 `src/main/resources/skills/` 下创建目录和 SKILL.md：

```markdown
---
name: my_skill
description: 技能描述
---

技能的具体指令内容...
```

SkillLoader 会自动扫描并加载，技能描述会出现在系统提示词中。

### 切换到标准模式

在 `AIConstants.java` 中设置：

```java
public static final Boolean REACT_ENABLE = false;
```

此时 Agent-Web 使用标准 tool_use 模式，与 Agent01~08 的工具调用行为一致。

## 注意事项

1. **API 密钥安全**：不要将 API 密钥提交到版本控制系统
2. **ReAct 格式约束**：ReAct 模式下 LLM 必须严格遵守 Thought/Action/Action Input 格式，否则正则解析失败
3. **会话隔离**：通过 `X-Session-Id` Header 实现会话隔离，`SessionContextHolder` 基于 `InheritableThreadLocal`，需注意线程池场景下的上下文传递
4. **后台任务上限**：`BackgroundManager` 最多保留 100 条任务记录，超出自动清理非运行任务
5. **待办事项上限**：`TodoManager` 最多 20 项，且同一时间只能有 1 项处于 in_progress 状态
6. **上下文压缩阈值**：`TOKEN_THRESHOLD=20000` 是估算值，实际 token 数取决于 LLM 的 tokenizer
7. **GBK 编码处理**：Windows 下命令输出使用 GBK 编码读取，避免乱码
8. **Buff Cookie**：BuffService 的 Cookie 认证信息在 `BuffDataSyncTask` 中硬编码，生产环境应使用配置中心管理

## 许可证

MIT License

## 作者

@hoppinzq
