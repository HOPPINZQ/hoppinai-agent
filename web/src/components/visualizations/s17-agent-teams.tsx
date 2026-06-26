"use client";

import { AnimatePresence, motion } from "framer-motion";
import { Inbox, MessageSquareText, UsersRound } from "lucide-react";
import { StepControls } from "@/components/visualizations/shared/step-controls";
import { useSteppedVisualization } from "@/hooks/useSteppedVisualization";
import { cn } from "@/lib/utils";

type AgentId = "lead" | "coder" | "reviewer";

interface Mail {
  id: string;
  from: AgentId;
  to: AgentId;
  subject: string;
  body: string;
  appearsAt: number;
  consumedAt?: number;
}

const AGENTS: { id: AgentId; label: string; role: string }[] = [
  { id: "lead", label: "主管", role: "拆分工作并阅读结果" },
  { id: "coder", label: "编码员", role: "实现一个切片" },
  { id: "reviewer", label: "审查员", role: "检查结果" },
];

const MAIL: Mail[] = [
  {
    id: "assign",
    from: "lead",
    to: "coder",
    subject: "实现登录界面",
    body: "请实现登录表单并回报。",
    appearsAt: 1,
    consumedAt: 2,
  },
  {
    id: "result",
    from: "coder",
    to: "reviewer",
    subject: "登录界面已完成",
    body: "文件已修改，等待审查。",
    appearsAt: 4,
    consumedAt: 5,
  },
  {
    id: "feedback",
    from: "reviewer",
    to: "lead",
    subject: "审查通过",
    body: "没有阻塞。只有一条小的打磨建议。",
    appearsAt: 5,
  },
];

const STEPS = [
  {
    title: "一个团队就是一组邮箱",
    desc: "每个智能体都有自己的收件箱文件。团队协调无需共享内存。",
  },
  {
    title: "主管投递一张卡",
    desc: "派发工作意味着往编码员的收件箱追加一条消息。",
  },
  {
    title: "编码员先读后想",
    desc: "在下一次模型调用前，编码员会清空收件箱并把消息转为上下文。",
  },
  {
    title: "编码员独立工作",
    desc: "编码员现在跑自己的循环。主管不必一直持有完整上下文。",
  },
  {
    title: "结果变成邮件",
    desc: "编码员通过同样的邮箱机制把结果卡发给审查员。",
  },
  {
    title: "审查员反馈",
    desc: "审查反馈也只是又一张卡。主管从自己的邮箱读到它。",
  },
  {
    title: "文件就是协调层",
    desc: "整个团队作为只追加的邮箱文件可见可查：lead.jsonl、coder.jsonl、reviewer.jsonl。",
  },
] as const;

function visibleMail(agent: AgentId, step: number) {
  return MAIL.filter((mail) => mail.to === agent && mail.appearsAt <= step && (mail.consumedAt === undefined || step < mail.consumedAt));
}

function agentState(agent: AgentId, step: number): "waiting" | "reading" | "working" | "reviewing" | "done" {
  if (agent === "lead" && step === 1) return "working";
  if (agent === "coder" && step === 2) return "reading";
  if (agent === "coder" && (step === 3 || step === 4)) return "working";
  if (agent === "reviewer" && step === 5) return "reviewing";
  if (agent === "lead" && step >= 5) return "reading";
  if (step === 6) return "done";
  return "waiting";
}

const STATE_LABEL: Record<ReturnType<typeof agentState>, string> = {
  waiting: "等待中",
  reading: "读取中",
  working: "工作中",
  reviewing: "审查中",
  done: "完成",
};

function stateClass(state: ReturnType<typeof agentState>) {
  if (state === "working") return "border-blue-300 bg-blue-50 dark:border-blue-800 dark:bg-blue-950/30";
  if (state === "reading" || state === "reviewing") return "border-amber-300 bg-amber-50 dark:border-amber-800 dark:bg-amber-950/30";
  if (state === "done") return "border-emerald-300 bg-emerald-50 dark:border-emerald-800 dark:bg-emerald-950/30";
  return "border-zinc-200 bg-white dark:border-zinc-700 dark:bg-zinc-900";
}

