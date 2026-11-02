package com.hoppinzq.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoppinzq.agent.tool.schema.BashInput;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import static com.hoppinzq.agent.constant.AIConstants.LOG_ENABLE;
import static com.hoppinzq.agent.constant.AIConstants.OBJECT_MAPPER;


/**
 * @author hoppinzq
 */
@Slf4j
public class Tools {

    /**
     * 执行命令并返回结果
     * 该函数接收一个JSON格式的输入字符串，解析出要执行的命令和命令类型，
     * 根据指定的类型（cmd/powershell/bash）使用不同的方式执行命令，
     * 并捕获命令的标准输出和错误输出。
     * <p>
     * 支持的命令类型：
     * - cmd: Windows CMD 命令提示符
     * - powershell: Windows PowerShell
     * - bash: Linux/Mac Bash shell（或 Windows Git Bash）
     * <p>
     * 如果不指定类型，则根据操作系统自动选择：
     * - Windows: 默认使用 cmd
     * - Linux/Mac: 默认使用 bash
     *
     * @param input JSON格式的输入字符串，包含：
     *              - command: 要执行的命令（必填）
     *              - type: 命令类型，可选值为 "cmd"、"powershell"、"bash"（可选）
     * @return 命令执行结果字符串，包含标准输出和错误信息（如果有）；如果执行失败，会包含错误码和错误信息
     */
    /** bash 危险命令黑名单：最后一道底线（与 permission 能力的可配置规则互补，参考 Python s01） */
    private static final String[] DANGEROUS_COMMANDS = {
            "rm -rf /", "sudo", "shutdown", "reboot", "> /dev/"
    };
    /** 单条命令最大执行时长（秒）：超时强杀子进程，防止交互式命令把 Agent 挂死 */
    private static final long BASH_TIMEOUT_SECONDS = 120;
    /** 单次工具结果最大字符数：超长截断，防止大输出撑爆上下文 */
    private static final int BASH_MAX_OUTPUT_CHARS = 50_000;

    public static String executeBash(String input) {
        try {
            BashInput bashInput = OBJECT_MAPPER.readValue(input, BashInput.class);
            String command = bashInput.getCommand();
            String type = bashInput.getType();

            if (command == null || command.isBlank()) {
                return "错误: command 不能为空";
            }
            // 危险命令黑名单：命中直接拦截，不执行
            for (String dangerous : DANGEROUS_COMMANDS) {
                if (command.contains(dangerous)) {
                    return "Error: Dangerous command blocked";
                }
            }

            if (LOG_ENABLE) {
                log.info("执行指令: {}, 类型: {}", command, type);
            }

            ProcessBuilder processBuilder;
            if (type != null && !type.isEmpty()) {
                switch (type.toLowerCase()) {
                    case "cmd":
                        processBuilder = new ProcessBuilder("cmd.exe", "/c", command);
                        break;
                    case "powershell":
                        processBuilder = new ProcessBuilder("powershell.exe", "-Command", command);
                        break;
                    case "bash":
                        processBuilder = new ProcessBuilder("bash", "-c", command);
                        break;
                    default:
                        return "错误: 不支持的命令类型 '" + type + "'，仅支持 cmd、powershell 或 bash";
                }
            } else {
                // 未指定类型时，根据操作系统自动选择
                if (System.getProperty("os.name").toLowerCase().contains("win")) {
                    processBuilder = new ProcessBuilder("cmd.exe", "/c", command);
                } else {
                    processBuilder = new ProcessBuilder("bash", "-c", command);
                }
            }

            // 合并 stderr 到 stdout：单流顺序读取，避免"先读 stdout 再读 stderr"在子进程
            // 大量写 stderr 时互相等死（管道缓冲区写满即阻塞）
            processBuilder.redirectErrorStream(true);

            // 根据命令类型与操作系统选择编码
            String charsetName = "bash".equalsIgnoreCase(type)
                    || (!System.getProperty("os.name").toLowerCase().contains("win")
                        && (type == null || type.isEmpty()))
                    ? "UTF-8" : "GBK";

            Process process = processBuilder.start();

            // 持续排空管道（读到 EOF），超出上限后只排空不再拼接，保证 waitFor 不会因缓冲区满而挂起
            StringBuilder output = new StringBuilder();
            boolean truncated = false;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), charsetName))) {
                char[] buf = new char[4096];
                int n;
                while ((n = reader.read(buf)) != -1) {
                    if (output.length() < BASH_MAX_OUTPUT_CHARS) {
                        output.append(buf, 0, Math.min(n, BASH_MAX_OUTPUT_CHARS - output.length()));
                    } else {
                        truncated = true;
                    }
                }
            }

            // 带超时等待：交互式命令（ping / python 无参）不再挂死 Agent
            boolean finished = process.waitFor(BASH_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "Error: Timeout (" + BASH_TIMEOUT_SECONDS + "s)";
            }
            int exitCode = process.exitValue();

            String result = output.toString().strip();
            if (truncated) {
                result += "... (输出超过 " + BASH_MAX_OUTPUT_CHARS + " 字符已截断)";
            }
            if (result.isEmpty()) {
                return "(no output)";
            }
            if (exitCode != 0) {
                if (LOG_ENABLE) {
                    log.error("执行指令失败: {}, 类型: {}, exitCode: {}", command, type, exitCode);
                }
                return "执行指令失败，exitCode: " + exitCode + "\n输出: " + result;
            }

            if (LOG_ENABLE) {
                log.info("执行指令成功: {}, 类型: {}", command, type);
            }
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Error: 执行被中断";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
