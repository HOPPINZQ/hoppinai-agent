package com.hoppinzq.agent.tool.mockmcp;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 教学版 Mock MCP 客户端（仿 Python s19_mcp_plugin / s20 的 MCPClient）。
 * <p>
 * 与真实 {@code io.modelcontextprotocol.sdk} 不同，这里完全在进程内 mock：
 * <ul>
 *   <li>{@link MockMcpServers} 内置两个 mock 服务器（{@code docs} 和 {@code deploy}）</li>
 *   <li>{@link #connect(String)} 把指定服务器的工具按 {@code mcp__<server>__<tool>} 前缀注册</li>
 *   <li>{@link #callTool(String, Map)} 路由到对应 mock 实现</li>
 * </ul>
 *
 * <p>这个名字前缀是 Python s19 教学版里值得借鉴的安全实践：避免不同服务器的同名工具互相覆盖。
 *
 * @author hoppinzq
 */
public class MockMcpClient {

    private static final ObjectMapper M = new ObjectMapper();

    /** 已连接的 server 名 → 服务器实例 */
    private final Map<String, MockMcpServers.MockServer> connected = new LinkedHashMap<>();
    /** 工具全名 mcp__server__tool → 所属 server 名 */
    private final Map<String, String> toolIndex = new HashMap<>();

    public boolean isConnected(String server) {
        return connected.containsKey(server);
    }

    /** 连接一个 mock server，把它的工具注册进 toolIndex。 */
    public List<String> connect(String server) {
        MockMcpServers.MockServer s = MockMcpServers.find(server);
        if (s == null) {
            throw new IllegalArgumentException("unknown mock MCP server: " + server
                    + "；可选：" + String.join(", ", MockMcpServers.names()));
        }
        connected.put(server, s);
        List<String> registered = new java.util.ArrayList<>();
        for (String tool : s.tools().keySet()) {
            String fullName = normalize(server, tool);
            toolIndex.put(fullName, server);
            registered.add(fullName);
        }
        System.out.printf("\u001b[95m[mcp]\u001b[0m connected %s, tools=%s%n", server, registered);
        return registered;
    }

    public void disconnect(String server) {
        MockMcpServers.MockServer s = connected.remove(server);
        if (s != null) {
            for (String tool : s.tools().keySet()) {
                toolIndex.remove(normalize(server, tool));
            }
        }
    }

    /** 调用一个已注册的 mock MCP 工具；fullName 形如 mcp__docs__search。 */
    public String callTool(String fullName, Map<String, Object> args) {
        String server = toolIndex.get(fullName);
        if (server == null) {
            return "错误：未连接或未注册的 MCP 工具：" + fullName;
        }
        MockMcpServers.MockServer s = connected.get(server);
        String shortName = unnormalize(fullName);
        MockMcpServers.ToolHandler handler = s.tools().get(shortName);
        if (handler == null) {
            return "错误：服务器 " + server + " 没有工具 " + shortName;
        }
        try {
            Object result = handler.invoke(args == null ? Map.of() : args);
            return M.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            return "MCP 工具 " + fullName + " 调用失败: " + e.getMessage();
        }
    }

    /** 列出所有已连接工具的全名。 */
    public List<String> listConnectedTools() {
        return new java.util.ArrayList<>(toolIndex.keySet());
    }

    /** 名称归一化：server + tool → mcp__server__tool（与 Python s19 一致）。 */
    public static String normalize(String server, String tool) {
        return "mcp__" + server + "__" + tool;
    }

    public static String unnormalize(String fullName) {
        // mcp__server__tool → tool
        int first = fullName.indexOf("__");
        if (first < 0) return fullName;
        int second = fullName.indexOf("__", first + 2);
        if (second < 0) return fullName;
        return fullName.substring(second + 2);
    }
}
