package com.hoppinzq.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.hook.HookEvent;
import com.hoppinzq.agent.tool.hook.HookRegistry;
import com.hoppinzq.agent.tool.hook.LargeOutputHook;
import com.hoppinzq.agent.tool.hook.LogHook;
import com.hoppinzq.agent.tool.hook.PermissionHook;
import com.hoppinzq.agent.tool.hook.SummaryHook;

import java.util.ArrayList;
import java.util.List;

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
        agent.run();
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
