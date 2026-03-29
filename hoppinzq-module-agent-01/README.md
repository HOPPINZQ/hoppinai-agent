# AI智能体 - Java实现 (Module 01)

基于 Java 的 AI 智能体实现，通过 Anthropic API 与语言模型交互，支持工具调用（Function Calling）。这是整个项目的起点 —— **一个工具 + 一个循环 = 一个智能体**。

## 项目简介

本项目展示了如何用不到 200 行 Java 代码构建一个具备工具调用能力的 AI 智能体。核心思路非常简单：

- **一个循环**：用户输入 → LLM → 工具调用 → 结果返回 → 循环（直到 LLM 不再调用工具）
- **一个工具**：Bash —— 执行 Shell 命令并返回输出

## 核心特性

- **工具调用循环**：自动检测 LLM 的工具调用请求，执行后返回结果，持续循环直到完成
- **多平台支持**：自动适配 Windows（cmd）、Linux/Mac（bash），支持 PowerShell
- **中文系统提示词**：内置详细的中文系统提示词，引导 LLM 正确使用工具
- **类型安全**：使用 `ToolDefinition` + `BashInput` 实现强类型工具定义
- **错误处理**：工具未找到、执行异常等场景均有处理

## 实现原理

### 架构设计

```
+--------+      +-------+      +---------+
|  User  | ---> |  LLM  | ---> |  Tool   |
| prompt |      |       |      | execute |
+--------+      +---+---+      +----+----+
                    ^                |
                    |   tool_result  |
                    +----------------+
                    (loop until stop_reason != "tool_use")
```

Agent01 使用**组合模式**（不继承 ZQAgent），创建 `ZQAgent` 实例并注入工具列表：

```java
public class Agent01 {
    public static void main(String[] args) {
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(API_KEY).baseUrl(BASE_URL).build();

        List<ToolDefinition> tools = new ArrayList<>();
        tools.add(BashDefinition);

        ZQAgent agent = new ZQAgent(client, MODEL, tools);
        agent.setSystemPrompt(buildSystemPrompt());
        agent.run();
    }
}
```

### 核心组件

| 组件 | 职责 |
|------|------|
| `Agent01` | 入口类，配置客户端、工具和系统提示词 |
| `ZQAgent` | 智能体基类，实现工具调用循环（`run()` / `chatMessage()` / `invokeTool()`） |
| `ToolDefinition` | 工具定义类，描述工具名称、描述、输入 Schema 和执行函数 |
| `Tools` | 工具实现类，包含 `executeBash()` 方法 |
| `BashInput` | Bash 工具的输入参数类（command + type） |
| `AIConstants` | 常量配置（API地址、密钥、模型等） |

### 工作流程

1. **用户输入**：通过 `Scanner` 获取用户输入，构建 `MessageParam` 添加到消息列表
2. **发送请求**：将消息历史 + 工具定义通过 `chatMessage()` 发送给 LLM
3. **响应处理**：遍历响应内容块，文本直接输出，工具调用标记 `hasToolUse = true`
4. **工具执行**：匹配工具名，通过 `invokeTool()` 转换参数并执行
5. **结果返回**：工具结果作为 `ToolResultBlockParam` 追加到消息历史
6. **循环判断**：`hasToolUse == false` 时退出内层循环，回到外层等待下一次用户输入

### 工具定义模式

每个工具通过 `ToolDefinition` 静态字段定义：

```java
public static ToolDefinition BashDefinition = new ToolDefinition(
    "bash",                                              // 工具名
    "执行 Shell 命令并返回其输出结果...",                   // 描述
    createInputSchema(                                    // JSON Schema
        Map.of("command", createProperty("string", "要执行的命令字符串"),
               "type", createProperty("string", "命令类型...")),
        List.of("command", "type")
    ),
    BashInput.class,                                      // 参数类型
    Tools::executeBash                                    // 执行函数
);
```

## 使用方法

### 环境要求

- Java 17+
- Maven 3.6+
- Anthropic API 密钥（或兼容代理服务）

