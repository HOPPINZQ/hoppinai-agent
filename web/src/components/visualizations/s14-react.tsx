"use client";

import { motion, AnimatePresence } from "framer-motion";
import { useSteppedVisualization } from "@/hooks/useSteppedVisualization";
import { StepControls } from "@/components/visualizations/shared/step-controls";
import { useSvgPalette } from "@/hooks/useDarkMode";

// -- Flowchart node definitions --
interface FlowNode {
  id: string;
  label: string;
  subLabel: string;
  icon?: string;
  x: number;
  y: number;
  w: number;
  h: number;
  color: string;
}

const NODES: FlowNode[] = [
  {
    id: "start",
    label: "开始",
    subLabel: "",
    x: 250,
    y: 30,
    w: 100,
    h: 40,
    color: "#8b5cf6"
  },
  {
    id: "thought",
    label: "思考",
    subLabel: "Thought",
    icon: "🤔",
    x: 250,
    y: 120,
    w: 130,
    h: 70,
    color: "#6366f1"
  },
  {
    id: "action",
    label: "行动",
    subLabel: "Action",
    icon: "⚡",
    x: 400,
    y: 280,
    w: 130,
    h: 70,
    color: "#f59e0b"
  },
  {
    id: "observation",
    label: "观察",
    subLabel: "Observation",
    icon: "👁️",
    x: 100,
    y: 280,
    w: 130,
    h: 70,
    color: "#10b981"
  },
  {
    id: "summary",
    label: "总结",
    subLabel: "",
    x: 250,
    y: 380,
    w: 100,
    h: 50,
    color: "#8b5cf6"
  }
];

// Edges between nodes
interface FlowEdge {
  from: string;
  to: string;
  curved?: boolean;
}

const EDGES: FlowEdge[] = [
  { from: "start", to: "thought" },
  { from: "thought", to: "action", curved: true },
  { from: "action", to: "observation", curved: true },
  { from: "observation", to: "thought", curved: true },
  { from: "thought", to: "summary", curved: true }
];

// Which nodes light up at each step
const ACTIVE_NODES_PER_STEP: string[][] = [
  [],
  ["start"],
  ["thought"],
  ["action"],
  ["observation"],
  ["thought"],
  ["action"],
  ["observation"],
  ["thought"],
  ["summary"]
];

// Which edges highlight at each step
const ACTIVE_EDGES_PER_STEP: string[][] = [
  [],
  [],
  ["start->thought"],
  ["thought->action"],
  ["action->observation"],
  ["observation->thought"],
  ["thought->action"],
  ["action->observation"],
  ["observation->thought"],
  ["thought->summary"]
];

// -- Message blocks --
interface MessageBlock {
  role: string;
  detail: string;
  colorClass: string;
}

const MESSAGES_PER_STEP: (MessageBlock | null)[][] = [
  [],
  [{ role: "user", detail: "帮我分析项目代码质量并给出改进建议", colorClass: "bg-blue-500 dark:bg-blue-600" }],
  [{ role: "thought", detail: "需要列出项目文件，然后逐个分析代码质量", colorClass: "bg-indigo-500 dark:bg-indigo-600" }],
  [{ role: "action", detail: "list_files(pattern='**/*.java')", colorClass: "bg-amber-500 dark:bg-amber-600" }],
  [{ role: "observation", detail: "找到8个Java文件，准备逐个分析", colorClass: "bg-green-500 dark:bg-green-600" }],
  [{ role: "thought", detail: "继续分析核心文件Agent.java", colorClass: "bg-indigo-500 dark:bg-indigo-600" }],
  [{ role: "action", detail: "analyze_code(file='Agent.java')", colorClass: "bg-amber-500 dark:bg-amber-600" }],
  [{ role: "observation", detail: "注释不足(4/10)，复杂度高(6/10)", colorClass: "bg-green-500 dark:bg-green-600" }],
  [{ role: "thought", detail: "信息已充足，可以给出完整的分析报告", colorClass: "bg-indigo-500 dark:bg-indigo-600" }],
  [{ role: "assistant", detail: "分析完成！项目整体质量7.5/10，建议增加注释和测试覆盖率。", colorClass: "bg-purple-500 dark:bg-purple-600" }],
];

