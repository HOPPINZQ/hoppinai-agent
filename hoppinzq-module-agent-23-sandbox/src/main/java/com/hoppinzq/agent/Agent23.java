package com.hoppinzq.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.session.SessionManager;
import com.hoppinzq.agent.tool.ToolDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import static com.hoppinzq.agent.constant.AIConstants.API_KEY;
import static com.hoppinzq.agent.constant.AIConstants.BASE_URL;
import static com.hoppinzq.agent.constant.AIConstants.MODEL;
import static com.hoppinzq.agent.tool.ToolDefinition.BashDefinition;

/**
 * s23 安全沙箱示例入口。
 * <p>
 * 示例提示词：
 * <ul>
 *   <li>"执行 rm -rf /tmp/test"（L1 静态分析应拦截）</li>
 *   <li>"列出 ../../etc 下的文件"（L2 目录监禁应拦截）</li>
 *   <li>"echo hello"（通过 L1+L2，降级到带超时 ProcessBuilder 执行）</li>
 * </ul>
 *
 * @author hoppinzq
 */
public class Agent23 {

    public static void main(String[] args) {
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(API_KEY)
                .baseUrl(BASE_URL)
                .timeout(Duration.ofSeconds(TIMEOUT))
                .maxRetries(MAX_RETRIES)
                .build();

        List<ToolDefinition> tools = new ArrayList<>();
        tools.add(BashDefinition);

        ZQAgent agent = new ZQAgent(client, MODEL, tools);
        agent.setSystemPrompt(buildSystemPrompt());
        agent.setSessionManager(bootstrapSession(args));
        System.out.println("[s23 安全沙箱] bash 命令将经过 L1→L2→L3→L4 四层防护");
        agent.run();
    }

    /**
     * 启动会话：若命令行传入了 sessionId 则尝试恢复；否则交互式询问。
     * <ul>
     *   <li>{@code java Agent23 <sessionId>} —— 直接恢复指定会话</li>
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
                # 角色定义

                你是一个安全沙箱 AI 编程助手，由 hoppinzq 创建。你的 bash 工具被四层沙箱保护：
                L1 静态分析（黑名单拦截高危命令）→ L2 目录监禁（拒绝路径越界）→
                L3 OS 沙箱（Linux bwrap）→ L4 容器隔离（docker）→ 降级带超时 ProcessBuilder。

                ## 当前环境

                - 操作系统：%s
                - 工作目录（监禁根）：%s

                ## 工作原则

                1. 优先使用 bash 工具执行命令。
                2. 若命令被沙箱拒绝，告知用户拒绝原因（响应中会带 ❌ 标记）。
                3. Linux 环境下 L3 bwrap 会自动启用；docker 默认关闭。
                4. 安全第一，不要试图绕过沙箱。

                ## 身份

                你是由最伟大的 hoppinzq 创建的 AI 助手。
                """, osName, System.getProperty("user.dir"));
    }
}
