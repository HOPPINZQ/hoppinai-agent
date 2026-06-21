"use client";

import { AnimatePresence, motion } from "framer-motion";
import { CheckCircle2, ClipboardList, FileJson, LockKeyhole, PlayCircle } from "lucide-react";
import { StepControls } from "@/components/visualizations/shared/step-controls";
import { useSteppedVisualization } from "@/hooks/useSteppedVisualization";
import { cn } from "@/lib/utils";

type Status = "blocked" | "ready" | "active" | "done";

interface TaskCard {
  id: string;
  title: string;
  blockers: string[];
  status: Status;
}

const STEPS = [
  {
    title: "任务变成文件",
    desc: "智能体把工作写成磁盘上的任务卡，使计划能跨压缩和重启存活。",
  },
  {
    title: "找到第一张就绪卡",
    desc: "没有阻塞的任务立即可用，其他任务在板子上可见地等待。",
  },
  {
    title: "推进一张卡",
    desc: "进行中的任务不仅是模型脑中的文字，它有持久的状态。",
  },
  {
    title: "完成解锁依赖者",
    desc: "当 T1 完成后，依赖 T1 的卡片就变为就绪。",
  },
  {
    title: "并行就绪工作",
    desc: "T2 和 T3 可以独立推进，而 T4 仍需同时等待两者。",
  },
  {
    title: "所有阻塞清除",
    desc: "一旦 T2 与 T3 完成，T4 从等待转为进行中。",
  },
  {
    title: "看板收敛",
    desc: "每张卡都到达完成状态；依赖关系无需画图即可见。",
  },
] as const;

const BASE_TASKS = [
  { id: "T1", title: "搭建数据库", blockers: [] },
  { id: "T2", title: "添加 API 路由", blockers: ["T1"] },
  { id: "T3", title: "构建鉴权模块", blockers: ["T1"] },
  { id: "T4", title: "集成联调", blockers: ["T2", "T3"] },
  { id: "T5", title: "部署上线", blockers: ["T4"] },
];

function taskStatus(id: string, step: number): Status {
  const table: Record<string, Status[]> = {
    T1: ["ready", "ready", "active", "done", "done", "done", "done"],
    T2: ["blocked", "blocked", "blocked", "ready", "active", "done", "done"],
    T3: ["blocked", "blocked", "blocked", "ready", "active", "done", "done"],
    T4: ["blocked", "blocked", "blocked", "blocked", "blocked", "active", "done"],
    T5: ["blocked", "blocked", "blocked", "blocked", "blocked", "blocked", "done"],
  };
  return table[id]?.[step] ?? "blocked";
}

function getTasks(step: number): TaskCard[] {
  return BASE_TASKS.map((task) => ({ ...task, status: taskStatus(task.id, step) }));
}

function statusClass(status: Status): string {
  if (status === "done") return "border-emerald-200 bg-emerald-50 text-emerald-800 dark:border-emerald-900 dark:bg-emerald-950/40 dark:text-emerald-200";
  if (status === "active") return "border-blue-200 bg-blue-50 text-blue-800 dark:border-blue-900 dark:bg-blue-950/40 dark:text-blue-200";
  if (status === "ready") return "border-amber-200 bg-amber-50 text-amber-800 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-200";
  return "border-zinc-200 bg-zinc-50 text-zinc-600 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-300";
}

function statusIcon(status: Status) {
  if (status === "done") return <CheckCircle2 size={15} />;
  if (status === "active") return <PlayCircle size={15} />;
  if (status === "ready") return <ClipboardList size={15} />;
  return <LockKeyhole size={15} />;
}

function statusLabel(status: Status): string {
  if (status === "done") return "完成";
  if (status === "active") return "进行中";
  if (status === "ready") return "就绪";
  return "阻塞";
}

