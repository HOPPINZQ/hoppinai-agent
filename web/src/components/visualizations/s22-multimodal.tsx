"use client";

import { AnimatePresence, motion } from "framer-motion";
import { Camera, FileImage, FileText, MessageCircle, ScanEye } from "lucide-react";
import { StepControls } from "@/components/visualizations/shared/step-controls";
import { useSteppedVisualization } from "@/hooks/useSteppedVisualization";
import { cn } from "@/lib/utils";

type ContentType = "text" | "image" | "file";

interface ContentBlock {
  id: string;
  type: ContentType;
  label: string;
  detail: string;
}

const STEPS = [
  {
    title: "消息不再是纯文本",
    desc: "MessageParam 从 String 升级为 List<MessageContent>：每条消息可以同时携带文本、图像和文件。",
  },
  {
    title: "read_file 自动识别二进制",
    desc: "read_file 调用 Files.probeContentType()，检测到 image/* 就自动 base64 编码，不依赖 LLM 手动判断。",
  },
  {
    title: "screenshot 新工具",
    desc: "Java Robot 截屏 → BufferedImage → base64 PNG → 进 MessageContent，让 Agent 看到用户看到了什么。",
  },
  {
    title: "API 适配层分发",
    desc: "AIConstants.supportsVision 标志控制是否把 ContentBlock 数组原样送入模型请求，不支持的模型降级为文本描述。",
  },
  {
    title: "PDF / Word 走提取器",
    desc: "非图像文件（pdf、docx）走 PdfBox / Apache POI 提取纯文本，大的解析结果触发上下文压缩。",
  },
  {
    title: "多模态卡片墙",
    desc: "前端用卡片墙展示每一轮的多块 content：文本卡 / 图像卡 / 文件卡，每张标注 type + 大小。",
  },
] as const;

function contentClass(type: ContentType): string {
  if (type === "image") return "border-violet-300 bg-violet-50 dark:border-violet-800 dark:bg-violet-950/30";
  if (type === "file") return "border-amber-300 bg-amber-50 dark:border-amber-800 dark:bg-amber-950/30";
  return "border-zinc-200 bg-white dark:border-zinc-700 dark:bg-zinc-900";
}

function iconForType(type: ContentType) {
  if (type === "image") return <FileImage size={15} className="text-violet-500" />;
  if (type === "file") return <FileText size={15} className="text-amber-500" />;
  return <MessageCircle size={15} className="text-zinc-400" />;
}

function contentBlocks(step: number): ContentBlock[] {
  const blocks: ContentBlock[] = [];
  if (step >= 0) {
    blocks.push({ id: "text", type: "text", label: "修复这个错误", detail: "type: text — 用户原始请求" });
  }
  if (step >= 1) {
    blocks.push({ id: "img", type: "image", label: "error.png (286 KB)", detail: "type: image — read_file 自动 base64 编码" });
  }
  if (step >= 2) {
    blocks.push({ id: "ss", type: "image", label: "screenshot.png (1.2 MB)", detail: "type: image — Robot.createScreenCapture()" });
  }
  if (step >= 3) {
    blocks.push({ id: "pdf", type: "file", label: "spec.pdf (4.1 MB)", detail: "type: file — PdfBox 提取 → 文本块" });
  }
  return blocks;
}

