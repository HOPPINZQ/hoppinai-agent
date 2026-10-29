package com.hoppinzq.agent.capability.multimodal;

/**
 * 多模态能力自持配置。
 * <p>原 {@code AIConstants.SUPPORTS_VISION} 属于多模态特性常量，随能力化移入本类：
 * 内核常量类不再感知"是否支持 vision"这类特性开关，拔除本能力目录即一并消失。
 *
 * @author hoppinzq
 */
public final class MultimodalConfig {
    private MultimodalConfig() {
    }

    /**
     * 当前模型是否支持 vision（图片输入）。
     * deepseek-chat 不支持，需设为 false，此时图片被降级为文本描述。
     */
    public static final boolean SUPPORTS_VISION = false;
}
