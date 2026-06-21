"use client";

import { AnimatePresence, motion } from "framer-motion";
import { ArrowRight, Box, CheckCircle2, FolderLock, ShieldCheck, TerminalSquare } from "lucide-react";
import { StepControls } from "@/components/visualizations/shared/step-controls";
import { useSteppedVisualization } from "@/hooks/useSteppedVisualization";
import { cn } from "@/lib/utils";

type GateId = "static" | "jail" | "os" | "container";

interface Gate {
  id: GateId;
  label: string;
  desc: string;
  icon: React.ReactNode;
  color: string;
}

const GATES: Gate[] = [
  { id: "static", label: "静态分析", desc: "黑名单 regex + AST 展开 alias", icon: <ShieldCheck size={14} />, color: "border-red-300 bg-red-50 text-red-800 dark:border-red-800 dark:bg-red-950/30 dark:text-red-200" },
  { id: "jail", label: "目录监禁", desc: "ProcessBuilder.directory(worktreePath) + 路径校验", icon: <FolderLock size={14} />, color: "border-orange-300 bg-orange-50 text-orange-800 dark:border-orange-800 dark:bg-orange-950/30 dark:text-orange-200" },
  { id: "os", label: "OS 沙箱", desc: "bwrap --unshare-all --bind /work --die-with-parent", icon: <TerminalSquare size={14} />, color: "border-amber-300 bg-amber-50 text-amber-800 dark:border-amber-800 dark:bg-amber-950/30 dark:text-amber-200" },
  { id: "container", label: "容器隔离", desc: "docker run --network=none --read-only --memory=512m", icon: <Box size={14} />, color: "border-blue-300 bg-blue-50 text-blue-800 dark:border-blue-800 dark:bg-blue-950/30 dark:text-blue-200" },
];

const STEPS = [
  {
    title: "bash 指令先过 L1 静态检查",
    desc: "黑名单 regex 拒绝 rm -rf /、mkfs、curl|sh 等明显恶意命令；bashlex AST 展开 alias 阻止伪装。",
    step: "static",
  },
  {
    title: "L2 目录监禁：只能动自己的 worktree",
    desc: "ProcessBuilder.directory(worktreePath) 夹工作目录，外加路径 normalize 校验拒绝 ../ 越界。结合 s20 的 WorktreeContext。",
    step: "jail",
  },
  {
    title: "L3 OS 级沙箱：新 mount/user/pid 命名空间",
    desc: "bwrap 创建隔离进程：只绑定工作目录为 /work，系统库只读挂载，文件系统不可写，--die-with-parent 防止孤儿进程。",
    step: "os",
  },
  {
    title: "L4 容器完全隔离",
    desc: "docker run --rm --network=none --read-only --memory=512m，网络断开 + 只读 rootfs + 512MB 上限，fork bomb 自然失效。",
    step: "container",
  },
  {
    title: "与 s09 权限 + s10 钩子的协作",
    desc: "LLM 要执行 bash → s09 三道闸（denyList/rules/askUser）→ s10 PreToolUse hook 记录 → SandboxRunner 套 4 层 → s10 PostToolUse hook 截断大输出 → 结果回 messages。",
    step: "all",
  },
  {
    title: "所有层一起点亮",
    desc: "每一层可独立启用；L1+L2 适合开发环境（零配置），L3 适合内部 CI（本地 Linux），L4 适合生产多租户（最安全）。",
    step: "all",
  },
] as const;

