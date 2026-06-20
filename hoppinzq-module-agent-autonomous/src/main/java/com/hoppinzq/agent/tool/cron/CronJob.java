package com.hoppinzq.agent.tool.cron;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单个 cron 任务定义。
 * <p>
 * 与 Python 教程 s14 的 CronJob 对应：5 字段 cron 表达式 + prompt + recurring/durable 控制位 +
 * lastFire/nextFire 防同分钟重复触发。
 *
 * @author hoppinzq
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CronJob {
    /** 任务 id（UUID 短串） */
    @JsonProperty("id")
    private String id;

    /** 5 字段 cron 表达式：min hour day-of-month month day-of-week */
    @JsonProperty("cron")
    private String cron;

    /** 命中时要注入 agent 的 prompt */
    @JsonProperty("prompt")
    private String prompt;

    /** true=周期触发，false=仅触发一次（one-shot） */
    @JsonProperty("recurring")
    @Builder.Default
    private boolean recurring = true;

    /** true=持久化到 .scheduled_tasks.json，进程重启后恢复 */
    @JsonProperty("durable")
    @Builder.Default
    private boolean durable = false;

    /** 最近一次触发时间（毫秒），用于防止同一分钟重复触发 */
    @JsonProperty("lastFire")
    private Long lastFire;

    /** 下一次预期触发时间（毫秒），仅用于展示 */
    @JsonProperty("nextFire")
    private Long nextFire;
}
