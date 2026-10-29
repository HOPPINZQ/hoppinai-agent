package com.hoppinzq.agent.capability.multimodal;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.capability.AgentCommand;
import com.hoppinzq.agent.capability.Capability;
import com.hoppinzq.agent.client.LlmMessage;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.schema.ReadFileInput;
import lombok.extern.slf4j.Slf4j;

import java.awt.AWTException;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import static com.hoppinzq.agent.capability.multimodal.MultimodalConfig.SUPPORTS_VISION;
import static com.hoppinzq.agent.constant.AIConstants.OBJECT_MAPPER;
import static com.hoppinzq.agent.constant.AIConstants.ROOT;

/**
 * 多模态能力（可整体复制的能力包）。
 *
 * <p>向内核提供：
 * <ul>
 *   <li>2 个工具：{@code read_file}（图片自动转 base64 内容块）、{@code screenshot}（Robot 抓屏）</li>
 *   <li>1 个命令：{@code /img &lt;path&gt; [问题]} —— 以 user 消息的 parts（文本/图片块）直接附图给模型</li>
 *   <li>系统提示片段：告知模型多模态使用约定与降级行为</li>
 * </ul>
 *
 * <p>多模态内容的两条通路：
 * <ol>
 *   <li><b>user parts</b>（真正的图片块）：{@link LlmMessage#getParts()} 非空时，
 *       由 Provider 映射为 Anthropic {@code image} 块 / OpenAI {@code image_url} 块 —— /img 命令走此路；</li>
 *   <li><b>工具结果 JSON</b>：中立模型的 {@code role=tool} 消息只有文本，图片以
 *       {@code {"type":"image",...}} JSON 文本回灌（模型可读但不构成图片块），仅作降级通路。</li>
 * </ol>
 *
 * <p>对内核的依赖仅限：Capability 契约、client（协议中立模型）、tool/ToolDefinition、
 * {@code AIConstants.ROOT}（工作目录）。拔除本目录并删除注册行即可完全移除该能力。
 *
 * @author hoppinzq
 */
@Slf4j
public class MultimodalCapability implements Capability {

    /** onStart 时捕获的宿主 Agent，用于 /img 命令直接向上下文追加多模态消息 */
    private ZQAgent agent;

    @Override
    public String name() {
        return "multimodal";
    }

    @Override
    public void onStart(ZQAgent agent) {
        this.agent = agent;
    }

    // ============================== 工具 ==============================

    @Override
    public List<ToolDefinition> tools() {
        ToolDefinition readFile = new ToolDefinition(
                "read_file",
                "读取指定文件路径。若为图片（image/*）自动转 base64 image block；若为文本返回字符串；其他二进制返回文件描述。",
                ToolDefinition.createInputSchema(
                        Map.of("path", ToolDefinition.createProperty("string", "工作目录中文件的相对路径。")),
                        List.of("path")
                ),
                ReadFileInput.class,
                this::invokeReadFile
        );

        ToolDefinition screenshot = new ToolDefinition(
                "screenshot",
                "抓取当前屏幕截图，返回 base64 编码的 PNG。适用于需要查看屏幕内容的场景。",
                ToolDefinition.createInputSchema(Map.of(), List.of()),
                ScreenshotInput.class,
                this::invokeScreenshot
        );
        return List.of(readFile, screenshot);
    }

    /**
     * read_file 工具执行体：根据 MIME 自动决定返回文本、base64 图片 JSON 还是文件描述。
     * （原 Tools.readFile 静态实现随能力化迁入本包，工具与逻辑同包后直接调用。）
     */
    private String invokeReadFile(ReadFileInput input) {
        try {
            Path fullPath = Path.of(ROOT).resolve(input.getPath()).normalize();
            if (!Files.exists(fullPath)) {
                return "文件不存在: " + input.getPath();
            }

            byte[] bytes = Files.readAllBytes(fullPath);
            String mime = Files.probeContentType(fullPath);
            if (mime == null) {
                mime = guessMime(fullPath.getFileName().toString());
            }

            // 图片：base64 图片 JSON（vision 开启时）或降级文本描述
            if (mime != null && mime.startsWith("image/")) {
                String base64 = Base64.getEncoder().encodeToString(bytes);
                if (SUPPORTS_VISION) {
                    // 中立模型的 tool 消息只承载文本：图片以 JSON 文本回灌，
                    // 真正的图片块请用 /img 命令（user 消息 parts 通路）
                    ObjectNode node = OBJECT_MAPPER.createObjectNode();
                    node.put("type", "image");
                    node.put("mediaType", mime);
                    node.put("data", base64);
                    return OBJECT_MAPPER.writeValueAsString(node);
                }
                return String.format("[图片: %s, %s] 当前模型不支持 vision，已降级为文本描述。",
                        mime, humanSize(bytes.length));
            }

            // 文本：直接返回
            if (mime != null && (mime.startsWith("text/") || mime.endsWith("json")
                    || mime.endsWith("javascript") || mime.endsWith("xml") || mime.endsWith("yaml"))) {
                return new String(bytes);
            }

            // 其它二进制：给描述
            return String.format("[二进制文件: %s, %s]",
                    mime == null ? "未知类型" : mime, humanSize(bytes.length));
        } catch (Exception e) {
            return "读取文件错误: " + e.getMessage();
        }
    }

