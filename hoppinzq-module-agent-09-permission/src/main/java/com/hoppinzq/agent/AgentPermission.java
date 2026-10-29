package com.hoppinzq.agent;

import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.capability.permission.PermissionCapability;
import com.hoppinzq.agent.client.LlmProvider;
import com.hoppinzq.agent.client.LlmProviders;
import com.hoppinzq.agent.session.SessionManager;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.manager.TodoManager;
import com.hoppinzq.agent.tool.skill.SkillLoader;

import java.util.ArrayList;
import java.util.List;

import static com.hoppinzq.agent.constant.AIConstants.MODEL;
import static com.hoppinzq.agent.constant.AIConstants.ROOT;
import static com.hoppinzq.agent.tool.ToolDefinition.*;

/**
 * 权限系统智能体（对应 Python 教程 s03_permission）。
 *
 * <p>本类只负责<b>装配</b>：三重权限闸门（denyList → rules → askUser）全部收拢在
 * {@link PermissionCapability}（capability.permission 包）。能力通过
 * {@code hooks()} 返回 {@code beforeToolCall} 钩子实现工具拦截——这正是内核钩子契约的
 * 标准用法，取代了旧版在 ZQAgent 里硬编码 {@code setPermissionChecker(...)} 的静态接线。
 *
 * <p>被拦截的调用以 "已拒绝: ..." 文案作为工具结果回灌给模型，工具不会真实执行。
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
public class AgentPermission extends ZQAgent {

    /**
     * 工具定义（{@code ToolDefinition} 静态字段）在类初始化时读取这两个实例，
     * 必须在构建工具列表之前赋值（基座工具能力化是后续工作，本模式暂保留）。
     */
    public static SkillLoader skillLoader;
    public static TodoManager todoManager;

    public AgentPermission(LlmProvider provider, String model, List<ToolDefinition> tools) {
        super(provider, model, tools);
    }

    /**
     * 基础系统提示词（不含能力片段；权限闸门的说明由 PermissionCapability.promptFragments 贡献）。
     */
    public static String generateSystemPrompt(String root, String osName) {
        return String.format("""
                ## 角色
                你是一个工作在目录 %s 的开发助手（操作系统：%s）。

                ## 工作方式
                1. 优先使用工具解决任务，而非仅提供建议
                2. 文件操作使用 read_file / write_file / edit_file / list_files
                3. 命令执行使用 bash，搜索优先使用 content_search

                ## 可用工具
                - bash / read_file / write_file / edit_file / list_files / glob / content_search
                """, root, osName);
    }

    public static void main(String[] args) {
        LlmProvider provider = LlmProviders.create();

        // 工具定义静态初始化依赖这两个实例，须先创建
        skillLoader = new SkillLoader();
        todoManager = new TodoManager();

        // 会话启动交互已收拢到 SessionManager.bootstrap
        SessionManager sessionManager = SessionManager.bootstrap(args);

        // 基座工具（不含任何能力工具）
        List<ToolDefinition> baseTools = new ArrayList<>(List.of(
                BashDefinition, ReadFileDefinition, EditFileDefinition, WriteFileDefinition,
                GlobDefinition, ContentSearchDefinition, SubAgentDefinition, TodoDefinition,
                SkillsDefinition, ListFilesDefinition));

        AgentPermission agent = new AgentPermission(provider, MODEL, baseTools);

        // 插拔式集成：复制 capability/permission/ 目录 + 这一行注册
        // （ASK 闸门需要与主循环共用的控制台输入，故传入 Agent 持有的 Scanner）
        PermissionCapability permission = new PermissionCapability(agent.getScanner());
        agent.capability(permission);

        // 基础提示 + 能力贡献的提示片段
        agent.setSystemPrompt(generateSystemPrompt(ROOT, System.getProperty("os.name").toLowerCase())
                + "\n\n" + String.join("\n", permission.promptFragments()));

        System.out.println("=== 权限系统智能体 ===");
        System.out.println(permission.description());
        System.out.println();

        agent.setSessionManager(sessionManager, skillLoader);
        try {
            agent.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
