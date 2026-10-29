package com.hoppinzq.agent;

import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.capability.recovery.RecoveryCapability;
import com.hoppinzq.agent.capability.recovery.RecoveryConfig;
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
 * 错误恢复智能体（对应 Python 教程 s11_error_recovery）。
 *
 * <p>本类只负责<b>装配</b>：重试包装器（RetryWrapper）、错误分类（ErrorClassifier）、
 * 会话级恢复状态（RecoveryState）与响应式压缩（ReactiveCompactor）全部收拢在
 * {@link RecoveryCapability}（capability.recovery 包）。
 *
 * <p>与其它能力不同，恢复必须"包住"provider 调用本身，故该能力以装饰器 Provider
 * 的形式接入（见 {@link RecoveryCapability#provider()}），不改内核钩子契约。
 *
 * <p><b>测试方式：</b>
 * <ul>
 *   <li>把 {@code AIConstants.MAX_TOKENS} 改到极小（如 64）→ 应观察到 maxTokens 升级</li>
 *   <li>让历史对话变长（多轮）→ 应触发 reactive compact</li>
 *   <li>断网测试 / 临时改 OPENAI_BASE_URL 到无效地址 → 应观察到指数退避重试</li>
 * </ul>
 *
 * @author hoppinzq
 */
public class AgentErrorRecovery extends ZQAgent {

    public AgentErrorRecovery(LlmProvider provider, String model, List<ToolDefinition> tools) {
        super(provider, model, tools);
    }

    /**
     * 基础系统提示词（不含能力片段；恢复机制的说明由能力通过 promptFragments 贡献）。
     */
    public static String generateSystemPrompt(String root) {
        String osName = System.getProperty("os.name").toLowerCase();
        return String.format("""
                ## 角色
                你是一个带错误恢复能力的开发助手。

                ## 可用工具
                - bash / read_file / write_file / edit_file / content_search / list_files

                ## 环境
                - 操作系统：%s
                - 工作目录：%s""", osName, root);
    }

    public static void main(String[] args) {
        // 插拔式集成第一步：复制 capability/recovery/ 目录 + 用其装饰器包装原始 Provider
        // （内核 provider 字段为 final，包装须在构造 Agent 之前完成）
        RecoveryCapability recovery = new RecoveryCapability(
                LlmProviders.create(), MODEL, RecoveryConfig.FALLBACK_MODEL);

        // 基座工具（不含任何能力工具）
        List<ToolDefinition> baseTools = new ArrayList<>(List.of(
                BashDefinition, ReadFileDefinition, WriteFileDefinition, EditFileDefinition,
                ContentSearchDefinition, ListFilesDefinition));

        // 会话启动交互已收拢到 SessionManager.bootstrap
        SessionManager sessionManager = SessionManager.bootstrap(args);

        AgentErrorRecovery agent = new AgentErrorRecovery(recovery.provider(), MODEL, baseTools);

        // 插拔式集成第二步：注册能力（命令 / 提示片段经内核契约生效）
        agent.capability(recovery);

        // 基础提示 + 能力贡献的提示片段
        agent.setSystemPrompt(generateSystemPrompt(ROOT)
                + "\n\n" + String.join("\n", recovery.promptFragments()));

        System.out.println("=== 错误恢复智能体 ===");
        System.out.println(recovery.description());
        System.out.println();

        agent.setSessionManager(sessionManager, new SkillLoader());
        try {
            agent.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
