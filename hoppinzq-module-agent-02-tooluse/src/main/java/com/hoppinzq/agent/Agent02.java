package com.hoppinzq.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.tool.ToolDefinition;

import java.util.ArrayList;
import java.util.List;

import static com.hoppinzq.agent.constant.AIConstants.*;
import static com.hoppinzq.agent.tool.ToolDefinition.*;

/**
 * 使用工具
 * 
 * 这是一个基于Java的AI智能体框架，使用Anthropic API实现工具调用能力。
 * 该项目展示了如何构建一个能够使用多种工具（文件操作、命令执行等）的AI智能体。
 * 
 * <p><b>示例提示词：</b></p>
 * <ul>
 *   <li>"请帮我创建一个名为test.txt的文件，内容为'Hello, World!'"</li>
 *   <li>"查看src目录下所有的Java文件"</li>
 *   <li>"搜索项目中所有包含TODO注释的代码"</li>
 *   <li>"修改配置文件中的debug参数为true"</li>
 *   <li>"列出当前目录下的所有文件"</li>
 *   <li>"读取README.md文件的内容"</li>
 * </ul>
 *
 * @author hoppinzq
 * @version 1.0
 * @since 2024
 */
public class Agent02 {

    public static void main(String[] args) {
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(API_KEY)
                .baseUrl(BASE_URL)
                .build();
        List<ToolDefinition> tools = new ArrayList<>();
        tools.add(BashDefinition);
        tools.add(EditFileDefinition);
        tools.add(WriteFileDefinition);
        tools.add(ReadFileDefinition);

        tools.add(ListFilesDefinition);
        tools.add(ContentSearchDefinition);

        ZQAgent agent = new ZQAgent(client, MODEL, tools);
        agent.setSystemPrompt(buildSystemPrompt());
        agent.run();
    }

    /**
     * 构建系统提示词
     *
     * @return 格式化的系统提示词
     */
    private static String buildSystemPrompt() {
        String osName = System.getProperty("os.name").toLowerCase();
        return String.format("""
                ## 角色定义
                你是一个专业的软件开发助手。你拥有多种文件操作和代码分析工具，可以帮助用户完成各种开发任务。
                
                " +
                "## 可用工具
                " +
                "1. **bash** - 执行Shell命令（支持cmd、powershell、bash）
                " +
                "2. **read_file** - 读取文件内容
                " +
                "3. **write_file** - 写入文件内容（自动创建文件）
                " +
                "4. **edit_file** - 编辑文本文件（替换指定内容）
                " +
                "5. **list_files** - 列出文件和目录（支持文件类型过滤）
                " +
                "6. **content_search** - 搜索代码内容（支持正则表达式）
                
                " +
                "## 工作原则
                " +
                "1. **主动使用工具**：当用户提出需求时，立即思考如何组合使用工具来完成任务
                " +
                "2. **分步执行**：对于复杂任务，提供分步指导并逐步执行
                " +
                "3. **验证结果**：每次操作后验证结果，确保任务正确完成
                " +
                "4. **安全第一**：避免执行危险的系统命令，确保文件操作安全
                " +
                "5. **清晰解释**：对工具调用结果提供清晰的解释，对错误信息提供修复建议
                
                " +
                "## 最佳实践
                " +
                "- 使用 `list_files` 了解项目结构
                " +
                "- 使用 `content_search` 查找相关代码
                " +
                "- 使用 `read_file` 查看文件内容后再修改
                " +
                "- 使用 `bash` 执行命令验证修改效果
                " +
                "- 对于重要文件，建议先备份再进行修改
                
                " +
                "## 工作环境
                " +
                "- 操作系统：%s
                " +
                "- 工作目录：%s
                " +
                "记住：你是一个专业的开发助手，应该主动使用工具来解决问题，而不是仅仅提供建议。""", osName, ROOT);
    }
}