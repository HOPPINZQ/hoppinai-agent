package com.hoppinzq.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoppinzq.agent.tool.sandbox.SandboxRunner;
import com.hoppinzq.agent.tool.schema.BashInput;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;

import static com.hoppinzq.agent.constant.AIConstants.LOG_ENABLE;
import static com.hoppinzq.agent.constant.AIConstants.ROOT;

/**
 * s23 工具集合。bash 工具改为走 {@link SandboxRunner} 四层沙箱防护。
 *
 * @author hoppinzq
 */
@Slf4j
public class Tools {

    /**
     * 执行 bash 命令，经过四层沙箱：
     * L1 静态分析 → L2 目录监禁 → L3 OS 沙箱 → L4 容器隔离 → 降级 ProcessBuilder。
     * <p>
     * 注意：SandboxRunner 已自行处理 Windows/Linux 自动选择，BashInput.type 在此被忽略。
     */
    public static String executeBash(String input) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            BashInput bashInput = mapper.readValue(input, BashInput.class);
            if (LOG_ENABLE) {
                log.info("执行指令: {}", bashInput.getCommand());
            }

            boolean isLinux = System.getProperty("os.name").toLowerCase().contains("linux");
            SandboxRunner sandbox = new SandboxRunner.Builder()
                    .workDir(Path.of(ROOT))
                    .enableOsSandbox(isLinux)
                    .enableContainer(false)  // 默认关闭，需要时手动开启
                    .timeoutSec(30)
                    .build();
            return sandbox.run(bashInput.getCommand());
        } catch (Exception e) {
            if (LOG_ENABLE) {
                log.error("执行指令失败: {}", e.getMessage());
            }
            return "执行指令失败: " + e.getMessage();
        }
    }
}
