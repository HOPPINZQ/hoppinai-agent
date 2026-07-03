package com.hoppinzq.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.session.SessionManager;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.memory.MemoryExtractor;
import com.hoppinzq.agent.tool.memory.MemorySelector;
import com.hoppinzq.agent.tool.memory.MemoryStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import static com.hoppinzq.agent.constant.AIConstants.*;

/**
 * 持久记忆示例（对应 Python 教程 s09_memory）。
 *
 * <p>跨会话记忆：每轮 LLM 调用前从 .memory/ 索引里挑相关条目注入 system prompt；
 * 每轮结束后用 LLM 抽取新记忆写入。重启后下次对话即可读到上次的记忆。
 *
 * <p>文件结构：
 * <pre>
 * {ROOT}/
 *   ├── .memory/
 *   │   ├── user_prefers_vim.md      (YAML frontmatter + 正文)
 *   │   └── ...
 *   └── MEMORY.md                    (索引，每条一行摘要)
 * </pre>
 *
 * <p><b>示例 prompt：</b>
 * <ul>
 *   <li>第一轮："我喜欢用 vim 编辑器" → 应在 .memory/ 落盘一条 user 偏好</li>
 *   <li>第二轮（重启后）："我喜欢用什么编辑器？" → 应从 .memory/ 读出 vim</li>
 * </ul>
 *
 * @author hoppinzq
 */
public class AgentMemory {

    public static void main(String[] args) {
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(API_KEY)
                .baseUrl(BASE_URL)
                .timeout(Duration.ofSeconds(TIMEOUT))
                .maxRetries(MAX_RETRIES)
                .build();

        List<ToolDefinition> tools = new ArrayList<>();
        tools.add(ToolDefinition.BashDefinition);
        tools.add(ToolDefinition.ReadFileDefinition);
        tools.add(ToolDefinition.WriteFileDefinition);
        tools.add(ToolDefinition.EditFileDefinition);
        tools.add(ToolDefinition.ListFilesDefinition);

        ZQAgent agent = new ZQAgent(client, MODEL, tools);
        agent.setSystemPrompt(buildSystemPrompt());

        // 装配记忆三件套
        MemoryStore store = new MemoryStore(ROOT, client);
        agent.setMemoryStore(store);
        agent.setMemorySelector(new MemorySelector(store));
        agent.setMemoryExtractor(new MemoryExtractor(store));

        System.out.printf("\u001b[95m[memory]\u001b[0m 工作目录 %s，已存在 %d 条记忆%n",
                ROOT, store.fileCount());

        agent.setSessionManager(bootstrapSession(args));
        agent.run();
    }

    /**
     * 启动会话：若命令行传入了 sessionId 则尝试恢复；否则交互式询问。
     * <ul>
     *   <li>{@code java AgentMemory <sessionId>} —— 直接恢复指定会话</li>
     *   <li>无参启动 —— 列出已有会话，输入序号恢复或回车开新会话</li>
     * </ul>
     */
    private static SessionManager bootstrapSession(String[] args) {
        SessionManager sm = new SessionManager();
        if (args.length > 0 && !args[0].isBlank()) {
            boolean ok = sm.resume(args[0]);
            System.out.println(ok
                    ? "已恢复会话 " + args[0]
                    : "会话 " + args[0] + " 不存在或为空，将以该 ID 开始新会话");
            return sm;
        }
        Scanner sc = new Scanner(System.in);
        List<String> sessions = sm.listSessions();
        if (sessions.isEmpty()) {
            sm.startNew();
            System.out.println("新会话已创建: " + sm.getSessionId());
            return sm;
        }
        System.out.println("\n\033[95m========== 历史会话 ==========\033[0m");
        for (int i = 0; i < sessions.size(); i++) {
            System.out.printf("\033[95m%d\033[0m) %s%n", i + 1, sessions.get(i));
        }
        System.out.print("输入序号恢复对应会话，或直接回车开启新会话: ");
        String line = sc.nextLine().trim();
        if (line.isEmpty()) {
            sm.startNew();
            System.out.println("新会话已创建: " + sm.getSessionId());
            return sm;
        }
        try {
            int idx = Integer.parseInt(line) - 1;
            if (idx >= 0 && idx < sessions.size()) {
                String id = sessions.get(idx);
                sm.resume(id);
                System.out.println("已恢复会话: " + id);
                return sm;
            }
        } catch (NumberFormatException ignore) {
            // 用户可能直接输入了 sessionId
            if (sessions.contains(line)) {
                sm.resume(line);
                System.out.println("已恢复会话: " + line);
                return sm;
            }
        }
        sm.startNew();
        System.out.println("输入无效，已开启新会话: " + sm.getSessionId());
        return sm;
    }

    private static String buildSystemPrompt() {
        String osName = System.getProperty("os.name").toLowerCase();
        return String.format("""
                ## 角色
                你是一个具备跨会话持久记忆的开发助手。系统会自动：
                  - 每轮从 .memory/ 选相关记忆注入到这段系统提示后
                  - 每轮结束时把你新提供的事实写入 .memory/
                如果用户告诉你偏好、纠正你、或描述项目结构，请用陈述句表达，便于记忆抽取器落盘。

                ## 可用工具
                - bash / read_file / write_file / edit_file / glob

                ## 环境
                - 操作系统：%s
                - 工作目录：%s""", osName, ROOT);
    }
}