export default function MultimodalVisualization({ title }: { title?: string }) {
  const vis = useSteppedVisualization({ totalSteps: STEPS.length, autoPlayInterval: 2500 });
  const step = vis.currentStep;
  const current = STEPS[step];
  const blocks = contentBlocks(step);

  return (
    <section className="min-h-[500px] space-y-4">
      <h2 className="text-xl font-semibold text-zinc-900 dark:text-zinc-100">
        {title || "多模态消息管道"}
      </h2>

      <div className="rounded-lg border border-zinc-200 bg-white p-4 dark:border-zinc-700 dark:bg-zinc-900">
        <div className="grid gap-3 lg:grid-cols-[1fr_1fr]">
          {/* Left: message pipeline */}
          <div className="rounded-lg border border-zinc-200 bg-zinc-50 p-3 dark:border-zinc-700 dark:bg-zinc-800/70">
            <div className="mb-3 flex items-center gap-2 text-sm font-semibold text-zinc-800 dark:text-zinc-100">
              <ScanEye size={16} />
              MessageParam.content[]
            </div>
            <div className="space-y-2">
              <AnimatePresence mode="popLayout">
                {blocks.map((block) => (
                  <motion.div
                    key={block.id}
                    layout
                    initial={{ opacity: 0, y: 8, scale: 0.98 }}
                    animate={{ opacity: 1, y: 0, scale: 1 }}
                    exit={{ opacity: 0, y: -6 }}
                    transition={{ duration: 0.22 }}
                    className={cn("rounded-lg border p-3", contentClass(block.type))}
                  >
                    <div className="flex items-center gap-2">
                      {iconForType(block.type)}
                      <span className="text-xs font-semibold">{block.label}</span>
                    </div>
                    <div className="mt-1 text-[11px] opacity-70">{block.detail}</div>
                  </motion.div>
                ))}
              </AnimatePresence>
              {blocks.length === 0 && (
                <div className="rounded-lg border border-dashed border-zinc-300 px-3 py-12 text-center text-xs text-zinc-500 dark:border-zinc-700 dark:text-zinc-400">
                  等待消息内容
                </div>
              )}
            </div>
          </div>

          {/* Right: flow diagram */}
          <div className="space-y-3">
            <div className="rounded-lg border border-zinc-200 bg-zinc-50 p-3 dark:border-zinc-700 dark:bg-zinc-800/70">
              <div className="mb-3 flex items-center gap-2 text-sm font-semibold text-zinc-800 dark:text-zinc-100">
                <Camera size={16} />
                内容检测流
              </div>
              <div className="flex flex-col gap-2">
                {[
                  { label: "read_file(path)", act: step >= 1, text: "Files.probeContentType() → mime 判断" },
                  { label: "image/* → base64", act: step >= 1, text: "Base64.encode(content) 进 MessageContent" },
                  { label: "screenshot()", act: step >= 2, text: "Robot → BufferedImage → PNG base64" },
                  { label: "pdf / docx → extract", act: step >= 4, text: "PdfBox / POI → 纯文本 → MessageContent" },
                  { label: "API dispatch", act: step >= 3, text: "supportsVision ? 原样送入 : 降级为 text" },
                ].map((item, i) => (
                  <motion.div
                    key={item.label}
                    initial={{ opacity: 0, x: 8 }}
                    animate={{ opacity: 1, x: 0 }}
                    transition={{ duration: 0.2, delay: i * 0.05 }}
                    className={cn(
                      "rounded-md border px-3 py-2 text-xs",
                      item.act
                        ? "border-emerald-200 bg-emerald-50 text-emerald-800 dark:border-emerald-900 dark:bg-emerald-950/30 dark:text-emerald-200"
                        : "border-zinc-200 bg-white text-zinc-400 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-500"
                    )}
                  >
                    <span className="font-mono font-semibold">{item.label}</span>
                    <div className="mt-0.5 opacity-80">{item.text}</div>
                  </motion.div>
                ))}
              </div>
            </div>

            <div className="rounded-lg border border-zinc-200 bg-zinc-50 p-3 dark:border-zinc-700 dark:bg-zinc-800/70">
              <div className="text-xs font-semibold text-zinc-800 dark:text-zinc-100 mb-1">MessageContent 结构</div>
              <pre className="rounded bg-white p-2 font-mono text-[10px] leading-relaxed text-zinc-600 dark:bg-zinc-900 dark:text-zinc-300">
{`{
  type: "text" | "image" | "file",
  text?: string,
  mediaType?: "image/png",
  data?: string  // base64
}`}
              </pre>
            </div>
          </div>
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
