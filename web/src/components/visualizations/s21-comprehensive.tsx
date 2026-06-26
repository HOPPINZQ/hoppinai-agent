"use client";

import { type ReactNode } from "react";
import { AnimatePresence, motion } from "framer-motion";
import {
  Archive,
  Blocks,
  Bot,
  CheckCircle2,
  Clock3,
  FileText,
  GitBranch,
  Inbox,
  Network,
  ShieldCheck,
  Sparkles,
  Wrench,
} from "lucide-react";
import { StepControls } from "@/components/visualizations/shared/step-controls";
import { useSteppedVisualization } from "@/hooks/useSteppedVisualization";
import { cn } from "@/lib/utils";

type StageId =
  | "intake"
  | "guardrails"
  | "route"
  | "execute"
  | "external"
  | "recover"
  | "append";

const STAGES: {
  id: StageId;
  label: string;
  detail: string;
  icon: ReactNode;
}[] = [
  {
    id: "intake",
    label: "入口",
    detail: "请求、记忆、后台备注",
    icon: <Inbox size={15} />,
  },
  {
    id: "guardrails",
    label: "护栏",
    detail: "权限、钩子、策略",
    icon: <ShieldCheck size={15} />,
  },
  {
    id: "route",
    label: "路由",
    detail: "挑合适的工作面",
    icon: <GitBranch size={15} />,
  },
  {
    id: "execute",
    label: "执行",
    detail: "本地工具、团队、worktree",
    icon: <Wrench size={15} />,
  },
  {
    id: "external",
    label: "外部",
    detail: "MCP 工具箱回传结果",
    icon: <Blocks size={15} />,
  },
  {
    id: "recover",
    label: "恢复",
    detail: "重试、压缩、修复状态",
    icon: <Sparkles size={15} />,
  },
  {
    id: "append",
    label: "落账",
    detail: "一份记录就是唯一真相",
    icon: <FileText size={15} />,
  },
];

const SURFACES = [
  { label: "后台", icon: <Clock3 size={14} />, text: "慢命令稍后再完成" },
  { label: "团队", icon: <Network size={14} />, text: "队友通过邮箱协同" },
  { label: "worktree", icon: <GitBranch size={14} />, text: "高风险改动被隔离" },
  { label: "MCP", icon: <Blocks size={14} />, text: "外部工具被归一化" },
];

const STEPS: {
  title: string;
  desc: string;
  stage: StageId;
  used: StageId[];
  packet: {
    request: string;
    carried: string[];
    decision: string;
    result: string;
  };
  transcript: string[];
}[] = [
  {
    title: "一轮从数据包开始",
    desc: "综合智能体先把模型该看到的一切汇拢，而不是把上下文散落在隐藏角落。",
    stage: "intake",
    used: ["intake"],
    packet: {
      request: "修好课程页的可视化并验证页面。",
      carried: ["最近的对话", "相关记忆", "后台备注"],
      decision: "组装一份模型可见的输入包",
      result: "准备好一次模型调用",
    },
    transcript: ["用户请求进入", "记忆与备注已附加"],
  },
  {
    title: "护栏检查这个包",
    desc: "权限和钩子不是另一个支线任务；它们是工作发生前的检查站。",
    stage: "guardrails",
    used: ["intake", "guardrails"],
    packet: {
      request: "改文件、跑构建、开浏览器。",
      carried: ["权限模式", "钩子输出", "工作区规则"],
      decision: "允许的活继续；高风险的活先问",
      result: "得到安全的动作外廓",
    },
    transcript: ["策略已核对", "允许的动作可见"],
  },
  {
    title: "智能体挑选工作面",
    desc: "模型不需要一次启动所有机制。它挑能匹配任务的最小工作面。",
    stage: "route",
    used: ["route", "execute", "external"],
    packet: {
      request: "搜代码、改 UI、检查渲染后的页面。",
      carried: ["可用工具", "团队状态", "MCP 注册表"],
      decision: "先做本地改动，缺什么才用外部工具",
      result: "工作被切成清晰的车道",
    },
    transcript: ["路由：代码搜索", "路由：浏览器检查", "路由：不需要队友"],
  },
  {
    title: "工作在受界的地方跑",
    desc: "工具、队友和 worktree 都吐出小张结果卡，并行工作不会变成一份读不完的聊天日志。",
    stage: "execute",
    used: ["execute", "route"],
    packet: {
      request: "应用补丁并跑构建。",
      carried: ["工具调用", "worktree 车道", "期望输出"],
      decision: "执行后返回摘要结果",
      result: "本地证据已收集",
    },
    transcript: ["补丁已应用", "构建输出已摘要"],
  },
  {
    title: "外部结果从同一车道回灌",
    desc: "MCP 工具扩展能力，但它们最终还是作为普通工具结果回到智能体能推理的地方。",
    stage: "external",
    used: ["external", "execute"],
    packet: {
      request: "本地缺上下文时，用一个外部来源或工具。",
      carried: ["MCP 工具名", "结构化参数", "回传产物"],
      decision: "在下一步模型调用前归一化外部输出",
      result: "外部工作不再特殊",
    },
    transcript: ["已收到 MCP 结果", "结果卡已追加"],
  },
  {
    title: "恢复让这一轮始终可读",
    desc: "长上下文、命令报错、重试都以命名的恢复动作处理，而不是变成神秘的分支。",
    stage: "recover",
    used: ["recover", "intake"],
    packet: {
      request: "上下文或执行变糟时，先修复再继续。",
      carried: ["错误文本", "重试计数", "压缩摘要"],
      decision: "重试一次、压缩旧细节、保留可见原因",
      result: "这一轮仍然清晰",
    },
    transcript: ["错误已分类", "恢复备注已追加", "工作继续"],
  },
  {
    title: "一切回写到同一份记录",
    desc: "最大的教训乏味得令人愉快：所有机制最终都把证据追加到同一份真相源。",
    stage: "append",
    used: ["append", "intake"],
    packet: {
      request: "报告改了什么、验证了什么。",
      carried: ["工具证据", "浏览器检查", "剩余风险"],
      decision: "从记录回答，而不是只凭记忆",
      result: "下一轮有干净的起点",
    },
    transcript: ["测试通过", "视觉检查已记录", "最终答案已起草"],
  },
];