// -- Step annotations --
const STEP_INFO = [
  { title: "ReAct 三角循环", desc: "思考-行动-观察形成三角形循环，持续迭代直到任务完成" },
  { title: "用户输入", desc: "用户发送请求，ReAct 循环开始。" },
  { title: "思考阶段", desc: "🤔 AI 分析当前状态，评估是否需要调用工具，制定行动策略。" },
  { title: "思考 → 行动", desc: "⚡ 根据思考结果执行具体操作，调用相应工具。" },
  { title: "行动 → 观察", desc: "👁️ 获取工具执行结果，分析返回数据。" },
  { title: "观察 → 思考", desc: "🔄 观察结果反馈给思考阶段，开始新一轮迭代。每次循环都基于新观察调整策略。" },
  { title: "观察 → 思考", desc: "🔄 观察结果反馈给思考阶段，开始新一轮迭代。每次循环都基于新观察调整策略。" },
  { title: "观察 → 思考", desc: "🔄 观察结果反馈给思考阶段，开始新一轮迭代。每次循环都基于新观察调整策略。" },
  { title: "思考", desc: "检查是否已获得足够信息，判断是否需要继续循环。" },
  { title: "总结阶段", desc: "无需更多工具调用，循环退出，对本次用户输入 → 工具执行 → 工具输出 做一个总结" },
  { title: "任务完成", desc: "给出最终答案。" },
];

// -- Helpers --
function getNode(id: string): FlowNode {
  return NODES.find((n) => n.id === id)!;
}

function edgePath(fromId: string, toId: string, curved = false): string {
  const from = getNode(fromId);
  const to = getNode(toId);

  if (curved) {
    // Curved path for triangle edges
    const midX = (from.x + to.x) / 2;
    const midY = (from.y + to.y) / 2;
    const controlX = midX + (midX - 250) * 0.1;
    const controlY = midY + (midY - 200) * 0.1;

    return `M ${from.x} ${from.y + from.h / 2} Q ${controlX} ${controlY} ${to.x} ${to.y - to.h / 2}`;
  }

  // Straight line (default)
  const startX = from.x;
  const startY = from.y + from.h / 2;
  const endX = to.x;
  const endY = to.y - to.h / 2;
  return `M ${startX} ${startY} L ${endX} ${endY}`;
}

