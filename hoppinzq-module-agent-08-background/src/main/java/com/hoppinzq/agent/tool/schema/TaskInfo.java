package com.hoppinzq.agent.tool.schema;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 任务数据类
 *
 * @author hoppinzq
 */
@Data
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class TaskInfo {

    @JsonProperty("id")
    private String id;

    @JsonProperty("sessionId")
    private String sessionId;

    @JsonProperty("subject")
    private String subject;

    @JsonProperty("description")
    private String description;

    @JsonProperty("status")
    private String status; // pending, in_progress, completed

    @JsonProperty("blockedBy")
    private List<String> blockedBy; // 被哪些任务阻塞

    @JsonProperty("blocks")
    private List<String> blocks;    // 阻塞哪些任务

    @JsonProperty("owner")
    private String owner;

    @JsonProperty("createdAt")
    private long createdAt;

    public TaskInfo() {
        this.blockedBy = new ArrayList<>();
        this.blocks = new ArrayList<>();
        this.status = "pending";
        this.owner = "";
        this.createdAt = System.currentTimeMillis();
    }

    @JsonIgnore
    public String getShortId() {
        if (id == null || id.length() < 8) return id;
        return id.substring(0, 8);
    }

    @JsonIgnore
    public String getStatusMarker() {
        return switch (status) {
            case "pending" -> "○";
            case "in_progress" -> "●";
            case "completed" -> "✓";
            default -> "?";
        };
    }

    @JsonIgnore
    public String getStatusText() {
        return switch (status) {
            case "pending" -> "待处理";
            case "in_progress" -> "进行中";
            case "completed" -> "已完成";
            default -> "未知";
        };
    }

    @JsonIgnore
    public String getDisplayString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getStatusMarker()).append(" ");
        sb.append(getShortId()).append(": ");
        sb.append(subject);

        // 状态文本
        sb.append(" [").append(getStatusText()).append("]");

        // 所有者信息
        if (owner != null && !owner.isEmpty()) {
            sb.append(" (").append(owner).append(")");
        }

        // 阻塞信息（单行）
        if (!blockedBy.isEmpty()) {
            sb.append(" {blocked: ").append(formatIdList(blockedBy)).append("}");
        }

        return sb.toString();
    }

    @JsonIgnore
    private String formatIdList(List<String> ids) {
        return ids.stream()
                .map(id -> id.length() > 8 ? id.substring(0, 8) : id)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }
}
