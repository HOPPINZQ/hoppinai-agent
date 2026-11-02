package com.hoppinzq.agent.capability.skill;

import com.hoppinzq.agent.capability.Capability;
import com.hoppinzq.agent.tool.ToolDefinition;


import java.util.List;
import java.util.Map;

/**
 * 技能加载能力（可整体复制的特性能力包）。
 *
 * <p>两层技能注入机制：
 * <ul>
 *   <li>Layer 1（便宜）：系统提示里只放技能名称和描述（约 100 tokens/技能），由
 *       {@link #promptFragments()} 贡献</li>
 *   <li>Layer 2（按需）：模型调用 {@code load_skill} 工具时，才在 tool_result 中注入完整技能内容</li>
 * </ul>
 *
 * <p>与旧实现的差异（为什么）：
 * <ul>
 *   <li>原先 {@code SkillsDefinition} 是 {@code ToolDefinition} 静态字段，描述文案与执行体
 *       都依赖 {@code Agent05.skillLoader} 静态注入；现在加载器由能力自持（构造时创建），
 *       工具定义改为 {@link #tools()} 返回的实例。</li>
 *   <li>原先 {@code Tools.skillLoad} 每次执行都 {@code new SkillLoader()} 重新扫描技能目录；
 *       现在复用能力持有的同一个加载器，避免重复 IO。</li>
 *   <li>{@code /skills} 命令随技能能力一起迁移：内核 {@code AgentCommandHandler} 不再持有
 *       {@code SkillLoader}，{@code /skills} 由本能力的 {@link #commands()} 注册，
 *       内核命令处理器只认 {@code AgentCommand} 契约。</li>
 * </ul>
 *
 * <p>{@code SkillLoader} 与能力同包（capability/skill）：本能力即技能功能的全部，
 * 内核不再引用任何技能相关类型。拔除本目录并删除装配处注册行后，技能工具、命令与提示片段随之消失。
 *
 * @author hoppinzq
 */
public class SkillCapability implements Capability {

    /** 能力自持的技能加载器（替代原 Agent05.skillLoader 静态注入与 Tools 内的临时 new） */
    private final SkillLoader skillLoader = new SkillLoader();

    @Override
    public String name() {
        return "skill";
    }

    // ============================== 工具 ==============================

    @Override
    public List<ToolDefinition> tools() {
        // 原 ToolDefinition.SkillsDefinition 静态字段整体迁入
        ToolDefinition skills = new ToolDefinition(
                "load_skill",
                "加载指定的技能（工具集合）。可用技能：" + String.join(", ", skillLoader.getAvailableSkills()),
                ToolDefinition.createInputSchema(
                        Map.of("skill_name", ToolDefinition.createProperty("string", "要加载的技能名称")),
                        List.of("skill_name")
                ),
                LoadSkillInput.class,
                this::invokeLoadSkillTool);
        return List.of(skills);
    }

    /**
     * load_skill 工具执行体（原 Tools.skillLoad 静态方法迁入能力包）：
     * Layer 2 按需注入——把完整技能内容作为工具结果回灌给模型。
     */
    private String invokeLoadSkillTool(LoadSkillInput input) {
        try {
            return skillLoader.getSkill(input.getSkillName());
        } catch (Exception e) {
            return "加载Skills错误: " + e.getMessage();
        }
    }

    /** 技能描述清单（"名称: 描述" 每行一条），供模块基础提示沿用旧装配方式 */
    /** 已加载技能名列表（供模块启动横幅等沿用旧装配方式） */
    public List<String> getAvailableSkills() {
        return skillLoader.getAvailableSkills();
    }

    public String getDescriptions() {
        return skillLoader.getDescriptions();
    }

    // ============================== 命令 ==============================

    @Override
    public List<com.hoppinzq.agent.capability.AgentCommand> commands() {
        return List.of(new com.hoppinzq.agent.capability.AgentCommand() {
            @Override
            public String name() {
                return "/skills";
            }

            @Override
            public String description() {
                return "查询已加载的技能信息";
            }

            @Override
            public void execute() {
                List<String> available = skillLoader.getAvailableSkills();
                System.out.println("=== 技能加载智能体 ===");
                System.out.println("已加载技能数量: " + available.size());
                System.out.println("可用技能: " + String.join(", ", available));
                System.out.println();
            }
        });
    }

    // ============================== 提示词与描述 ==============================

    @Override
    public List<String> promptFragments() {
        return List.of(
                "# 技能使用策略（skill 能力）\n" +
                "可用的技能：\n" + skillLoader.getDescriptions() + "\n" +
                "- 在处理不熟悉的主题之前，请使用 load_skill 工具来获取专业知识（Layer 2 按需注入完整内容）\n" +
                "- 遇到不熟悉的编程概念、需要遵循特定最佳实践或标准化工作流程时，主动加载相关技能\n" +
                "- 加载技能后先阅读内容理解核心概念，再应用技能知识解决问题");
    }

    /** 启动横幅用的配置摘要 */
    public String description() {
        return "技能能力：两层注入（提示层只放描述，load_skill 按需注入全文），可用技能 = "
                + String.join(", ", skillLoader.getAvailableSkills());
    }
}
