package com.hoppinzq.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.session.SessionManager;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.recovery.RetryWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import static com.hoppinzq.agent.constant.AIConstants.*;

/**
 * 错误恢复示例（对应 Python 教程 s11_error_recovery）。
 *
 * <p>包装 LLM 调用，三条恢复路径：
 * <ol>
 *   <li>输出截断（stop_reason=max_tokens）→ maxTokens 8K 升 64K，再让模型续写</li>
 *   <li>上下文溢出（prompt_too_long）→ {@link com.hoppinzq.agent.tool.recovery.ReactiveCompactor} 紧急压缩历史</li>
 *   <li>瞬态错误（429 / 529 / 网络抖动）→ 指数退避 min(500×2^n, 32000)+jitter；
 *       连续 3 次 529 后自动切到 {@code FALLBACK_MODEL}</li>
 * </ol>
 *
 * <p><b>测试方式：</b>
 * <ul>
 *   <li>把 {@code AIConstants.MAX_TOKENS} 改到极小（如 64）→ 应观察到 maxTokens 升级</li>
 *   <li>让历史对话变长（多轮）→ 应触发 reactive compact</li>
 *   <li>断网测试 / 临时改 BASE_URL 到无效地址 → 应观察到指数退避重试</li>
 * </ul>
 *
 * @author hoppinzq
 */
public class AgentErrorRecovery {

    /** 529 连续命中后切到的兜底模型；为 null 不切换 */
    private static final String FALLBACK_MODEL = null;

    public static void main(String[] args) {
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(API_KEY)
                .baseUrl(BASE_URL)
                .build();

        List<ToolDefinition> tools = new ArrayList<>();
        tools.add(ToolDefinition.BashDefinition);
        tools.add(ToolDefinition.ReadFileDefinition);
        tools.add(ToolDefinition.WriteFileDefinition);
        tools.add(ToolDefinition.EditFileDefinition);
        tools.add(ToolDefinition.ListFilesDefinition);

        ZQAgent agent = new ZQAgent(client, MODEL, tools);
        agent.setSystemPrompt(buildSystemPrompt());
        // 注入重试包装器
        agent.setRetryWrapper(new RetryWrapper(client));
        agent.setFallbackModel(FALLBACK_MODEL);

        System.out.println("\u001b[95m[recovery]\u001b[0m 启用错误恢复：maxTokens 升级 / prompt_too_long 压缩 / 瞬态退避");
        agent.setSessionManager(bootstrapSession(args));
        agent.run();
    }

    /**
     * 启动会话：若命令行传入了 sessionId 则尝试恢复；否则交互式询问。
     * <ul>
     *   <li>{@code java AgentErrorRecovery <sessionId>} —— 直接恢复指定会话</li>
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
                你是一个带错误恢复能力的开发助手。系统会自动处理：
                  - 输出截断：自动升级 maxTokens 并继续生成
                  - 上下文溢出：紧急压缩历史消息
                  - 瞬态错误：指数退避重试；连续 overload 会切到兜底模型
                如果某轮回复被截断，请尝试在下一轮继续。

                ## 可用工具
                - bash / read_file / write_file / edit_file / list_files

                ## 环境
                - 操作系统：%s
                - 工作目录：%s""", osName, ROOT);
    }
}
