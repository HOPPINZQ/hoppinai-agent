package com.hoppinzq.agent.tool.cron;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * 5 字段 cron 表达式解析与匹配。
 * <p>
 * 字段顺序：minute hour day-of-month month day-of-week。
 * <ul>
 *   <li>支持 {@code *}、逗号列表、{@code -} 范围、{@code /} 步长，以及数字字面量</li>
 *   <li>DOM 与 DOW 任一命中即触发（Python s14 教学版同款语义：{@code domOk || dowOk}）</li>
 *   <li>不支持的语法（L、W、#、? 等高级特性）会被解析为 IllegalArgumentException，便于教学版尽早暴露</li>
 * </ul>
 *
 * @author hoppinzq
 */
public final class CronExpression {

    private final BitSet minutes = new BitSet(60);
    private final BitSet hours = new BitSet(24);
    private final BitSet daysOfMonth = new BitSet(32);
    private final BitSet months = new BitSet(13);
    private final BitSet daysOfWeek = new BitSet(7);

    private final boolean domRestricted;
    private final boolean dowRestricted;
    private final String raw;

    public CronExpression(String expr) {
        this.raw = expr;
        if (expr == null || expr.isBlank()) {
            throw new IllegalArgumentException("cron 表达式为空");
        }
        String[] fields = expr.trim().split("\\s+");
        if (fields.length != 5) {
            throw new IllegalArgumentException("cron 表达式必须是 5 字段：" + expr);
        }
        parseField(fields[0], 0, 59, minutes, "minute");
        parseField(fields[1], 0, 23, hours, "hour");
        parseField(fields[2], 1, 31, daysOfMonth, "day-of-month");
        parseField(fields[3], 1, 12, months, "month");
        parseField(fields[4], 0, 6, daysOfWeek, "day-of-week");

        this.domRestricted = !fields[2].equals("*");
        this.dowRestricted = !fields[4].equals("*");
    }

    private static void parseField(String field, int min, int max, BitSet out, String name) {
        if (field.equals("*")) {
            out.set(min, max + 1);
            return;
        }
        for (String token : field.split(",")) {
            parseToken(token, min, max, out, name);
        }
    }

    private static void parseToken(String token, int min, int max, BitSet out, String name) {
        int step = 1;
        String rangePart = token;
        int slash = token.indexOf('/');
        if (slash >= 0) {
            rangePart = token.substring(0, slash);
            try {
                step = Integer.parseInt(token.substring(slash + 1));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(name + " 步长必须是数字：" + token);
            }
            if (step <= 0) {
                throw new IllegalArgumentException(name + " 步长必须 > 0：" + token);
            }
        }

        int start;
        int end;
        if (rangePart.equals("*")) {
            start = min;
            end = max;
        } else if (rangePart.contains("-")) {
            String[] parts = rangePart.split("-", 2);
            start = parseIntOrName(parts[0], min, max, name);
            end = parseIntOrName(parts[1], min, max, name);
        } else {
            start = parseIntOrName(rangePart, min, max, name);
            end = slash >= 0 ? max : start;
        }
        if (start < min || end > max || start > end) {
            throw new IllegalArgumentException(name + " 范围非法：" + token + "（合法 " + min + "-" + max + "）");
        }
        for (int v = start; v <= end; v += step) {
            out.set(v);
        }
    }

    private static int parseIntOrName(String s, int min, int max, String name) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " 字段必须是数字：" + s);
        }
    }

    /** 给定时间是否命中。 */
    public boolean matches(long epochMillis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(epochMillis);
        if (!minutes.get(c.get(Calendar.MINUTE))) return false;
        if (!hours.get(c.get(Calendar.HOUR_OF_DAY))) return false;
        int month = c.get(Calendar.MONTH) + 1;
        if (!months.get(month)) return false;

        boolean domOk = daysOfMonth.get(c.get(Calendar.DAY_OF_MONTH));
        int javaDow = c.get(Calendar.DAY_OF_WEEK); // Sunday=1
        int cronDow = (javaDow - 1) % 7; // 0=Sunday，与 cron 约定一致
        boolean dowOk = daysOfWeek.get(cronDow);

        // 与 Python s14 一致：若两边都受限则任一命中；只有一边受限则只看那边；两边都是 * 则必中
        if (domRestricted && dowRestricted) {
            return domOk || dowOk;
        } else if (domRestricted) {
            return domOk;
        } else if (dowRestricted) {
            return dowOk;
        }
        return true;
    }

    /** 计算从 from 之后下一次触发的时间戳；用于 nextFire 展示，扫描线程不依赖此值。 */
    public long nextFireAfter(long fromMillis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(fromMillis);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.add(Calendar.MINUTE, 1);
        for (int i = 0; i < 60 * 24 * 366; i++) { // 最远扫一年
            if (matches(c.getTimeInMillis())) {
                return c.getTimeInMillis();
            }
            c.add(Calendar.MINUTE, 1);
        }
        return -1;
    }

    public List<String> describe() {
        List<String> d = new ArrayList<>();
        d.add("raw=" + raw);
        d.add("minutes=" + bits(minutes, 0, 59));
        d.add("hours=" + bits(hours, 0, 23));
        d.add("dom=" + bits(daysOfMonth, 1, 31));
        d.add("months=" + bits(months, 1, 12));
        d.add("dow=" + bits(daysOfWeek, 0, 6));
        return d;
    }

    private static String bits(BitSet s, int min, int max) {
        StringBuilder sb = new StringBuilder();
        for (int i = min; i <= max; i++) {
            if (s.get(i)) {
                if (sb.length() > 0) sb.append(',');
                sb.append(i);
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return raw;
    }

    public String getRaw() { return raw; }

    public static void main(String[] args) {
        CronExpression e = new CronExpression("*/2 * * * *");
        System.out.println(e.describe());
        System.out.println(new Date(e.nextFireAfter(System.currentTimeMillis())));
    }
}
