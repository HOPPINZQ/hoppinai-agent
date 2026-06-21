"use client";

import { useState, type ReactNode } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { ArrowRight, CheckCircle2, ClipboardCheck, FileText, LockKeyhole, UserCheck } from "lucide-react";
import { StepControls } from "@/components/visualizations/shared/step-controls";
import { useSteppedVisualization } from "@/hooks/useSteppedVisualization";
import { cn } from "@/lib/utils";

type Protocol = "shutdown" | "plan";

const REQUEST_ID = "req_abc";

const SHUTDOWN_STEPS = [
  {
    title: "约定一种小格式",
    desc: "协议就是一张共享卡片的形状：请求类型、request_id 和期望的回答。",
  },
  {
    title: "主管提交一个请求",
    desc: "主管写下关停请求卡，而不是强制停止队友。",
  },
  {
    title: "队友做出选择",
    desc: "队友可以批准或拒绝，request_id 让回答附在正确的请求上。",
  },
  {
    title: "干净退出",
    desc: "被批准的响应回到主管处，队友干净退出。",
  },
];

const PLAN_STEPS = [
  {
    title: "工作被锁定",
    desc: "在 plan 模式下，实现保持锁定，直到方案卡被批准。",
  },
  {
    title: "提交方案卡",
    desc: "队友发送一份带相同请求-响应形状的具体方案。",
  },
  {
    title: "批准解锁行动",
    desc: "主管批准这张卡，然后实现才能开始。",
  },
];

const PROTOCOL_STATES: Record<Protocol, { label: string; detail: string }[]> = {
  shutdown: [
    { label: "已起草", detail: "主管创建 request_id" },
    { label: "待处理", detail: "卡等待在收件箱" },
    { label: "决策中", detail: "队友回复" },
    { label: "已关闭", detail: "主管匹配响应" },
  ],
  plan: [
    { label: "已锁定", detail: "工作无法开始" },
    { label: "已提交", detail: "方案卡已发送" },
    { label: "已批准", detail: "实现解锁" },
  ],
};

function ToggleButton({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <button
      onClick={onClick}
      className={cn(
        "rounded-md px-3 py-1.5 text-xs font-medium transition-colors active:scale-95",
        active
          ? "bg-blue-500 text-white"
          : "bg-zinc-100 text-zinc-600 hover:bg-zinc-200 dark:bg-zinc-800 dark:text-zinc-300 dark:hover:bg-zinc-700"
      )}
    >
      {children}
    </button>
  );
}