### 配置

编辑 `src/main/java/com/hoppinzq/agent/constant/AIConstants.java`：

```java
// API地址（支持Anthropic官方API或兼容的代理服务）
public static final String BASE_URL = "https://hoppinzq.com:520/deepseek/anthropic";

// API密钥
public static final String API_KEY = "your-api-key-here";

// 模型名称
public static final String MODEL = "deepseek-chat";
```

支持的API服务商示例：

```java
// DeepSeek代理
BASE_URL = "https://hoppinzq.com:520/deepseek/anthropic"
MODEL    = "deepseek-chat"

// Anthropic官方
BASE_URL = "https://api.anthropic.com"
MODEL    = "claude-3-5-sonnet-20241022"
```

### 编译运行

```bash
# 编译项目
mvn clean install

# 运行智能体
mvn exec:java -Dexec.mainClass="com.hoppinzq.agent.Agent01"
```

或在 IDE 中直接运行 `Agent01.java` 的 `main` 方法。

### 交互示例

```
你: 查看当前目录有哪些文件
AI: 我来查看当前目录的文件列表。
工具: bash({"command":"dir"})
结果: hello.txt  test.py  README.md
AI: 当前目录下有以下文件：
    - hello.txt
    - test.py
    - README.md

你: 打开百度
AI: 好的，我来帮你打开百度。
工具: bash({"command":"start https://www.baidu.com"})
```

## 项目结构

```
hoppinzq-module-agent-01/
├── src/main/java/com/hoppinzq/agent/
│   ├── Agent01.java               # 入口类（组合模式，创建ZQAgent实例）
│   ├── base/
│   │   └── ZQAgent.java           # 智能体基类（工具调用循环）
│   ├── tool/
│   │   ├── Tools.java             # 工具实现（executeBash）
│   │   ├── ToolDefinition.java    # 工具定义（BashDefinition）
│   │   └── schema/
│   │       └── BashInput.java     # Bash输入参数（command + type）
│   └── constant/
│       └── AIConstants.java       # 常量配置（API/模型/路径）
├── README.md                       # 本文件
└── s1.md                           # 智能体循环原理解析
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

1. **组合优于继承**：Agent01 不继承 ZQAgent，而是创建实例并注入工具 —— 后续模块（03-08）才会使用继承
2. **双循环架构**：外层循环处理多轮对话，内层循环处理单次工具调用链 —— 循环本身在后续 11 个模块中始终不变
3. **自动平台适配**：Bash 工具根据 `os.name` 自动选择 cmd/bash 执行方式
4. **静态工具定义**：`ToolDefinition` 作为静态字段，通过 `createInputSchema()` / `createProperty()` 辅助方法构建 JSON Schema
5. **GBK 编码处理**：Windows 下命令输出使用 GBK 编码读取，避免乱码

## 扩展开发

### 添加自定义工具

```java
// 1. 定义输入参数类
@Data
public class MyToolInput {
    @JsonProperty("param")
    private String param;
}

// 2. 在 ToolDefinition 中添加静态字段
public static ToolDefinition MyToolDefinition = new ToolDefinition(
    "my_tool",
    "工具描述",
    createInputSchema(
        Map.of("param", createProperty("string", "参数描述")),
        List.of("param")
    ),
    MyToolInput.class,
    Tools::myToolMethod
);

// 3. 在 Tools 中实现方法
public static String myToolMethod(String input) {
    MyToolInput parsed = OBJECT_MAPPER.readValue(input, MyToolInput.class);
    return "result: " + parsed.getParam();
}
```

## 注意事项

1. **API密钥安全**：不要将 API 密钥提交到版本控制系统
2. **命令执行风险**：Bash 工具可执行任意命令，生产环境需添加安全限制
3. **日志控制**：`AIConstants.LOG_ENABLE = false` 关闭日志；MCP stdio 模式下严禁使用 `System.out`
4. **编码处理**：Windows 命令输出使用 GBK 编码读取

## 许可证

MIT License

## 作者

@hoppinzq
