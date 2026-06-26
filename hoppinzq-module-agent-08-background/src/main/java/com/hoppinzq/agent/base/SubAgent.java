package com.hoppinzq.agent.base;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.schema.SubAgentInput;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

import static com.hoppinzq.agent.constant.AIConstants.*;
import static com.hoppinzq.agent.tool.ToolDefinition.*;

/**
 * 子Agent助手类
 * 用于创建和管理子智能体，处理具体的文件操作任务
 *
 * @author hoppinzq
 */
@Slf4j
public class SubAgent {

    /**
     * 执行子Agent任务
     *
     * @param client Anthropic客户端实例，用于与AI模型交互
     * @param model  使用的AI模型名称
     * @param input  子Agent的输入参数，包含任务提示词等信息
     * @return 子Agent执行结果，如果出错则返回错误信息
     */
    public static String executeSubAgent(AnthropicClient client, String model, SubAgentInput input) {
        try {
            if (LOG_ENABLE) {
                log.info("--- 子Agent启动 ---");
                log.info("子Agent提示词: {}", input.getPrompt());
            }

            // 创建子Agent可用的工具列表
            List<ToolDefinition> subTools = new ArrayList<>();
            subTools.add(ReadFileDefinition);    // 添加读取文件工具
            subTools.add(EditFileDefinition);    // 添加编辑文件工具
            subTools.add(WriteFileDefinition);   // 添加写入文件工具

            // 创建子Agent实例，配置客户端、模型和可用工具
            ZQAgent subAgent = new ZQAgent(client, model, subTools);

            // 设置系统提示词，定义子Agent的角色和行为
            subAgent.setSystemPrompt("你是一个子智能体。你已接收一个具体任务，请使用可用的工具（读取文件、写入文件、编辑文件、搜索内容）来完成该任务。任务完成后，请直接返回结果。");

            // 执行任务并获取结果
            String result = subAgent.runTask(input.getPrompt());

            if (LOG_ENABLE) {
                log.info("--- 子Agent结束 ---");
                log.info("子Agent执行结果: {}", result);
            }

            return result;
        } catch (Exception e) {
            // 捕获异常并记录错误日志
            String errorMsg = "子Agent执行错误: " + e.getMessage();
            if (LOG_ENABLE) {
                log.error(errorMsg, e);
            }
            return errorMsg;
        }
    }

    /**
     * 执行子Agent任务（使用默认配置）
     *
     * @param input 子Agent的输入参数，包含任务提示词等信息
     * @return 子Agent执行结果，如果出错则返回错误信息
     */
    public static String executeSubAgent(SubAgentInput input) {
        // 使用默认配置创建Anthropic客户端
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(API_KEY)
                .baseUrl(BASE_URL)
                .build();
        return executeSubAgent(client, MODEL, input);
    }
}