function StateRail({
  states,
  currentStep,
}: {
  states: { label: string; detail: string }[];
  currentStep: number;
}) {
  return (
    <div className="mb-4 rounded-lg border border-zinc-200 bg-zinc-50 p-3 dark:border-zinc-700 dark:bg-zinc-800/70">
      <div className="mb-3 flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
        <div className="text-sm font-semibold text-zinc-800 dark:text-zinc-100">
          协议状态
        </div>
        <div className="break-words font-mono text-[11px] text-zinc-500 dark:text-zinc-400">
          request_id: {REQUEST_ID}
        </div>
      </div>
      <div className="flex flex-col gap-2 sm:flex-row sm:items-stretch">
        {states.map((state, index) => {
          const active = index === currentStep;
          const done = index < currentStep;
          return (
            <div key={state.label} className="flex min-w-0 flex-1 items-stretch gap-2">
              <motion.div
                layout
                animate={active ? { y: [0, -2, 0] } : { y: 0 }}
                transition={{ duration: 0.8, repeat: active ? Infinity : 0 }}
                className={cn(
                  "min-w-0 flex-1 rounded-md border px-3 py-2 transition-colors",
                  active
                    ? "border-blue-300 bg-blue-50 text-blue-800 dark:border-blue-800 dark:bg-blue-950/35 dark:text-blue-200"
                    : done
                      ? "border-emerald-200 bg-emerald-50 text-emerald-800 dark:border-emerald-900 dark:bg-emerald-950/30 dark:text-emerald-200"
                      : "border-zinc-200 bg-white text-zinc-500 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-400"
                )}
              >
                <div className="break-words text-sm font-semibold">{state.label}</div>
                <div className="mt-1 break-words text-[11px] leading-snug opacity-80">
                  {state.detail}
                </div>
              </motion.div>
              {index < states.length - 1 && (
                <div className="hidden items-center text-zinc-300 dark:text-zinc-600 sm:flex">
                  <ArrowRight size={15} />
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}

function Desk({
  title,
  icon,
  active,
  children,
}: {
  title: string;
  icon: ReactNode;
  active: boolean;
  children: ReactNode;
}) {
  return (
    <div
      className={cn(
        "min-h-[260px] rounded-lg border p-3 transition-colors",
        active
          ? "border-blue-300 bg-blue-50 dark:border-blue-800 dark:bg-blue-950/30"
          : "border-zinc-200 bg-white dark:border-zinc-700 dark:bg-zinc-900"
      )}
    >
      <div className="mb-3 flex min-w-0 items-center gap-2 text-sm font-semibold text-zinc-800 dark:text-zinc-100">
        <span
          className={cn(
            "flex h-7 w-7 items-center justify-center rounded-md",
            active
              ? "bg-blue-500 text-white"
              : "bg-zinc-100 text-zinc-500 dark:bg-zinc-800 dark:text-zinc-300"
          )}
        >
          {icon}
        </span>
        <span className="min-w-0 break-words">{title}</span>
      </div>
      {children}
    </div>
  );
}

function ProtocolCard({
  title,
  rows,
  tone = "blue",
}: {
  title: string;
  rows: string[];
  tone?: "blue" | "amber" | "emerald" | "zinc";
}) {
  const toneClass = {
    blue: "border-blue-200 bg-blue-50 text-blue-800 dark:border-blue-900 dark:bg-blue-950/40 dark:text-blue-200",
    amber: "border-amber-200 bg-amber-50 text-amber-800 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-200",
    emerald:
      "border-emerald-200 bg-emerald-50 text-emerald-800 dark:border-emerald-900 dark:bg-emerald-950/40 dark:text-emerald-200",
    zinc: "border-zinc-200 bg-zinc-50 text-zinc-700 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-200",
  }[tone];

  return (
    <motion.div
      layout
      initial={{ opacity: 0, y: 10, scale: 0.98 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      exit={{ opacity: 0, y: -8, scale: 0.98 }}
      transition={{ duration: 0.25 }}
      className={cn("rounded-md border p-3 shadow-sm", toneClass)}
    >
      <div className="break-words font-mono text-xs font-semibold">{title}</div>
      <div className="mt-2 space-y-1 font-mono text-[11px] opacity-85">
        {rows.map((row) => (
          <div key={row} className="break-words">
            {row}
          </div>
        ))}
      </div>
    </motion.div>
  );
}

function EmptyTray({ label }: { label: string }) {
  return (
    <div className="rounded-md border border-dashed border-zinc-300 px-3 py-5 text-center text-xs text-zinc-500 dark:border-zinc-700 dark:text-zinc-400">
      {label}
    </div>
  );
}

export default function TeamProtocols({ title }: { title?: string }) {
  const [protocol, setProtocol] = useState<Protocol>("shutdown");
  const steps = protocol === "shutdown" ? SHUTDOWN_STEPS : PLAN_STEPS;
  const vis = useSteppedVisualization({ totalSteps: steps.length, autoPlayInterval: 2500 });
  const step = vis.currentStep;

  const switchProtocol = (value: Protocol) => {
    setProtocol(value);
    vis.reset();
  };

  const isPlan = protocol === "plan";

  return (
    <section className="min-h-[500px] space-y-4">
      <h2 className="text-xl font-semibold text-zinc-900 dark:text-zinc-100">
        {title || "团队协议卡"}
      </h2>

      <div className="rounded-lg border border-zinc-200 bg-white p-4 dark:border-zinc-700 dark:bg-zinc-900">
        <div className="mb-4 flex justify-center gap-2">
          <ToggleButton active={!isPlan} onClick={() => switchProtocol("shutdown")}>
            关停协议
          </ToggleButton>
          <ToggleButton active={isPlan} onClick={() => switchProtocol("plan")}>
            方案审批
          </ToggleButton>
        </div>

        <StateRail states={PROTOCOL_STATES[protocol]} currentStep={step} />

        <div className="grid gap-3 lg:grid-cols-3">
          <Desk
            title="主管工作台"
            icon={<UserCheck size={15} />}
            active={(!isPlan && (step === 1 || step === 3)) || (isPlan && step === 2)}
          >
            <div className="space-y-3">
              <AnimatePresence mode="popLayout">
                {!isPlan && step >= 1 && (
                  <ProtocolCard
                    key="shutdown-request"
                    title="shutdown_request"
                    rows={[`request_id: ${REQUEST_ID}`, "target: teammate", "mode: polite"]}
                    tone={step >= 3 ? "zinc" : "blue"}
                  />
                )}
                {!isPlan && step >= 3 && (
                  <ProtocolCard
                    key="shutdown-response"
                    title="shutdown_response"
                    rows={[`request_id: ${REQUEST_ID}`, "approve: true", "status: closed"]}
                    tone="emerald"
                  />
                )}
                {isPlan && step >= 2 && (
                  <ProtocolCard
                    key="plan-approved"
                    title="plan_approval_response"
                    rows={[`request_id: ${REQUEST_ID}`, "approve: true", "unlock: implementation"]}
                    tone="emerald"
                  />
                )}
              </AnimatePresence>
              {((!isPlan && step === 0) || (isPlan && step < 2)) && (
                <EmptyTray label="等待协议卡" />
              )}
            </div>
          </Desk>

          <Desk
            title="共享卡片结构"
            icon={<ClipboardCheck size={15} />}
            active={(!isPlan && step === 0) || (isPlan && step === 0)}
          >
            <div className="space-y-3">
              <ProtocolCard
                title="protocol fields"
                rows={["type", "request_id", "payload", "response"]}
                tone="amber"
              />
              <div className="rounded-md border border-zinc-200 bg-zinc-50 px-3 py-2 text-xs text-zinc-600 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-300">
                关键是关联，不是仪式感。
              </div>
              {isPlan && (
                <div className="flex items-center gap-2 rounded-md border border-zinc-200 bg-white px-3 py-2 text-xs text-zinc-600 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-300">
                  <LockKeyhole size={14} />
                  批准前实现保持锁定
                </div>
              )}
            </div>
          </Desk>

          <Desk
            title="队友工作台"
            icon={isPlan ? <FileText size={15} /> : <CheckCircle2 size={15} />}
            active={(!isPlan && step === 2) || (isPlan && step === 1)}
          >
            <div className="space-y-3">
              <AnimatePresence mode="popLayout">
                {!isPlan && step >= 2 && (
                  <ProtocolCard
                    key="teammate-decision"
                    title="decision card"
                    rows={[`request_id: ${REQUEST_ID}`, "choice: approve", step >= 3 ? "state: exited" : "state: deciding"]}
                    tone={step >= 3 ? "emerald" : "amber"}
                  />
                )}
                {isPlan && step >= 1 && (
                  <ProtocolCard
                    key="plan-card"
                    title="exit_plan_mode"
                    rows={[`request_id: ${REQUEST_ID}`, "1. 编辑模块", "2. 跑测试", "3. 报告 diff"]}
                    tone={step >= 2 ? "emerald" : "blue"}
                  />
                )}
              </AnimatePresence>
              {((!isPlan && step < 2) || (isPlan && step === 0)) && (
                <EmptyTray label={isPlan ? "草稿方案尚未提交" : "未收到请求"} />
              )}
            </div>
          </Desk>
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
          stepTitle={steps[step].title}
          stepDescription={steps[step].desc}
        />
      </div>
    </section>
  );
}
