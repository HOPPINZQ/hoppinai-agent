package com.hoppinzq.agent.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;

import static com.hoppinzq.agent.constant.AIConstants.JSON_FAIL;
import static com.hoppinzq.agent.constant.AIConstants.OBJECT_MAPPER;

/**
 * 多模态消息块。一条消息可以由多个 MessageContent 组成。
 * <ul>
 *   <li>{@code type="text"}  — 纯文本（{@link #text}）</li>
 *   <li>{@code type="image"} — base64 图片（{@link #mediaType} + {@link #data}）</li>
 *   <li>{@code type="file"}  — 文件（{@link #text} 为抽取/描述）</li>
 * </ul>
 *
 * @author hoppinzq
 */
@Data
@Builder
public class MessageContent {
    private String type;
    /** type=text 或 type=file 时有效；type=file 时为抽取出的文本。 */
    private String text;
    /** type=image 时有效，如 "image/png"。 */
    private String mediaType;
    /** type=image 时有效，base64 编码的图片字节。 */
    private String data;
    /** type=file 时可选，文件名。 */
    private String filename;

    public static MessageContent text(String t) {
        return MessageContent.builder().type("text").text(t).build();
    }

    public static MessageContent image(String mediaType, String base64Data) {
        return MessageContent.builder().type("image").mediaType(mediaType).data(base64Data).build();
    }

    public static MessageContent file(String filename, String extractedText) {
        return MessageContent.builder().type("file").filename(filename).text(extractedText).build();
    }

    /** 序列化为 JSON（用于工具返回值传递）。 */
    public String toJson() {
        try {
            ObjectMapper mapper = OBJECT_MAPPER;
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return JSON_FAIL;
        }
    }

    @Override
    public String toString() {
        return toJson();
    }
}