    /**
     * screenshot 工具执行体：Robot 抓屏，返回 base64 PNG（封装为图片 JSON）或降级文本描述。
     */
    private String invokeScreenshot(ScreenshotInput input) {
        try {
            // 无头环境会抛 AWTException / HeadlessException
            if (java.awt.GraphicsEnvironment.isHeadless()) {
                return "错误: 当前为无头环境，无法抓屏";
            }

            Robot robot = new Robot();
            Rectangle screen = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            BufferedImage img = robot.createScreenCapture(screen);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            byte[] bytes = baos.toByteArray();
            String base64 = Base64.getEncoder().encodeToString(bytes);

            if (SUPPORTS_VISION) {
                ObjectNode node = OBJECT_MAPPER.createObjectNode();
                node.put("type", "image");
                node.put("mediaType", "image/png");
                node.put("data", base64);
                return OBJECT_MAPPER.writeValueAsString(node);
            }
            return String.format("[截图: image/png, %s] 当前模型不支持 vision，已降级为文本描述。",
                    humanSize(bytes.length));
        } catch (AWTException | java.awt.HeadlessException e) {
            return "错误: 无法抓屏 — " + e.getMessage();
        } catch (Exception e) {
            return "错误: " + e.getMessage();
        }
    }

    // ============================== 命令 ==============================

    @Override
    public List<AgentCommand> commands() {
        return List.of(new AgentCommand() {
            @Override
            public String name() {
                return "/img";
            }

            @Override
            public String description() {
                return "把图片附给模型：/img <路径> [问题]";
            }

            @Override
            public void execute() {
                // execute() 拿不到原始参数行，真正解析在 Agent22 的 /img 前缀分发里
                printImgUsage();
            }
        });
    }

    /**
     * /img 命令入口（含参数）。由入口类在命令分发处按前缀转交：
     * {@code /img <路径> [问题]} —— 读取图片并以 user 消息 parts 直接附图。
     *
     * @return 是否为 /img 命令（ true 时外层循环继续等待下一条输入）
     */
    public boolean handleImgCommand(String input) {
        if (!input.startsWith("/img")) {
            return false;
        }
        String rest = input.substring("/img".length()).trim();
        if (rest.isEmpty()) {
            printImgUsage();
            return true;
        }
        // 第一个 token 是路径，其余（若有）作为随图提出的问题
        String[] segments = rest.split("\\s+", 2);
        String path = segments[0];
        String question = segments.length > 1 ? segments[1] : "请描述这张图片的内容。";

        List<LlmMessage.ContentPart> parts = new ArrayList<>();
        try {
            Path fullPath = Path.of(ROOT).resolve(path).normalize();
            byte[] bytes = Files.readAllBytes(fullPath);
            String mime = Files.probeContentType(fullPath);
            if (mime == null || !mime.startsWith("image/")) {
                System.out.println("[img] 不是图片文件: " + path);
                return true;
            }
            if (SUPPORTS_VISION) {
                // 真正的图片块：user 消息 parts，由 Provider 映射为各协议的 image 内容块
                parts.add(LlmMessage.ContentPart.builder()
                        .type("image").mediaType(mime)
                        .data(Base64.getEncoder().encodeToString(bytes))
                        .build());
            } else {
                // 降级：图片不可发送，以文本描述占位
                parts.add(LlmMessage.ContentPart.builder()
                        .type("text")
                        .text(String.format("[图片: %s, %s] 当前模型不支持 vision，已降级为文本描述。",
                                mime, humanSize(bytes.length)))
                        .build());
            }
        } catch (Exception e) {
            System.out.println("[img] 读取图片失败: " + e.getMessage());
            return true;
        }
        parts.add(LlmMessage.ContentPart.builder().type("text").text(question).build());

        appendMultimodalMessage(parts);
        System.out.printf("[img] 已附带图片 %s（vision=%s），请继续输入你的问题或直接等待回答%n",
                path, SUPPORTS_VISION ? "开" : "关（降级为文本）");
        return true;
    }

    /**
     * 向上下文追加一条多模态 user 消息。内核 {@code appendMessage} 是 protected，
     * 能力包通过"改列表 + 通知会话管理器"两步复刻同一行为（含落盘）。
     */
    private void appendMultimodalMessage(List<LlmMessage.ContentPart> parts) {
        LlmMessage message = LlmMessage.builder()
                .role(LlmMessage.ROLE_USER)
                .parts(parts)
                .build();
        agent.getMessageParams().add(message);
        if (agent.getSessionManager() != null) {
            // 多模态 parts 会被 MessageConverter 降级为描述文本落盘（base64 不进会话文件）
            agent.getSessionManager().onMessageAppended(message);
        }
    }

    private void printImgUsage() {
        System.out.println("[img] 用法: /img <路径> [问题]，例如 /img test.png 这张图里有什么？");
    }

    // ============================== 辅助 ==============================

    private static String guessMime(String filename) {
        if (filename == null) {
            return null;
        }
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".txt") || lower.endsWith(".md")) {
            return "text/plain";
        }
        if (lower.endsWith(".json")) {
            return "application/json";
        }
        return null;
    }

    private static String humanSize(int bytes) {
        if (bytes < 1024) {
            return bytes + "B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1fKB", bytes / 1024.0);
        }
        return String.format("%.1fMB", bytes / (1024.0 * 1024));
    }

    // ============================== 提示词与描述 ==============================

    @Override
    public List<String> promptFragments() {
        return List.of(
                "- read_file 遇到图片会自动转 base64 图片内容，screenshot 可抓取当前屏幕\n" +
                "- 用户给出图片时，先描述图片内容再回答问题\n" +
                "- 当前模型若不支持 vision，图片会被降级为文本描述，请明确告诉用户");
    }

    /** 启动横幅用的能力摘要 */
    public String description() {
        return "多模态能力: read_file(图片自动 base64) / screenshot(抓屏) / /img <路径> [问题]\n" +
                "  vision 支持 = " + (SUPPORTS_VISION ? "开启" : "关闭（图片降级为文本描述）");
    }
}
