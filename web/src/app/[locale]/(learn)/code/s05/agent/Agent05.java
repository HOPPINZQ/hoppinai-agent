package com.hoppinzq.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.TextBlockParam;
import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.tool.manager.TodoManager;
import com.hoppinzq.agent.tool.skill.SkillLoader;
import com.hoppinzq.agent.tool.ToolDefinition;

import java.util.*;

import static com.hoppinzq.agent.constant.AIConstants.*;
import static com.hoppinzq.agent.tool.ToolDefinition.*;
import static com.hoppinzq.agent.tool.ToolDefinition.SubAgentDefinition;

/**
 * 技能加载智能体
 *
 * 核心概念：
 * - 两层技能注入机制
 * - Layer 1（便宜）：在系统提示中只放技能名称和描述（~100 tokens/skill）
 * - Layer 2（按需）：当模型调用load_skill工具时，才在tool_result中注入完整技能内容（~2000 tokens）
 *
 * 技能文件结构：
 * skills/
 *   pdf/
 *     SKILL.md  # ---\n name: pdf\n description: Process PDF files\n ---\n ...
 *   code-review/
 *     SKILL.md  # ---\n name: code-review\n description: Review code\n ---\n ...
 *
 * @author hoppinzq
 */
public class Agent05 extends ZQAgent {

    public static TodoManager todoManager;
    private int roundsSinceTodo = 0;
    private long lastTodoVersion = 0;
    public static SkillLoader skillLoader;

    public Agent05(AnthropicClient client, String model, List<ToolDefinition> tools, SkillLoader skillLoader, TodoManager todoManager) {
        super(client, model, tools);
        Agent05.skillLoader = skillLoader;
        Agent05.todoManager = todoManager;
        this.lastTodoVersion = todoManager.getVersion();
    }

    @Override
    protected void onToolExecution(List<ContentBlockParam> toolResults) {
        long currentVersion = todoManager.getVersion();
        if (currentVersion > lastTodoVersion) {
            roundsSinceTodo = 0;
            lastTodoVersion = currentVersion;
        } else {
            roundsSinceTodo++;
        }

        if (roundsSinceTodo >= 3) {
            String reminder = String.format("""
                <reminder>
                您已经 %d 个回合没有更新待办事项列表了。请更新列表以反映当前进度。
                
                建议操作：
                1. 检查当前任务状态
                2. 更新已完成的任务
                3. 添加新的任务（如果需要）
                4. 调整任务优先级
                </reminder>
                """, roundsSinceTodo);
            toolResults.add(ContentBlockParam.ofText(TextBlockParam.builder()
                    .text(reminder)
                    .build()));
        }
    }

    public static void main(String[] args) {
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(API_KEY)
                .baseUrl(BASE_URL)
                .build();

        // 创建技能加载器
        skillLoader = new SkillLoader();
        todoManager = new TodoManager();

        // 创建工具列表（基础工具 + load_skill工具）
        List<ToolDefinition> tools = new ArrayList<>();
        tools.add(BashDefinition);
        tools.add(ReadFileDefinition);
        tools.add(EditFileDefinition);
        tools.add(WriteFileDefinition);
        tools.add(ListFilesDefinition);
        tools.add(ContentSearchDefinition);
        tools.add(SubAgentDefinition);
        tools.add(TodoDefinition);

        tools.add(SkillsDefinition);

        // 创建智能体
        Agent05 agent = new Agent05(client, MODEL, tools, skillLoader,todoManager);

        agent.setSystemPrompt(buildSystemPrompt());

        System.out.println("=== 技能加载智能体 ===");
        System.out.println("已加载技能数量: " + skillLoader.getAvailableSkills().size());
        System.out.println("可用技能: " + String.join(", ", skillLoader.getAvailableSkills()));
        System.out.println();
        agent.run();
    }

    /**
     * 构建系统提示词
     * @return 格式化的系统提示词
     */
    private static String buildSystemPrompt() {
        String osName = System.getProperty("os.name").toLowerCase();
        // 构建系统提示（Layer 1: 技能名称和描述）
        String systemPrompt = String.format("""
            你是一个带有待办事项列表的编程智能体。你可以将复杂的任务委托给子智能体来处理。
            你工作在 %s 操作系统，工作根目录为：%s
            子智能体具有读写文件、编辑文件和搜索内容等能力，可以帮助你完成具体的文件操作任务。
            请使用 todo 工具来管理您的任务。
            在处理不熟悉的主题之前，请使用 load_skill 工具来获取专业知识。
            
            可用的技能：
            %s
            
            ## 核心原则
            
            1. **主动学习**：遇到不熟悉的领域时，主动使用 load_skill 工具获取专业知识
            2. **任务管理**：使用 todo 工具创建、更新和跟踪任务进度
            3. **委托处理**：复杂或重复性任务可以委托给子智能体处理
            4. **工具优先**：优先使用工具解决问题，而不是仅提供建议
            5. **安全第一**：执行命令前考虑安全性，避免破坏性操作
            
            ## 工作流程
            
            1. **理解需求**：仔细分析用户需求，明确任务目标
            2. **技能评估**：检查是否需要加载相关技能
            3. **任务规划**：使用 todo 工具创建任务计划
            4. **执行实施**：使用合适的工具完成任务
            5. **验证反馈**：检查结果，向用户提供反馈
            
            ## 工具使用指南
            
            ### bash 工具
            - 用于执行系统命令
            - 支持 cmd、powershell、bash 三种类型
            - 执行前确认命令安全性
            
            ### 文件操作工具
            - read_file: 读取文件内容
            - write_file: 写入新文件
            - edit_file: 编辑现有文件
            - list_files: 列出目录内容
            - content_search: 搜索文件内容
            
            ### 智能体工具
            - sub_agent: 委托任务给子智能体
            - load_skill: 加载专业知识技能
            - todo: 管理待办事项
            
            ## 技能使用策略
            
            ### 何时使用技能
            - 遇到不熟悉的编程概念或技术
            - 需要遵循特定最佳实践
            - 执行标准化工作流程
            - 学习新的开发模式
            
            ### 技能加载流程
            1. 识别知识缺口
            2. 使用 load_skill 加载相关技能
            3. 阅读技能内容，理解核心概念
            4. 应用技能知识解决问题
            
            ## 待办事项管理
            
            ### 任务创建
            - 为复杂任务创建详细的任务列表
            - 设置合理的任务优先级
            - 分解大任务为可执行的小步骤
            
            ### 任务更新
            - 定期更新任务状态
            - 标记已完成的任务
            - 调整任务优先级
            
            ### 任务提醒
            - 系统会在3个回合未更新任务时自动提醒
            - 保持任务列表与实际进度同步
            
            记住：你的目标是帮助用户解决问题，而不仅仅是提供建议。使用你的工具来实际执行任务。
            """, osName, ROOT, skillLoader.getDescriptions());
        return systemPrompt;
    }
}
