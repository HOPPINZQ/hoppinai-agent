"use client";

import { motion, AnimatePresence } from "framer-motion";
import { useSteppedVisualization } from "@/hooks/useSteppedVisualization";
import { StepControls } from "@/components/visualizations/shared/step-controls";

// -- Task definitions --

type TaskStatus = "pending" | "in_progress" | "done";

interface Task {
  id: number;
  label: string;
  status: TaskStatus;
}

// Snapshot of all 4 tasks at each step
const TASK_STATES: Task[][] = [
  // Step 0: all pending
  [
    { id: 1, label: "去网上搜索解决方案", status: "pending" },
    { id: 2, label: "创建脚手架", status: "pending" },
    { id: 3, label: "开发xxx功能", status: "pending" },
    { id: 4, label: "编写测试用例", status: "pending" },
  ],
  // Step 1: still all pending (idle round 1)
  [
    { id: 1, label: "去网上搜索解决方案", status: "pending" },
    { id: 2, label: "创建脚手架", status: "pending" },
    { id: 3, label: "开发xxx功能", status: "pending" },
    { id: 4, label: "编写测试用例", status: "pending" },
  ],
  // Step 2: still all pending (idle round 2)
  [
    { id: 1, label: "去网上搜索解决方案", status: "pending" },
    { id: 2, label: "创建脚手架", status: "pending" },
    { id: 3, label: "开发xxx功能", status: "pending" },
    { id: 4, label: "编写测试用例", status: "pending" },
  ],
  // Step 3: NAG fires, task 1 moves to in_progress
  [
    { id: 1, label: "去网上搜索解决方案", status: "in_progress" },
    { id: 2, label: "创建脚手架", status: "pending" },
    { id: 3, label: "开发xxx功能", status: "pending" },
    { id: 4, label: "编写测试用例", status: "pending" },
  ],
  // Step 4: task 1 done
  [
    { id: 1, label: "去网上搜索解决方案", status: "done" },
    { id: 2, label: "创建脚手架", status: "pending" },
    { id: 3, label: "开发xxx功能", status: "pending" },
    { id: 4, label: "编写测试用例", status: "pending" },
  ],
  // Step 5: task 2 self-directed to in_progress
  [
    { id: 1, label: "去网上搜索解决方案", status: "done" },
    { id: 2, label: "创建脚手架", status: "in_progress" },
    { id: 3, label: "开发xxx功能", status: "pending" },
    { id: 4, label: "编写测试用例", status: "pending" },
  ],
  // Step 6: tasks 2,3 done, task 4 in_progress
  [
    { id: 1, label: "去网上搜索解决方案", status: "done" },
    { id: 2, label: "创建脚手架", status: "done" },
    { id: 3, label: "开发xxx功能", status: "done" },
    { id: 4, label: "编写测试用例", status: "in_progress" },
  ],
];

// Nag timer value at each step (out of 3)
const NAG_TIMER_PER_STEP = [0, 1, 2, 3, 0, 0, 0];
const NAG_THRESHOLD = 3;

// Whether the nag fires at this step
const NAG_FIRES_PER_STEP = [false, false, false, true, false, false, false];

// Step annotations
const STEP_INFO = [
  { title: "计划可视化", desc: "TodoWrite 为模型提供一份可见的计划。所有任务初始为 pending。" },
  { title: "第 1 轮——未推进", desc: "模型在做事，但没有更新待办；催办计数器递增。" },
  { title: "第 2 轮——仍未推进", desc: "两轮没有进展，压力开始累积。" },
  { title: "催办提醒！", desc: "达到阈值！系统注入消息：'你有未完成的任务。现在就领取一个！'" },
  { title: "任务完成", desc: "模型完成任务。计时器保持在 0——推进待办会重置计数器。" },
  { title: "自主推进", desc: "一旦学会模式，模型会主动领取任务。" },
  { title: "目标达成", desc: "可见计划 + 催办压力 = 可靠的任务完成。" },
];

// -- Column component --

