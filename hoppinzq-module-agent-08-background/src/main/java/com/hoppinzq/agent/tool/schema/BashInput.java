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
 * Bash 命令执行输入参数
 * <p>
 * 用于封装执行 shell 命令所需的参数，支持多种命令类型
 * </p>
 *
 * @author hoppinzq
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BashInput {

    /**
     * 要执行的命令字符串
     */
    @JsonProperty("command")
    private String command;

    /**
     * 命令类型
     * 支持的值：
     * <ul>
     * <li>cmd - Windows CMD 命令提示符</li>
     * <li>powershell - Windows PowerShell</li>
     * <li>bash - Linux/Mac Bash shell（或 Windows Git Bash）</li>
     * </ul>
     * 如果不指定此字段，系统将根据操作系统自动选择：
     * <ul>
     * <li>Windows: 默认使用 cmd</li>
     * <li>Linux/Mac: 默认使用 bash</li>
     * </ul>
     */
    @JsonProperty("type")
    private String type;

   @Override
   public String toString() {
      try {
         return OBJECT_MAPPER.writeValueAsString(this);
      } catch (JsonProcessingException e) {
         return JSON_FAIL;
      }
   }
}