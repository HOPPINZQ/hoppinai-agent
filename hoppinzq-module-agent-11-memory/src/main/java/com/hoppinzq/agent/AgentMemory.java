package com.hoppinzq.agent;

import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.capability.memory.MemoryCapability;
import com.hoppinzq.agent.client.LlmProvider;
import com.hoppinzq.agent.client.LlmProviders;
import com.hoppinzq.agent.session.SessionManager;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.skill.SkillLoader;

import java.util.ArrayList;
import java.util.List;

import static com.hoppinzq.agent.constant.AIConstants.MODEL;
import static com.hoppinzq.agent.constant.AIConstants.ROOT;
import static com.hoppinzq.agent.tool.ToolDefinition.*;

/**
 * 持久记忆智能体（对应 Python 教程 s09_memory）。
 *
 * <p>本类只负责<b>装配</b>：记忆的存取（MemoryStore）、相关性选择（MemorySelector）、
 * 事实抽取（MemoryExtractor）、逐轮注入与 /memory 命令全部收拢在
 * {@link MemoryCapability}（capability.memory 包）；每轮的挂载点由内核的
 * beforeLlmCall 钩子分发，入口类不再覆写任何循环方法。
 *
 * <p>这就是插拔式集成的完整形态：复制 {@code capability/memory/} 目录 +
 * 下面 main 中的一行 {@code agent.capability(memory)}。
 *
 * <p><b>示例 prompt：</b>
 * <ul>
 *   <li>第一轮："我喜欢用 vim 编辑器" → 应在 .memory/ 落盘一条 user 偏好</li>
 *   <li>第二轮（重启后）："我喜欢用什么编辑器？" → 应从 .memory/ 读出 vim</li>
 * </ul>
 *
 * @author hoppinzq
 */
public class AgentMemory extends ZQAgent {

    public AgentMemory(LlmProvider provider, String model, List<ToolDefinition> tools) {
        super(provider, model, tools);
    }

    /**
     * 基础系统提示词（不含能力片段；记忆的动态注入由能力在每轮钩子里完成）。
     */
    public static String generateSystemPrompt(String root) {
        String osName = System.getProperty("os.name").toLowerCase();
        return String.format("""
                ## 角色
                你是一个具备跨会话持久记忆的开发助手。

                ## 可用工具
                - bash / read_file / write_file / edit_file / content_search / list_files

                ## 环境
                - 操作系统：%s
                - 工作目录：%s""", osName, root);
    }

    public static void main(String[] args) {
        LlmProvider provider = LlmProviders.create();

        // 基座工具（不含任何能力工具）
        List<ToolDefinition> baseTools = new ArrayList<>(List.of(
                BashDefinition, ReadFileDefinition, WriteFileDefinition, EditFileDefinition,
                ContentSearchDefinition, ListFilesDefinition));

        // 会话启动交互已收拢到 SessionManager.bootstrap
        SessionManager sessionManager = SessionManager.bootstrap(args);

        AgentMemory agent = new AgentMemory(provider, MODEL, baseTools);

        // 插拔式集成：复制 capability/memory/ 目录 + 这一行注册
        MemoryCapability memory = new MemoryCapability(provider);
        agent.capability(memory);

        // 基础提示 + 能力贡献的提示片段
        agent.setSystemPrompt(generateSystemPrompt(ROOT)
                + "\n\n" + String.join("\n", memory.promptFragments()));

        System.out.println("=== 持久记忆智能体 ===");
        System.out.println(memory.description());
        System.out.println("[memory] 工作目录 " + ROOT + "，已存在 " + memory.fileCount() + " 条记忆");
        System.out.println();

        agent.setSessionManager(sessionManager, new SkillLoader());
        try {
            agent.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