function KanbanColumn({
  title,
  tasks,
  accentClass,
  headerBg,
}: {
  title: string;
  tasks: Task[];
  accentClass: string;
  headerBg: string;
}) {
  return (
    <div className="flex min-h-[280px] flex-1 flex-col rounded-lg border border-zinc-200 bg-zinc-50 dark:border-zinc-700 dark:bg-zinc-900">
      <div
        className={`rounded-t-lg px-3 py-2 text-center text-xs font-bold uppercase tracking-wider ${headerBg}`}
      >
        {title}
        <span className={`ml-1.5 inline-flex h-5 w-5 items-center justify-center rounded-full text-[10px] font-bold ${accentClass}`}>
          {tasks.length}
        </span>
      </div>
      <div className="flex flex-1 flex-col gap-2 p-2">
        <AnimatePresence mode="popLayout">
          {tasks.map((task) => (
            <TaskCard key={task.id} task={task} />
          ))}
        </AnimatePresence>
        {tasks.length === 0 && (
          <div className="flex flex-1 items-center justify-center text-xs text-zinc-400 dark:text-zinc-600">
            --
          </div>
        )}
      </div>
    </div>
  );
}

// -- Task card --

function TaskCard({ task }: { task: Task }) {
  const statusStyles: Record<TaskStatus, string> = {
    pending: "bg-zinc-100 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-400",
    in_progress: "bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300",
    done: "bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300",
  };

  const borderStyles: Record<TaskStatus, string> = {
    pending: "border-zinc-200 bg-white dark:border-zinc-700 dark:bg-zinc-800",
    in_progress: "border-amber-300 bg-amber-50 dark:border-amber-700 dark:bg-amber-950/30",
    done: "border-emerald-300 bg-emerald-50 dark:border-emerald-700 dark:bg-emerald-950/30",
  };

  return (
    <motion.div
      layout
      layoutId={`task-${task.id}`}
      initial={{ opacity: 0, scale: 0.8 }}
      animate={{ opacity: 1, scale: 1 }}
      exit={{ opacity: 0, scale: 0.8 }}
      transition={{ type: "spring", stiffness: 400, damping: 30 }}
      className={`rounded-md border p-2.5 ${borderStyles[task.status]}`}
    >
      <div className="mb-1.5 flex items-center justify-between">
        <span className="font-mono text-[10px] text-zinc-400 dark:text-zinc-500">
          #{task.id}
        </span>
        <span
          className={`rounded-full px-1.5 py-0.5 text-[9px] font-semibold uppercase tracking-wide ${statusStyles[task.status]}`}
        >
          {task.status.replace("_", " ")}
        </span>
      </div>
      <div className="text-xs font-medium text-zinc-700 dark:text-zinc-300">
        {task.label}
      </div>
    </motion.div>
  );
}

// -- Nag gauge --

function NagGauge({ value, max, firing }: { value: number; max: number; firing: boolean }) {
  const pct = Math.min((value / max) * 100, 100);

  const barColor =
    value === 0
      ? "bg-zinc-300 dark:bg-zinc-600"
      : value === 1
        ? "bg-green-400 dark:bg-green-500"
        : value === 2
          ? "bg-yellow-400 dark:bg-yellow-500"
          : "bg-red-500 dark:bg-red-500";

  return (
    <div className="space-y-1">
      <div className="flex items-center justify-between">
        <span className="text-xs font-medium text-zinc-600 dark:text-zinc-300">
          时间线
        </span>
        <span className="font-mono text-xs text-zinc-500 dark:text-zinc-400">
          {value}/{max}
        </span>
      </div>
      <div className="relative h-4 w-full overflow-hidden rounded-full bg-zinc-200 dark:bg-zinc-700">
        <motion.div
          className={`absolute inset-y-0 left-0 rounded-full ${barColor}`}
          initial={{ width: "0%" }}
          animate={{
            width: `${pct}%`,
            ...(firing ? { scale: [1, 1.05, 1] } : {}),
          }}
          transition={{
            width: { duration: 0.5, ease: "easeOut" },
            scale: { duration: 0.3, repeat: 2 },
          }}
        />
        {firing && (
          <motion.div
            className="absolute inset-0 rounded-full border-2 border-red-500"
            initial={{ opacity: 0 }}
            animate={{ opacity: [0, 1, 0, 1, 0] }}
            transition={{ duration: 1 }}
          />
        )}
      </div>
    </div>
  );
}

