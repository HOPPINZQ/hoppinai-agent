package com.hoppinzq.agent.tool.goal;

import com.hoppinzq.agent.client.LlmMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * GoalController 状态机 + 判断器输出解析的冒烟测试（不依赖 junit，直接跑 main）。
 *
 * <pre>
 *   mvn -pl hoppinzq-module-agent-25-goaloop test-compile
 *   mvn -pl hoppinzq-module-agent-25-goaloop dependency:build-classpath -Dmdep.outputFile=target/cp.txt
 *   java -cp "target/test-classes;target/classes;$(cat target/cp.txt)"  *        com.hoppinzq.agent.tool.goal.GoalSmoke
 * </pre>
 */
public class GoalSmoke {

    static int failures = 0;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + what);
        if (!ok) failures++;
    }

    public static void main(String[] args) throws Exception {
        List<LlmMessage> msgs = new ArrayList<>();
        msgs.add(LlmMessage.user("跑测试"));

        // ---- 1. 无目标时放行 ----
        GoalController g = new GoalController((c, m) -> new GoalEvaluation(true, "x", false), 3);
        check(GoalDecision.ALLOW.equals(g.evaluateAfterTurn(msgs, false).action()), "无活跃目标 → allow");

        // ---- 2. 设置目标 + block → limit → achieved ----
        AtomicInteger calls = new AtomicInteger();
        GoalEvaluator sequence = (condition, m) -> {
            int n = calls.incrementAndGet();
            if (n <= 4) return new GoalEvaluation(false, "还缺证据 " + n, false);
            return new GoalEvaluation(true, "退出码为 0 已出现", false);
        };
        g = new GoalController(sequence, 3);
        check(g.setGoal("exit 0", 100).getCondition().equals("exit 0"), "setGoal 保存条件");
        check(g.hasActiveGoal(), "setGoal 后有活跃目标");
        check("block".equals(g.evaluateAfterTurn(msgs, false).action()), "第 1 轮未满足 → block");
        check("block".equals(g.evaluateAfterTurn(msgs, false).action()), "第 2 轮未满足 → block");
        check("block".equals(g.evaluateAfterTurn(msgs, false).action()), "第 3 轮未满足 → block");
        GoalDecision over = g.evaluateAfterTurn(msgs, false);
        check("limit".equals(over.action()), "连续 block 超上限 → limit（实际 " + over.action() + "）");
        check(g.hasActiveGoal(), "limit 后目标仍保持活跃");
        check("achieved".equals(g.evaluateAfterTurn(msgs, false).action()), "条件满足 → achieved");
        check(!g.hasActiveGoal(), "achieved 后目标清空");

        // ---- 3. 替换目标 + 清除 ----
        g.setGoal("a", 0);
        g.setGoal("b", 0);
        check("b".equals(g.activeCondition()), "新目标替换旧目标");
        check(g.clear("cleared").contains("b"), "clear 返回旧条件");
        check("No goal set".equals(g.clear("cleared")), "重复 clear 返回 No goal set");

        // ---- 4. impossible → failed ----
        GoalController g4 = new GoalController((c, m) -> new GoalEvaluation(false, "依赖的服务不存在", true), 3);
        g4.setGoal("部署上线", 0);
        GoalDecision d4 = g4.evaluateAfterTurn(msgs, false);
        check("failed".equals(d4.action()), "impossible → failed（实际 " + d4.action() + "）");
        check(!g4.hasActiveGoal(), "failed 后目标清空");

        // ---- 5. 判断器抛异常 → error，但目标保留 ----
        GoalController g5 = new GoalController((c, m) -> {
            throw new RuntimeException("boom");
        }, 3);
        g5.setGoal("x", 0);
        GoalDecision d5 = g5.evaluateAfterTurn(msgs, false);
        check("error".equals(d5.action()), "判断器异常 → error（实际 " + d5.action() + "）");
        check(g5.hasActiveGoal(), "error 后目标仍保持活跃");

        // ---- 6. 后台运行中 → defer，不调用判断器 ----
        AtomicInteger deferCalls = new AtomicInteger();
        GoalController g6 = new GoalController((c, m) -> {
            deferCalls.incrementAndGet();
            return new GoalEvaluation(true, "x", false);
        }, 3);
        g6.setGoal("x", 0);
        check("defer".equals(g6.evaluateAfterTurn(msgs, true).action()), "后台运行中 → defer");
        check(deferCalls.get() == 0, "defer 时不调用判断器");

        // ---- 7. 空条件 / 超长条件被拒 ----
        boolean emptyRejected = false;
        try {
            g.setGoal("   ", 0);
        } catch (RuntimeException e) {
            emptyRejected = true;
        }
        check(emptyRejected, "空条件被拒绝");
        boolean tooLongRejected = false;
        try {
            g.setGoal("x".repeat(5000), 0);
        } catch (RuntimeException e) {
            tooLongRejected = true;
        }
        check(tooLongRejected, "超长条件被拒绝");

        // ---- 8. status 报告 ----
        g.setGoal("目标A", 1000);
        String status = g.status(1500);
        check(status.contains("目标A") && status.contains("Tokens: 500"), "status 报告条件与 token 消耗");

        // ---- 9. stop hook 上限必须 >= 1 ----
        boolean capRejected = false;
        try {
            new GoalController((c, m) -> null, 0);
        } catch (RuntimeException e) {
            capRejected = true;
        }
        check(capRejected, "blockCap < 1 被拒绝");

        // ---- 10. restore：活跃目标恢复，已完成不恢复 ----
        List<GoalController.GoalStatusEvent> events = g.getEvents();
        GoalController restored = GoalController.restore((c, m) -> null, events, 3);
        check(restored.hasActiveGoal() && "目标A".equals(restored.activeCondition()), "restore 恢复活跃目标");
        GoalController done = new GoalController((c, m) -> new GoalEvaluation(true, "ok", false), 3);
        done.setGoal("done", 0);
        done.evaluateAfterTurn(msgs, false);
        GoalController restoredDone = GoalController.restore((c, m) -> null, done.getEvents(), 3);
        check(!restoredDone.hasActiveGoal(), "restore 不重启已完成的目标");

        // ---- 11. 判断器输出解析 ----
        check(PromptGoalEvaluator.parseJsonObject("{\"ok\":true,\"reason\":\"r\",\"impossible\":false}").ok(),
                "解析合法 JSON");
        check(PromptGoalEvaluator.parseJsonObject("```json\n{\"ok\":false,\"reason\":\"r\"}\n```").impossible() == false,
                "解析带代码围栏的 JSON");
        check(rejected("{\"ok\":\"yes\",\"reason\":\"r\"}"), "ok 非布尔被拒");
        check(rejected("{\"ok\":true,\"reason\":\"  \"}"), "reason 为空被拒");
        check(rejected("{\"ok\":true,\"reason\":\"r\",\"impossible\":true}"), "ok+impossible 同时为真被拒");
        check(rejected("not json"), "非 JSON 被拒");

        // ---- 12. transcriptText：过长的最新消息只留开头结尾 ----
        List<LlmMessage> big = new ArrayList<>();
        big.add(LlmMessage.user("早期消息"));
        big.add(LlmMessage.tool("c1", "x".repeat(50000)));
        String t = PromptGoalEvaluator.transcriptText(big, 1000);
        check(t.length() <= 1000, "transcriptText 不超过上限（实际 " + t.length() + "）");
        check(t.contains("middle omitted"), "过长消息带省略标记");
        check(t.contains("TOOL:"), "tool 消息渲染为 TOOL: 前缀");

        // ---- 13. continueMessage 回灌文本 ----
        GoalController g13 = new GoalController((c, m) -> new GoalEvaluation(false, "还缺 pytest 退出码", false), 3);
        g13.setGoal("exit 0", 0);
        GoalDecision d13 = g13.evaluateAfterTurn(msgs, false);
        String cm = g13.continueMessage(d13);
        check(cm.contains("Goal still active") && cm.contains("exit 0") && cm.contains("还缺 pytest 退出码"),
                "continueMessage 含条件与判断理由");

        System.out.println();
        System.out.println(failures == 0 ? "全部通过" : (failures + " 项失败"));
        if (failures > 0) System.exit(1);
    }

    static boolean rejected(String json) {
        try {
            PromptGoalEvaluator.parseJsonObject(json);
            return false;
        } catch (RuntimeException e) {
            return true;
        }
    }
}