function StageNode({
  stage,
  index,
  currentIndex,
}: {
  stage: (typeof STAGES)[number];
  index: number;
  currentIndex: number;
}) {
  const active = index === currentIndex;
  const done = index < currentIndex;

  return (
    <motion.div
      layout
      animate={active ? { y: [0, -2, 0] } : { y: 0 }}
      transition={{ duration: 0.8, repeat: active ? Infinity : 0 }}
      className={cn(
        "min-w-0 rounded-lg border p-3 transition-colors",
        active
          ? "border-blue-300 bg-blue-50 text-blue-800 dark:border-blue-800 dark:bg-blue-950/35 dark:text-blue-200"
          : done
            ? "border-emerald-200 bg-emerald-50 text-emerald-800 dark:border-emerald-900 dark:bg-emerald-950/30 dark:text-emerald-200"
            : "border-zinc-200 bg-white text-zinc-600 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-300"
      )}
    >
      <div className="flex min-w-0 items-center gap-2">
        <span
          className={cn(
            "flex h-7 w-7 shrink-0 items-center justify-center rounded-md",
            active
              ? "bg-blue-500 text-white"
              : done
                ? "bg-emerald-500 text-white"
                : "bg-zinc-100 text-zinc-500 dark:bg-zinc-800 dark:text-zinc-300"
          )}
        >
          {done ? <CheckCircle2 size={15} /> : stage.icon}
        </span>
        <div className="min-w-0">
          <div className="break-words text-sm font-semibold">
            {index + 1}. {stage.label}
          </div>
          <div className="break-words text-[11px] leading-snug opacity-80">{stage.detail}</div>
        </div>
      </div>
    </motion.div>
  );
}

function PacketLine({
  label,
  value,
  tone = "zinc",
}: {
  label: string;
  value: string;
  tone?: "zinc" | "blue" | "emerald";
}) {
  const toneClass = {
    zinc: "border-zinc-200 bg-white text-zinc-700 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-200",
    blue: "border-blue-200 bg-blue-50 text-blue-800 dark:border-blue-900 dark:bg-blue-950/35 dark:text-blue-200",
    emerald:
      "border-emerald-200 bg-emerald-50 text-emerald-800 dark:border-emerald-900 dark:bg-emerald-950/35 dark:text-emerald-200",
  }[tone];

  return (
    <motion.div
      layout
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -8 }}
      transition={{ duration: 0.22 }}
      className={cn("min-w-0 rounded-md border px-3 py-2 shadow-sm", toneClass)}
    >
      <div className="font-mono text-[10px] uppercase tracking-normal opacity-70">{label}</div>
      <div className="mt-1 break-words text-sm font-medium leading-snug">{value}</div>
    </motion.div>
  );
}

