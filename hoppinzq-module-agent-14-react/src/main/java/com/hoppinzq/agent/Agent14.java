package com.hoppinzq.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.session.SessionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import static com.hoppinzq.agent.constant.AIConstants.*;
import static com.hoppinzq.agent.tool.ToolDefinition.*;

/**
 * ReAct Agent 示例
 * 使用 ReAct 模式让 AI 能够推理和行动
 *
 * @author hoppinzq
 */
public class Agent14 {

    public static void main(String[] args) {
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(API_KEY)
                .baseUrl(BASE_URL)
                .timeout(Duration.ofSeconds(TIMEOUT))
                .maxRetries(MAX_RETRIES)
                .build();

        List<ToolDefinition> tools = new ArrayList<>();
        // 添加基础工具
        tools.add(BashDefinition);
        tools.add(EditFileDefinition);
        tools.add(WriteFileDefinition);
        tools.add(ReadFileDefinition);
        tools.add(ListFilesDefinition);
        tools.add(ContentSearchDefinition);

        ZQAgent agent = new ZQAgent(client, MODEL, tools);

        // 构建 ReAct 系统提示词或普通系统提示词
        String systemPrompt;
        if (REACT_ENABLE) {
            systemPrompt = buildReActSystemPrompt(tools);
        } else {
            systemPrompt = buildNormalSystemPrompt(tools);
        }

        agent.setSystemPrompt(systemPrompt);
        agent.setSessionManager(bootstrapSession(args));
        agent.run();
    }

    /**
     * 启动会话：若命令行传入了 sessionId 则尝试恢复；否则交互式询问。
     * <ul>
     *   <li>{@code java Agent14 <sessionId>} —— 直接恢复指定会话</li>
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

    /**
     * 构建普通模式系统提示词
     *
     * @param tools 工具列表
     * @return 构建好的提示词字符串
     */
    private static String buildNormalSystemPrompt(List<ToolDefinition> tools) {
        StringBuilder toolDescriptions = new StringBuilder();
        for (ToolDefinition tool : tools) {
            toolDescriptions.append("- ").append(tool.getName()).append(": ").append(tool.getDescription()).append("\n");
            try {
                toolDescriptions.append("  参数 Schema: ").append(OBJECT_MAPPER.writeValueAsString(tool.getInputSchema())).append("\n");
            } catch (Exception ignored) {}
        }

        return String.format("""
            你是一个专业的编程智能体，具有强大的后台任务执行能力。你可以使用工具来帮助用户解决编程和系统任务。
            
            ## 可用工具
            
            %s
            
            ## 工作目录
            
            你工作在 %s 目录下。
            
            请记住：你的目标是帮助用户高效地完成任务。如果需要使用工具，请直接调用。
            """, toolDescriptions, ROOT);
    }

    /**
     * 构建 ReAct 系统提示词
     *
     * @param tools 工具列表
     * @return 构建好的提示词字符串
     */
    private static String buildReActSystemPrompt(List<ToolDefinition> tools) {
        StringBuilder toolDescriptions = new StringBuilder();
        for (ToolDefinition tool : tools) {
            toolDescriptions.append("- ").append(tool.getName()).append(": ").append(tool.getDescription()).append("\n");
            try {
                toolDescriptions.append("  参数 Schema: ").append(OBJECT_MAPPER.writeValueAsString(tool.getInputSchema())).append("\n");
            } catch (Exception ignored) {}
        }

        String actionNames = tools.stream().map(ToolDefinition::getName).reduce((a, b) -> a + "," + b).orElse("");
        
        return String.format("""
            你是一个专业的编程智能体，使用 ReAct 模式（推理+行动）解决复杂问题。

            ========== 输出格式（必须 100%% 严格遵守，违反即任务失败） ==========

            当需要使用工具时，**单次回复**只能按下面三行的原文格式输出（顺序固定、关键字固定、冒号后有一个空格）：

            Thought: 你的思考内容
            Action: 工具名
            Action Input: JSON参数

            不需要使用工具时，直接用自然语言回答用户，回复中**绝不能**出现 Thought / Action / Action Input / Observation 这些关键字。

            ---------- 以下写法都属于格式错误，严禁出现 ----------

            [错] <Action: list_files>          ← 禁止用尖括号 <> 包裹
            [错] <Action Input: {}>            ← 禁止用尖括号 <> 包裹
            [错] <Thought: ...>                ← 禁止用尖括号 <> 包裹
            [错] **Action**: list_files        ← 禁止用 markdown 加粗
            [错] `Action: list_files`          ← 禁止用反引号包裹
            [错] ```Action: list_files```      ← 禁止用代码块包裹
            [错] Action：list_files            ← 禁止用中文冒号 ：
            [错] Action:list_files             ← 冒号后必须有一个空格
            [错] 一次回复出现多组 Thought/Action/Action Input
            [错] 自己编造 Observation 的值

            ---------- 唯一正确写法 ----------

            Thought: 用户想要查看当前目录的文件列表，应使用 list_files 工具
            Action: list_files
            Action Input: {}

            ========== 工作流程 ==========

            1. 收到用户问题，判断是否需要工具
            2. 需要工具：输出一组 Thought/Action/Action Input 后立即停止，等待系统返回 Observation
            3. 收到 Observation 后，根据结果继续思考下一步（如需再次调用工具，重复步骤 2）
            4. 不再需要工具：用自然语言直接回答用户（此时不要输出 Thought/Action/Action Input）
            5. 缺少必要参数时：直接向用户提问，不要调用工具
            6. 工具执行出错时：向用户说明情况并寻求帮助

            ========== 完整对话示例 ==========

            用户: 请列出当前目录的文件

            AI:
            Thought: 用户想要查看当前目录的文件列表，应使用 list_files 工具
            Action: list_files
            Action Input: {}

            （系统执行后返回）Observation: ["file1.txt", "file2.java"]

            AI: 我找到了以下文件：file1.txt, file2.java

            ========== 关键约束 ==========

            1. 工具名只能是以下集合中的一个：[%s]
            2. Action Input 必须是合法 JSON 对象，以 { 开头、} 结尾
            3. 一次回复只能有一组 Thought/Action/Action Input，等待 Observation 才能继续
            4. Observation 永远由系统返回，**绝对不能**自己填写
            5. 同一工具需要多次调用时，每轮都完整输出 Thought/Action/Action Input

            ## 可用工具

            %s

            ## 工作目录

            你工作在 %s 目录下。

            请记住：输出格式错误（例如使用尖括号、加粗、中文冒号、自编 Observation）会导致整个任务失败。
            """, actionNames, toolDescriptions, ROOT);
    }
}
