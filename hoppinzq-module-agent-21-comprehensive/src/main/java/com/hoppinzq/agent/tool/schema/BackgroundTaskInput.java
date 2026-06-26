package com.hoppinzq.agent.tool.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import static com.hoppinzq.agent.constant.AIConstants.JSON_FAIL;
import static com.hoppinzq.agent.constant.AIConstants.OBJECT_MAPPER;

/**
 * 后台任务数据类
 * @author hoppinzq
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BackgroundTaskInput {

    @JsonProperty("taskId")
    private String taskId;

    @JsonProperty("command")
    private String command;

    @JsonProperty("status")
    private String status;  // running, completed, timeout, error

    @JsonProperty("result")
    private String result;

    @JsonProperty("startTime")
    private long startTime;

    public String getDisplayString() {
        String statusChinese = getStatusChinese(status);
        return String.format("[%s] %s", statusChinese, command.length() > 60 ? command.substring(0, 60) + "..." : command);
    }

    /**
     * 将英文状态转换为中文状态描述
     */
    private String getStatusChinese(String status) {
        if (status == null) return "未知";
        switch (status) {
            case "running": return "运行中";
            case "completed": return "已完成";
            case "timeout": return "超时";
            case "error": return "错误";
            default: return status;
        }
    }

    @Override
    public String toString() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return JSON_FAIL;
        }
    }
}
