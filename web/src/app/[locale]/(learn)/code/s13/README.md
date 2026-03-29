# AI智能体 - Module 13 (MCP工具集成)

基于 Java 的 AI 智能体实现，通过 **Model Context Protocol (MCP)** 集成外部工具服务器，动态扩展 AI 的能力边界。

## 项目简介

Agent13 是项目中第一个引入外部协议的智能体模块。它通过 MCP（Model Context Protocol）连接外部工具服务器，将远程提供的工具动态注册到本地智能体中，使 LLM 能够调用原本不具备的专业能力。

核心思路：**6 个基础工具 + N 个 MCP 工具 = 动态扩展的智能体**。

Agent13 使用**组合模式**（不继承 ZQAgent），与 Agent01、Agent02 保持一致的架构风格。

## 核心特性

- **MCP 协议集成**：通过 MCP Java SDK 连接外部工具服务器，支持 STDIO、SSE、Streamable HTTP 三种传输方式
- **动态工具加载**：运行时从 MCP 服务器自动发现并注册工具，工具数量 = 6（基础） + N（MCP 动态）
- **配置文件驱动**：通过 `mcp.json` 配置 MCP 服务器（Claude 标准 MCP 配置格式），支持 `enabled` 字段控制启停
- **自动传输检测**：根据配置自动判断传输类型 —— 有 `command` 则 STDIO，有 `url` 则根据是否包含 `/sse` 区分 SSE / Streamable HTTP
- **统一工具接口**：McpLoader 将 MCP 工具的 JSON Schema 转换为 Anthropic `ToolDefinition`，LLM 无需感知工具来源
- **同步/异步客户端**：支持 MCP 同步客户端（默认）和异步客户端，通过 `ClientType` 配置
- **动态系统提示**：系统提示通过 `%s` 占位符动态注入 MCP 工具描述，LLM 能感知所有可用工具

## 实现原理

### 架构设计

```
+--------+      +-------+      +------------------+
|  User  | ---> |  LLM  | ---> |  invokeTool()    |
| prompt |      |       |      |  (ZQAgent)       |
+--------+      +---+---+      +--------+---------+
                    ^                    |
                    |   tool_result      |  判断工具来源
                    +--------------------+  (基础 / MCP)
                         (loop until stop)

                    +--------------------+
                    |  基础工具 (6个)     |  McpLoader.loadTools()
                    |  bash, read_file,  |  ──────────────────────>  MCP 服务器
                    |  write_file, ...   |     createExecuteFunction()
                    +--------------------+     callTool(toolName, args)
```

### 启动流程

```
main()
  │
  ├─ 1. 创建 AnthropicClient
  │
  ├─ 2. McpConfigLoader.loadMcpSettings()
  │     └─ 读取 resources/mcp.json
  │        └─ 解析 mcpServers 对象 (key=服务器名, value=配置)
  │           ├─ 有 command → STDIO (ServerParameters)
  │           └─ 有 url → SSE/Streamable_HTTP (WebFluxSseClientTransport)
  │
  ├─ 3. MCPAgent.initializeClient()
  │     └─ 遍历 settings，为每个创建 Transport + McpClient
  │
  ├─ 4. McpLoader.loadTools()
  │     └─ 遍历 settings → loadToolsFromSetting()
  │        └─ setting.getMcpSyncClient().listTools()
  │           └─ convertToToolDefinition() 转换为 ToolDefinition
  │              └─ createExecuteFunction() 生成执行闭包
  │
  ├─ 5. 组装工具列表: 6 基础 + N MCP
  │
  ├─ 6. new ZQAgent(client, MODEL, tools)
  │
  ├─ 7. buildSystemPrompt(mcpLoader)  (动态注入 MCP 工具描述)
  │
  └─ 8. agent.run()
```

### MCP 工具执行流程

MCP 工具的执行函数是一个闭包，由 `McpLoader.createExecuteFunction()` 创建：

