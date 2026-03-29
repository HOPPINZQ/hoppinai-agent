# AI智能体 - Module 14 (ReAct推理行动模式)

基于 Java 的 AI 智能体实现，通过 **ReAct（Reasoning + Acting）** 模式让 LLM 以"思考→行动→观察"的循环解决复杂问题。核心思路：**REACT_ENABLE 开关控制两条完全不同的执行路径**。

## 项目简介

Agent14 是项目中第一个在智能体层面引入推理模式的模块。它通过 `REACT_ENABLE` 配置开关，在同一个 `ZQAgent` 基类中实现两种截然不同的工具调用方式：

- **标准模式**（`REACT_ENABLE=false`）：LLM 通过 Anthropic `tool_use` API 返回结构化工具调用，由 ZQAgent 解析并执行
- **ReAct 模式**（`REACT_ENABLE=true`）：工具不发送给 API，LLM 以纯文本输出 Thought/Action/Action Input 格式，由 ZQAgent 用正则解析后直接执行工具，再注入 Observation

Agent14 使用**组合模式**（不继承 ZQAgent），与 Agent01、Agent02、Agent13 保持一致的架构风格。

## 核心特性

- **ReAct 推理模式**：LLM 以 Thought → Action → Action Input 的纯文本格式输出，模拟人类"先想再做"的推理过程
- **双模式切换**：通过 `REACT_ENABLE` 一键切换标准 tool_use 模式和 ReAct 文本解析模式
- **正则解析引擎**：ZQAgent 使用 `Pattern.compile("Action:\\s*([^\\n]+)")` 和 `Pattern.compile("Action Input:\\s*(\\{.*?\\})(?=\\s*\\n|$)", Pattern.DOTALL)` 从文本中提取工具名和参数
- **Observation 注入**：工具执行结果以 `Observation:` 前缀注入为用户消息，形成完整的推理闭环
- **双系统提示词**：根据模式自动选择 `buildReActSystemPrompt()` 或 `buildNormalSystemPrompt()`
- **6 个基础工具**：与 Agent01-08 相同的基础工具集（bash、read_file、write_file、edit_file、list_files、content_search）

## 实现原理

### 架构设计

```
                 REACT_ENABLE
                 ┌────────┴────────┐
                 ▼                 ▼
          ReAct 模式            标准模式
          ┌──────────┐      ┌──────────┐
          │ 不发送    │      │ 发送     │
          │ tools    │      │ tools    │
          │ 给 API   │      │ 给 API   │
          └────┬─────┘      └────┬─────┘
               │                  │
               ▼                  ▼
          LLM 输出纯文本      LLM 输出
          Thought/Action/     tool_use
          Action Input        结构化响应
               │                  │
               ▼                  ▼
          正则解析提取        SDK 解析
          Action + Input      ToolUseBlock
               │                  │
               └────────┬─────────┘
                        ▼
                   invokeTool()
                        │
                        ▼
                   工具执行结果
                        │
               ┌────────┴────────┐
               ▼                 ▼
          Observation:        ToolResultBlock
          注入为用户消息       追加到消息历史
               │                 │
               └────────┬────────┘
                        ▼
                   循环直到完成
```

### 启动流程

```
main()
  │
  ├─ 1. 创建 AnthropicClient
  │
  ├─ 2. 组装 6 个基础工具
  │     └─ BashDefinition, EditFileDefinition, WriteFileDefinition,
  │        ReadFileDefinition, ListFilesDefinition, ContentSearchDefinition
  │
  ├─ 3. 判断 REACT_ENABLE
  │     ├─ true  → buildReActSystemPrompt(tools)
  │     │          └─ 强制 Thought/Action/Action Input/Observation 格式
  │     │             一次只返回一组，不能私自赋值 Observation
  │     └─ false → buildNormalSystemPrompt(tools)
  │                 └─ 标准工具描述列表
  │
  ├─ 4. new ZQAgent(client, MODEL, tools)
  │
  ├─ 5. agent.setSystemPrompt(systemPrompt)
  │
  └─ 6. agent.run()
```

### ReAct 模式下的 ZQAgent.run() 核心逻辑

ReAct 模式下，`chatMessage()` 不发送工具给 API，LLM 以纯文本输出推理过程：

