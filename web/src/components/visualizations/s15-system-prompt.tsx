"use client";

import { AnimatePresence, motion } from "framer-motion";
import { Boxes, Brain, CheckCircle2, FileText, KeyRound, Library, Rocket, Wrench } from "lucide-react";
import { StepControls } from "@/components/visualizations/shared/step-controls";
import { useSteppedVisualization } from "@/hooks/useSteppedVisualization";
import { cn } from "@/lib/utils";

const STEPS = [
  {
    title: "运行时状态到来",
    desc: "提示词不是固定的一段文字；它从工作区、工具、记忆和技能开始构造。",
    mode: "state",
  },
  {
    title: "段落货架分派归属",
    desc: "每个子系统拥有一个提示段，这样出问题的规则有处可查。",
    mode: "sections",
  },
  {
    title: "上下文键查缓存",
    desc: "相同的运行时状态会生成相同的确定性缓存键。",
    mode: "cache-miss",
  },
  {
    title: "组装提示词",
    desc: "被选中的段落拼成一份 LLM 可读的系统提示。",
    mode: "assemble",
  },
  {
    title: "相同的键复用提示",
    desc: "如果什么都没变，运行时跳过组装，直接复用缓存的提示。",
    mode: "cache-hit",
  },
  {
    title: "LLM 看到拼好的提示",
    desc: "模型收到的是运行时状态的可追溯产物，而不是过时的硬编码字符串。",
    mode: "llm",
  },
] as const;

const SOURCES = [
  { id: "workspace", label: "工作区", value: "/repo", icon: <Boxes size={16} />, tone: "blue" },
  { id: "tools", label: "工具", value: "bash, read_file", icon: <Wrench size={16} />, tone: "emerald" },
  { id: "memory", label: "记忆", value: "已启用", icon: <Brain size={16} />, tone: "amber" },
  { id: "skills", label: "技能", value: "code-review", icon: <Library size={16} />, tone: "violet" },
] as const;

const SECTIONS = [
  { id: "identity", title: "identity", body: "你是一个乐于助人的编码智能体。", owner: "core" },
  { id: "tools", title: "tools", body: "可用工具：bash、read_file。", owner: "tool registry" },
  { id: "workspace", title: "workspace", body: "当前工作区：/repo。", owner: "runtime" },
  { id: "memory", title: "memory + skills", body: "加载记忆索引和 code-review 技能。", owner: "context loader" },
] as const;

type StepMode = (typeof STEPS)[number]["mode"];
type Tone = "blue" | "emerald" | "amber" | "violet" | "zinc";

function toneClass(tone: Tone, active = true) {
  if (!active) return "border-zinc-200 bg-white text-zinc-700 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-200";
  if (tone === "blue") return "border-blue-200 bg-blue-50 text-blue-800 dark:border-blue-900 dark:bg-blue-950/40 dark:text-blue-200";
  if (tone === "emerald") return "border-emerald-200 bg-emerald-50 text-emerald-800 dark:border-emerald-900 dark:bg-emerald-950/40 dark:text-emerald-200";
  if (tone === "amber") return "border-amber-200 bg-amber-50 text-amber-800 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-200";
  if (tone === "violet") return "border-violet-200 bg-violet-50 text-violet-800 dark:border-violet-900 dark:bg-violet-950/40 dark:text-violet-200";
  return "border-zinc-200 bg-zinc-50 text-zinc-700 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-200";
}

function Surface({
  title,
  icon,
  active,
  children,
}: {
  title: string;
  icon: React.ReactNode;
  active: boolean;
  children: React.ReactNode;
}) {
  return (
    <div
      className={cn(
        "min-w-0 rounded-lg border p-4 transition-colors",
        active
          ? "border-blue-300 bg-blue-50 dark:border-blue-900 dark:bg-blue-950/30"
          : "border-zinc-200 bg-white dark:border-zinc-700 dark:bg-zinc-900"
      )}
    >
      <div className="mb-4 flex min-w-0 items-center gap-3 text-lg font-semibold text-zinc-900 dark:text-zinc-100">
        <span
          className={cn(
            "flex h-10 w-10 shrink-0 items-center justify-center rounded-lg",
            active ? "bg-blue-500 text-white" : "bg-zinc-100 text-zinc-500 dark:bg-zinc-800 dark:text-zinc-300"
          )}
        >
          {icon}
        </span>
        <span className="min-w-0 text-wrap">{title}</span>
      </div>
      {children}
    </div>
  );
}

function SourceCard({
  source,
  active,
}: {
  source: (typeof SOURCES)[number];
  active: boolean;
}) {
  return (
    <motion.div layout animate={active ? { y: -1 } : { y: 0 }} className={cn("rounded-lg border p-3", toneClass(source.tone as Tone, active))}>
      <div className="mb-2 flex items-center gap-2 text-sm font-semibold">
        {source.icon}
        {source.label}
      </div>
      <code className="block min-w-0 whitespace-pre-wrap break-words rounded bg-white/70 px-2 py-1 font-mono text-xs dark:bg-zinc-950/30">
        {source.value}
      </code>
    </motion.div>
  );
}

