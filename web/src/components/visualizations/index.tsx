"use client";

import { lazy, Suspense } from "react";
import { useTranslations } from "@/lib/i18n";

const visualizations: Record<
  string,
  React.LazyExoticComponent<React.ComponentType<{ title?: string }>>
> = {
  s01: lazy(() => import("./s01-agent-loop")),
  s02: lazy(() => import("./s02-tool-dispatch")),
  s03: lazy(() => import("./s03-todo-write")),
  s04: lazy(() => import("./s04-subagent")),
  s05: lazy(() => import("./s05-skill-loading")),
  s06: lazy(() => import("./s06-context-compact")),
  s07: lazy(() => import("./s07-task-system")),
  s08: lazy(() => import("./s08-background-tasks")),
  s09: lazy(() => import("./s09-permission")),
  s10: lazy(() => import("./s10-hooks")),
  s11: lazy(() => import("./s11-memory")),
  s12: lazy(() => import("./s12-error-recovery")),
  s13: lazy(() => import("./s13-mcp")),
  s14: lazy(() => import("./s14-react")),
  s15: lazy(() => import("./s15-system-prompt")),
  s16: lazy(() => import("./s16-cron-scheduler")),
  s17: lazy(() => import("./s17-agent-teams")),
  s18: lazy(() => import("./s18-team-protocols")),
  s19: lazy(() => import("./s19-autonomous-agents")),
  s20: lazy(() => import("./s20-worktree-task-isolation")),
  s21: lazy(() => import("./s21-comprehensive")),
  s22: lazy(() => import("./s22-multimodal")),
  s23: lazy(() => import("./s23-sandbox")),
};

export function SessionVisualization({ version }: { version: string }) {
  const t = useTranslations("viz");
  const Component = visualizations[version];
  if (!Component) return null;
  return (
    <Suspense
      fallback={
        <div className="min-h-[500px] animate-pulse rounded-lg bg-[#f6efe0]" />
      }
    >
      <div className="min-h-[500px]">
        <Component title={t(version)} />
      </div>
    </Suspense>
  );
}