// -- Main component --

export default function TodoWrite({ title }: { title?: string }) {
  const {
    currentStep,
    totalSteps,
    next,
    prev,
    reset,
    isPlaying,
    toggleAutoPlay,
  } = useSteppedVisualization({ totalSteps: 7, autoPlayInterval: 2500 });

  const tasks = TASK_STATES[currentStep];
  const nagValue = NAG_TIMER_PER_STEP[currentStep];
  const nagFires = NAG_FIRES_PER_STEP[currentStep];
  const stepInfo = STEP_INFO[currentStep];

  const pendingTasks = tasks.filter((t) => t.status === "pending");
  const inProgressTasks = tasks.filter((t) => t.status === "in_progress");
  const doneTasks = tasks.filter((t) => t.status === "done");

  return (
    <section className="min-h-[500px] space-y-4">
      <h2 className="text-xl font-semibold text-zinc-900 dark:text-zinc-100">
        {title || "待办清单"}
      </h2>

      <div className="rounded-lg border border-zinc-200 bg-white p-4 dark:border-zinc-700 dark:bg-zinc-900">
        {/* Nag gauge + nag message */}
        <div className="mb-4 space-y-2">
          <NagGauge value={nagValue} max={NAG_THRESHOLD} firing={nagFires} />

          <AnimatePresence>
            {nagFires && (
              <motion.div
                initial={{ opacity: 0, y: -8, height: 0 }}
                animate={{ opacity: 1, y: 0, height: "auto" }}
                exit={{ opacity: 0, y: -8, height: 0 }}
                className="rounded-md border border-red-300 bg-red-50 px-3 py-2 text-center text-xs font-bold text-red-700 dark:border-red-700 dark:bg-red-950/30 dark:text-red-300"
              >
                SYSTEM: "你有未完成的任务。现在就领取一个!"
              </motion.div>
            )}
          </AnimatePresence>
        </div>

        {/* Kanban board */}
        <div className="flex gap-3">
          <KanbanColumn
            title="Pending"
            tasks={pendingTasks}
            accentClass="bg-zinc-200 text-zinc-600 dark:bg-zinc-700 dark:text-zinc-300"
            headerBg="bg-zinc-200 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300"
          />
          <KanbanColumn
            title="In Progress"
            tasks={inProgressTasks}
            accentClass="bg-amber-200 text-amber-700 dark:bg-amber-800 dark:text-amber-200"
            headerBg="bg-amber-100 text-amber-800 dark:bg-amber-900/40 dark:text-amber-300"
          />
          <KanbanColumn
            title="Done"
            tasks={doneTasks}
            accentClass="bg-emerald-200 text-emerald-700 dark:bg-emerald-800 dark:text-emerald-200"
            headerBg="bg-emerald-100 text-emerald-800 dark:bg-emerald-900/40 dark:text-emerald-300"
          />
        </div>

        {/* Progress summary */}
        <div className="mt-3 flex items-center justify-between rounded-md bg-zinc-100 px-3 py-2 dark:bg-zinc-800">
          <span className="font-mono text-[11px] text-zinc-500 dark:text-zinc-400">
            进度: {doneTasks.length}/{tasks.length} 完成
          </span>
          <div className="flex gap-0.5">
            {tasks.map((t) => (
              <div
                key={t.id}
                className={`h-2 w-6 rounded-sm ${
                  t.status === "done"
                    ? "bg-emerald-500"
                    : t.status === "in_progress"
                      ? "bg-amber-400"
                      : "bg-zinc-300 dark:bg-zinc-600"
                }`}
              />
            ))}
          </div>
        </div>
      </div>

      <StepControls
        currentStep={currentStep}
        totalSteps={totalSteps}
        onPrev={prev}
        onNext={next}
        onReset={reset}
        isPlaying={isPlaying}
        onToggleAutoPlay={toggleAutoPlay}
        stepTitle={stepInfo.title}
        stepDescription={stepInfo.desc}
      />
    </section>
  );
}
