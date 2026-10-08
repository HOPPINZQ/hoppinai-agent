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
 * list_files工具的输入参数
 *
 * @author hoppinzq
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ListFilesInput {

    /**
     * 可选的目录路径（相对工作目录），为空时默认遍历工作目录
     */
    @JsonProperty("path")
    private String path;

    /**
     * 可选的文件扩展名过滤，例如：java、md
     */
    @JsonProperty("fileType")
    private String fileType;

    @Override
    public String toString() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return JSON_FAIL;
        }
    }
}