function SectionCard({
  section,
  active,
  assembled,
}: {
  section: (typeof SECTIONS)[number];
  active: boolean;
  assembled: boolean;
}) {
  return (
    <motion.div
      layout
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      className={cn(
        "min-w-0 rounded-lg border p-3",
        active || assembled ? toneClass("emerald") : "border-zinc-200 bg-white dark:border-zinc-700 dark:bg-zinc-900"
      )}
    >
      <div className="mb-1 flex min-w-0 items-center justify-between gap-2">
        <div className="min-w-0 break-words font-mono text-sm font-semibold leading-snug">{section.title}</div>
        {(active || assembled) && <CheckCircle2 size={15} className="shrink-0" />}
      </div>
      <div className="mb-2 text-xs leading-relaxed text-zinc-500 dark:text-zinc-400">归属：{section.owner}</div>
      <div className="text-sm leading-relaxed text-zinc-700 dark:text-zinc-200">{section.body}</div>
    </motion.div>
  );
}

function CachePanel({ mode }: { mode: StepMode }) {
  const isHit = mode === "cache-hit";
  const isActive = mode === "cache-miss" || mode === "cache-hit";

  return (
    <div className={cn("rounded-lg border p-3", toneClass(isHit ? "emerald" : "amber", isActive))}>
      <div className="mb-2 flex items-center gap-2 text-sm font-semibold">
        <KeyRound size={16} />
        上下文键
      </div>
      <code className="block min-w-0 whitespace-pre-wrap break-words rounded bg-white/70 p-2 font-mono text-xs dark:bg-zinc-950/30">
        json.dumps(context, sort_keys=True)
      </code>
      <div className="mt-2 text-sm font-semibold">{isHit ? "命中缓存：复用提示" : isActive ? "未命中：组装段落" : "等待状态"}</div>
    </div>
  );
}

function PromptPreview({ mode }: { mode: StepMode }) {
  const assembled = mode === "assemble" || mode === "cache-hit" || mode === "llm";

  if (!assembled) {
    return (
      <div className="rounded-lg border border-dashed border-zinc-300 px-4 py-8 text-center text-sm text-zinc-500 dark:border-zinc-700 dark:text-zinc-400">
        提示词尚未生成
      </div>
    );
  }

  return (
    <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} className="space-y-2">
      {SECTIONS.map((section) => (
        <div key={section.id} className="rounded-lg border border-zinc-200 bg-white p-3 dark:border-zinc-700 dark:bg-zinc-900">
          <div className="mb-1 font-mono text-xs font-semibold text-blue-700 dark:text-blue-300">[{section.title}]</div>
          <div className="text-sm leading-relaxed text-zinc-700 dark:text-zinc-200">{section.body}</div>
        </div>
      ))}
      <div className={cn("rounded-xl border p-4", toneClass(mode === "llm" ? "blue" : "zinc"))}>
        <div className="mb-2 flex items-center gap-2 text-base font-semibold">
          <Rocket size={17} />
          {mode === "llm" ? "已发送给 LLM" : "系统提示就绪"}
        </div>
        <div className="text-sm leading-relaxed">可追溯的提示文本，由命名的运行时属主拼装而成。</div>
      </div>
    </motion.div>
  );
}

export default function SystemPromptVisualization({ title }: { title?: string }) {
  const vis = useSteppedVisualization({ totalSteps: STEPS.length, autoPlayInterval: 2600 });
  const current = STEPS[vis.currentStep];
  const mode = current.mode;
  const sourceActive = mode === "state" || mode === "sections" || mode === "cache-miss";
  const sectionsActive = mode === "sections" || mode === "assemble";
  const promptActive = mode === "assemble" || mode === "cache-hit" || mode === "llm";

  return (
    <section className="min-h-[500px] space-y-4">
      <h2 className="text-xl font-semibold text-zinc-900 dark:text-zinc-100">{title || "运行时提示组装"}</h2>

      <div className="rounded-lg border border-zinc-200 bg-white p-4 dark:border-zinc-700 dark:bg-zinc-900">
        <div className="grid gap-3 xl:grid-cols-[0.95fr_1.1fr_0.95fr]">
          <Surface title="运行时上下文" icon={<Boxes size={20} />} active={sourceActive}>
            <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-1">
              {SOURCES.map((source) => (
                <SourceCard key={source.id} source={source} active={sourceActive} />
              ))}
            </div>
          </Surface>

          <Surface title="段落货架 + 缓存" icon={<FileText size={20} />} active={sectionsActive || mode === "cache-miss" || mode === "cache-hit"}>
            <div className="space-y-3">
              <div className="grid gap-2 sm:grid-cols-2">
                {SECTIONS.map((section) => (
                  <SectionCard key={section.id} section={section} active={sectionsActive} assembled={promptActive} />
                ))}
              </div>
              <CachePanel mode={mode} />
            </div>
          </Surface>

          <Surface title="系统提示" icon={<Rocket size={20} />} active={promptActive}>
            <AnimatePresence mode="wait">
              <PromptPreview key={mode} mode={mode} />
            </AnimatePresence>
          </Surface>
        </div>

        <div className="mt-3 rounded-lg border border-zinc-200 bg-zinc-50 px-4 py-3 text-sm leading-relaxed text-zinc-600 dark:border-zinc-700 dark:bg-zinc-800/70 dark:text-zinc-300">
          入门要点：系统提示应当由命名的运行时事实拼装而成，并且只有当这些事实未变时才缓存复用。
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
