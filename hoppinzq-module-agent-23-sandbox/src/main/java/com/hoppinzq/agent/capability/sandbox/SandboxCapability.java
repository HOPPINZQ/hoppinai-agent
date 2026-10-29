package com.hoppinzq.agent.capability.sandbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.hoppinzq.agent.capability.AgentHook;
import com.hoppinzq.agent.capability.Capability;
import com.hoppinzq.agent.client.LlmMessage;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.schema.BashInput;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static com.hoppinzq.agent.capability.sandbox.SandboxConfig.ENABLE_CONTAINER;
import static com.hoppinzq.agent.capability.sandbox.SandboxConfig.ENABLE_OS_SANDBOX;
import static com.hoppinzq.agent.capability.sandbox.SandboxConfig.TIMEOUT_SEC;
import static com.hoppinzq.agent.capability.sandbox.SandboxConfig.UNRESTRICTED_PATH_MODE;
import static com.hoppinzq.agent.constant.AIConstants.LOG_ENABLE;
import static com.hoppinzq.agent.constant.AIConstants.OBJECT_MAPPER;
import static com.hoppinzq.agent.constant.AIConstants.ROOT;

/**
 * 安全沙箱能力（可整体复制的能力包）。
 *
 * <p>向内核提供：
 * <ul>
 *   <li>1 个工具：{@code bash}（经 L1→L2→L3→L4→降级 四层防护执行）</li>
 *   <li>1 个钩子：{@code beforeToolCall} —— 工具执行前的路径越界检查
 *       （bash 命令文本走 L2 目录监禁扫描；带 {@code path} 字段的工具对路径做监禁目录校验，
 *       越界返回拦截文案，内核以该文本作为工具结果回灌）</li>
 *   <li>系统提示片段：告知模型沙箱层级与被拒时的应对</li>
 * </ul>
 *
 * <p>层级划分：
 * <ul>
 *   <li>L1 静态分析（{@link StaticAnalyzer}）—— 黑名单拦截高危命令</li>
 *   <li>L2 目录监禁（{@link DirectoryJail}）—— 路径 token 解析后必须在监禁目录（ROOT）内</li>
 *   <li>L3 OS 沙箱（{@link OsSandbox}，Linux bwrap）</li>
 *   <li>L4 容器隔离（{@link ContainerIsolation}，docker）</li>
 *   <li>降级：带超时、cwd 锁定监禁目录的 ProcessBuilder（{@link SandboxRunner}）</li>
 * </ul>
 * beforeToolCall 的路径检查是 L2 的<b>前置层</b>：在工具执行前就拦截并回灌原因，
 * {@link SandboxRunner#run} 内部的 L2 仍然保留（纵深防御，防绕过钩子的路径）。
 *
 * <p>对内核的依赖仅限：Capability 契约、client（协议中立模型）、tool/ToolDefinition、
 * {@code AIConstants.ROOT}（监禁根目录）。拔除本目录并删除注册行即可完全移除该能力。
 *
 * @author hoppinzq
 */
@Slf4j
public class SandboxCapability implements Capability {

    /** L2 目录监禁（监禁根 = 模块工作目录） */
    private final DirectoryJail jail = new DirectoryJail(Path.of(ROOT));

    @Override
    public String name() {
        return "sandbox";
    }

    // ============================== 工具 ==============================

    @Override
    public List<ToolDefinition> tools() {
        ToolDefinition bash = new ToolDefinition(
                "bash",
                "执行 Shell 命令并返回其输出结果。适用于需要运行各种 Shell 命令的场景，可用于文件操作、程序执行、系统信息查询等多种任务。"
                        + "比如：如果用户让你打开网站，直接使用 start [url]；你要进入目录，如果返回系统找不到指定的路径，则尝试一下绝对路径。"
                        + "始终记住，你处于 " + System.getProperty("os.name").toLowerCase() + " 操作系统。",
                ToolDefinition.createInputSchema(
                        Map.of(
                                "command", ToolDefinition.createProperty("string", "要执行的命令字符串。"),
                                "type", ToolDefinition.createProperty("string", "命令类型，可选值：cmd（Windows CMD）、powershell（Windows PowerShell）、bash（Linux/Mac）。如不指定，系统将根据操作系统自动选择。")
                        ),
                        List.of("command", "type")
                ),
                BashInput.class,
                this::invokeBashTool
        );
        return List.of(bash);
    }

