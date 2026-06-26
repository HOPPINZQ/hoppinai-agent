# AI智能体 - 多工具调用 (Module 02)

基于 Java 的 AI 智能体实现，在 Module 01 的基础上扩展为 **6 个专用工具**。核心思路：**加工具不需要改循环，只需注册新的 ToolDefinition**。

## 项目简介

Module 01 只有一个 bash 工具，所有操作都走 Shell。`cat` 截断不可预测，`sed` 遇到特殊字符就崩，每次 bash 调用都是不受约束的安全面。Module 02 的解决方案是引入专用工具，每个工具做一件事，在工具层面实现路径沙箱和参数校验。

- **组合模式**：Agent02 不继承 ZQAgent，而是创建实例并注入工具列表（与 Agent01 相同）
- **6 个工具**：bash、read_file、write_file、edit_file、list_files、content_search
- **循环不变**：加工具 = 加 handler + 加 schema，ZQAgent 的 run/chatMessage/invokeTool 永远不变

## 核心特性

- **多工具支持**：bash 命令执行 + 5 个文件操作专用工具
- **路径沙箱**：所有文件操作基于当前工作目录，防止路径逃逸
- **参数校验**：通过 JSON Schema 进行严格的类型检查
- **类型安全**：`ToolDefinition` + 泛型 Schema 类实现强类型工具调用
- **灵活调度**：`List<ToolDefinition>` 列表式注册，一次遍历匹配工具名

## 实现原理

### 架构设计

```
+--------+      +-------+      +------------------+
|  User  | ---> |  LLM  | ---> | Tool Dispatch    |
| prompt |      |       |      | {                |
+--------+      +---+---+      |   bash: Bash     |
                    ^           |   read_file: Read |
                    |           |   write_file: Wr  |
                    +-----------+   edit_file: Edit |
                    tool_result |   list_files: List |
                                |   content_search: |
                                +------------------+
```

Agent02 使用**组合模式**，创建 `ZQAgent` 实例并注入工具列表：

```java
public class Agent02 {
    public static void main(String[] args) {
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(API_KEY).baseUrl(BASE_URL).build();

        List<ToolDefinition> tools = new ArrayList<>();
        tools.add(BashDefinition);
        tools.add(EditFileDefinition);
        tools.add(WriteFileDefinition);
        tools.add(ReadFileDefinition);
        tools.add(ListFilesDefinition);
        tools.add(ContentSearchDefinition);

        ZQAgent agent = new ZQAgent(client, MODEL, tools);
        agent.setSystemPrompt(buildSystemPrompt());
        agent.run();
    }
}
```

### 核心组件

| 组件 | 职责 |
|------|------|
| `Agent02` | 入口类，配置客户端、6 个工具和系统提示词 |
| `ZQAgent` | 智能体基类，实现工具调用循环（`run()` / `chatMessage()` / `invokeTool()`） |
| `ToolDefinition` | 工具定义类，支持 `Function<String,String>` 和 `TypedToolFunction<T>` 两种注册方式 |
| `Tools` | 工具实现类，包含 6 个工具的处理方法 |
| `AIConstants` | 常量配置（API地址、密钥、模型等） |

### 工具定义模式

Module 02 的 `ToolDefinition` 新增了 `TypedToolFunction<T>` 泛型接口，支持类型安全的工具注册：

```java
@FunctionalInterface
public interface TypedToolFunction<T> {
    String apply(T input) throws Exception;
}

@FunctionalInterface
public interface TypedToolInvoker {
    String apply(Object input) throws Exception;
}
```

`invoke()` 方法实现三路分发：typedInvoker → function(String) → function(Object→JSON)：

```java
public String invoke(Object convertedInput) throws Exception {
    if (typedInvoker != null) {
        return typedInvoker.apply(convertedInput);
    }
    if (function != null) {
        if (convertedInput instanceof String) {
            return function.apply((String) convertedInput);
        }
        return function.apply(OBJECT_MAPPER.writeValueAsString(convertedInput));
    }
    throw new IllegalStateException("没有该工具的处理方法: " + name);
}
```

## 可用工具

### 1. bash
执行 Shell 命令并返回输出结果。

**参数：**
- `command` (string, 必填): 要执行的命令字符串
- `type` (string, 必填): 命令类型（cmd / powershell / bash），不指定则自动检测

### 2. read_file
读取指定文件的内容。

**参数：**
- `path` (string, 必填): 文件的相对路径

### 3. write_file
将内容写入文件，文件不存在则创建。

**参数：**
- `path` (string, 必填): 文件路径
- `content` (string, 必填): 要写入的内容

### 4. edit_file
编辑文本文件，替换指定内容。`oldStr` 和 `newStr` 必须不同。