```java
// chatMessage() 中：ReAct 模式不发送工具
if (!REACT_ENABLE && tools != null && !tools.isEmpty()) {
    messageBuilder.tools(anthropicTools);
}

// run() 中：ReAct 模式用正则解析文本输出
if (REACT_ENABLE) {
    // 将 LLM 文本输出作为 assistant 消息添加
    messageParams.add(MessageParam.builder()
            .role(MessageParam.Role.ASSISTANT)
            .content(resultText).build());

    if (resultText.contains("Action:")) {
        // 正则提取 Action 和 Action Input
        Pattern actionPattern = Pattern.compile("Action:\\s*([^\\n]+)");
        Pattern inputPattern = Pattern.compile(
            "Action Input:\\s*(\\{.*?\\})(?=\\s*\n|$)", Pattern.DOTALL);

        // 直接调用 invokeTool() 执行工具
        observation = invokeTool(targetTool, inputJson);

        // 将 Observation 注入为用户消息
        messageParams.add(MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content("Observation: " + observation).build());
    } else {
        // 没有 Action = 最终答案，退出循环
        System.out.printf("AI: %s%n", resultText);
        break;
    }
} else {
    // 标准 tool_use 流程（与 Agent01-08 相同）
}
```

### 核心组件

| 组件 | 职责 |
|------|------|
| `Agent14` | 入口类（组合模式），根据 `REACT_ENABLE` 选择提示词，组装工具列表并启动 ZQAgent |
| `ZQAgent` | **本地副本**，集成 REACT_ENABLE 分支：ReAct 模式下正则解析文本 / 标准模式下 SDK 解析 tool_use |
| `ToolDefinition` | 工具定义类，包含 6 个基础工具的静态字段定义（无 ReAct 工具定义） |
| `Tools` | 6 个基础工具的实现（Bash、ReadFile、WriteFile、EditFile、ListFiles、ContentSearch） |
| `ReActInput` | ReAct 数据模型类（thought/action/actionInput/observation），仅用于序列化，**不是工具** |
| `AIConstants` | 常量配置（API/模型/路径 + `REACT_ENABLE` 开关） |

### ReAct vs 标准 tool_use 对比

| 维度 | ReAct 模式 | 标准 tool_use 模式 |
|------|-----------|-------------------|
| 工具发送给 API | 否 | 是 |
| LLM 输出格式 | 纯文本（Thought/Action/Action Input） | 结构化 `tool_use` block |
| 解析方式 | 正则表达式 | Anthropic SDK 自动解析 |
| 工具结果注入 | `Observation:` 前缀，作为用户消息 | `ToolResultBlockParam`，作为 tool_result |
| 推理可见性 | 完整思考过程可见 | 思考过程隐藏在结构化响应中 |
| 适用场景 | 需要透明推理链、调试、兼容非 tool_use API | 生产环境、稳定性优先 |

## 使用方法

### 环境要求

- Java 17+
- Maven 3.6+
- Anthropic API 密钥（或兼容代理服务）
- ripgrep（用于 content_search 工具）

### 配置

编辑 `src/main/java/com/hoppinzq/agent/constant/AIConstants.java`：

```java
// API 配置
public static final String BASE_URL = "https://hoppinzq.com:520/deepseek/anthropic";
public static final String API_KEY = "your-api-key-here";
public static final String MODEL = "deepseek-chat";

// ReAct 模式开关
public static final Boolean REACT_ENABLE = true;   // true=ReAct模式, false=标准tool_use模式

// 其他配置
public static final String RG_PATH = "C:\\ProgramData\\chocolatey\\bin\\rg.exe";
public static final String ROOT = System.getProperty("user.dir");
```

### 编译运行

```bash
# 编译项目
mvn clean install

# 运行智能体
mvn exec:java -Dexec.mainClass="com.hoppinzq.agent.Agent14"
```

### 交互示例（ReAct 模式）

```
你: 请查看当前目录有哪些文件，并读取 README.md 的内容

AI:
Thought: 用户需要两个操作：先列出目录文件，再读取 README.md。先执行 list_files
Action: list_files
Action Input: {"path":"."}

Observation: [Agent14.java, ZQAgent.java, ToolDefinition.java, README.md, ...]

Thought: 已获取文件列表，现在读取 README.md
Action: read_file
Action Input: {"path":"README.md"}

Observation: # AI智能体 - Module 14 (ReAct推理行动模式)
基于 Java 的 AI 智能体实现...

AI: 当前目录包含以下文件：
    - Agent14.java（主入口）
    - ZQAgent.java（智能体基类）
    - ToolDefinition.java（工具定义）
    - README.md（项目文档）
    ...
    README.md 的内容是关于本项目的 ReAct 推理行动模式智能体。
```

### 交互示例（标准模式）

```
你: 查看当前目录
AI: [内部调用 bash("dir") ]
    当前目录下有：Agent14.java, ZQAgent.java, ...
```

## 项目结构

