package com.hoppinzq.anthropic.mcp;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hoppinzq.anthropic.agent.ZQAgent;
import com.hoppinzq.anthropic.tool.ToolDefinition;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.spec.McpSchema;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static com.hoppinzq.anthropic.constant.AIConstants.*;
import static com.hoppinzq.anthropic.tool.ToolDefinition.BashDefinition;
import static com.hoppinzq.anthropic.tool.ToolDefinition.ReadFileDefinition;

/**
 * 使用mcp server配置
 * 示例提示词：AI，你有哪些工具？这些工具的定义是什么？
 * @author hoppinzq
 */
public class McpChatAgent {

    public static void main(String[] args) {
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(API_KEY)
                .baseUrl(BASE_URL)
                .build();

        List<McpSetting> settings = new ArrayList<>();
        // 配置mcp server的配置示例
        // 1、stdio
        ServerParameters jar = ServerParameters.builder("java")
                .args("-Dfile.encoding=UTF-8",
                        "-jar",
                        "D:\\mcp\\hoppinzq-module-spring-ai-mcp-server-0.0.1-SNAPSHOT.jar")
                .env(Map.of("ZQ_TOKEN", "hoppinzq"))
                .build();
        settings.add(
                McpSetting.builder()
                    .mcpId(UUID.randomUUID().toString())
                    .name("演示stdio的mcp")
                    .description("这是一个测试用的mcp。")
                    .clientType(McpSetting.ClientType.SYNC)
                    .timeout(Duration.ofSeconds(10))
                    .serverParameters(jar)
                    .transportType(McpSetting.TransportType.STDIO)
                    .anthropicClient(client)
                    .build()
        );

        // 2、sse,streamable http配置示例
        settings.add(
                McpSetting.builder()
                        .mcpId(UUID.randomUUID().toString())
                        .name("芋道源码演示用mcp（基于sse）")
                        .description("访问我部署的芋道源码地址：http://hoppinzq.com/yudao/")
                        .clientType(McpSetting.ClientType.SYNC)
                        .timeout(Duration.ofSeconds(10))
                        .sseUrl("http://43.142.242.237:48000")
                        .authorization("Bearer test1")
                        .transportType(McpSetting.TransportType.Streamable_HTTP)
                        .anthropicClient(client)
                        .build()
        );

        MCPAgent mcpAgent = new MCPAgent(settings);
        mcpAgent.initializeClient();
        mcpAgent.listTools();
        List<ToolDefinition> tools = new ArrayList<>();

        settings.forEach(setting -> {
            // 异步先阻塞获取，后续再优化
            McpSchema.ListToolsResult mcpTools = setting.isSync() ? setting.getMcpSyncClient().listTools() : setting.getMcpAsyncClient().listTools().block();

            mcpTools.tools().forEach(tool -> {
                McpSchema.JsonSchema jsonSchema = tool.inputSchema();
                ObjectNode inputSchema = OBJECT_MAPPER.valueToTree(jsonSchema.properties());
                inputSchema.putIfAbsent("required", OBJECT_MAPPER.valueToTree(jsonSchema.required()));
                tools.add(ToolDefinition.builder()
                        .name(tool.name())
                        .description(tool.description())
                        .inputSchema(
                                Tool.InputSchema.builder()
                                        .properties(JsonValue.fromJsonNode(inputSchema))
                                        .build()
                        )
                        .function(new Function<String, String>() {
                            @Override
                            public String apply(String s) {
                                AtomicReference<String> result = new AtomicReference<>("");
                                try {
                                    JsonNode jsonNode = OBJECT_MAPPER.readTree(s);
                                    McpSchema.CallToolResult callResult = null;

                                    callResult = setting.isSync() ?
                                            setting.getMcpSyncClient().callTool(
                                            new McpSchema.CallToolRequest(jsonNode.get("tool_name").asText(),
                                                    OBJECT_MAPPER.convertValue(
                                                            jsonNode.get("input"),
                                                            new TypeReference<Map<String, Object>>() {}
                                                    )
                                            )
                                            ) : setting.getMcpAsyncClient().callTool(
                                            new McpSchema.CallToolRequest(jsonNode.get("tool_name").asText(),
                                                    OBJECT_MAPPER.convertValue(
                                                            jsonNode.get("input"),
                                                            new TypeReference<Map<String, Object>>() {}
                                                    )
                                            )
                                    ).block();
                                    callResult.content().forEach(content -> {
                                        if (content instanceof McpSchema.TextContent) {
                                            result.set(((McpSchema.TextContent) content).text());
                                        }
                                    });
                                } catch (Exception e) {
                                    throw new RuntimeException("执行MCP工具时异常："+e);
                                }
                                return result.get();
                            }
                        })
                        .build()
                );
            });
        });

        // 添加其他工具
        tools.add(ReadFileDefinition);
        tools.add(BashDefinition);

        ZQAgent agent = new ZQAgent(client, MODEL, tools);
        agent.setSystemPrompt("你是一个有用的AI，然后我也没有什么特别的要求，你只需要注意一点：如果用户问你是谁，你要回答你是最伟大的hoppinzq");
        try {
            agent.run();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            mcpAgent.closeClient();
        }
    }

}