**参数：**
- `path` (string): 文件路径
- `oldStr` (string): 要被替换的文本
- `newStr` (string): 替换后的文本

> 注意：edit_file 的所有参数都是**可选**的（required 列表为空），由 LLM 自行决定填写哪些字段。

### 5. list_files
列出指定路径下的文件和目录，支持按文件类型筛选。

**参数：**
- `path` (string): 相对路径，默认为当前目录
- `fileType` (string): 文件扩展名筛选（如 "md"、"java"、"txt"）

### 6. content_search
使用 ripgrep (rg) 搜索代码或文本内容，支持正则表达式。

**参数：**
- `pattern` (string, 必填): 要搜索的文本或正则表达式
- `path` (string): 搜索路径（文件或目录）
- `fileType` (string): 文件扩展名筛选
- `caseSensitive` (boolean): 是否区分大小写，默认 false

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

### 编译运行

```bash
# 编译项目
mvn clean install

# 运行智能体
mvn exec:java -Dexec.mainClass="com.hoppinzq.agent.Agent02"
```

### 交互示例

```
你: 请创建一个名为hello.txt的文件，内容为"Hello, World!"
AI: 我将为您创建这个文件。
工具: write_file({"path":"hello.txt","content":"Hello, World!"})
结果: 已写入 13 字节到 hello.txt
AI: 文件已成功创建。

你: 读取hello.txt的内容
AI: 我来读取这个文件。
工具: read_file({"path":"hello.txt"})
结果: Hello, World!
AI: 文件内容是：Hello, World!

你: 查看src目录下所有的Java文件
工具: list_files({"path":"./src","fileType":"java"})
结果: Agent02.java  ToolDefinition.java  Tools.java ...

你: 搜索项目中所有包含TODO注释的代码
工具: content_search({"pattern":"TODO","path":"./src","fileType":"java"})
```

## 项目结构

```
hoppinzq-module-agent-02/
├── src/main/java/com/hoppinzq/agent/
│   ├── Agent02.java               # 入口类（组合模式，创建ZQAgent实例）
│   ├── base/
│   │   └── ZQAgent.java           # 智能体基类（工具调用循环）
│   ├── tool/
│   │   ├── ToolDefinition.java    # 工具定义（6个静态字段 + TypedToolFunction接口）
│   │   ├── Tools.java             # 工具实现（executeBash, readFile, writeFile等）
│   │   └── schema/
│   │       ├── BashInput.java     # Bash输入参数（command + type）
│   │       ├── ReadFileInput.java # read_file输入参数（path）
│   │       ├── WriteFileInput.java# write_file输入参数（path + content）
│   │       ├── EditFileInput.java # edit_file输入参数（path + oldStr + newStr）
│   │       ├── ListFilesInput.java# list_files输入参数（path + fileType）
│   │       └── ContentSearchInput.java # content_search输入参数
│   └── constant/
│       └── AIConstants.java       # 常量配置（API/模型/路径）
├── README.md                       # 本文件
└── s2.md                           # 多工具调度原理解析
```

## 技术栈

| 技术 | 用途 |
|------|------|
| Java 17 | 编程语言 |
| Anthropic Java SDK | LLM API 客户端 |
| OkHttp | HTTP 客户端（Anthropic SDK 底层） |
| Jackson | JSON 序列化/反序列化 |
| Lombok | 减少样板代码 |
| ripgrep (rg) | content_search 底层搜索引擎 |

## 设计亮点

1. **加工具不改循环**：dispatch 从硬编码 bash 调用升级为 `List<ToolDefinition>` 列表遍历 —— 循环体与 Module 01 完全一致
2. **路径沙箱**：所有文件操作通过 `Paths.get("").toAbsolutePath().resolve(path).normalize()` 解析，防止路径逃逸
3. **TypedToolFunction 泛型接口**：Module 02 新增类型安全的工具注册方式，参数直接以对象传入，无需手动 JSON 反序列化
4. **edit_file 无必填参数**：`required` 列表为空，LLM 根据上下文自行决定填写哪些字段 —— 更灵活的编辑体验
5. **组合模式延续**：与 Agent01 保持一致的设计模式，创建 ZQAgent 实例而非继承

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

// 3. 在 Agent02.main() 中注册
tools.add(MyToolDefinition);
```

## 注意事项

1. **API密钥安全**：不要将 API 密钥提交到版本控制系统
2. **命令执行风险**：Bash 工具可执行任意命令，生产环境需添加安全限制
3. **rg 依赖**：content_search 依赖 ripgrep，需确保系统已安装 rg
4. **日志控制**：`AIConstants.LOG_ENABLE = false` 关闭日志

## 许可证

MIT License

## 作者

@hoppinzq
