package com.hoppinzq.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoppinzq.agent.tool.MessageContent;
import com.hoppinzq.agent.tool.schema.BashInput;
import com.hoppinzq.agent.tool.schema.ReadFileInput;
import com.hoppinzq.agent.tool.schema.ScreenshotInput;
import lombok.extern.slf4j.Slf4j;

import java.awt.AWTException;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import javax.imageio.ImageIO;

import static com.hoppinzq.agent.constant.AIConstants.LOG_ENABLE;
import static com.hoppinzq.agent.constant.AIConstants.ROOT;
import static com.hoppinzq.agent.constant.AIConstants.SUPPORTS_VISION;

/**
 * s22 工具集合：
 * <ul>
 *   <li>{@link #executeBash} — 与 s01 一致</li>
 *   <li>{@link #readFile} — 增强：image/* 自动 base64，二进制给描述</li>
 *   <li>{@link #screenshot} — 通过 Robot 抓屏并返回 base64 PNG</li>
 * </ul>
 *
 * @author hoppinzq
 */
@Slf4j
public class Tools {

    /**
     * 执行 Shell 命令。与 s01 实现一致。
     */
    public static String executeBash(String input) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            BashInput bashInput = mapper.readValue(input, BashInput.class);

            if (LOG_ENABLE) {
                log.info("执行指令: {}, 类型: {}", bashInput.getCommand(), bashInput.getType());
            }

            ProcessBuilder processBuilder;
            String type = bashInput.getType();
            String command = bashInput.getCommand();

            if (type != null && !type.isEmpty()) {
                switch (type.toLowerCase()) {
                    case "cmd" -> processBuilder = new ProcessBuilder("cmd.exe", "/c", command);
                    case "powershell" -> processBuilder = new ProcessBuilder("powershell.exe", "-Command", command);
                    case "bash" -> processBuilder = new ProcessBuilder("bash", "-c", command);
                    default -> {
                        return "错误: 不支持的命令类型 '" + type + "'";
                    }
                }
            } else {
                if (System.getProperty("os.name").toLowerCase().contains("win")) {
                    processBuilder = new ProcessBuilder("cmd.exe", "/c", command);
                } else {
                    processBuilder = new ProcessBuilder("bash", "-c", command);
                }
            }

            Process process = processBuilder.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), "GBK"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            StringBuilder error = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), "GBK"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    error.append(line).append("\n");
                }
            }
            int exitCode = process.waitFor();
            String result = output.toString();
            if (!error.toString().isEmpty()) {
                result += "错误:\n" + error;
            }
            if (exitCode != 0) {
                return "执行指令失败，exitCode: " + exitCode + "\n输出: " + result;
            }
            return result.trim();
        } catch (Exception e) {
            return "执行指令失败: " + e.getMessage();
        }
    }

    /**
     * 增强版 read_file：根据 MIME 自动决定返回文本、base64 image block 还是文件描述。
     */
    public static String readFile(String input) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ReadFileInput readFileInput = mapper.readValue(input, ReadFileInput.class);

            Path fullPath = Path.of(ROOT).resolve(readFileInput.getPath()).normalize();
            if (!Files.exists(fullPath)) {
                return "文件不存在: " + readFileInput.getPath();
            }

            byte[] bytes = Files.readAllBytes(fullPath);
            String mime = Files.probeContentType(fullPath);
            if (mime == null) {
                mime = guessMime(fullPath.getFileName().toString());
            }

            // 图片：base64 image block
            if (mime != null && mime.startsWith("image/")) {
                String base64 = Base64.getEncoder().encodeToString(bytes);
                MessageContent mc = MessageContent.image(mime, base64);
                String sizeDesc = humanSize(bytes.length);

                if (SUPPORTS_VISION) {
                    // 返回 JSON 让 ZQAgent 组装成 image ContentBlockParam
                    return mc.toJson();
                } else {
                    // 降级为文本描述
                    return String.format("[图片: %s, %s] 当前模型不支持 vision，已降级为文本描述。",
                            mime, sizeDesc);
                }
            }

            // 文本：直接返回
            if (mime != null && (mime.startsWith("text/") || mime.endsWith("json")
                    || mime.endsWith("javascript") || mime.endsWith("xml") || mime.endsWith("yaml"))) {
                return new String(bytes);
            }

            // 其它二进制：给描述
            return String.format("[二进制文件: %s, %s]", mime == null ? "未知类型" : mime, humanSize(bytes.length));
        } catch (Exception e) {
            return "读取文件错误: " + e.getMessage();
        }
    }

    /**
     * 抓取屏幕截图。返回 base64 PNG（封装为 MessageContent image JSON）或降级文本描述。
     */
    public static String screenshot(String input) {
        try {
            // 无头环境会抛 AWTException / HeadlessException
            if (java.awt.GraphicsEnvironment.isHeadless()) {
                return "错误: 当前为无头环境，无法抓屏";
            }

            @SuppressWarnings("unused")
            ScreenshotInput parsed = new ObjectMapper().readValue(input, ScreenshotInput.class);

            Robot robot = new Robot();
            Rectangle screen = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            BufferedImage img = robot.createScreenCapture(screen);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            byte[] bytes = baos.toByteArray();
            String base64 = Base64.getEncoder().encodeToString(bytes);

            MessageContent mc = MessageContent.image("image/png", base64);
            if (SUPPORTS_VISION) {
                return mc.toJson();
            } else {
                return String.format("[截图: image/png, %s] 当前模型不支持 vision，已降级为文本描述。",
                        humanSize(bytes.length));
            }
        } catch (AWTException | java.awt.HeadlessException e) {
            return "错误: 无法抓屏 — " + e.getMessage();
        } catch (Exception e) {
            return "错误: " + e.getMessage();
        }
    }

    // —— 辅助方法 ——

    private static String guessMime(String filename) {
        if (filename == null) return null;
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".txt") || lower.endsWith(".md")) return "text/plain";
        if (lower.endsWith(".json")) return "application/json";
        return null;
    }

    private static String humanSize(int bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        return String.format("%.1fMB", bytes / (1024.0 * 1024));
    }
}