```java
private Function<String, String> createExecuteFunction(McpSetting setting) {
    return new Function<String, String>() {
        @Override
        public String apply(String inputJson) {
            // 1. 解析 JSON 获取 tool_name 和 input
            JsonNode jsonNode = OBJECT_MAPPER.readTree(inputJson);
            String toolName = jsonNode.get("tool_name").asText();
            Map<String, Object> arguments = OBJECT_MAPPER.convertValue(
                    jsonNode.get("input"), new TypeReference<Map<String, Object>>() {});

            // 2. 调用 MCP 客户端执行工具
            McpSchema.CallToolResult callResult = setting.isSync()
                    ? setting.getMcpSyncClient().callTool(
                        new McpSchema.CallToolRequest(toolName, arguments))
                    : setting.getMcpAsyncClient().callTool(
                        new McpSchema.CallToolRequest(toolName, arguments)).block();

            // 3. 提取 TextContent 作为结果
            if (callResult != null && callResult.content() != null) {
                for (McpSchema.Content content : callResult.content()) {
                    if (content instanceof McpSchema.TextContent) {
                        return ((McpSchema.TextContent) content).text();
                    }
                }
            }
            return "";
        }
    };
}
```

### 核心组件

| 组件 | 职责 |
|------|------|
| `Agent13` | 入口类（组合模式），组装工具列表并启动 ZQAgent |
| `ZQAgent` | 智能体基类，实现工具调用循环（`run()` / `chatMessage()` / `invokeTool()`） |
| `McpConfigLoader` | 读取 `mcp.json`，解析 Claude 标准 MCP 配置格式，自动检测传输类型 |
| `MCPAgent` | MCP 客户端生命周期管理：初始化 Transport、创建 Client、listTools/Resources/Prompts、关闭 |
| `McpLoader` | MCP → Anthropic 桥梁：加载 MCP 工具列表，转换为 `ToolDefinition`，生成执行闭包 |
| `McpSetting` | MCP 服务器配置模型：mcpId、name、transportType(STDIO/SSE/Streamable_HTTP)、clientType(SYNC/ASYNC) |
| `ToolDefinition` | 工具定义类，包含 6 个基础工具的静态字段定义 |
| `Tools` | 6 个基础工具的实现（Bash、ReadFile、WriteFile、EditFile、ListFiles、ContentSearch） |

## 使用方法

### 环境要求

- Java 17+
- Maven 3.6+
- Anthropic API 密钥（或兼容代理服务）
- MCP 服务器（本地 JAR 或远程服务）

### 配置 MCP 服务器

编辑 `src/main/resources/mcp.json`（Claude 标准 MCP 配置格式）：

```json
{
  "mcpServers": {
    "服务器名称": {
      "command": "java",
      "args": ["-jar", "/path/to/mcp-server.jar"],
      "env": { "API_KEY": "your-key" }
    },
    "远程服务": {
      "url": "http://localhost:8080/mcp",
      "headers": { "Authorization": "Bearer your-token" }
    }
  }
}
```

传输类型自动检测规则：
- 有 `command` 字段 → **STDIO**（本地进程通信）
- 有 `url` 字段且 URL 包含 `/sse` → **SSE**（Server-Sent Events）
- 有 `url` 字段且 URL 不含 `/sse` → **Streamable HTTP**

可选字段：
- `enabled: false` — 禁用该服务器（默认 `true`）

### 编译运行

```bash
# 编译项目
mvn clean install

# 运行智能体
mvn exec:java -Dexec.mainClass="com.hoppinzq.agent.Agent13"
```

### 交互示例

```
=== MCP服务器连接状态 ===
已连接的MCP服务器:
- 潍柴论坛MCP (MCP服务器: 潍柴论坛MCP)
  类型: STDIO | 客户端: SYNC

成功加载 6 个MCP工具

你: 你有哪些工具可用？
AI: 我可以通过以下MCP服务器访问专业工具：
    - 潍柴论坛MCP：提供论坛数据操作能力
    也可以使用基础工具（bash、文件读写、搜索等）。
```

## 项目结构