export default function ComprehensiveVisualization({ title }: { title?: string }) {
  const vis = useSteppedVisualization({ totalSteps: STEPS.length, autoPlayInterval: 2800 });
  const step = STEPS[vis.currentStep];
  const currentStageIndex = STAGES.findIndex((stage) => stage.id === step.stage);

  return (
    <section className="min-h-[500px] space-y-4">
      <h2 className="text-xl font-semibold text-zinc-900 dark:text-zinc-100">
        {title || "综合智能体的一轮"}
      </h2>

      <div className="rounded-lg border border-zinc-200 bg-white p-4 dark:border-zinc-700 dark:bg-zinc-900">
        <div className="grid gap-3 lg:grid-cols-[0.9fr_1.2fr]">
          <div className="rounded-lg border border-zinc-200 bg-zinc-50 p-3 dark:border-zinc-700 dark:bg-zinc-800/70">
            <div className="mb-3 flex items-center gap-2 text-sm font-semibold text-zinc-800 dark:text-zinc-100">
              <Bot size={16} />
              一轮的旅程
            </div>
            <div className="space-y-2">
              {STAGES.map((stage, index) => (
                <StageNode
                  key={stage.id}
                  stage={stage}
                  index={index}
                  currentIndex={currentStageIndex}
                />
              ))}
            </div>
          </div>

          <div className="space-y-3">
            <div className="rounded-lg border border-zinc-200 bg-zinc-50 p-3 dark:border-zinc-700 dark:bg-zinc-800/70">
              <div className="mb-3 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                <div className="flex items-center gap-2 text-sm font-semibold text-zinc-800 dark:text-zinc-100">
                  <Archive size={16} />
                  本轮数据包
                </div>
                <span className="w-fit rounded-md bg-white px-2 py-1 font-mono text-[11px] text-zinc-500 dark:bg-zinc-900 dark:text-zinc-300">
                  第 {vis.currentStep + 1}/{STEPS.length} 步
                </span>
              </div>

              <AnimatePresence mode="wait">
                <motion.div
                  key={step.title}
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -8 }}
                  transition={{ duration: 0.25 }}
                  className="space-y-3"
                >
                  <PacketLine label="request" value={step.packet.request} tone="blue" />

                  <div className="grid gap-2 sm:grid-cols-2">
                    <div className="rounded-md border border-zinc-200 bg-white px-3 py-2 dark:border-zinc-700 dark:bg-zinc-900">
                      <div className="font-mono text-[10px] uppercase tracking-normal text-zinc-500 dark:text-zinc-400">
                        carried context
                      </div>
                      <div className="mt-2 flex flex-wrap gap-1.5">
                        {step.packet.carried.map((item) => (
                          <span
                            key={item}
                            className="max-w-full break-words rounded bg-zinc-100 px-2 py-1 text-[11px] text-zinc-700 dark:bg-zinc-800 dark:text-zinc-200"
                          >
                            {item}
                          </span>
                        ))}
                      </div>
                    </div>
                    <PacketLine label="decision" value={step.packet.decision} />
                  </div>

                  <PacketLine label="result" value={step.packet.result} tone="emerald" />
                </motion.div>
              </AnimatePresence>
            </div>

            <div className="rounded-lg border border-zinc-200 bg-white p-3 dark:border-zinc-700 dark:bg-zinc-900">
              <div className="mb-2 flex items-center gap-2 text-sm font-semibold text-zinc-800 dark:text-zinc-100">
                <FileText size={15} />
                真相源记录
              </div>
              <div className="space-y-2">
                <AnimatePresence mode="popLayout">
                  {step.transcript.map((item) => (
                    <motion.div
                      key={item}
                      layout
                      initial={{ opacity: 0, x: 12 }}
                      animate={{ opacity: 1, x: 0 }}
                      exit={{ opacity: 0, x: -8 }}
                      transition={{ duration: 0.22 }}
                      className="break-words rounded-md border border-zinc-200 bg-zinc-50 px-3 py-2 text-xs text-zinc-700 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-200"
                    >
                      {item}
                    </motion.div>
                  ))}
                </AnimatePresence>
              </div>
            </div>
          </div>
        </div>

        <div className="mt-3 grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
          {SURFACES.map((surface) => (
            <div
              key={surface.label}
              className="min-w-0 rounded-lg border border-zinc-200 bg-zinc-50 p-3 dark:border-zinc-700 dark:bg-zinc-800/70"
            >
              <div className="flex min-w-0 items-center gap-2 text-sm font-semibold text-zinc-800 dark:text-zinc-100">
                <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-zinc-100 text-zinc-500 dark:bg-zinc-900 dark:text-zinc-300">
                  {surface.icon}
                </span>
                <span className="break-words">{surface.label}</span>
              </div>
              <div className="mt-2 break-words text-[11px] leading-snug text-zinc-500 dark:text-zinc-400">
                {surface.text}
              </div>
            </div>
          ))}
        </div>

        <StepControls
          className="mt-4"
          currentStep={vis.currentStep}
          totalSteps={vis.totalSteps}
          onPrev={vis.prev}
          onNext={vis.next}
          onReset={vis.reset}
          isPlaying={vis.isPlaying}
          onToggleAutoPlay={vis.toggleAutoPlay}
          stepTitle={step.title}
          stepDescription={step.desc}
        />
      </div>
    </section>
  );
}
