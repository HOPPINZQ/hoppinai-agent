package com.hoppinzq.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoppinzq.agent.tool.schema.BashInput;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import static com.hoppinzq.agent.constant.AIConstants.LOG_ENABLE;

/**
 * s22 工具集合。
 * <p>多模态工具（read_file 图片增强 / screenshot）已随能力化迁入
 * {@code capability/multimodal/MultimodalCapability}，本类只保留基座工具 bash。
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
}