export default function SandboxVisualization({ title }: { title?: string }) {
  const vis = useSteppedVisualization({ totalSteps: STEPS.length, autoPlayInterval: 2500 });
  const step = vis.currentStep;
  const current = STEPS[step];

  return (
    <section className="min-h-[500px] space-y-4">
      <h2 className="text-xl font-semibold text-zinc-900 dark:text-zinc-100">
        {title || "bash 四层沙箱防护"}
      </h2>

      <div className="rounded-lg border border-zinc-200 bg-white p-4 dark:border-zinc-700 dark:bg-zinc-900">
        {/* 4-layer wall */}
        <div className="mb-4 rounded-lg border border-zinc-200 bg-zinc-50 p-4 dark:border-zinc-700 dark:bg-zinc-800/70">
          <div className="mb-3 text-sm font-semibold text-zinc-800 dark:text-zinc-100">
            bash 指令 → 穿过四道沙箱门
          </div>
          <div className="flex flex-col gap-2 sm:flex-row sm:items-stretch">
            {GATES.map((gate, index) => {
              const active = current.step === gate.id || current.step === "all";
              const reached =
                GATES.findIndex((g) => g.id === current.step) >= index ||
                current.step === "all";
              return (
                <div key={gate.id} className="flex min-w-0 flex-col items-stretch gap-2 sm:flex-row sm:items-center">
                  <motion.div
                    animate={reached ? { y: [0, -1, 0] } : { y: 0 }}
                    transition={{ duration: 0.6, repeat: active ? Infinity : 0 }}
                    className={cn(
                      "flex-1 rounded-lg border p-3 text-center transition-colors",
                      reached ? gate.color : "border-zinc-200 bg-white text-zinc-400 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-500"
                    )}
                  >
                    <div className="flex items-center justify-center gap-1.5">
                      {reached ? gate.icon : <ShieldCheck size={14} />}
                      <span className="text-xs font-semibold">L{index + 1}</span>
                    </div>
                    <div className="mt-1 text-xs font-bold">{gate.label}</div>
                    <div className="mt-0.5 text-[10px] leading-snug opacity-80">{gate.desc}</div>
                  </motion.div>
                  {index < GATES.length - 1 && (
                    <ArrowRight size={14} className="hidden shrink-0 text-zinc-300 dark:text-zinc-600 sm:block" />
                  )}
                </div>
              );
            })}
          </div>
        </div>

        <div className="grid gap-3 lg:grid-cols-2">
          {/* Command flow on the left */}
          <div className="rounded-lg border border-zinc-200 bg-zinc-50 p-3 dark:border-zinc-700 dark:bg-zinc-800/70">
            <div className="mb-3 text-sm font-semibold text-zinc-800 dark:text-zinc-100">SandboxRunner 执行流</div>
            <div className="space-y-2">
              <AnimatePresence mode="popLayout">
                {([
                  { label: "s09 PermissionChecker", show: current.step !== "static", detail: "denyList → rules → askUser" },
                  { label: "s10 PreToolUse Hook", show: current.step !== "static", detail: "LogHook 记录命令 + 参数" },
                  { label: "SandboxRunner.run()", show: true, detail: "L1 regex → L2 jail → L3 bwrap → L4 docker" },
                  { label: "cgroup 资源限制", show: current.step === "os" || current.step === "container" || current.step === "all", detail: "MemoryMax=512M TasksMax=64 CPUQuota=50%" },
                  { label: "超时 + 销毁", show: true, detail: "waitFor(30s, TimeUnit.SECONDS) → destroyForcibly()" },
                  { label: "s10 PostToolUse Hook", show: current.step === "all", detail: "LargeOutputHook 截断过长输出" },
                ].map((item, i) => (
                  <motion.div
                    key={item.label}
                    initial={{ opacity: 0, x: 6 }}
                    animate={{ opacity: item.show ? 1 : 0.3, x: 0 }}
                    transition={{ duration: 0.2, delay: i * 0.04 }}
                    className={cn(
                      "rounded-md border px-3 py-2 text-xs",
                      item.show
                        ? "border-emerald-200 bg-emerald-50 text-emerald-800 dark:border-emerald-900 dark:bg-emerald-950/30 dark:text-emerald-200"
                        : "border-zinc-200 bg-white text-zinc-400 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-500"
                    )}
                  >
                    <span className="font-mono font-semibold">{item.label}</span>
                    <div className="mt-0.5 opacity-80">{item.detail}</div>
                  </motion.div>
                )))}
              </AnimatePresence>
            </div>
          </div>

          {/* Code sample on the right */}
          <div className="rounded-lg border border-zinc-200 bg-zinc-50 p-3 dark:border-zinc-700 dark:bg-zinc-800/70">
            <div className="mb-3 flex items-center gap-2 text-sm font-semibold text-zinc-800 dark:text-zinc-100">
              <CheckCircle2 size={15} />
              安全链全貌
            </div>
            <div className="space-y-2 font-mono text-[10px] leading-relaxed">
              <div className="rounded bg-white p-2 text-zinc-600 dark:bg-zinc-900 dark:text-zinc-300">
                <span className="text-zinc-400">// 命令进入</span><br />
                String cmd = <span className="text-amber-600 dark:text-amber-400">"npm test"</span>;
              </div>
              <div className="rounded bg-white p-2 text-zinc-600 dark:bg-zinc-900 dark:text-zinc-300">
                <span className="text-zinc-400">// L1 静态检查</span><br />
                <span className="text-red-600 dark:text-red-400">if</span> (denyPattern.matcher(cmd).matches()) {"{"}<br />
                &nbsp;&nbsp;<span className="text-red-600 dark:text-red-400">return</span> <span className="text-amber-600 dark:text-amber-400">"命令被黑名单拒绝"</span>;<br />
                {"}"}
              </div>
              <div className="rounded bg-white p-2 text-zinc-600 dark:bg-zinc-900 dark:text-zinc-300">
                <span className="text-zinc-400">// L2 目录监禁</span><br />
                Path cwd = WorktreeContext.effectiveRoot();<br />
                Path resolved = cwd.resolve(path).normalize();<br />
                <span className="text-red-600 dark:text-red-400">if</span> (!resolved.startsWith(cwd)) <span className="text-red-600 dark:text-red-400">throw</span>;
              </div>
              <div className="rounded bg-white p-2 text-zinc-600 dark:bg-zinc-900 dark:text-zinc-300">
                <span className="text-zinc-400">// L3 OS 沙箱 (Linux)</span><br />
                bwrap --unshare-all \<br />
                &nbsp;&nbsp;--bind {'{cwd}'} /work \<br />
                &nbsp;&nbsp;--die-with-parent \<br />
                &nbsp;&nbsp;bash -c <span className="text-amber-600 dark:text-amber-400">"{'{cmd}'}"</span>
              </div>
              <div className="rounded bg-white p-2 text-zinc-600 dark:bg-zinc-900 dark:text-zinc-300">
                <span className="text-zinc-400">// L4 容器隔离 (生产)</span><br />
                docker run --rm \<br />
                &nbsp;&nbsp;--network=<span className="text-amber-600 dark:text-amber-400">none</span> \<br />
                &nbsp;&nbsp;--read-only \<br />
                &nbsp;&nbsp;--memory=<span className="text-amber-600 dark:text-amber-400">512m</span>
              </div>
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
