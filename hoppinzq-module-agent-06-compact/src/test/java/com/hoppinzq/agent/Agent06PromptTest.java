package com.hoppinzq.agent;

import com.hoppinzq.agent.capability.compact.CompactCapability;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 基础系统提示词测试。
 * <p>能力化后 {@link Agent06#generateSystemPrompt(String, String)} 只负责"基础提示"，
 * 压缩相关文案由 {@link CompactCapability#promptFragments()} 贡献，
 * 因此完整提示 = 基础提示 + 能力片段（与 main 里的拼装方式一致）。
 */
public class Agent06PromptTest {

    private static String fullPrompt(String root, String skillDescriptions) {
        CompactCapability compact = new CompactCapability(null, "", null);
        return Agent06.generateSystemPrompt(root, skillDescriptions)
                + "\n\n" + String.join("\n", compact.promptFragments());
    }

    @Test
    public void testGenerateSystemPrompt() {
        String root = "D:\\ai\\hoppinzq-agent\\hoppinzq-module-agent-06";
        String skillDescriptions = "- git: Git工作流助手和最佳实践\n  - test: 测试最佳实践和模式\n  - code-review: 审查代码中的错误、风格问题和最佳实践";

        String prompt = fullPrompt(root, skillDescriptions);

        assertNotNull(prompt, "生成的提示词不应为null");
        assertTrue(prompt.contains(root), "提示词应包含工作目录");
        assertTrue(prompt.contains("自动压缩上下文"), "提示词应包含压缩能力描述（来自 CompactCapability）");
        assertTrue(prompt.contains("工作指南"), "提示词应包含工作指南");
        assertTrue(prompt.contains("可用的技能"), "提示词应包含技能描述");
        assertTrue(prompt.contains("git"), "提示词应包含技能名称");

        // 基础提示以角色定义开头；能力片段追加在后
        assertTrue(prompt.startsWith("你是一个编程智能体"), "提示词应以正确的开头开始");

        System.out.println("=== 生成的提示词 ===");
        System.out.println(prompt);
        System.out.println("=== 测试通过 ===");
    }

    @Test
    public void testGenerateSystemPromptWithEmptySkills() {
        String root = "D:\\ai\\hoppinzq-agent\\hoppinzq-module-agent-06";
        String skillDescriptions = "";

        String prompt = Agent06.generateSystemPrompt(root, skillDescriptions);

        assertNotNull(prompt, "生成的提示词不应为null");
        assertTrue(prompt.contains(root), "提示词应包含工作目录");
        assertTrue(prompt.contains("可用的技能"), "提示词应包含技能描述部分");

        System.out.println("=== 空技能测试通过 ===");
    }
}