```
hoppinzq-module-agent-13-mcp/
├── src/main/java/com/hoppinzq/agent/
│   ├── Agent13.java                         # 入口类（组合模式，创建ZQAgent实例）
│   ├── base/
│   │   └── ZQAgent.java                     # 智能体基类（工具调用循环）
│   ├── tool/
│   │   ├── ToolDefinition.java              # 工具定义（6个基础工具静态字段）
│   │   ├── Tools.java                       # 基础工具实现
│   │   ├── mcp/
│   │   │   ├── MCPAgent.java                # MCP客户端生命周期管理
│   │   │   ├── McpConfigLoader.java         # mcp.json配置加载器
│   │   │   ├── McpLoader.java               # MCP→Anthropic工具桥接
│   │   │   └── McpSetting.java              # MCP服务器配置模型
│   │   └── schema/
│   │       ├── BashInput.java               # Bash输入参数
│   │       ├── ContentSearchInput.java      # 内容搜索输入参数
│   │       ├── EditFileInput.java           # 编辑文件输入参数
│   │       ├── ListFilesInput.java          # 列出文件输入参数
│   │       ├── ReadFileInput.java           # 读取文件输入参数
│   │       └── WriteFileInput.java          # 写入文件输入参数
│   └── constant/
│       └── AIConstants.java                 # 常量配置（API/模型/路径）
├── src/main/resources/
│   └── mcp.json                             # MCP服务器配置（Claude标准格式）
├── README.md                                # 本文件
└── s13.md                                   # MCP工具集成原理解析
```

## 技术栈

| 技术 | 用途 |
|------|------|
| Java 17 | 编程语言 |
| Anthropic Java SDK | LLM API 客户端 |
| MCP Java SDK | Model Context Protocol 客户端 |
| OkHttp | HTTP 客户端（Anthropic SDK 底层） |
| WebFlux | SSE/Streamable HTTP 传输层 |
| Jackson | JSON 序列化/反序列化 |
| Lombok | 减少样板代码 |
| SLF4J | 日志框架 |

## 设计亮点

1. **组合模式**：Agent13 不继承 ZQAgent，而是创建实例并注入工具列表 —— 与 Agent01、Agent02 一致
2. **配置文件驱动**：通过 `mcp.json`（Claude 标准 MCP 配置格式）管理服务器，支持多服务器、启用/禁用控制
3. **自动传输检测**：McpConfigLoader 根据配置字段自动判断 STDIO / SSE / Streamable HTTP，无需手动指定
4. **MCP→Anthropic 无缝桥接**：McpLoader 将 MCP 工具的 JSON Schema 转换为 Anthropic `ToolDefinition`，LLM 统一调用，无需区分工具来源
5. **执行闭包模式**：`createExecuteFunction()` 为每个 MCP 工具创建独立的执行闭包，内部处理 JSON 解析、客户端调用、结果提取
6. **动态系统提示**：系统提示通过 `String.format("%s", mcpLoader.getToolDescriptions())` 注入 MCP 工具描述，LLM 自动感知可用工具
7. **资源生命周期管理**：`MCPAgent.closeClient()` 在 finally 块中调用，确保 MCP 客户端连接正确关闭

## 扩展开发

### 添加新的 MCP 服务器

在 `mcp.json` 中添加配置即可，无需修改代码：

```json
{
  "mcpServers": {
    "已有服务器": { ... },
    "新服务器": {
      "command": "python",
      "args": ["-m", "my_mcp_server"],
      "env": { "TOKEN": "xxx" }
    }
  }
}
```

### 使用异步客户端

在 `mcp.json` 中暂不支持，需在 `McpConfigLoader.parseMcpSetting()` 中扩展：

```java
// 在 McpConfigLoader.parseMcpSetting() 中读取配置
if (serverConfig.has("clientType") && "async".equals(serverConfig.get("clientType").asText())) {
    builder.clientType(McpSetting.ClientType.ASYNC);
}
```

### 添加自定义工具过滤

在 `McpLoader.loadTools()` 中可以按服务器名称或工具名称过滤：

```java
public List<ToolDefinition> loadTools(List<String> allowedServerNames) {
    return settings.stream()
            .filter(s -> allowedServerNames.contains(s.getName()))
            .flatMap(s -> loadToolsFromSetting(s).stream())
            .collect(Collectors.toList());
}
```

## 注意事项

1. **API密钥安全**：不要将 API 密钥和 MCP 服务器 token 提交到版本控制系统
2. **MCP stdio 日志**：MCP STDIO 模式下严禁使用 `System.out`，会干扰标准输入输出通信，使用 `log` 代替
3. **超时配置**：默认超时 10 秒，根据 MCP 服务器响应速度适当调整
4. **资源清理**：`MCPAgent.closeClient()` 必须在 finally 块中调用，确保连接关闭
5. **线程安全**：当前实现非线程安全，不要在多线程环境中使用

## 许可证

MIT License

## 作者

@hoppinzq