function TaskCardView({ task }: { task: TaskCard }) {
  return (
    <motion.div
      layout
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, scale: 0.97 }}
      transition={{ duration: 0.22 }}
      className={cn("rounded-md border p-3 shadow-sm", statusClass(task.status))}
    >
      <div className="mb-2 flex items-center justify-between gap-2">
        <div className="font-mono text-xs font-semibold">{task.id}</div>
        <div className="flex items-center gap-1 text-[11px] font-semibold">
          {statusIcon(task.status)}
          {statusLabel(task.status)}
        </div>
      </div>
      <div className="text-sm font-semibold leading-snug">{task.title}</div>
      <div className="mt-2 flex flex-wrap gap-1">
        {task.blockers.length === 0 ? (
          <span className="rounded bg-white/70 px-1.5 py-0.5 text-[10px] dark:bg-zinc-950/30">
            无阻塞
          </span>
        ) : (
          task.blockers.map((blocker) => (
            <span key={blocker} className="rounded bg-white/70 px-1.5 py-0.5 font-mono text-[10px] dark:bg-zinc-950/30">
              等待 {blocker}
            </span>
          ))
        )}
      </div>
    </motion.div>
  );
}

function Lane({
  title,
  subtitle,
  tasks,
}: {
  title: string;
  subtitle: string;
  tasks: TaskCard[];
}) {
  return (
    <div className="rounded-lg border border-zinc-200 bg-white p-3 dark:border-zinc-700 dark:bg-zinc-900">
      <div className="mb-3">
        <div className="text-sm font-semibold text-zinc-800 dark:text-zinc-100">{title}</div>
        <div className="text-[11px] text-zinc-500 dark:text-zinc-400">{subtitle}</div>
      </div>
      <div className="space-y-2">
        <AnimatePresence mode="popLayout">
          {tasks.length > 0 ? (
            tasks.map((task) => <TaskCardView key={`${task.id}-${task.status}`} task={task} />)
          ) : (
            <motion.div
              key="empty"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="rounded-md border border-dashed border-zinc-300 px-3 py-6 text-center text-xs text-zinc-500 dark:border-zinc-700 dark:text-zinc-400"
            >
              空
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </div>
  );
}

export default function TaskSystem({ title }: { title?: string }) {
  const vis = useSteppedVisualization({ totalSteps: STEPS.length, autoPlayInterval: 2500 });
  const step = vis.currentStep;
  const tasks = getTasks(step);
  const current = STEPS[step];

  const blocked = tasks.filter((task) => task.status === "blocked");
  const ready = tasks.filter((task) => task.status === "ready");
  const active = tasks.filter((task) => task.status === "active");
  const done = tasks.filter((task) => task.status === "done");

  return (
    <section className="min-h-[500px] space-y-4">
      <h2 className="text-xl font-semibold text-zinc-900 dark:text-zinc-100">
        {title || "任务看板与依赖"}
      </h2>

      <div className="rounded-lg border border-zinc-200 bg-white p-4 dark:border-zinc-700 dark:bg-zinc-900">
        <div className="mb-4 flex flex-col gap-3 rounded-lg border border-zinc-200 bg-zinc-50 p-3 dark:border-zinc-700 dark:bg-zinc-800/70 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-2 text-sm font-semibold text-zinc-800 dark:text-zinc-100">
            <FileJson size={16} />
            .tasks 看板
          </div>
          <div className="grid grid-cols-4 gap-2 text-center text-xs">
            <div className="rounded bg-zinc-100 px-2 py-1 dark:bg-zinc-900">{blocked.length} 阻塞</div>
            <div className="rounded bg-amber-100 px-2 py-1 text-amber-700 dark:bg-amber-900/30 dark:text-amber-300">{ready.length} 就绪</div>
            <div className="rounded bg-blue-100 px-2 py-1 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300">{active.length} 进行</div>
            <div className="rounded bg-emerald-100 px-2 py-1 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-300">{done.length} 完成</div>
          </div>
        </div>

        <div className="grid gap-3 lg:grid-cols-4">
          <Lane title="等待" subtitle="被其他卡片阻塞" tasks={blocked} />
          <Lane title="就绪" subtitle="现在可被认领" tasks={ready} />
          <Lane title="进行中" subtitle="当前在推进" tasks={active} />
          <Lane title="已完成" subtitle="解锁后续依赖" tasks={done} />
        </div>

        <div className="mt-4 rounded-md border border-blue-200 bg-blue-50 px-3 py-2 text-xs leading-relaxed text-blue-800 dark:border-blue-900 dark:bg-blue-950/30 dark:text-blue-200">
          依赖关系不再是一张需要追踪的箭头图，而是卡片上一个可见的阻塞徽章。
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
