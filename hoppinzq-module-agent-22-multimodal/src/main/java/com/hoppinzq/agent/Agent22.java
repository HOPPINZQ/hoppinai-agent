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
import static com.hoppinzq.agent.constant.AIConstants.SUPPORTS_VISION;
import static com.hoppinzq.agent.tool.ToolDefinition.BashDefinition;
import static com.hoppinzq.agent.tool.ToolDefinition.ReadFileDefinition;
import static com.hoppinzq.agent.tool.ToolDefinition.ScreenshotDefinition;

/**
 * s22 多模态输入示例入口。
 * <p>
 * 示例提示词：
 * <ul>
 *   <li>"读取 test.png"（工作目录放一张图片，会自动 base64）</li>
 *   <li>"截个屏"（调用 screenshot 工具）</li>
 *   <li>"/img test.png 这张图里有什么？"（直接附图给 AI）</li>
 * </ul>
 *
 * @author hoppinzq
 */
public class Agent22 {

    public static void main(String[] args) {
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(API_KEY)
                .baseUrl(BASE_URL)
                .build();

        List<ToolDefinition> tools = new ArrayList<>();
        tools.add(BashDefinition);
        tools.add(ReadFileDefinition);
        tools.add(ScreenshotDefinition);

        ZQAgent agent = new ZQAgent(client, MODEL, tools);
        agent.setSystemPrompt(buildSystemPrompt());
        agent.setSessionManager(bootstrapSession(args));
        System.out.println("[s22 多模态] SUPPORTS_VISION = " + SUPPORTS_VISION
                + "（修改 AIConstants.SUPPORTS_VISION 切换）");
        agent.run();
    }

    /**
     * 启动会话：若命令行传入了 sessionId 则尝试恢复；否则交互式询问。
     * <ul>
     *   <li>{@code java Agent22 <sessionId>} —— 直接恢复指定会话</li>
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
        return """
                你是一个多模态 AI 编程助手，由 hoppinzq 创建。

                ## 核心能力

                - bash: 执行 Shell 命令
                - read_file: 读取文件；遇到图片会自动转 base64 image block（当前模型 vision 支持度: %s）
                - screenshot: 抓取当前屏幕

                ## 工作原则

                1. 优先使用工具完成任务。
                2. 用户给出图片时，先描述图片内容再回答问题。
                3. 当前模型若不支持 vision，图片会被降级为文本描述，请明确告诉用户。

                ## 身份

                你是由最伟大的 hoppinzq 创建的 AI 助手。
                """.formatted(SUPPORTS_VISION ? "已开启" : "已关闭");
    }
}
