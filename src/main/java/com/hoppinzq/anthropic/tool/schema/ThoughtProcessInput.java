package com.hoppinzq.anthropic.tool.schema;

import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hoppinzq.anthropic.tool.ToolDefinition;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ThoughtProcessInput {
    @JsonProperty("Thought")
    private String thought;

    @JsonProperty("Action")
    private String action;

    @JsonProperty("Action Input")
    private String actionInput;

    @JsonProperty("Observation")
    private String observation;// 使用 JsonNode

    public ThoughtProcessInput(String content) {
        try {
            String[] lines = content.split("\n");
            for (String line : lines) {
                if (line.startsWith("Thought:")) {
                    thought = line.split("Thought:")[1].trim();
                } else if (line.startsWith("Action:")) {
                    action = line.split("Action:")[1].trim();
                } else if (line.startsWith("Observation:")) {
                    observation = line.split("Observation:")[1].trim();
                } else if (line.startsWith("Action Input:")) {
                    actionInput = line.split("Action Input:")[1].trim();
                }
            }
            if (thought == null && action == null && actionInput == null && observation == null) {
                throw new RuntimeException("格式化错误x");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void toolCallJson(List<ToolDefinition> tools, List<ContentBlockParam> toolResults) {
        if (action != null && actionInput != null) {
            System.out.printf("\u001b[96m工具\u001b[0m: %s(%s)%n", action, actionInput);
            String toolResult = null;
            Exception toolError = null;
            boolean toolFound = false;

            for (ToolDefinition tool : tools) {
                if (tool.getName().equals(action)) {
                    try {
                        toolResult = tool.getFunction().apply(actionInput);
                        observation = toolResult;
                        System.out.printf("\u001b[92m结果\u001b[0m: %s%n", toolResult);
                    } catch (Exception e) {
                        toolError = e;
                        System.out.printf("\u001b[91m错误\u001b[0m: %s%n", e.getMessage());
                    }
                    toolFound = true;
                    break;
                }
            }

            if (!toolFound) {
                toolError = new Exception("工具 '" + action + "' 没有找到");
                System.out.printf("\u001b[91m错误\u001b[0m: %s%n", toolError.getMessage());
            }

            if (toolError != null) {
                toolResults.add(ContentBlockParam.ofToolResult(
                        ToolResultBlockParam.builder()
                                .toolUseId(action)
                                .content(toolError.getMessage())
                                .isError(true)
                                .build()
                ));
            } else {
                toolResults.add(ContentBlockParam.ofToolResult(
                        ToolResultBlockParam.builder()
                                .toolUseId(action)
                                .content(toolResult)
                                .isError(false)
                                .build()
                ));
            }
        }
    }
}