package com.hoppinzq.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.tool.ToolDefinition;

import java.util.ArrayList;
import java.util.List;

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
        agent.run();
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
                - bash / read_file / write_file / edit_file / list_files

                ## 环境
                - 操作系统：%s
                - 工作目录：%s

                注意：不要尝试绕过权限规则；如果用户请求被拦，请解释原因。""", osName, ROOT);
    }
}