// -- Component --
export default function ReactPattern({ title }: { title?: string }) {
  const {
    currentStep,
    totalSteps,
    next,
    prev,
    reset,
    isPlaying,
    toggleAutoPlay,
  } = useSteppedVisualization({ totalSteps: 10, autoPlayInterval: 2500 });

  const palette = useSvgPalette();
  const activeNodes = ACTIVE_NODES_PER_STEP[currentStep];
  const activeEdges = ACTIVE_EDGES_PER_STEP[currentStep];

  // Build accumulated messages up to the current step
  const visibleMessages: MessageBlock[] = [];
  for (let s = 0; s <= currentStep; s++) {
    for (const msg of MESSAGES_PER_STEP[s]) {
      if (msg) visibleMessages.push(msg);
    }
  }

  const stepInfo = STEP_INFO[currentStep];
  const currentIteration = currentStep >= 5 ? 2 : 1;

  return (
    <section className="min-h-[500px] space-y-4">
      <h2 className="text-xl font-semibold text-zinc-900 dark:text-zinc-100">
        {title || "ReAct: 思考-行动-观察模式"}
      </h2>

      <div className="rounded-lg border border-zinc-200 bg-white p-4 dark:border-zinc-700 dark:bg-zinc-900">
        <div className="flex flex-col gap-4 lg:flex-row">
          {/* Left panel: SVG Flowchart (60%) */}
          <div className="w-full lg:w-[60%]">
            <div className="mb-2 font-mono text-xs text-zinc-400 dark:text-zinc-500">
              while (需要工具调用) {"{"} 思考 → 行动 → 观察 {"}"}
            </div>
            <svg
              viewBox="0 0 550 450"
              className="w-full rounded-md border border-zinc-100 bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-950"
              style={{ minHeight: 350 }}
            >
              <defs>
                {/* Glowing filters */}
                <filter id="glow-thought" x="-50%" y="-50%" width="200%" height="200%">
                  <feDropShadow dx="0" dy="0" stdDeviation="6" floodColor="#6366f1" floodOpacity="0.8" />
                </filter>
                <filter id="glow-action" x="-50%" y="-50%" width="200%" height="200%">
                  <feDropShadow dx="0" dy="0" stdDeviation="6" floodColor="#f59e0b" floodOpacity="0.8" />
                </filter>
                <filter id="glow-observation" x="-50%" y="-50%" width="200%" height="200%">
                  <feDropShadow dx="0" dy="0" stdDeviation="6" floodColor="#10b981" floodOpacity="0.8" />
                </filter>
                <filter id="glow-purple" x="-50%" y="-50%" width="200%" height="200%">
                  <feDropShadow dx="0" dy="0" stdDeviation="6" floodColor="#a855f7" floodOpacity="0.8" />
                </filter>

                {/* Gradients */}
                <linearGradient id="grad-thought" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stopColor="#818cf8" />
                  <stop offset="100%" stopColor="#6366f1" />
                </linearGradient>
                <linearGradient id="grad-action" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stopColor="#fbbf24" />
                  <stop offset="100%" stopColor="#f59e0b" />
                </linearGradient>
                <linearGradient id="grad-observation" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stopColor="#34d399" />
                  <stop offset="100%" stopColor="#10b981" />
                </linearGradient>
              </defs>

              {/* Edges with animated effects */}
              {EDGES.map((edge) => {
                const key = `${edge.from}->${edge.to}`;
                const isActive = activeEdges.includes(key);
                const isTriangleEdge = edge.from === "thought" || edge.from === "action" || edge.from === "observation";
                const d = edgePath(edge.from, edge.to, edge.curved);

                return (
                  <g key={key}>
                    {/* Base edge */}
                    <path
                      d={d}
                      fill="none"
                      stroke={palette.edgeStroke}
                      strokeWidth={2}
                      strokeOpacity="0.2"
                    />

                    {/* Active edge with glow */}
                    {isActive && (
                      <>
                        <motion.path
                          d={d}
                          fill="none"
                          stroke={getNode(edge.from).color}
                          strokeWidth={isTriangleEdge ? 4 : 3}
                          strokeLinecap="round"
                          initial={{ pathLength: 0, opacity: 0 }}
                          animate={{ pathLength: 1, opacity: 1 }}
                          transition={{ duration: 0.6, ease: "easeInOut" }}
                        />

                        {/* Glowing effect */}
                        <motion.path
                          d={d}
                          fill="none"
                          stroke={getNode(edge.from).color}
                          strokeWidth={8}
                          strokeOpacity="0.2"
                          animate={{ strokeOpacity: [0.2, 0.5, 0.2] }}
                          transition={{ duration: 1.5, repeat: Infinity }}
                        />

                        {/* Animated particles on triangle edges */}
                        {isTriangleEdge && currentStep >= 3 && currentStep <= 6 && (
                          <>
                            {[0, 0.3, 0.6].map((delay, i) => (
                              <motion.circle
                                key={`particle-${i}`}
                                r="3"
                                fill={getNode(edge.from).color}
                                initial={{ offsetDistance: "0%", opacity: 0 }}
                                animate={{ offsetDistance: "100%", opacity: [0, 1, 0] }}
                                transition={{
                                  duration: 2,
                                  delay,
                                  ease: "linear",
                                  repeat: Infinity,
                                  repeatDelay: 0.5,
                                }}
                                style={{
                                  offsetPath: `path('${d}')`,
                                }}
                              />
                            ))}
                          </>
                        )}
                      </>
                    )}
                  </g>
                );
              })}

              {/* Nodes */}
              {NODES.map((node) => {
                const isActive = activeNodes.includes(node.id);
                const isStart = node.id === "start";
                const isEnd = node.id === "summary";
                const isTriangleNode = node.id === "thought" || node.id === "action" || node.id === "observation";

                const filterId = isActive
                  ? isEnd || isStart
                    ? "url(#glow-purple)"
                    : isEnd
                      ? "url(#glow-observation)"
                      : `url(#glow-${node.id})`
                  : "none";

                return (
                  <g key={node.id}>
                    <motion.g
                      animate={
                        isActive
                          ? {
                              scale: [1, 1.03, 1],
                            }
                          : { scale: 1 }
                      }
                      transition={{
                        duration: 0.6,
                        repeat: isActive ? Infinity : 0,
                        ease: "easeInOut",
                      }}
                    >
                      {/* Outer pulsing ring */}
                      {isActive && (
                        <motion.circle
                          cx={node.x}
                          cy={node.y}
                          r={isTriangleNode ? node.w / 2 + 12 : node.w / 2 + 8}
                          fill="none"
                          stroke={node.color}
                          strokeWidth={2}
                          strokeOpacity="0.4"
                          animate={{
                            r: [isTriangleNode ? node.w / 2 + 12 : node.w / 2 + 8, isTriangleNode ? node.w / 2 + 20 : node.w / 2 + 15],
                            strokeOpacity: [0.4, 0],
                          }}
                          transition={{
                            duration: 2,
                            repeat: Infinity,
                            ease: "easeOut",
                          }}
                        />
                      )}

                      {/* Main node shape */}
                      <motion.rect
                        x={node.x - node.w / 2}
                        y={node.y - node.h / 2}
                        width={node.w}
                        height={node.h}
                        rx={isTriangleNode ? 16 : 12}
                        fill={isActive ? node.color : palette.nodeFill}
                        stroke={isActive ? "#fff" : palette.nodeStroke}
                        strokeWidth={isActive ? 2.5 : 1.5}
                        filter={filterId}
                        animate={{
                          fill: isActive ? node.color : palette.nodeFill,
                          stroke: isActive ? "#fff" : palette.nodeStroke,
                        }}
                        transition={{ duration: 0.4 }}
                      />

                      {/* Icon with animation */}
                      {node.icon && (
                        <motion.text
                          x={node.x}
                          y={node.y - (node.subLabel ? 10 : 5)}
                          textAnchor="middle"
                          fontSize={isTriangleNode ? 24 : 20}
                          animate={{
                            scale: isActive ? [1, 1.15, 1] : 1,
                            rotate: isActive ? [0, 5, -5, 0] : 0,
                          }}
                          transition={{
                            duration: 0.8,
                            repeat: isActive ? Infinity : 0,
                            repeatDelay: 0.3,
                          }}
                        >
                          {node.icon}
                        </motion.text>
                      )}

                      {/* Label */}
                      <motion.text
                        x={node.x}
                        y={node.y + (node.icon ? (node.subLabel ? 10 : 14) : (node.subLabel ? -5 : 0))}
                        textAnchor="middle"
                        fontSize={isTriangleNode ? 14 : 12}
                        fontWeight="bold"
                        fill={isActive ? "#fff" : palette.nodeText}
                        animate={{
                          fill: isActive ? "#fff" : palette.nodeText,
                        }}
                        transition={{ duration: 0.4 }}
                      >
                        {node.label}
                      </motion.text>

                      {/* Sub-label */}
                      {node.subLabel && (
                        <motion.text
                          x={node.x}
                          y={node.y + 28}
                          textAnchor="middle"
                          fontSize={10}
                          fontWeight="500"
                          fill={isActive ? "rgba(255,255,255,0.9)" : palette.nodeText}
                          animate={{
                            fill: isActive ? "rgba(255,255,255,0.9)" : palette.nodeText,
                          }}
                          transition={{ duration: 0.4 }}
                        >
                          {node.subLabel}
                        </motion.text>
                      )}

                      {/* Inner glow for active nodes */}
                      {isActive && (
                        <motion.rect
                          x={node.x - node.w / 2}
                          y={node.y - node.h / 2}
                          width={node.w}
                          height={node.h}
                          rx={isTriangleNode ? 16 : 12}
                          fill={node.color}
                          opacity={0.2}
                          animate={{
                            scale: [1, 1.08, 1],
                            opacity: [0.2, 0.05, 0.2],
                          }}
                          transition={{
                            duration: 2.5,
                            repeat: Infinity,
                            ease: "easeInOut",
                          }}
                        />
                      )}
                    </motion.g>
                  </g>
                );
              })}

              {/* Iteration counter */}
              {currentStep >= 5 && (
                <motion.g
                  initial={{ opacity: 0, scale: 0.5 }}
                  animate={{ opacity: 1, scale: 1 }}
                >
                  <rect x="30" y="160" width="70" height="32" rx="8" fill="#6366f1" opacity="0.9" />
                  <motion.text
                    x="65"
                    y="182"
                    textAnchor="middle"
                    fontSize={12}
                    fontWeight="bold"
                    fill="#fff"
                    animate={{ scale: [1, 1.05, 1] }}
                    transition={{ duration: 2, repeat: Infinity }}
                  >
                    循环 #{currentIteration}
                  </motion.text>
                </motion.g>
              )}

              {/* Loop indicator */}
              {currentStep >= 5 && currentStep <= 6 && (
                <motion.text
                  x="250"
                  y="220"
                  textAnchor="middle"
                  fontSize={12}
                  fontWeight="bold"
                  fill="#6366f1"
                  opacity="0.7"
                  animate={{
                    scale: [1, 1.1, 1],
                    opacity: [0.7, 0.9, 0.7],
                  }}
                  transition={{
                    duration: 2,
                    repeat: Infinity,
                  }}
                >
                  🔄 循环迭代
                </motion.text>
              )}
            </svg>
          </div>

          {/* Right panel: messages[] array (40%) */}
          <div className="w-full lg:w-[40%]">
            <div className="mb-2 font-mono text-xs text-zinc-400 dark:text-zinc-500">
              messages[]
            </div>
            <div className="min-h-[300px] space-y-2 rounded-md border border-zinc-100 bg-zinc-50 p-3 dark:border-zinc-800 dark:bg-zinc-950">
              <AnimatePresence mode="popLayout">
                {visibleMessages.length === 0 && (
                  <motion.div
                    key="empty"
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    exit={{ opacity: 0 }}
                    className="py-8 text-center text-xs text-zinc-400 dark:text-zinc-600"
                  >
                    [ 空的 ]
                  </motion.div>
                )}
                {visibleMessages.map((msg, i) => (
                  <motion.div
                    key={`${msg.role}-${msg.detail}-${i}`}
                    initial={{ opacity: 0, y: 12, scale: 0.9 }}
                    animate={{ opacity: 1, y: 0, scale: 1 }}
                    exit={{ opacity: 0, scale: 0.9 }}
                    transition={{
                      duration: 0.35,
                      type: "spring",
                      bounce: 0.3,
                      delay: i * 0.05,
                    }}
                    className={`rounded-md px-3 py-2 ${msg.colorClass}`}
                  >
                    <div className="font-mono text-[11px] font-semibold text-white">
                      {msg.role}
                    </div>
                    <div className="mt-0.5 text-[10px] text-white/90">
                      {msg.detail}
                    </div>
                  </motion.div>
                ))}
              </AnimatePresence>

              {/* Array index markers */}
              {visibleMessages.length > 0 && (
                <div className="mt-3 border-t border-zinc-200 pt-2 dark:border-zinc-700">
                  <span className="font-mono text-[10px] text-zinc-400">
                    循环次数: {currentIteration} | 消息条数: {visibleMessages.length}
                  </span>
                </div>
              )}
            </div>
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