function MailCard({ mail }: { mail: Mail }) {
  return (
    <motion.div
      layout
      initial={{ opacity: 0, y: 8, scale: 0.98 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      exit={{ opacity: 0, y: -6, scale: 0.98 }}
      transition={{ duration: 0.22 }}
      className="rounded-md border border-amber-200 bg-amber-50 p-3 text-amber-900 shadow-sm dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-100"
    >
      <div className="mb-1 flex items-center justify-between gap-2">
        <span className="font-mono text-[11px] font-semibold">{mail.from} -&gt; {mail.to}</span>
        <MessageSquareText size={14} />
      </div>
      <div className="text-sm font-semibold leading-snug">{mail.subject}</div>
      <div className="mt-1 text-xs leading-relaxed opacity-85">{mail.body}</div>
    </motion.div>
  );
}

function AgentPanel({ agent, step }: { agent: (typeof AGENTS)[number]; step: number }) {
  const state = agentState(agent.id, step);
  const inbox = visibleMail(agent.id, step);

  return (
    <div className={cn("rounded-lg border p-3 transition-colors", stateClass(state))}>
      <div className="mb-3 flex items-start justify-between gap-2">
        <div>
          <div className="text-base font-bold text-zinc-900 dark:text-zinc-100">{agent.label}</div>
          <div className="text-xs leading-relaxed text-zinc-500 dark:text-zinc-400">{agent.role}</div>
        </div>
        <span className="rounded-md bg-white px-2 py-1 text-[11px] font-semibold text-zinc-600 shadow-sm dark:bg-zinc-900 dark:text-zinc-300">
          {STATE_LABEL[state]}
        </span>
      </div>

      <div className="rounded-md border border-zinc-200 bg-white p-3 dark:border-zinc-700 dark:bg-zinc-900">
        <div className="mb-2 flex items-center gap-2 text-sm font-semibold text-zinc-800 dark:text-zinc-100">
          <Inbox size={15} />
          {agent.id}.jsonl
        </div>
        <div className="min-h-[118px] space-y-2">
          <AnimatePresence mode="popLayout">
            {inbox.length > 0 ? (
              inbox.map((mail) => <MailCard key={mail.id} mail={mail} />)
            ) : (
              <motion.div
                key="empty"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                className="rounded-md border border-dashed border-zinc-300 px-3 py-8 text-center text-xs text-zinc-500 dark:border-zinc-700 dark:text-zinc-400"
              >
                收件箱为空
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </div>
    </div>
  );
}

function ActivityLog({ step }: { step: number }) {
  const items = [
    "团队配置创建了 lead、coder、reviewer",
    "主管向 coder.jsonl 追加任务卡",
    "编码员在模型调用前清空收件箱",
    "编码员跑自己的循环",
    "编码员向 reviewer.jsonl 追加结果",
    "审查员向 lead.jsonl 追加反馈",
    "所有协调在磁盘上始终可见",
  ].slice(0, step + 1);

  return (
    <div className="rounded-lg border border-zinc-200 bg-zinc-50 p-3 dark:border-zinc-700 dark:bg-zinc-800/70">
      <div className="mb-2 flex items-center gap-2 text-sm font-semibold text-zinc-800 dark:text-zinc-100">
        <UsersRound size={16} />
        发生了什么
      </div>
      <div className="space-y-2">
        {items.map((item) => (
          <motion.div
            key={item}
            initial={{ opacity: 0, x: 8 }}
            animate={{ opacity: 1, x: 0 }}
            className="rounded-md border border-zinc-200 bg-white px-3 py-2 text-xs text-zinc-700 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-200"
          >
            {item}
          </motion.div>
        ))}
      </div>
    </div>
  );
}

export default function AgentTeams({ title }: { title?: string }) {
  const vis = useSteppedVisualization({ totalSteps: STEPS.length, autoPlayInterval: 2500 });
  const step = vis.currentStep;
  const current = STEPS[step];

  return (
    <section className="min-h-[500px] space-y-4">
      <h2 className="text-xl font-semibold text-zinc-900 dark:text-zinc-100">
        {title || "智能体团队邮箱"}
      </h2>

      <div className="rounded-lg border border-zinc-200 bg-white p-4 dark:border-zinc-700 dark:bg-zinc-900">
        <div className="grid gap-3 xl:grid-cols-[1fr_1fr_1fr_0.9fr]">
          {AGENTS.map((agent) => (
            <AgentPanel key={agent.id} agent={agent} step={step} />
          ))}
          <ActivityLog step={step} />
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
          stepTitle={current.title}
          stepDescription={current.desc}
        />
      </div>
    </section>
  );
}
