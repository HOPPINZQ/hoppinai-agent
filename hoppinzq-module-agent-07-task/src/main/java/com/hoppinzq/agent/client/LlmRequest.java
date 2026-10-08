package com.hoppinzq.agent.client;

import com.hoppinzq.agent.tool.ToolDefinition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 协议中立的 LLM 请求。
 *
 * @author hoppinzq
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LlmRequest {
    /** 模型名称 */
    private String model;
    /** 系统提示词；null 表示不设置 */
    private String systemPrompt;
    /** 对话消息列表（user / assistant / tool） */
    @Builder.Default
    private List<LlmMessage> messages = new ArrayList<>();
    /** 可用工具列表 */
    @Builder.Default
    private List<ToolDefinition> tools = new ArrayList<>();
    /** 最大输出 token 数 */
    private int maxTokens;
    /** 采样温度；null 使用服务端默认值 */
    private Double temperature;
}
