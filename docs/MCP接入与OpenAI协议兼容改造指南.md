# MCP 接入与 OpenAI 协议兼容改造指南

> 本文记录 `wc-module-demo-app` 中 MCP 接入改造的完整做法，供在其他 Spring Boot 工程中复用。
> 覆盖：请求头配置、`mcp.json` 传输类型、Streamable HTTP 传输、MCP 工具注册、OpenAI / Anthropic 协议兼容、启动报错修复。

---

## 0. 先看症状：你是否也需要这套改造

| 症状 | 根因 | 对应章节 |
|------|------|----------|
| 启动报 `No suitable default ClientHttpConnector found` | `mcp-spring-webflux` 只带 `spring-webflux`，classpath 上没有 Reactor Netty | [3.1](#31-依赖) |
| `mcp.json` 里配了 `X-Api-Key`，服务端却返回 401 | 客户端只写死了 `Authorization` 头，自定义请求头没生效 | [3.2](#32-配置模型-mcpsetting) [3.4](#34-传输层选择-mcpagent) |
| 配了 `type: streamable-http` 和 `/mcp` 地址，却连不上 | SDK 0.10.0 只有废弃的 SSE 传输，打不到 Streamable 端点 | [3.5](#35-streamable-http-传输实现) |
| MCP 工具在提示词里能看到，模型调用却提示 `Tool not found` | MCP 工具没有注册进 Agent 的工具列表 | [3.6](#36-注册-mcp-工具) |
| 换成 Anthropic 协议正常，换 OpenAI（DeepSeek 等）后工具调用丢失/400 | 工具 schema 只保留了 `properties`；流式 tool_call 缺 id 时被静默丢弃 | [3.7](#37-schema-与执行函数协议兼容) [3.8](#38-openai-provider-工具调用兜底) |

---

## 1. 版本前提（很重要）

本方案的实现方式和下面的版本强相关：

| 组件 | 当前版本 | 说明 |
|------|----------|------|
| JDK | 17 | 使用 `java.net.http.HttpClient` |
| Spring Boot | 2.7.x（Spring 5.3） | 决定能否直接用新版 MCP SDK |
| MCP SDK | `io.modelcontextprotocol.sdk:mcp` / `mcp-spring-webflux` **0.10.0** | 0.10.0 只有 SSE 客户端传输 |
| Reactor | 3.4.x（Boot BOM 管理） | 与 Spring 5.3 配套 |
| Jackson | 2.13.x | — |

**版本结论：**

1. **Spring Boot 2.7 / Spring 5.3 不能直接升 MCP SDK 0.12.x。**
   0.12.1 的 `WebClientStreamableHttpTransport` 引用了 `org.springframework.http.HttpStatusCode`（Spring 6.0 才引入），且依赖 Reactor 3.7 / Jackson 2.17；实测会直接 `NoClassDefFoundError`。
2. 因此 Streamable HTTP 需要**自己实现一个 `McpClientTransport`**（见 3.5），只用 JDK HttpClient，不引入任何新框架依赖。
3. 如果是 **Spring Boot 3.x / Spring 6** 的新工程：直接升 `mcp` + `mcp-spring-webflux` 到 0.12.x 及以上，用自带的 `WebClientStreamableHttpTransport` 即可，**不需要** 3.5 的自研类，其余章节仍然适用。

---

## 2. 整体结构

```
mcp.json ──> McpConfigLoader ──> McpSetting（type / url / headers / timeout）
                                      │
                                      ▼
                                  MCPAgent ──> Transport（三选一）
                                      │          ├─ StdioClientTransport        (stdio)
                                      │          ├─ WebFluxSseClientTransport   (sse)
                                      │          └─ StreamableHttpClientTransport(streamable-http)
                                      ▼
                                  McpSyncClient / McpAsyncClient
                                      │
                     ┌────────────────┴────────────────┐
                     ▼                                 ▼
         McpLoader.loadTools()                McpLoader.getToolDescriptions()
       （转成 ToolDefinition 注册给 Agent）        （写进 system prompt）
```

关键原则：
- **配置解析、传输选择、工具转换三层分开**，换传输方式只动 `MCPAgent` 的 switch。
- **工具 schema 用协议中立的 ObjectNode**，由各 Provider 自己转 SDK 类型。
- **单个 MCP 服务不可用不能影响应用启动**。

---

## 3. 改造步骤（可照抄）

### 3.1 依赖

在声明了 `mcp-spring-webflux` 的模块（本工程是 `wc-spring-boot-starter-ai`）以及实际运行的业务模块 pom 中都加上：

```xml
<!-- MCP SDK -->
<dependency>
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>mcp</artifactId>
    <version>0.10.0</version>
</dependency>
<dependency>
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>mcp-spring-webflux</artifactId>
    <version>0.10.0</version>
</dependency>

<!-- 关键：WebClient 的默认 ClientHttpConnector 由 Reactor Netty 提供 -->
<dependency>
    <groupId>io.projectreactor.netty</groupId>
    <artifactId>reactor-netty-http</artifactId>
    <!-- 版本交给 spring-boot-dependencies → reactor-bom 管理即可 -->
</dependency>
```

> 为什么两个地方都加：框架 starter 负责传递依赖；但如果你引用的是**远端快照 jar**，它的 pom 可能还是旧的，业务模块显式声明可以立即生效。等待框架重新发布后，业务模块那一条可以去掉。

验证：

```bash
mvn dependency:tree -Dincludes=io.projectreactor.netty:reactor-netty-http
# 期望看到 reactor-netty-http:jar:1.0.x:compile
```

---

### 3.2 配置模型 McpSetting

要点：
- `headers` 作为主要的自定义请求头配置（`X-Api-Key`、`Cookie` 等都能配）；
- 保留 `authorization` 兼容老配置；
- 增加 `headerName` / `headerValue` 单请求头便捷写法；
- 提供 `resolveHeaders()` 统一合并，**过滤 null**（`HttpHeaders` 不接受 null 值）。

```java
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class McpSetting {
    public static final String sseEndpoint = "/sse";

    private String baseUrl;
    private String mcpId;
    private String name;
    private String description;

    @Builder.Default
    private ClientType clientType = ClientType.SYNC;

    // 读配置文件（mcp.json 的 type 字段）：stdio / sse / streamable-http
    @Builder.Default
    private TransportType transportType = TransportType.Streamable_HTTP;

    private McpSyncClient mcpSyncClient;
    private McpAsyncClient mcpAsyncClient;
    private Duration timeout;
    private ServerParameters serverParameters;
    private String sseUrl;

    // 旧字段：等价于 headers 中的 Authorization
    private String authorization;
    // 主要配置：任意自定义请求头
    @Builder.Default
    private Map<String, String> headers = new LinkedHashMap<>();
    // 便捷配置：单个请求头
    private String headerName;
    private String headerValue;

    private List<McpSchema.Root> roots;

    public Boolean isSync() {
        return this.getClientType() == ClientType.SYNC;
    }

    /**
     * 合并实际发送的请求头。
     * 优先级：headers > headerName/headerValue > authorization
     */
    public Map<String, String> resolveHeaders() {
        Map<String, String> resolved = new LinkedHashMap<>();
        if (authorization != null && !authorization.isBlank()) {
            resolved.put("Authorization", authorization.trim());
        }
        if (headerName != null && !headerName.isBlank() && headerValue != null) {
            resolved.put(headerName.trim(), headerValue);
        }
        if (headers != null) {
            headers.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null) {
                    resolved.put(key.trim(), value);
                }
            });
        }
        return resolved;
    }

    public enum ClientType { SYNC, ASYNC }

    public enum TransportType { STDIO, SSE, Streamable_HTTP }
}
```

> `@Builder.Default` 不能省：否则用 builder 构建时字段初始值会被忽略（`headers` 会变成 null）。

---

### 3.3 mcp.json 与类型解析

`mcp.json` 增加 `type` 字段，显式声明传输方式；地址、超时、请求头都在同一条配置里：

```json
{
  "mcpServers": {
    "本地stdio服务": {
      "type": "stdio",
      "enabled": false,
      "command": "java",
      "args": ["-jar", "D:\\mcp\\your-mcp-server.jar"],
      "env": { "TOKEN": "xxx" }
    },
    "远程streamable服务": {
      "type": "streamable-http",
      "enabled": true,
      "url": "http://your-host:18080/mcp",
      "timeout": 60,
      "headers": {
        "X-Api-Key": "your-api-key"
      }
    },
    "远程sse服务": {
      "type": "sse",
      "enabled": false,
      "url": "http://your-host:9002/sse",
      "headerName": "Authorization",
      "headerValue": "Bearer xxx"
    }
  }
}
```

加载器解析规则（`McpConfigLoader`）：

```java
private McpSetting.TransportType resolveTransportType(JsonNode serverConfig) {
    String declaredType = getText(serverConfig, "type", null);
    if (declaredType != null && !declaredType.isBlank()) {
        String normalized = declaredType.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        switch (normalized) {
            case "stdio":            return McpSetting.TransportType.STDIO;
            case "sse":              return McpSetting.TransportType.SSE;
            case "http":
            case "streamable-http":
            case "streamablehttp":   return McpSetting.TransportType.Streamable_HTTP;
            default:
                throw new IllegalArgumentException("不支持的 type 配置：" + declaredType
                        + "（可选值：stdio / sse / streamable-http）");
        }
    }
    // 兼容旧配置：未写 type 时按 command/url 推断
    if (serverConfig.has("command")) {
        return McpSetting.TransportType.STDIO;
    }
    if (serverConfig.has("url")) {
        String url = serverConfig.get("url").asText();
        return url.contains("/sse")
                ? McpSetting.TransportType.SSE
                : McpSetting.TransportType.Streamable_HTTP;
    }
    throw new IllegalArgumentException("无法确定传输类型：请配置 type 字段，或提供 'command' / 'url'");
}
```

地址处理：

```java
switch (transportType) {
    case STDIO:
        configureStdio(builder, serverConfig);   // 必填 command，解析 args/env
        break;
    case SSE:
        // SSE：客户端会自己拼 /sse，baseUrl 必须把 /sse 去掉，否则变成 /sse/sse
        configureSseUrl(builder, requireUrl(serverConfig));
        configureHttp(builder, serverConfig);
        break;
    case Streamable_HTTP:
    default:
        // Streamable HTTP：完整地址原样使用
        String url = requireUrl(serverConfig);
        builder.baseUrl(trimTrailingSlash(url));
        builder.sseUrl(url);
        configureHttp(builder, serverConfig);
        break;
}
```

请求头 / 超时解析：(McpSetting.McpSettingBuilder builder, JsonNode configNode) {
    JsonNode headersNode = configNode.get("headers");
    if (headersNode != null && headersNode.isObject()) {
        Map<String, String> headersMap = objectMapper.convertValue(headersNode, Map.class);
        builder.headers(headersMap);
        if (headersMap.containsKey("Authorization")) {
            builder.authorization(headersMap.get("Authorization"));
        }
    }
    builder.headerName(getText(configNode, "headerName", null));
    builder.headerValue(getText(configNode, "headerValue", null));

    String authorization = getText(configNode, "authorization", null);
    if (authorization != null) {
        builder.authorization(authorization);
    }
}

// timeout：单位秒，默认 60
long timeoutSeconds = getLong(serverConfig, "timeout", 60);
builder.timeout(Duration.ofSeconds(timeoutSeconds));
```

---

### 3.4 传输层选择 MCPAgent

三处关键改动：

1. 每个服务单独 try/catch，**初始化失败只跳过，不阻断 Spring 启动**；
2. 请求头通过 `resolveHeaders()` 统一写入；
3. `sse` 和 `streamable-http` 走不同传输。

```java
public void initializeClient() {
    mcpSettings.forEach(setting -> {
        try {
            initializeClient(setting);
        } catch (Exception e) {
            // 单个 MCP 不可用不应拖垮整个应用启动
            log.error("初始化 MCP 服务器 [{}] 失败，已跳过：{}", setting.getName(), e.getMessage(), e);
        }
    });
}

private void initializeClient(McpSetting setting) {
    if (setting.getName() == null) {
        setting.setName(setting.getMcpId());
    }

    McpClientTransport transport;
    switch (setting.getTransportType()) {
        case STDIO:
            transport = new StdioClientTransport(setting.getServerParameters());
            break;
        case SSE:
            transport = new WebFluxSseClientTransport(
                    buildWebClient(setting), OBJECT_MAPPER, McpSetting.sseEndpoint);
            break;
        case Streamable_HTTP:
            transport = new StreamableHttpClientTransport(
                    setting.getSseUrl() != null ? setting.getSseUrl() : setting.getBaseUrl(),
                    setting.resolveHeaders(),
                    OBJECT_MAPPER,
                    setting.getTimeout());
            break;
        default:
            throw new RuntimeException("不支持的传输方式：" + setting.getTransportType());
    }

    if (setting.isSync()) {
        McpClient.SyncSpec spec = McpClient.sync(transport).requestTimeout(setting.getTimeout());
        if (setting.getRoots() != null) {
            spec.roots(setting.getRoots());
        }
        McpSyncClient client = spec.build();
        client.initialize();
        setting.setMcpSyncClient(client);
    } else {
        McpClient.AsyncSpec spec = McpClient.async(transport).requestTimeout(setting.getTimeout());
        if (setting.getRoots() != null) {
            spec.roots(setting.getRoots());
        }
        McpAsyncClient client = spec.build();
        // 注意：异步 initialize 返回 Mono，是惰性的，必须订阅（block）才会真正建连
        client.initialize().block();
        setting.setMcpAsyncClient(client);
    }
}

/** 统一注入所有自定义请求头，不能再用 header.add("Authorization", null) */
private WebClient.Builder buildWebClient(McpSetting setting) {
    WebClient.Builder builder = WebClient.builder().baseUrl(setting.getBaseUrl());
    Map<String, String> requestHeaders = setting.resolveHeaders();
    if (!requestHeaders.isEmpty()) {
        builder.defaultHeaders(httpHeaders -> requestHeaders.forEach(httpHeaders::set));
    }
    return builder;
}
```

---

### 3.5 Streamable HTTP 传输实现

**什么时候需要：** SDK `< 0.11`，或工程还是 Spring 5.3。
**什么时候不需要：** Spring Boot 3 / Spring 6 + MCP SDK 0.12+，直接用 `WebClientStreamableHttpTransport`。

实现要点（对照代码看）：
- 每条 JSON-RPC 消息 `POST` 到端点；
- 必须带 `Accept: application/json, text/event-stream`，服务端可能回 JSON 也可能回 SSE；
- 从响应头拿 `Mcp-Session-Id`，后续请求回传；
- 通知类消息返回 `202`，无响应体；
- 收到的消息交给 `McpClientSession` 提供的 handler 派发（**不能 block**，否则死锁）。

完整类（可直接复制，改包名即可）：

```java
package com.your.pkg.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

@Slf4j
public class StreamableHttpClientTransport implements McpClientTransport {

    private static final String SESSION_ID_HEADER = "Mcp-Session-Id";

    private final URI endpoint;
    private final Map<String, String> headers;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Duration timeout;
    private final AtomicReference<String> sessionId = new AtomicReference<>();
    private volatile Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> handlerRef =
            msg -> Mono.empty();

    public StreamableHttpClientTransport(String url, Map<String, String> headers,
                                         ObjectMapper objectMapper, Duration timeout) {
        this.endpoint = URI.create(url);
        this.headers = headers;
        this.objectMapper = objectMapper;
        this.timeout = timeout != null ? timeout : Duration.ofSeconds(60);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public Mono<Void> connect(Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> handler) {
        this.handlerRef = handler;
        // 请求-响应模型，不需要长连接；服务端主动推送（GET 流）暂不实现
        return Mono.empty();
    }

    @Override
    public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
        return Mono.fromCallable(() -> {
            String payload = objectMapper.writeValueAsString(message);
            HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
            String sid = sessionId.get();
            if (sid != null && !sid.isBlank()) {
                builder.header(SESSION_ID_HEADER, sid);
            }
            headers.forEach(builder::header);

            HttpResponse<InputStream> response = httpClient.send(
                    builder.build(), HttpResponse.BodyHandlers.ofInputStream());

            response.headers().firstValue(SESSION_ID_HEADER).ifPresent(sessionId::set);

            int status = response.statusCode();
            String contentType = response.headers()
                    .firstValue("Content-Type").orElse("").toLowerCase();

            if (status == 202) {
                closeQuietly(response.body());
                return null;   // 通知消息，无响应体
            }
            if (status >= 400) {
                throw new IllegalStateException(String.format(
                        "MCP 请求失败: HTTP %d, url=%s, body=%s",
                        status, endpoint, readAll(response.body())));
            }
            if (contentType.contains("text/event-stream")) {
                readSseStream(response.body());
            } else {
                String body = readAll(response.body());
                if (!body.isBlank()) {
                    dispatch(McpSchema.deserializeJsonRpcMessage(objectMapper, body));
                }
            }
            return null;
        }).then();
    }

    @Override
    public Mono<Void> closeGracefully() {
        return Mono.fromRunnable(() -> {
            String sid = sessionId.get();
            if (sid == null || sid.isBlank()) {
                return;
            }
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                        .timeout(timeout)
                        .header(SESSION_ID_HEADER, sid)
                        .DELETE();
                headers.forEach(builder::header);
                httpClient.send(builder.build(), HttpResponse.BodyHandlers.discarding());
            } catch (Exception e) {
                log.debug("[MCP] 关闭会话失败: {}", e.getMessage());
            }
        }).then();
    }

    @Override
    public <T> T unmarshalFrom(Object data, TypeReference<T> typeRef) {
        return objectMapper.convertValue(data, typeRef);
    }

    /** 解析 SSE 流并按事件派发 JSON-RPC 消息 */
    private void readSseStream(InputStream body) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            StringBuilder data = null;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    dispatchSseData(data);
                    data = null;
                    continue;
                }
                if (line.startsWith(":")) {
                    continue; // 注释行
                }
                int colon = line.indexOf(':');
                String field = colon < 0 ? line : line.substring(0, colon);
                String value = colon < 0 ? "" : line.substring(colon + 1);
                if (value.startsWith(" ")) {
                    value = value.substring(1);
                }
                if ("data".equals(field)) {
                    if (data == null) {
                        data = new StringBuilder();
                    } else {
                        data.append('\n');
                    }
                    data.append(value);
                }
            }
            dispatchSseData(data);
        }
    }

    private void dispatchSseData(StringBuilder data) {
        if (data == null || data.length() == 0) {
            return;
        }
        try {
            dispatch(McpSchema.deserializeJsonRpcMessage(objectMapper, data.toString()));
        } catch (Exception e) {
            log.error("[MCP] 解析 SSE 消息失败: {}", e.getMessage(), e);
        }
    }

    private void dispatch(McpSchema.JSONRPCMessage message) {
        if (message == null) {
            return;
        }
        // 交给 McpClientSession 路由到对应请求；不能 block，否则会和发送线程互相等待
        handlerRef.apply(Mono.just(message)).subscribe(
                null, e -> log.error("[MCP] 处理服务端消息失败: {}", e.getMessage(), e));
    }

    private static String readAll(InputStream in) {
        try (InputStream input = in) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static void closeQuietly(InputStream in) {
        try {
            in.close();
        } catch (Exception ignored) {
        }
    }
}
```

> **反序列化必须用 `McpSchema.deserializeJsonRpcMessage(objectMapper, json)`**，不要直接 `objectMapper.readValue(json, JSONRPCMessage.class)`——后者是接口，没有默认反序列化器。

---

### 3.6 注册 MCP 工具

只把 MCP 工具写进 system prompt 是不够的：ReAct 按名字分发、原生 function calling 按 schema 分发，都要求工具在**工具列表**里。

```java
List<ToolDefinition> addTools = new ArrayList<>();
addTools.add(BashDefinition);
// ... 其他内置工具

// 注册 MCP 工具；与内置工具重名时以内置工具为准
Set<String> toolNames = addTools.stream()
        .map(ToolDefinition::getName)
        .collect(Collectors.toSet());
for (ToolDefinition mcpTool : mcpLoader.loadTools()) {
    if (toolNames.add(mcpTool.getName())) {
        addTools.add(mcpTool);
    }
}

this.tools = addTools;
this.zqAgent.setTools(addTools);
this.zqAgent.setSystemPrompt(buildSystemPrompt(mcpLoader));
```

⚠️ **初始化顺序**：如果工具定义类（`ToolDefinition` 静态字段）依赖 Agent 的静态 manager，必须先 `initManagers(...)`，再访问任何 `ToolDefinition.*`，否则静态初始化直接 NPE。MCP 工具注册放在内置工具之后即可。

---

### 3.7 Schema 与执行函数协议兼容

#### 3.7.1 工具 schema：不要只保留 properties

原实现只把 `properties + required` 拼成 schema。Anthropic 能接受，但 OpenAI 系端点会校验完整 JSON Schema：一旦 schema 里有 `$ref: "#/$defs/..."`，丢掉 `$defs` 就会 400。同时 `"properties": null` 也会被拒绝。

```java
private ObjectNode buildInputSchema(McpSchema.JsonSchema jsonSchema) {
    ObjectNode schema = OBJECT_MAPPER.createObjectNode();
    if (jsonSchema != null) {
        // 原样序列化，保留 type/properties/required/additionalProperties/$defs/definitions
        ObjectNode raw = OBJECT_MAPPER.valueToTree(jsonSchema);
        // 剔除 null 字段，避免 OpenAI 兼容端点对 "properties": null 报 400
        raw.fields().forEachRemaining(entry -> {
            if (entry.getValue() != null && !entry.getValue().isNull()) {
                schema.set(entry.getKey(), entry.getValue());
            }
        });
    }
    if (!schema.hasNonNull("type")) {
        schema.put("type", "object");
    }
    if (!schema.has("properties") || !schema.get("properties").isObject()) {
        schema.set("properties", OBJECT_MAPPER.createObjectNode());
    }
    return schema;
}
```

另外 `description` 缺失时用工具名兜底（OpenAI / Anthropic 的 function description 都不接受 null）：

```java
.description(mcpTool.description() != null ? mcpTool.description() : mcpTool.name())
```

#### 3.7.2 执行函数：兼容两种入参形态

- Agent 内部对"无类型工具"会包装成 `{"tool_name": "...", "input": {...}}`；
- 原生 function calling 也可能直接传工具参数 `{...}`。

两种都要支持，否则换协议就调不动：

```java
JsonNode jsonNode = OBJECT_MAPPER.readTree(
        inputJson == null || inputJson.isBlank() ? "{}" : inputJson);

String toolName = mcpToolName;                 // 构造时传入的兜底工具名
JsonNode nameNode = jsonNode.get("tool_name");
if (nameNode != null && nameNode.isTextual() && !nameNode.asText().isBlank()) {
    toolName = nameNode.asText();
}

JsonNode inputNode = jsonNode.get("input");
Map<String, Object> arguments;
if (inputNode != null) {
    arguments = OBJECT_MAPPER.convertValue(inputNode, new TypeReference<Map<String, Object>>() {});
} else {
    arguments = OBJECT_MAPPER.convertValue(jsonNode, new TypeReference<Map<String, Object>>() {});
    arguments.remove("tool_name");
    arguments.remove("input");
}
```

工具结果里多个文本块要**拼接**而不是"后一个覆盖前一个"：

```java
StringBuilder text = new StringBuilder();
for (McpSchema.Content content : callResult.content()) {
    if (content instanceof McpSchema.TextContent) {
        text.append(((McpSchema.TextContent) content).text());
    }
}
```

---

### 3.8 OpenAI Provider 工具调用兜底

OpenAI 兼容端点（DeepSeek、各类代理）在流式 tool_call 上行为不完全一致，需要容错：

1. **按 index 排序**（`TreeMap`），保证多工具调用顺序稳定；
2. **id 缺失时生成稳定 id**（否则原实现 `filter(id != null)` 会把调用**静默丢弃**）；
3. **arguments 为空时补 `{}`**。

```java
Map<Long, LlmMessage.ToolCall> pendingCalls = new java.util.TreeMap<>();
// ... 流式分片累积：id/name 首片到达，arguments 逐片拼接 ...

pendingCalls.forEach((index, call) -> {
    if (call.getName() == null || call.getName().isBlank()) {
        return;
    }
    if (call.getId() == null || call.getId().isBlank()) {
        // 后续 tool 消息必须回传同一个 id
        call.setId("call_" + index);
    }
    if (call.getArgumentsJson() == null || call.getArgumentsJson().isBlank()) {
        call.setArgumentsJson("{}");
    }
    listener.onToolUse(call.getId(), call.getName(), call.getArgumentsJson());
});
```

回灌历史消息时也做空值防护：

```java
if (m.getToolCalls() != null) {
    for (LlmMessage.ToolCall tc : m.getToolCalls()) {
        // arguments 为空时用 "{}"
    }
}
```

---

### 3.9 启动健壮性

- `MCPAgent` 对每个服务 try/catch，失败只 `log.error` 跳过；
- `McpLoader` 增加 `isInitialized(setting)` 判断，避免对未建连的客户端反复抛 NPE：

```java
private boolean isInitialized(McpSetting setting) {
    return setting.isSync()
            ? setting.getMcpSyncClient() != null
            : setting.getMcpAsyncClient() != null;
}
```

`loadToolsFromSetting()` 和 `getToolDescriptions()` 开头：

```java
// loadToolsFromSetting(McpSetting setting)：方法内返回空列表
if (!isInitialized(setting)) {
    log.warn("MCP 服务器 [{}] 未初始化成功，跳过工具加载", setting.getName());
    return new ArrayList<>();
}

// getToolDescriptions()：循环内直接跳过该服务
for (McpSetting setting : mcpSettings) {
    if (!isInitialized(setting)) {
        continue;
    }
    // ...
}
```

> 代价：启动时仍会同步访问一次 MCP 服务（`initialize` + `listTools`）。如果服务很慢，会拖慢启动。可在配置里把 `timeout` 调小（例如 10s），或把 MCP 初始化改为懒加载。

---

## 4. 验证清单

### 4.1 编译

```bash
mvn -o compile -DskipTests
```

### 4.2 连接器是否就绪（修 `ClientHttpConnector` 报错）

```java
// 一个最小 main 即可验证
WebClient wc = WebClient.builder().baseUrl("http://localhost:1").build();
System.out.println("OK: " + wc);
```

用 `mvn dependency:build-classpath` 生成 classpath 后运行即可。能打印 `OK` 就说明 Reactor Netty 生效。

### 4.3 探测 MCP 服务端点类型

```bash
# 老 SSE 端点：应返回 200 + text/event-stream
curl -N -H "Accept: text/event-stream" http://your-host:9002/sse

# Streamable HTTP 端点：POST initialize 应返回 200 + JSON，并带 mcp-session-id 响应头
curl -i -X POST http://your-host:18080/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -H "X-Api-Key: your-api-key" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"t","version":"1"}}}'
```

- 返回 `404`：地址不对，或该服务根本没有 SSE 端点（不要配 `type: sse`）；
- 返回 `400`：多半是缺 `Accept` 头或 JSON 不合法。

### 4.4 最小联调

初始化 + 列工具 + 调一个工具：

```java
List<McpSetting> settings = new McpConfigLoader().loadMcpSettings();
settings.forEach(s -> s.setTimeout(Duration.ofSeconds(20)));
new MCPAgent(settings).initializeClient();

List<ToolDefinition> tools = new McpLoader(settings).loadTools();
tools.forEach(t -> System.out.println(t.getName() + " | " + t.getDescription()));
// 注意：访问 ToolDefinition 静态字段前必须先绑定 manager（与控制器顺序一致）
```

期望日志：

```
加载MCP服务器配置: xxx (Streamable_HTTP)
Server response with Protocol: 2024-11-05, Capabilities: ...
TOOL get_xxx | ...
```

---

## 5. 故障排查表

| 现象 | 可能原因 | 处理 |
|------|----------|------|
| `No suitable default ClientHttpConnector found` | 缺 `reactor-netty-http` | 加依赖并确认 `dependency:tree` |
| `404` / `GET /sse` 不通 | 服务是 Streamable HTTP，不是 SSE | `type` 改成 `streamable-http`，URL 用 `/mcp` |
| `Mcp 请求失败: HTTP 400` | 缺 `Accept: application/json, text/event-stream`、缺 session id、JSON 不合法 | 检查 transport 实现和请求头 |
| 启动卡住 / 超时 | MCP 服务不可达，`timeout` 太大 | 调小 `timeout`，或确认网络/地址 |
| 工具调用 `Tool not found` | MCP 工具没注册进 Agent 工具列表 | 参见 3.6 |
| OpenAI 下工具有时"消失" | 流式 tool_call 无 id 被过滤 | 参见 3.8 |
| OpenAI 返回 400 且提到 schema/`$ref` | schema 丢了 `$defs`，或 `properties: null` | 参见 3.7.1 |
| `NoClassDefFoundError: org/springframework/http/HttpStatusCode` | 在 Spring 5.3 上用了 MCP SDK 0.12.x 的 WebFlux transport | 降回 0.10.0 + 自研 transport，或升级到 Spring 6 |
| 启动日志里的 MCP 服务名和你配置的不一样 | classpath 上有别的 jar 带了同名 `mcp.json`，资源被遮蔽 | 调用 `mcp.json` 改名（如 `mcp-config.json`）或清理旧 jar |

> 最后一条是实际踩过的坑：框架快照 jar 里残留过一份 `mcp.json`，classpath 顺序一变就会加载到旧配置。资源文件名最好带项目前缀，例如 `your-app-mcp.json`。

---

## 6. 迁移到其他项目的步骤清单

1. **确认版本**：Spring Boot 2.x？→ 用本文 3.5 自研 transport；Spring Boot 3.x？→ 升 MCP SDK 0.12+，用官方 `WebClientStreamableHttpTransport`。
2. **加依赖**：`mcp`、`mcp-spring-webflux`、`reactor-netty-http`。
3. **抄配置模型**：`McpSetting`（headers / resolveHeaders / type）。
4. **抄加载器**：`McpConfigLoader`（type 解析、SSE/Streamable 地址规则、headers/headerName 解析、timeout）。
5. **抄传输层**：`MCPAgent`（三路 switch、单服务失败隔离、header 注入），必要时加 `StreamableHttpClientTransport`。
6. **注册工具**：在 Agent 构造时 `addTools.addAll(mcpLoader.loadTools())`，注意 manager 初始化顺序。
7. **修 schema / 执行函数**：完整保留 JSON Schema，兼容两种入参形态，文本块拼接。
8. **修 Provider**：OpenAI 流式 tool_call 按 index 排序、id/arguments 兜底。
9. **验证**：4.2 连接器 → 4.3 端点探测 → 4.4 最小联调 → 实际对话调用。

需要改动的文件一览：

| 文件 | 作用 |
|------|------|
| `mcp/McpSetting.java` | 请求头配置、传输类型、`resolveHeaders()` |
| `mcp/McpConfigLoader.java` | `type` 解析、SSE/Streamable 地址处理、headers 解析 |
| `mcp/MCPAgent.java` | 传输选择、header 注入、启动失败隔离 |
| `mcp/StreamableHttpClientTransport.java` | **新增**，Streamable HTTP 客户端传输 |
| `mcp/McpLoader.java` | Schema 转换、工具执行函数、初始化判断 |
| `client/OpenAIProvider.java` | 流式 tool_call 兜底、历史消息空值防护 |
| `controller/.../AgentChatController.java` | MCP 工具注册 |
| `resources/mcp.json` | `type` / `url` / `headers` / `timeout` 配置 |
| `pom.xml`（模块 + starter） | `reactor-netty-http` |

---

## 7. 注意事项

1. **不要把真实 token 提交到仓库。** `mcp.json` 里的 `X-Api-Key`、`env` 中的 token 建议改为环境变量或配置中心占位符。
2. **ReAct 与原生 function calling 的差异**：
   - ReAct 模式：工具以文本描述 + 名称集合进入 system prompt，模型输出 `Action:` 后按名字分发，**要求工具必须在工具列表里**才能被分发；
   - 原生模式：工具 schema 直接随请求发送，模型返回 `tool_calls`。
   两种模式都要保证 MCP 工具已注册，否则提示词里"能看到"但"调不到"。
3. **工具名限制**：OpenAI 的函数名要求 `^[a-zA-Z0-9_-]{1,64}$`。如果 MCP 工具名包含 `.`、`/`、`:` 等字符，需要在转换时做映射（同时维护反向映射用于执行）。
4. **超时**：`timeout` 同时作用于 MCP 请求和（自研 transport 的）HttpClient connect 超时。工具耗时较长时不要设太小。
5. **会话**：Streamable HTTP 是会话化的，务必保存并回传 `Mcp-Session-Id`；收到 404 时按规范应重新 `initialize`（当前实现未自动重连，可按需补充）。
6. **服务端主动消息**：自研 transport 暂不支持 GET SSE 流（服务端主动推送、`listChanged` 通知）。如果依赖这些能力，需要在 `connect()` 里补一条 GET 长连接。