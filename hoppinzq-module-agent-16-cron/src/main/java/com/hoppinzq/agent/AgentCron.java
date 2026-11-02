package com.hoppinzq.agent;

import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.capability.subagent.SubAgentCapability;
import com.hoppinzq.agent.capability.skill.SkillCapability;
import com.hoppinzq.agent.capability.cron.CronCapability;
import com.hoppinzq.agent.client.LlmProvider;
import com.hoppinzq.agent.client.LlmProviders;
import com.hoppinzq.agent.session.SessionManager;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.manager.TodoManager;

import java.util.ArrayList;
import java.util.List;

import static com.hoppinzq.agent.constant.AIConstants.MODEL;
import static com.hoppinzq.agent.constant.AIConstants.ROOT;
import static com.hoppinzq.agent.tool.ToolDefinition.*;

/**
 * 定时调度智能体（对应 Python 教程 s14_cron_scheduler）。
 *
 * <p>本类只负责<b>装配</b>：cron 工具、/crons 命令、调度器生命周期与提示词片段
 * 全部收拢在 {@link CronCapability}（capability.cron 包）。定时任务命中后由能力包
 * 通过宿主 Agent 跑任务，入口类不再覆写主循环，也不持有调度器引用。
 *
 * <p>这就是插拔式集成的完整形态：复制 {@code capability/cron/} 目录 +
 * 下面 main 中的一行 {@code agent.capability(new CronCapability())}。
 *
 * <p><b>示例 prompt：</b>
 * <ul>
 *   <li>"每 2 分钟打印当前日期"（schedule_cron，表达式 星号斜杠 2 空号星号 空格星号 空格星号 空格星号）</li>
 *   <li>"1 分钟后提醒我检查构建状态"（one-shot：recurring=false）</li>
 *   <li>"列出所有 cron 任务" / "取消任务 &lt;id&gt;"</li>
 * </ul>
 *
 * @author hoppinzq
 */
public class AgentCron extends ZQAgent {

    /**
     * 工具定义（{@code ToolDefinition} 静态字段）在类初始化时读取该实例，
     * 必须在构建工具列表之前赋值。
     */
    public static TodoManager todoManager;

    public AgentCron(LlmProvider provider, String model, List<ToolDefinition> tools) {
        super(provider, model, tools);
    }

    /**
     * 基础系统提示词（不含能力片段；cron 的用法说明由 CronCapability.promptFragments 贡献）。
     */
    public static String generateSystemPrompt(String root, String skillDescriptions) {
        return String.format(
                "你是一个编程智能体，工作在目录: %s\n\n" +
                "核心能力：\n" +
                "- 使用工具读写文件、编辑代码、执行命令、搜索内容\n" +
                "- 通过 todo 工具管理任务列表，追踪复杂任务进度\n" +
                "- 加载专业技能以应对特定领域的任务\n\n" +
                "工作指南：\n" +
                "1. 优先使用工具解决任务，而非仅提供建议\n" +
                "2. 在处理复杂或不熟悉的主题时，使用 load_skill 工具获取专业知识\n" +
                "3. 使用 todo 工具将大任务分解为小步骤，逐个完成\n\n" +
                "可用的技能：\n%s",
                root,
                skillDescriptions
        );
    }

    public static void main(String[] args) {
        LlmProvider provider = LlmProviders.create();

        // 工具定义静态初始化依赖该实例，须先创建
        todoManager = new TodoManager();

        // 会话启动交互已收拢到 SessionManager.bootstrap
        SessionManager sessionManager = SessionManager.bootstrap(args);

        // 基座工具（不含任何能力工具）
        List<ToolDefinition> baseTools = new ArrayList<>(List.of(
                BashDefinition, ReadFileDefinition, EditFileDefinition, WriteFileDefinition,
                GlobDefinition, ContentSearchDefinition, TodoDefinition));

        AgentCron agent = new AgentCron(provider, MODEL, baseTools);

        // 子智能体能力：sub_agent 工具（独立上下文执行子任务并回传 token 用量）
        SubAgentCapability subagent = new SubAgentCapability();
        agent.capability(subagent);

        // 技能能力：load_skill 工具 + /skills 命令 + 技能描述提示片段
        SkillCapability skill = new SkillCapability();
        agent.capability(skill);

        // 插拔式集成：复制 capability/cron/ 目录 + 这一行注册
        CronCapability cron = new CronCapability();
        agent.capability(cron);

        // 基础提示 + 能力贡献的提示片段
        agent.setSystemPrompt(generateSystemPrompt(ROOT, skill.getDescriptions())
                + "\n\n" + String.join("\n", cron.promptFragments())
                + "\n" + String.join("\n", subagent.promptFragments()));

        System.out.println("=== 定时调度智能体 ===");
        System.out.println(cron.description());
        System.out.println();

        // 技能能力：load_skill 工具 + /skills 命令 + 技能描述提示片段\n        SkillCapability skill = new SkillCapability();\n        agent.capability(skill);\n        agent.setSystemPrompt(((agent.getSystemPrompt() == null) ? "" : agent.getSystemPrompt())\n                + "\n" + String.join("\n", skill.promptFragments()));\n\n        agent.setSessionManager(sessionManager);
        try {
            agent.run();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            cron.onStop();
        }
    }
}