```
hoppinzq-module-agent-14-react/
├── src/main/java/com/hoppinzq/agent/
│   ├── Agent14.java                         # 入口类（组合模式，双提示词构建）
│   ├── base/
│   │   └── ZQAgent.java                     # 智能体基类（本地副本，集成REACT_ENABLE分支）
│   ├── tool/
│   │   ├── Tools.java                       # 基础工具实现
│   │   ├── ToolDefinition.java              # 工具定义（6个基础工具静态字段）
│   │   ├── schema/
│   │   │   ├── BashInput.java               # Bash输入参数
│   │   │   ├── ContentSearchInput.java      # 内容搜索输入参数
│   │   │   ├── EditFileInput.java           # 编辑文件输入参数
│   │   │   ├── ListFilesInput.java          # 列出文件输入参数
│   │   │   ├── ReadFileInput.java           # 读取文件输入参数
│   │   │   ├── WriteFileInput.java          # 写入文件输入参数
│   │   │   └── ReActInput.java              # ReAct数据模型（非工具定义）
│   │   └── util/
│   │       └── FileExclusionHelper.java     # 文件排除辅助
│   └── constant/
│       └── AIConstants.java                 # 常量配置（API/模型/REACT_ENABLE开关）
├── README.md                                # 本文件
└── s14.md                                   # ReAct推理模式原理解析
```

## 技术栈

| 技术 | 用途 |
|------|------|
| Java 17 | 编程语言 |
| Anthropic Java SDK | LLM API 客户端 |
| OkHttp | HTTP 客户端（Anthropic SDK 底层） |
| Jackson | JSON 序列化/反序列化 |
| Lombok | 减少样板代码 |
| SLF4J | 日志框架 |

## 设计亮点

1. **模式开关设计**：`REACT_ENABLE` 一键切换两种工具调用范式，共享同一套工具定义和执行逻辑
2. **本地 ZQAgent 副本**：Agent14 拥有自己的 `ZQAgent` 副本，在 `run()` 和 `chatMessage()` 中嵌入 ReAct 分支，不影响其他模块
3. **正则解析引擎**：使用 `Pattern.DOTALL` 模式的正则从 LLM 文本输出中提取 Action 和 Action Input，支持跨行 JSON
4. **Observation 注入机制**：工具结果以用户消息形式注入（`role=USER`），利用 LLM 对对话历史的自然处理能力实现推理链延续
5. **ReAct 不是工具**：`ReActInput` 仅作为数据模型存在，不注册为 `ToolDefinition` —— ReAct 是执行模式，不是可调用工具
6. **双系统提示词**：`buildReActSystemPrompt()` 严格约束输出格式（一次一组、禁止私自赋值 Observation），`buildNormalSystemPrompt()` 列出工具描述
7. **兼容非 tool_use API**：ReAct 模式不依赖 API 的 tool_use 能力，理论上可适配任何文本生成 API

## 扩展开发

### 添加自定义工具

在 `ToolDefinition.java` 中添加静态字段，然后在 `Agent14.main()` 的工具列表中注册：

```java
// 1. 定义输入参数类
@Data
public class MyToolInput {
    private String param1;
}

// 2. 在 ToolDefinition 中添加静态字段
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

// 3. 在 Agent14.main() 中注册
tools.add(MyToolDefinition);
```

### 自定义 ReAct 提示词

修改 `Agent14.buildReActSystemPrompt()` 中的规则约束：

```java
private static String buildReActSystemPrompt(List<ToolDefinition> tools) {
    StringBuilder sb = new StringBuilder();
    sb.append("你是一个使用 ReAct 模式的 AI 助手...\n\n");
    // 添加自定义规则
    sb.append("额外规则：...\n\n");
    // 添加工具列表
    for (ToolDefinition tool : tools) {
        sb.append("- ").append(tool.getName()).append(": ").append(tool.getDescription()).append("\n");
    }
    return sb.toString();
}
```

### 切换到标准模式

在 `AIConstants.java` 中设置：

```java
public static final Boolean REACT_ENABLE = false;
```

此时 Agent14 的行为与 Agent01 完全一致（6 个工具 + 标准 tool_use 流程）。

## 注意事项

1. **API密钥安全**：不要将 API 密钥提交到版本控制系统
2. **ReAct 格式约束**：ReAct 模式下 LLM 必须严格遵守 Thought/Action/Action Input 格式，否则正则解析失败
3. **正则解析局限**：Action Input 的正则 `(\\{.*?\\})(?=\\s*\\n|$)` 使用非贪婪匹配，嵌套 JSON 可能解析不完整
4. **REACT_ENABLE 全局生效**：该开关影响 ZQAgent 的所有调用，不能在同一运行中混用两种模式
5. **ReActInput 非工具**：`ReActInput.java` 仅定义了 ReAct 数据结构（thought/action/actionInput/observation），不作为工具注册
6. **GBK 编码处理**：Windows 下命令输出使用 GBK 编码读取，避免乱码

## 许可证

MIT License

## 作者

@hoppinzq
