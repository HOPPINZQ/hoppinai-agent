package com.hoppinzq.agent;

import com.hoppinzq.agent.capability.compact.CompactCapability;
import com.hoppinzq.agent.capability.skill.SkillCapability;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 系统提示词拼装测试。
 * <p>能力化后 {@link Agent06#generateSystemPrompt(String)} 只负责"基础提示"：
 * 压缩文案由 {@link CompactCapability#promptFragments()} 贡献，
 * 技能清单与使用策略由 {@link SkillCapability#promptFragments()} 贡献
 * （/skills 命令与 load_skill 工具同样来自该能力，内核不再持有 SkillLoader）。
 */
public class Agent06PromptTest {

    private static String fullPrompt(String root) {
        CompactCapability compact = new CompactCapability(null, "", null);
        SkillCapability skill = new SkillCapability();
        return Agent06.generateSystemPrompt(root)
                + "\n\n" + String.join("\n", compact.promptFragments())
                + "\n" + String.join("\n", skill.promptFragments());
    }

    @Test
    public void testGenerateSystemPrompt() {
        String root = "D:\\ai\\hoppinzq-agent\\hoppinzq-module-agent-06";

        String prompt = fullPrompt(root);

        assertNotNull(prompt, "生成的提示词不应为null");
        assertTrue(prompt.contains(root), "提示词应包含工作目录");
        assertTrue(prompt.contains("自动压缩上下文"), "提示词应包含压缩能力描述（来自 CompactCapability）");
        assertTrue(prompt.contains("工作指南"), "提示词应包含工作指南");
        assertTrue(prompt.contains("技能使用策略"), "提示词应包含技能策略段（来自 SkillCapability）");

        // 基础提示以角色定义开头；能力片段追加在后
        assertTrue(prompt.startsWith("你是一个编程智能体"), "提示词应以正确的开头开始");

        System.out.println("=== 生成的提示词 ===");
        System.out.println(prompt);
        System.out.println("=== 测试通过 ===");
    }

    @Test
    public void testBasePromptWithoutCapabilities() {
        String root = "D:\\ai\\hoppinzq-agent\\hoppinzq-module-agent-06";

        String prompt = Agent06.generateSystemPrompt(root);

        assertNotNull(prompt, "生成的提示词不应为null");
        assertTrue(prompt.contains(root), "提示词应包含工作目录");
        // 技能清单已移至能力包，基础提示不再包含该段
        assertFalse(prompt.contains("可用的技能："), "基础提示不应再内联技能清单");

        System.out.println("=== 基础提示测试通过 ===");
    }
}