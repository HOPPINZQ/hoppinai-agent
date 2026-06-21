package com.hoppinzq.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.tool.ToolDefinition;

import java.util.ArrayList;
import java.util.List;

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
                .build();

        List<ToolDefinition> tools = new ArrayList<>();
        tools.add(BashDefinition);

        ZQAgent agent = new ZQAgent(client, MODEL, tools);
        agent.setSystemPrompt(buildSystemPrompt());
        System.out.println("[s23 安全沙箱] bash 命令将经过 L1→L2→L3→L4 四层防护");
        agent.run();
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
