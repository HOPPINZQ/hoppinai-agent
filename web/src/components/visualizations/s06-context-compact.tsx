"use client";

import { useMemo } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { useSteppedVisualization } from "@/hooks/useSteppedVisualization";
import { StepControls } from "@/components/visualizations/shared/step-controls";

type BlockType = "user" | "assistant" | "tool_result";

interface ContextBlock {
  id: string;
  type: BlockType;
  label: string;
  tokens: number;
}

const BLOCK_COLORS: Record<BlockType, string> = {
  user: "bg-[#889df0]",
  assistant: "bg-[#9f927d]",
  tool_result: "bg-[#8ac68a]",
};

const BLOCK_LABELS: Record<BlockType, string> = {
  user: "USR",
  assistant: "AST",
  tool_result: "TRL",
};

function generateBlocks(count: number, seed: number): ContextBlock[] {
  const types: BlockType[] = ["user", "assistant", "tool_result"];
  const blocks: ContextBlock[] = [];
  for (let i = 0; i < count; i++) {
    const typeIndex = (i + seed) % 3;
    const type = types[typeIndex];
    const tokens = type === "tool_result" ? 4000 + (i % 3) * 1000 : 1500 + (i % 4) * 500;
    blocks.push({
      id: `b-${seed}-${i}`,
      type,
      label: `${BLOCK_LABELS[type]} ${i + 1}`,
      tokens,
    });
  }
  return blocks;
}

const MAX_TOKENS = 100000;
const WINDOW_HEIGHT = 350;

interface StepState {
  blocks: { id: string; type: BlockType; label: string; heightPx: number; compressed?: boolean }[];
  tokenCount: number;
  fillPercent: number;
  compressionLabel: string | null;
}

function computeStepState(step: number): StepState {
  switch (step) {
    case 0: {
      const raw = generateBlocks(8, 0);
      const tokenCount = 30000;
      const totalRawTokens = raw.reduce((a, b) => a + b.tokens, 0);
      const blocks = raw.map((b) => ({
        ...b,
        heightPx: Math.max(16, (b.tokens / totalRawTokens) * WINDOW_HEIGHT * 0.3),
      }));
      return { blocks, tokenCount, fillPercent: 30, compressionLabel: null };
    }
    case 1: {
      const raw = generateBlocks(16, 0);
      const tokenCount = 60000;
      const totalRawTokens = raw.reduce((a, b) => a + b.tokens, 0);
      const blocks = raw.map((b) => ({
        ...b,
        heightPx: Math.max(12, (b.tokens / totalRawTokens) * WINDOW_HEIGHT * 0.6),
      }));
      return { blocks, tokenCount, fillPercent: 60, compressionLabel: null };
    }
    case 2: {
      const raw = generateBlocks(20, 0);
      const tokenCount = 80000;
      const totalRawTokens = raw.reduce((a, b) => a + b.tokens, 0);
      const blocks = raw.map((b) => ({
        ...b,
        heightPx: Math.max(10, (b.tokens / totalRawTokens) * WINDOW_HEIGHT * 0.8),
      }));
      return { blocks, tokenCount, fillPercent: 80, compressionLabel: null };
    }
    case 3: {
      const raw = generateBlocks(20, 0);
      const tokenCount = 60000;
      const totalRawTokens = raw.reduce((a, b) => a + b.tokens, 0);
      const blocks = raw.map((b) => ({
        ...b,
        heightPx:
          b.type === "tool_result"
            ? 6
            : Math.max(12, (b.tokens / totalRawTokens) * WINDOW_HEIGHT * 0.6),
        compressed: b.type === "tool_result",
      }));
      return {
        blocks,
        tokenCount,
        fillPercent: 60,
        compressionLabel: "MICRO-COMPACT",
      };
    }
    case 4: {
      const raw = generateBlocks(24, 1);
      const tokenCount = 85000;
      const totalRawTokens = raw.reduce((a, b) => a + b.tokens, 0);
      const blocks = raw.map((b) => ({
        ...b,
        heightPx: Math.max(10, (b.tokens / totalRawTokens) * WINDOW_HEIGHT * 0.85),
      }));
      return { blocks, tokenCount, fillPercent: 85, compressionLabel: null };
    }
    case 5: {
      const tokenCount = 25000;
      const summaryBlock = {
        id: "auto-summary",
        type: "assistant" as BlockType,
        label: "SUMMARY",
        heightPx: 40,
        compressed: false,
      };
      const recentBlocks = generateBlocks(4, 2).map((b) => ({
        ...b,
        heightPx: 20,
      }));
      return {
        blocks: [summaryBlock, ...recentBlocks],
        tokenCount,
        fillPercent: 25,
        compressionLabel: "AUTO-COMPACT",
      };
    }
    case 6: {
      const tokenCount = 8000;
      const compactBlock = {
        id: "compact-summary",
        type: "assistant" as BlockType,
        label: "COMPACT SUMMARY",
        heightPx: 24,
        compressed: false,
      };
      return {
        blocks: [compactBlock],
        tokenCount,
        fillPercent: 8,
        compressionLabel: "/compact",
      };
    }
    default:
      return { blocks: [], tokenCount: 0, fillPercent: 0, compressionLabel: null };
  }
}