    /**
     * bash 工具执行体：走 {@link SandboxRunner} 四层沙箱。
     * （原 Tools.executeBash 静态实现随能力化迁入本包，工具与沙箱同包后直接调用，
     * 不再需要任何静态回调钩子。）
     * <p>注意：SandboxRunner 已自行处理 Windows/Linux 自动选择，BashInput.type 在此被忽略。
     */
    private String invokeBashTool(BashInput input) {
        try {
            if (LOG_ENABLE) {
                log.info("执行指令: {}", input.getCommand());
            }

            boolean isLinux = System.getProperty("os.name").toLowerCase().contains("linux");
            SandboxRunner sandbox = new SandboxRunner.Builder()
                    .workDir(Path.of(ROOT))
                    .enableOsSandbox(ENABLE_OS_SANDBOX && isLinux)
                    .enableContainer(ENABLE_CONTAINER)
                    .timeoutSec(TIMEOUT_SEC)
                    .build();
            return sandbox.run(input.getCommand());
        } catch (Exception e) {
            if (LOG_ENABLE) {
                log.error("执行指令失败: {}", e.getMessage());
            }
            return "执行指令失败: " + e.getMessage();
        }
    }

    // ============================== 钩子 ==============================

    @Override
    public List<AgentHook> hooks() {
        return List.of(new AgentHook() {
            @Override
            public String beforeToolCall(LlmMessage.ToolCall call) {
                return checkPaths(call);
            }
        });
    }

    /**
     * 工具执行前的路径越界检查（L2 目录监禁的前置层）：
     * <ul>
     *   <li>{@code bash} —— 对命令文本做路径 token 扫描（{@link DirectoryJail#validate}）；</li>
     *   <li>带 {@code path} 字段的工具（read_file / write_file 等，本模块当前未装配，
     *       规则前置以便复用）—— 路径解析后必须在监禁目录内。</li>
     * </ul>
     *
     * @return null = 放行；非 null = 拦截原因（内核以该文本作为工具结果回灌给模型）
     */
    private String checkPaths(LlmMessage.ToolCall call) {
        // 无限制模式：与内核 Tools.resolvePath 的同名开关语义一致，true 时跳过路径检查
        if (UNRESTRICTED_PATH_MODE) {
            return null;
        }
        String arguments = call.getArgumentsJson();
        try {
            JsonNode input = OBJECT_MAPPER.readTree(arguments == null || arguments.isBlank() ? "{}" : arguments);
            if ("bash".equals(call.getName()) && input.hasNonNull("command")) {
                // 命令文本里的路径 token 全部过一遍 L2 目录监禁
                return jail.validate(input.get("command").asText());
            }
            if (input.hasNonNull("path")) {
                Path resolved = Path.of(ROOT).resolve(input.get("path").asText()).normalize();
                if (!resolved.startsWith(Path.of(ROOT))) {
                    return "路径越界: '" + input.get("path").asText() + "' 解析后为 " + resolved
                            + "，不在监禁目录 " + Path.of(ROOT) + " 内";
                }
            }
        } catch (Exception ignore) {
            // 入参不是合法 JSON 时不拦截：交给工具执行体自行报错
        }
        return null;
    }

    // ============================== 提示词与描述 ==============================

    @Override
    public List<String> promptFragments() {
        return List.of(
                "- bash 工具被四层沙箱保护：L1 静态分析（黑名单拦截高危命令）→ L2 目录监禁（拒绝路径越界）→"
                        + " L3 OS 沙箱（Linux bwrap）→ L4 容器隔离（docker）→ 降级带超时 ProcessBuilder\n" +
                "- 若命令被沙箱拒绝，如实告知用户拒绝原因（响应中带 ❌ 标记）；安全第一，不要试图绕过沙箱");
    }

    /** 启动横幅用的能力摘要 */
    public String description() {
        boolean isLinux = System.getProperty("os.name").toLowerCase().contains("linux");
        return "四层沙箱防护:\n" +
                "  L1 静态分析: 总是启用（高危命令黑名单）\n" +
                "  L2 目录监禁: 监禁根 = " + Path.of(ROOT) + (UNRESTRICTED_PATH_MODE ? "（当前为无限制模式，已关闭）" : "") + "\n" +
                "  L3 OS 沙箱: " + (ENABLE_OS_SANDBOX && isLinux ? "启用（Linux bwrap，可用时生效）" : "关闭（仅 Linux 生效）") + "\n" +
                "  L4 容器隔离: " + (ENABLE_CONTAINER ? "启用（docker）" : "关闭") + "\n" +
                "  降级执行: 超时 " + TIMEOUT_SEC + "s，cwd 锁定监禁目录";
    }
}
