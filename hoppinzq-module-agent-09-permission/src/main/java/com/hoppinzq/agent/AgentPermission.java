package com.hoppinzq.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.session.SessionManager;
import com.hoppinzq.agent.tool.ToolDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import static com.hoppinzq.agent.constant.AIConstants.*;

/**
 * 权限系统示例（对应 Python 教程 s03_permission）。
 *
 * <p>在 module-02 工具循环的基础上，插入 {@link com.hoppinzq.agent.tool.permission.PermissionChecker}
 * 作为三重闸门（denyList → rules → askUser），所有工具执行前必须先过闸门：
 * <ol>
 *   <li>命中黑名单（rm -rf /、sudo、format、Windows del /f /s /q C:\ 等）→ 直接拒绝</li>
 *   <li>规则检查（write_file/edit_file/read_file 路径不能越出 ROOT）→ 拒绝</li>
 *   <li>破坏性命令（rm / del / git push --force 等）→ 交互式 y/N 确认</li>
 * </ol>
 *
 * <p><b>示例 prompt：</b>
 * <ul>
 *   <li>"请执行 rm -rf /tmp/x"  → 应被 denyList 拦截</li>
 *   <li>"请执行 sudo apt update" → 应被 denyList 拦截</li>
 *   <li>"把内容写到 ../secret.txt" → 应被路径越界规则拦截</li>
 *   <li>"执行 del old.log"        → 应触发交互式确认</li>
 * </ul>
 *
 * @author hoppinzq
 */
public class AgentPermission {

    public static void main(String[] args) {
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(API_KEY)
                .baseUrl(BASE_URL)
                .timeout(Duration.ofSeconds(TIMEOUT))
                .maxRetries(MAX_RETRIES)
                .build();

        // 5 个工具：去掉 content_search 简化示例
        List<ToolDefinition> tools = new ArrayList<>();
        tools.add(ToolDefinition.BashDefinition);
        tools.add(ToolDefinition.ReadFileDefinition);
        tools.add(ToolDefinition.WriteFileDefinition);
        tools.add(ToolDefinition.EditFileDefinition);
        tools.add(ToolDefinition.ListFilesDefinition);

        ZQAgent agent = new ZQAgent(client, MODEL, tools);
        agent.setSystemPrompt(buildSystemPrompt());
        // 注入权限闸门（与 scanner 共用，ASK 时通过控制台确认）
        agent.setPermissionChecker(
                new com.hoppinzq.agent.tool.permission.PermissionChecker(agent.getScanner()));
        agent.setSessionManager(bootstrapSession(args));
        agent.run();
    }

    /**
     * 启动会话：若命令行传入了 sessionId 则尝试恢复；否则交互式询问。
     * <ul>
     *   <li>{@code java AgentPermission <sessionId>} —— 直接恢复指定会话</li>
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
                你是一个有权限护栏的开发助手。任何工具调用都会先经过三重权限闸门：
                1) 危险命令黑名单（rm -rf /、sudo、format 等）
                2) 路径越界规则（所有文件操作必须在工作目录内）
                3) 破坏性命令需用户交互式确认
                如果工具被拒绝，你会收到 isError=true 的 ToolResult，请如实告知用户并建议替代方案。

                ## 可用工具
                - bash / read_file / write_file / edit_file / glob

                ## 环境
                - 操作系统：%s
                - 工作目录：%s

                注意：不要尝试绕过权限规则；如果用户请求被拦，请解释原因。""", osName, ROOT);
    }
}