const STEPS = [
  {
    title: "上下文增长",
    description:
      "上下文窗口承载整段对话；每次 API 调用都会追加更多消息。",
  },
  {
    title: "持续累积",
    description:
      "随着代理工作推进，消息不断累积，上下文窗口逐渐被填满。",
  },
  {
    title: "接近上限",
    description:
      "旧的 tool_results 最消耗 token；micro-compact 会优先处理它们。",
  },
  {
    title: "阶段 1：微压缩",
    description:
      "用短摘要替换旧 tool_results；自动执行，并对模型透明。",
  },
  {
    title: "仍在增长",
    description:
      "工作继续；上下文再次向阈值逼近……",
  },
  {
    title: "阶段 2：自动压缩",
    description:
      "把整段对话汇总成一个精简块，在 token 阈值时触发。",
  },
  {
    title: "阶段 3：/compact",
    description:
      "用户触发、最激进：三层策略性遗忘让会话可以无限延续。",
  },
];

export default function ContextCompact({ title }: { title?: string }) {
  const {
    currentStep,
    totalSteps,
    next,
    prev,
    reset,
    isPlaying,
    toggleAutoPlay,
  } = useSteppedVisualization({ totalSteps: STEPS.length, autoPlayInterval: 2500 });

  const state = useMemo(() => computeStepState(currentStep), [currentStep]);

  const fillColor =
    state.fillPercent > 75
      ? "bg-[#fc736d]"
      : state.fillPercent > 45
        ? "bg-[#e59266]"
        : "bg-[#8ac68a]";

  const tokenDisplay = `${(state.tokenCount / 1000).toFixed(0)}K`;

  return (
    <section className="space-y-4">
      <h2 className="text-xl font-semibold text-[#794f27]">
        {title || "Three-Layer Context Compression"}
      </h2>

      <div
        className="rounded-lg border border-[#e8dcc8] bg-[#fbf7eb] p-6"
        style={{ minHeight: 500 }}
      >
        <div className="flex gap-6">
          {/* Token Window (tall vertical bar on the left) */}
          <div className="flex flex-col items-center">
            <div className="mb-2 font-mono text-[10px] font-semibold text-[#8a7b66]">
              Context Window
            </div>
            <div
              className="relative w-24 overflow-hidden rounded-xl border-2 border-[#d4c9b4] bg-[#fbf7eb]"
              style={{ height: WINDOW_HEIGHT }}
            >
              {/* Blocks stacked from bottom up */}
              <div className="absolute bottom-0 left-0 right-0 flex flex-col-reverse gap-px p-1">
                <AnimatePresence mode="popLayout">
                  {state.blocks.map((block) => (
                    <motion.div
                      key={block.id}
                      initial={{ opacity: 0, scaleY: 0 }}
                      animate={{
                        opacity: 1,
                        scaleY: 1,
                        height: block.heightPx,
                      }}
                      exit={{ opacity: 0, scaleY: 0 }}
                      transition={{ duration: 0.4 }}
                      className={`flex w-full items-center justify-center rounded-sm ${
                        block.compressed
                          ? "bg-[#8ac68a]/60"
                          : BLOCK_COLORS[block.type]
                      }`}
                      style={{ originY: 1 }}
                    >
                      {block.heightPx >= 14 && (
                        <span className="truncate px-1 text-[8px] font-medium text-white">
                          {block.label}
                        </span>
                      )}
                    </motion.div>
                  ))}
                </AnimatePresence>
              </div>

              {/* Fill level line */}
              <motion.div
                animate={{ bottom: `${state.fillPercent}%` }}
                transition={{ duration: 0.5 }}
                className="absolute left-0 right-0 border-t-2 border-dashed border-[#fc736d]/60"
              >
                <span className="absolute -top-4 right-1 font-mono text-[9px] font-bold text-[#fc736d]">
                  {state.fillPercent}%
                </span>
              </motion.div>
            </div>

            {/* Token count */}
            <motion.div
              key={state.tokenCount}
              initial={{ scale: 0.85 }}
              animate={{ scale: 1 }}
              className="mt-2 font-mono text-sm font-bold text-[#9f927d]"
            >
              {tokenDisplay}
            </motion.div>
            <div className="font-mono text-[10px] text-[#725d42]">
              / 100K
            </div>
          </div>

          {/* Right side: state display and compression stage */}
          <div className="flex flex-1 flex-col justify-between">
            {/* Top: horizontal token bar */}
            <div>
              <div className="mb-1 flex items-center justify-between">
                <span className="text-xs text-[#8a7b66]">
                  Token usage
                </span>
                <span className="font-mono text-xs text-[#8a7b66]">
                  {state.tokenCount.toLocaleString()} / {MAX_TOKENS.toLocaleString()}
                </span>
              </div>
              <div className="h-3 overflow-hidden rounded-full bg-[#f6efe0]">
                <motion.div
                  animate={{ width: `${state.fillPercent}%` }}
                  transition={{ duration: 0.5 }}
                  className={`h-full rounded-full ${fillColor}`}
                />
              </div>
            </div>

            {/* Message type legend */}
            <div className="mt-4 flex items-center gap-4">
              <div className="flex items-center gap-1">
                <div className="h-3 w-3 rounded bg-[#889df0]" />
                <span className="text-[10px] text-[#8a7b66]">user</span>
              </div>
              <div className="flex items-center gap-1">
                <div className="h-3 w-3 rounded bg-[#9f927d]" />
                <span className="text-[10px] text-[#8a7b66]">assistant</span>
              </div>
              <div className="flex items-center gap-1">
                <div className="h-3 w-3 rounded bg-[#8ac68a]" />
                <span className="text-[10px] text-[#8a7b66]">tool_result</span>
              </div>
            </div>

            {/* Highlight old tool_results at step 2 */}
            <AnimatePresence>
              {currentStep === 2 && (
                <motion.div
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0 }}
                  className="mt-3 rounded border border-[#e59266]/40 bg-[#fde6d8] px-3 py-2"
                >
                  <div className="text-xs font-semibold text-[#e59266]">
                    tool_results are the largest blocks
                  </div>
                  <div className="text-[11px] text-[#e59266]">
                    File contents, command outputs, search results -- each one is thousands of tokens.
                  </div>
                </motion.div>
              )}
            </AnimatePresence>

            {/* Compression stage label */}
            <AnimatePresence>
              {state.compressionLabel && (
                <motion.div
                  initial={{ opacity: 0, scale: 0.9 }}
                  animate={{ opacity: 1, scale: 1 }}
                  exit={{ opacity: 0, scale: 0.9 }}
                  transition={{ duration: 0.4 }}
                  className="mt-4"
                >
                  <div className={`rounded-lg border-2 p-4 text-center ${
                    currentStep === 3
                      ? "border-[#e59266] bg-[#fde6d8]"
                      : currentStep === 5
                        ? "border-[#889df0] bg-[#e6eafb]"
                        : "border-[#8ac68a] bg-[#e0f0e0]"
                  }`}>
                    <div className={`text-lg font-black ${
                      currentStep === 3
                        ? "text-[#e59266]"
                        : currentStep === 5
                          ? "text-[#889df0]"
                          : "text-[#8ac68a]"
                    }`}>
                      {state.compressionLabel}
                    </div>
                    <div className={`mt-1 text-xs ${
                      currentStep === 3
                        ? "text-[#e59266]"
                        : currentStep === 5
                          ? "text-[#889df0]"
                          : "text-[#8ac68a]"
                    }`}>
                      {currentStep === 3 && "Old tool_results shrunk to tiny summaries"}
                      {currentStep === 5 && "Full conversation compressed to summary block"}
                      {currentStep === 6 && "Most aggressive compression -- near-empty context"}
                    </div>
                  </div>
                </motion.div>
              )}
            </AnimatePresence>

            {/* Three stages overview on final step */}
            {currentStep === 6 && (
              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                transition={{ delay: 0.4 }}
                className="mt-4 space-y-2"
              >
                <div className="flex items-center gap-2 rounded bg-[#fde6d8] px-3 py-1.5">
                  <div className="h-2 w-2 rounded-full bg-[#e59266]" />
                  <span className="text-xs text-[#e59266]">
                    Stage 1: Micro -- shrink old tool_results
                  </span>
                  <span className="ml-auto font-mono text-[10px] text-[#e59266]">
                    automatic
                  </span>
                </div>
                <div className="flex items-center gap-2 rounded bg-[#e6eafb] px-3 py-1.5">
                  <div className="h-2 w-2 rounded-full bg-[#889df0]" />
                  <span className="text-xs text-[#889df0]">
                    Stage 2: Auto -- summarize entire conversation
                  </span>
                  <span className="ml-auto font-mono text-[10px] text-[#889df0]">
                    at threshold
                  </span>
                </div>
                <div className="flex items-center gap-2 rounded bg-[#e0f0e0] px-3 py-1.5">
                  <div className="h-2 w-2 rounded-full bg-[#8ac68a]" />
                  <span className="text-xs text-[#8ac68a]">
                    Stage 3: /compact -- user-triggered, deepest compression
                  </span>
                  <span className="ml-auto font-mono text-[10px] text-[#8ac68a]">
                    manual
                  </span>
                </div>
              </motion.div>
            )}
          </div>
        </div>

        {/* Step Controls */}
        <div className="mt-6">
          <StepControls
            currentStep={currentStep}
            totalSteps={totalSteps}
            onPrev={prev}
            onNext={next}
            onReset={reset}
            isPlaying={isPlaying}
            onToggleAutoPlay={toggleAutoPlay}
            stepTitle={STEPS[currentStep].title}
            stepDescription={STEPS[currentStep].description}
          />
        </div>
      </div>
    </section>
  );
}
