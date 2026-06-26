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
    <div className="flex min-h-[280px] flex-1 flex-col rounded-lg border border-[#e8dcc8] bg-[#fbf7eb]">
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
          <div className="flex flex-1 items-center justify-center text-xs text-[#725d42]">
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
    pending: "bg-[#f6efe0] text-[#9f927d]",
    in_progress: "bg-[#fde6d8] text-[#e59266]",
    done: "bg-[#e0f0e0] text-[#8ac68a]",
  };

  const borderStyles: Record<TaskStatus, string> = {
    pending: "border-[#e8dcc8] bg-[#fbf7eb]",
    in_progress: "border-[#e59266]/40 bg-[#fde6d8]",
    done: "border-[#8ac68a]/40 bg-[#e0f0e0]",
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
        <span className="font-mono text-[10px] text-[#725d42]">
          #{task.id}
        </span>
        <span
          className={`rounded-full px-1.5 py-0.5 text-[9px] font-semibold uppercase tracking-wide ${statusStyles[task.status]}`}
        >
          {task.status.replace("_", " ")}
        </span>
      </div>
      <div className="text-xs font-medium text-[#9f927d]">
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
      ? "bg-[#d4c9b4]"
      : value === 1
        ? "bg-[#8ac68a]"
        : value === 2
          ? "bg-[#e59266]"
          : "bg-[#fc736d]";

  return (
    <div className="space-y-1">
      <div className="flex items-center justify-between">
        <span className="text-xs font-medium text-[#9f927d]">
          时间线
        </span>
        <span className="font-mono text-xs text-[#8a7b66]">
          {value}/{max}
        </span>
      </div>
      <div className="relative h-4 w-full overflow-hidden rounded-full bg-[#e8dcc8]">
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
            className="absolute inset-0 rounded-full border-2 border-[#fc736d]"
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
      <h2 className="text-xl font-semibold text-[#794f27]">
        {title || "待办清单"}
      </h2>

      <div className="rounded-lg border border-[#e8dcc8] bg-[#fbf7eb] p-4">
        {/* Nag gauge + nag message */}
        <div className="mb-4 space-y-2">
          <NagGauge value={nagValue} max={NAG_THRESHOLD} firing={nagFires} />

          <AnimatePresence>
            {nagFires && (
              <motion.div
                initial={{ opacity: 0, y: -8, height: 0 }}
                animate={{ opacity: 1, y: 0, height: "auto" }}
                exit={{ opacity: 0, y: -8, height: 0 }}
                className="rounded-md border border-[#fc736d]/40 bg-[#fde2e0] px-3 py-2 text-center text-xs font-bold text-[#fc736d]"
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
            accentClass="bg-[#e8dcc8] text-[#9f927d]"
            headerBg="bg-[#e8dcc8] text-[#9f927d]"
          />
          <KanbanColumn
            title="In Progress"
            tasks={inProgressTasks}
            accentClass="bg-[#fde6d8] text-[#e59266]"
            headerBg="bg-[#fde6d8] text-[#e59266]"
          />
          <KanbanColumn
            title="Done"
            tasks={doneTasks}
            accentClass="bg-[#e0f0e0] text-[#8ac68a]"
            headerBg="bg-[#e0f0e0] text-[#8ac68a]"
          />
        </div>

        {/* Progress summary */}
        <div className="mt-3 flex items-center justify-between rounded-md bg-[#f6efe0] px-3 py-2">
          <span className="font-mono text-[11px] text-[#8a7b66]">
            进度: {doneTasks.length}/{tasks.length} 完成
          </span>
          <div className="flex gap-0.5">
            {tasks.map((t) => (
              <div
                key={t.id}
                className={`h-2 w-6 rounded-sm ${
                  t.status === "done"
                    ? "bg-[#8ac68a]"
                    : t.status === "in_progress"
                      ? "bg-[#e59266]"
                      : "bg-[#d4c9b4]"
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
