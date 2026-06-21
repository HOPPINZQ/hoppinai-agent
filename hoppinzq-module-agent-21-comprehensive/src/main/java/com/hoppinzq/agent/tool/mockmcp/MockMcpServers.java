package com.hoppinzq.agent.tool.mockmcp;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置 mock MCP 服务器集合（仿 Python s20 的 MOCK_SERVERS）。
 * <p>提供两个 mock：
 * <ul>
 *   <li><b>docs</b>：search（关键词搜文档）/ get_version（查版本）</li>
 *   <li><b>deploy</b>：status（查部署状态）/ run（触发部署）</li>
 * </ul>
 *
 * <p>description 里加了 Python s19 风格的权限标注（readOnly / destructive），
 * 这是教学版相对 Java 13-mcp 的一个值得借鉴点。
 *
 * @author hoppinzq
 */
public final class MockMcpServers {

    private static final Map<String, MockServer> SERVERS = new LinkedHashMap<>();

    static {
        // docs server：搜文档
        MockServer docs = new MockServer("docs", "Mock 文档服务器（教学用）");
        docs.register("search", "(readOnly) 在 mock 文档库里按关键词搜索。", args -> {
            String q = str(args.get("query"));
            return Map.of(
                    "server", "docs",
                    "query", q,
                    "matches", List.of(
                            Map.of("title", "Agent Loop 概览", "snippet", "Agent loop = LLM + tools + memory..."),
                            Map.of("title", "Worktree Isolation", "snippet", "每个 teammate 跑在自己的 git worktree 里...")
                    )
            );
        });
        docs.register("get_version", "(readOnly) 返回 mock 文档库版本号。", args ->
                Map.of("server", "docs", "version", "mock-1.3.0", "date", LocalDate.now().toString()));
        SERVERS.put("docs", docs);

        // deploy server：部署
        MockServer deploy = new MockServer("deploy", "Mock 部署服务器（教学用）");
        deploy.register("status", "(readOnly) 查询当前部署状态。", args ->
                Map.of("server", "deploy", "state", "idle", "lastDeploy", "2026-06-19T10:00:00Z"));
        deploy.register("run", "(destructive) 触发一次部署。需要 env 参数。", args -> {
            String env = str(args.get("env"));
            if (env == null || env.isBlank()) {
                return Map.of("server", "deploy", "ok", false, "error", "env 不能为空");
            }
            return Map.of("server", "deploy", "ok", true, "env", env, "deployId", "d-" + System.nanoTime() % 100000);
        });
        SERVERS.put("deploy", deploy);
    }

    private MockMcpServers() {}

    public static MockServer find(String name) { return SERVERS.get(name); }
    public static List<String> names() { return new java.util.ArrayList<>(SERVERS.keySet()); }

    private static String str(Object o) { return o == null ? null : o.toString(); }

    /** 单个 mock 服务器。 */
    public static class MockServer {
        private final String name;
        private final String description;
        private final Map<String, ToolHandler> tools = new LinkedHashMap<>();
        private final Map<String, String> descriptions = new LinkedHashMap<>();

        public MockServer(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public void register(String tool, String desc, ToolHandler handler) {
            tools.put(tool, handler);
            descriptions.put(tool, desc);
        }

        public String name() { return name; }
        public String description() { return description; }
        public Map<String, ToolHandler> tools() { return tools; }
        public Map<String, String> descriptions() { return descriptions; }
    }

    @FunctionalInterface
    public interface ToolHandler {
        Object invoke(Map<String, Object> args) throws Exception;
    }
}
