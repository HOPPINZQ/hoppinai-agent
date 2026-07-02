package com.hoppinzq.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.session.SessionManager;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.prompt.PromptAssembler;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import static com.hoppinzq.agent.constant.AIConstants.*;

/**
 * 动态系统提示词装配示例（对应 Python 教程 s10_system_prompt）。
 *
 * <p>系统提示不再硬编码在 buildSystemPrompt() 里 —— 由 {@link PromptAssembler}
 * 在每轮 LLM 调用前按 {@link com.hoppinzq.agent.tool.prompt.AgentContext} 动态装配：
 * <ul>
 *   <li>identity 段：永远挂</li>
 *   <li>tools 段：按当前 tools 列表渲染</li>
 *   <li>workspace 段：工作目录 + OS</li>
 *   <li>memory 段：仅当 MEMORY.md 存在时挂</li>
 * </ul>
 *
 * <p>context hash 变化才会重新装配（带缓存）。
 *
 * <p><b>示例 prompt：</b>
 * <ul>
 *   <li>启动时若 MEMORY.md 不存在 → system prompt 不含 memory 段</li>
 *   <li>在工作目录手动创建 MEMORY.md → 下一轮 system prompt 自动加上 memory 段</li>
 *   <li>删掉 MEMORY.md → 下一轮 system prompt 又自动撤掉 memory 段</li>
 * </ul>
 *
 * @author hoppinzq
 */
public class  AgentSysPrompt {

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
        // 不再调 setSystemPrompt —— 完全由 PromptAssembler 装配
        agent.setPromptAssembler(new PromptAssembler());

        System.out.println("\u001b[95m[sysprompt]\u001b[0m 工作目录 " + ROOT);
        System.out.println("提示：手动创建/删除 " + ROOT + "/MEMORY.md，下一轮系统提示会动态切换 memory 段");

        agent.setSessionManager(bootstrapSession(args));
        agent.run();
    }

    /**
     * 启动会话：若命令行传入了 sessionId 则尝试恢复；否则交互式询问。
     * <ul>
     *   <li>{@code java AgentSysPrompt <sessionId>} —— 直接恢复指定会话</li>
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
}
