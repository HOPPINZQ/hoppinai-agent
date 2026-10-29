package com.hoppinzq.agent.capability.multimodal;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * screenshot 工具输入。当前无必填字段（抓整屏），保留 region 以备扩展。
 * <p>原在 {@code tool/schema} 包，随能力化迁入多模态能力包（与 06 的 {@code CompactInput} 同模式）。
 *
 * @author hoppinzq
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ScreenshotInput {
    @JsonProperty("region")
    private String region;
}
