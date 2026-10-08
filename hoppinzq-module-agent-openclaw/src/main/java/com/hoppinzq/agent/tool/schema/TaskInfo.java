package com.hoppinzq.agent.tool.schema;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

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
    private int id;

    @JsonProperty("subject")
    private String subject;

    @JsonProperty("description")
    private String description;

    @JsonProperty("status")
    private String status; // pending, in_progress, completed

    @JsonProperty("blockedBy")
    private List<Integer> blockedBy; // 被哪些任务阻塞

    @JsonProperty("blocks")
    private List<Integer> blocks;    // 阻塞哪些任务

    @JsonProperty("owner")
    private String owner;

    public TaskInfo() {
        this.blockedBy = new ArrayList<>();
        this.blocks = new ArrayList<>();
        this.status = "pending";
        this.owner = "";
    }

    @JsonIgnore
    public String getStatusMarker() {
        return switch (status) {
            case "pending" -> "[ ]";
            case "in_progress" -> "[>]";
            case "completed" -> "[x]";
            default -> "[?]";
        };
    }

    @JsonIgnore
    public String getDisplayString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getStatusMarker()).append(" #").append(id).append(": ").append(subject);
        if (!blockedBy.isEmpty()) {
            sb.append(" (blocked by: ").append(blockedBy).append(")");
        }
        return sb.toString();
    }
}
