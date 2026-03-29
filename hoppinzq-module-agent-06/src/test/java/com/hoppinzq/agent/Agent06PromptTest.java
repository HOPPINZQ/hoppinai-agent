package com.hoppinzq.agent;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class Agent06PromptTest {
    
    @Test
    public void testGenerateSystemPrompt() {
        // 测试数据
        String root = "D:\\ai\\hoppinzq-agent\\hoppinzq-module-agent-06";
        String skillDescriptions = "- git: Git工作流助手和最佳实践\n  - test: 测试最佳实践和模式\n  - code-review: 审查代码中的错误、风格问题和最佳实践";
        
        // 生成提示词
        String prompt = Agent06.generateSystemPrompt(root, skillDescriptions);
        
        // 验证提示词包含关键信息
        assertNotNull(prompt, "生成的提示词不应为null");
        assertTrue(prompt.contains(root), "提示词应包含工作目录");
        assertTrue(prompt.contains("上下文压缩能力"), "提示词应包含核心能力描述");
        assertTrue(prompt.contains("工作指南"), "提示词应包含工作指南");
        assertTrue(prompt.contains("可用的技能"), "提示词应包含技能描述");
        assertTrue(prompt.contains("git"), "提示词应包含技能名称");
        
        // 验证格式
        assertTrue(prompt.startsWith("你是一个具有上下文压缩能力的编程智能体"), "提示词应以正确的开头开始");
        
        System.out.println("=== 生成的提示词 ===");
        System.out.println(prompt);
        System.out.println("=== 测试通过 ===");
    }
    
    @Test
    public void testGenerateSystemPromptWithEmptySkills() {
        // 测试空技能描述
        String root = "D:\\ai\\hoppinzq-agent\\hoppinzq-module-agent-06";
        String skillDescriptions = "";
        
        // 生成提示词
        String prompt = Agent06.generateSystemPrompt(root, skillDescriptions);
        
        // 验证提示词仍然有效
        assertNotNull(prompt, "生成的提示词不应为null");
        assertTrue(prompt.contains(root), "提示词应包含工作目录");
        assertTrue(prompt.contains("可用的技能"), "提示词应包含技能描述部分");
        
        System.out.println("=== 空技能测试通过 ===");
    }
}