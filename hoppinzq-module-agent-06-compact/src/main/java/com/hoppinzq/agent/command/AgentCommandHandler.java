package com.hoppinzq.agent.command;

import com.hoppinzq.agent.capability.AgentCommand;
import com.hoppinzq.agent.session.SessionManager;
import com.hoppinzq.agent.tool.skill.SkillLoader;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 特殊命令处理器。
 * <p>处理以 {@code /} 开头的用户命令，不发送给 LLM。
 * <p>内核内置命令：
 * <ul>
 *   <li>{@code /stats} —— 打印当前会话的 token 统计</li>
 *   <li>{@code /usage} —— 打印每次 LLM 调用的 token 明细</li>
 *   <li>{@code /skills} —— 查询已加载的技能信息</li>
 *   <li>{@code /exit} —— 退出程序并打印统计</li>
 * </ul>
 * 其余命令（如 {@code /compact}、{@code /tokens}）由能力通过 {@link AgentCommand}
 * 注册，本类不感知任何具体能力。
 *
 * @author hoppinzq
 */
public class AgentCommandHandler {

    private final SessionManager sessionManager;
    private final SkillLoader skillLoader;
    /** 能力注册的额外命令，按 name 索引（LinkedHashMap 保持注册顺序） */
    private final Map<String, AgentCommand> extraCommands = new LinkedHashMap<>();

    public AgentCommandHandler(SessionManager sessionManager, SkillLoader skillLoader, List<AgentCommand> extraCommands) {
        this.sessionManager = sessionManager;
        this.skillLoader = skillLoader;
        if (extraCommands != null) {
            for (AgentCommand cmd : extraCommands) {
                this.extraCommands.put(cmd.name().toLowerCase(), cmd);
            }
        }
    }

    /**
     * 判断输入是否为特殊命令。
     *
     * @param input 用户输入
     * @return 若以 {@code /} 开头返回 {@code true}
     */
    public boolean isCommand(String input) {
        return input != null && input.startsWith("/");
    }

    /**
     * 执行特殊命令。
     *
     * @param input 用户输入（以 {@code /} 开头）
     * @return 若命令已处理返回 {@code true}；若为未知命令返回 {@code false}
     */
    public boolean handleCommand(String input) {
        if (!isCommand(input)) {
            return false;
        }

        switch (input.toLowerCase()) {
            case "/stats":
                handleStats();
                return true;
            case "/usage":
                handleUsage();
                return true;
            case "/skills":
                handleSkills();
                return true;
            case "/exit":
                handleExit();
                return true;
            default:
                AgentCommand cmd = extraCommands.get(input.toLowerCase());
                if (cmd != null) {
                    cmd.execute();
                    return true;
                }
                System.out.println("\u001b[90m[提示] 未知命令: " + input + "\u001b[0m");
                System.out.println("\u001b[90m可用命令: " + availableCommands() + "\u001b[0m");
                return true;
        }
    }

    /** 内置命令 + 能力注册命令的清单（用于未知命令提示与启动横幅） */
    public String availableCommands() {
        StringBuilder sb = new StringBuilder("/stats, /usage, /skills");
        for (String name : extraCommands.keySet()) {
            sb.append(", ").append(name);
        }
        return sb + ", /exit";
    }

    private void handleStats() {
        if (sessionManager != null) {
            sessionManager.printSummary();
        } else {
            System.out.println("\u001b[90m[提示] 本会话未启用 session 管理器，无统计数据\u001b[0m");
        }
    }

    private void handleUsage() {
        if (sessionManager == null) {
            System.out.println("\u001b[90m[提示] 本会话未启用 session 管理器，无统计数据\u001b[0m");
            return;
        }
        var usageList = sessionManager.getUsageList();
        if (usageList.isEmpty()) {
            System.out.println("\u001b[90m[提示] 本次会话暂无 token 记录\u001b[0m");
            return;
        }

        System.out.println("\u001b[90m========== Token 使用明细 ==========\u001b[0m");
        long totalInput = 0, totalOutput = 0, totalCache = 0;
        int idx = 1;
        for (var u : usageList) {
            long input = u.getInputTokens() != null ? u.getInputTokens() : 0;
            long output = u.getOutputTokens() != null ? u.getOutputTokens() : 0;
            long cache = u.getCacheReadTokens() != null ? u.getCacheReadTokens() : 0;
            totalInput += input;
            totalOutput += output;
            totalCache += cache;

            String time = u.getTimestamp() != null ? u.getTimestamp().substring(11, 19) : "--:--:--";
            System.out.printf("\u001b[90m[%d]\u001b[0m %s  输入:\u001b[90m%d\u001b[0m  输出:\u001b[90m%d\u001b[0m  缓存:\u001b[90m%d\u001b[0m%n",
                    idx++, time, input, output, cache);
        }
        System.out.println("\u001b[90m==================================\u001b[0m");
        System.out.printf("\u001b[90m总计\u001b[0m: 输入:\u001b[90m%d\u001b[0m  输出:\u001b[90m%d\u001b[0m  缓存:\u001b[90m%d\u001b[0m%n",
                totalInput, totalOutput, totalCache);
    }

    private void handleExit() {
        if (sessionManager != null) {
            sessionManager.printSummary();
        }
        System.out.println("再见！");
        System.exit(0);
    }

    private void handleSkills() {
        if (skillLoader == null) {
            System.out.println("\u001b[90m[提示] 本会话未启用技能加载器，无技能信息\u001b[0m");
            return;
        }

        List<String> availableSkills = skillLoader.getAvailableSkills();
        System.out.println("=== 技能加载智能体 ===");
        System.out.println("已加载技能数量: " + availableSkills.size());
        System.out.println("可用技能: " + String.join(", ", availableSkills));
        System.out.println();
    }
}
