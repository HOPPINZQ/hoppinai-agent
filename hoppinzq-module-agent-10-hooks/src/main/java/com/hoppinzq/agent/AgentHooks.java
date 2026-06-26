package com.hoppinzq.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.session.SessionManager;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.hook.HookEvent;
import com.hoppinzq.agent.tool.hook.HookRegistry;
import com.hoppinzq.agent.tool.hook.LargeOutputHook;
import com.hoppinzq.agent.tool.hook.LogHook;
import com.hoppinzq.agent.tool.hook.PermissionHook;
import com.hoppinzq.agent.tool.hook.SummaryHook;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import static com.hoppinzq.agent.constant.AIConstants.*;

/**
 * 钩子系统示例（对应 Python 教程 s04_hooks）。
 *
 * <p>把 permission 模块硬编码的权限检查改成可注册的钩子。
 * 注册 4 个示例钩子：
 * <ul>
 *   <li>{@link PermissionHook}  → PreToolUse，拦截危险命令</li>
 *   <li>{@link LogHook}         → 4 个事件全挂，做审计日志</li>
 *   <li>{@link LargeOutputHook} → PostToolUse，提示输出过长</li>
 *   <li>{@link SummaryHook}     → PostToolUse + Stop，统计每轮调用数</li>
 * </ul>
 *
 * <p><b>示例 prompt：</b>
 * <ul>
 *   <li>"执行 rm -rf /tmp/x"   → PreToolUse 的 PermissionHook 应阻断</li>
 *   <li>"列出当前目录所有文件" → 正常执行，LogHook 打印审计，SummaryHook 统计次数</li>
 * </ul>
 *
 * @author hoppinzq
 */
public class AgentHooks {

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

        HookRegistry registry = new HookRegistry();
        // PreToolUse：权限护栏
        registry.register(HookEvent.PRE_TOOL_USE, new PermissionHook());
        // 全事件：审计日志
        LogHook logHook = new LogHook();
        registry.register(HookEvent.USER_PROMPT_SUBMIT, logHook);
        registry.register(HookEvent.PRE_TOOL_USE, logHook);
        registry.register(HookEvent.POST_TOOL_USE, logHook);
        registry.register(HookEvent.STOP, logHook);
        // PostToolUse：长输出截断提示
        registry.register(HookEvent.POST_TOOL_USE, new LargeOutputHook());
        // PostToolUse + Stop：本轮小结
        SummaryHook summary = new SummaryHook();
        registry.register(HookEvent.POST_TOOL_USE, summary);
        registry.register(HookEvent.STOP, summary);

        agent.setHookRegistry(registry);
        agent.setSessionManager(bootstrapSession(args));
        agent.run();
    }

    /**
     * 启动会话：若命令行传入了 sessionId 则尝试恢复；否则交互式询问。
     * <ul>
     *   <li>{@code java AgentHooks <sessionId>} —— 直接恢复指定会话</li>
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
                你是一个钩子驱动的开发助手。所有工具调用前会经过 PreToolUse 钩子，
                其中 PermissionHook 会拦截 rm -rf /、sudo、format 等危险命令。
                如果工具被钩子阻断，你会收到包含原因的 ToolResult，请如实告知用户。

                ## 可用工具
                - bash / read_file / write_file / edit_file / list_files

                ## 环境
                - 操作系统：%s
                - 工作目录：%s""", osName, ROOT);
    }
}
