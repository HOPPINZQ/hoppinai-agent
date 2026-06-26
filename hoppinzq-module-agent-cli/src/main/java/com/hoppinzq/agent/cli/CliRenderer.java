package com.hoppinzq.agent.cli;

import java.util.Arrays;
import java.util.List;

/**
 * 终端 ANSI 面板渲染器。
 * 用 box-drawing 字符绘制带标题的面板，截断超长工具返回值。
 * 所有方法直接写到 {@link System#out}，调用方无需关心颜色。
 *
 * @author hoppinzq
 */
public final class CliRenderer {

    // —— ANSI 常量 ——
    public static final String RESET = "\u001b[0m";
    public static final String BOLD = "\u001b[1m";
    public static final String DIM = "\u001b[2m";
    public static final String CYAN = "\u001b[36m";
    public static final String YELLOW = "\u001b[33m";
    public static final String GREEN = "\u001b[32m";
    public static final String RED = "\u001b[31m";
    public static final String MAGENTA = "\u001b[35m";
    public static final String BLUE = "\u001b[34m";
    public static final String GRAY = "\u001b[90m";

    // 终端面板最大宽度（粗略）
    private static final int PANEL_WIDTH = 60;
    private static final int TRUNCATE_THRESHOLD = 500;
    private static final int TRUNCATE_HEAD = 200;
    private static final int TRUNCATE_TAIL = 100;

    private CliRenderer() {
    }

    // —— 顶层渲染 API ——

    /** 打印启动 banner。 */
    public static void banner() {
        String ascii = """
                  _   _ _____ __  ___   _    ___ _  __ ___ ___ _   _\s
                 | | | / |___// / / __| |_  | __|/ _|| _ \\ __/_\s
                 | |_| | |_  / /__\\__ \\ ' \\ | |_| \\__|  _/ _/(_-<
                 |_____|_\\_\\/____|___/_||_|\\___|_| |_|\\___/__/__/\s
                """;
        System.out.println(CYAN + BOLD + ascii + RESET);
        System.out.println(GRAY + "  hoppinzq CLI Agent · 单会话模式 · 输入 '退出' 或 Ctrl+C 退出" + RESET);
        separator();
    }

    /** 分隔线。 */
    public static void separator() {
        System.out.println(GRAY + "─".repeat(PANEL_WIDTH) + RESET);
    }

    /** AI 思考面板（dim 灰色）。 */
    public static void thinking(String text) {
        panel("🤔", "AI 思考", GRAY, splitLines(text));
    }

    /** 工具调用面板：包含参数、返回值摘要、耗时。 */
    public static void toolCall(String toolName, String params, String resultSummary, long elapsedMs) {
        String head = String.format("📥 参数: %s", truncateOneLine(params, 200));
        String ret = String.format("📤 返回: %s", truncateForPanel(resultSummary));
        String time = String.format("⏱ 耗时: %dms", elapsedMs);
        panel("🔧", "工具调用: " + toolName, CYAN, List.of(head, ret, time));
    }

    /** AI 最终回复面板。 */
    public static void aiReply(String text) {
        panel("💬", "AI 回复", YELLOW, splitLines(text));
    }

    /** 错误面板。 */
    public static void error(String msg) {
        panel("❌", "错误", RED, splitLines(msg));
    }

    /** token 统计行。 */
    public static void stats(long inputTokens, long outputTokens) {
        System.out.println(GRAY + String.format(
                "📊 token: 输入=%d  输出=%d  累计=%d",
                inputTokens, outputTokens, inputTokens + outputTokens) + RESET);
    }

    // —— 核心面板绘制 ——

    /**
     * 绘制一个带标题的 box-drawing 面板。
     *
     * @param icon  标题前 emoji（如 🤖）
     * @param title 标题文本
     * @param color 标题颜色（ANSI 常量）
     * @param lines 面板内每行内容
     */
    public static void panel(String icon, String title, String color, List<String> lines) {
        String header = " " + icon + " " + title + " ";
        int dashCount = Math.max(4, PANEL_WIDTH - header.length());
        String top = "┌─" + header + " " + "─".repeat(dashCount - 1) + "┐";
        System.out.println(color + BOLD + top + RESET);
        if (lines != null) {
            for (String line : lines) {
                System.out.println(color + "│ " + RESET + line);
            }
        }
        String bottom = "└" + "─".repeat(PANEL_WIDTH) + "┘";
        System.out.println(color + bottom + RESET);
    }

    // —— 工具方法 ——

    /** 把任意字符串按 \n 切成 List<String>（空内容返回单元素）。 */
    private static List<String> splitLines(String text) {
        if (text == null || text.isEmpty()) {
            return List.of("");
        }
        return Arrays.asList(text.split("\n", -1));
    }

    /** 单行截断（去换行 + 超长尾部省略）。 */
    private static String truncateOneLine(String s, int max) {
        if (s == null) return "";
        String one = s.replace("\n", " ").trim();
        if (one.length() <= max) return one;
        return one.substring(0, max) + "...";
    }

    /**
     * 面板内容截断策略：> {@value #TRUNCATE_THRESHOLD} 字符时，
     * 显示前 {@value #TRUNCATE_HEAD} + 末尾 {@value #TRUNCATE_TAIL} + `[已截断至 N 字]`。
     * 否则原样返回。
     */
    public static String truncateForPanel(String s) {
        if (s == null) return "";
        if (s.length() <= TRUNCATE_THRESHOLD) return s;
        return s.substring(0, TRUNCATE_HEAD)
                + "\n   ... [已截断] ...\n   "
                + s.substring(s.length() - TRUNCATE_TAIL)
                + " [已截断至 " + s.length() + " 字]";
    }
}
