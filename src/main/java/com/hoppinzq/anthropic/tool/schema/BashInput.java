package com.hoppinzq.anthropic.tool.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import static com.hoppinzq.anthropic.constant.AIConstants.JSON_FAIL;
import static com.hoppinzq.anthropic.constant.AIConstants.OBJECT_MAPPER;

/**
 * @author hoppinzq
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BashInput {

   @JsonProperty("command")
   private String command;

   @Override
   public String toString() {
      try {
         return OBJECT_MAPPER.writeValueAsString(this);
      } catch (JsonProcessingException e) {
         return JSON_FAIL;
      }
   }
}