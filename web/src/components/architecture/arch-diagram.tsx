"use client";

import { motion } from "framer-motion";
import { cn } from "@/lib/utils";
import { LAYERS } from "@/lib/constants";
import versionsData from "@/data/generated/versions.json";

const CLASS_DESCRIPTIONS: Record<string, string> = {
  TodoManager: "todo计划构造和管理器",
  SkillLoader: "动态加载并注入SKILL.md到上下文",
  ContextManager: "三层上下文压缩管理",
  Task: "File-based persistent task with dependencies",
  TaskManager: "File-based persistent task CRUD with dependencies",
  BackgroundTask: "Single background execution unit",
  BackgroundManager: "Non-blocking thread execution + notification queue",
  TeammateManager: "Multi-agent team lifecycle and coordination",
  Teammate: "Individual agent identity and state tracking",
  SharedBoard: "Cross-agent shared state coordination",
};

interface ArchDiagramProps {
  version: string;
}

function getLayerColor(versionId: string): string {
  const layer = LAYERS.find((l) => (l.versions as readonly string[]).includes(versionId));
  return layer?.color ?? "#71717a";
}

function getLayerColorClasses(versionId: string): {
  border: string;
  bg: string;
} {
  const v =
    versionsData.versions.find((v) => v.id === versionId) as { layer?: string } | undefined;
  const layer = v?.layer;
  switch (layer) {
    case "tools":
      return {
        border: "border-[#889df0]",
        bg: "bg-[#e6eafb]",
      };
    case "planning":
      return {
        border: "border-[#82d5bb]",
        bg: "bg-[#e0f0e0]",
      };
    case "memory":
      return {
        border: "border-[#b77dee]",
        bg: "bg-[#efe2fb]",
      };
    case "concurrency":
      return {
        border: "border-[#e59266]",
        bg: "bg-[#fde6d8]",
      };
    case "collaboration":
      return {
        border: "border-[#fc736d]",
        bg: "bg-[#fde2e0]",
      };
    default:
      return {
        border: "border-[#9f927d]",
        bg: "bg-[#f6efe0]",
      };
  }
}

function collectClassesUpTo(
  targetId: string
): { name: string; introducedIn: string }[] {
  const { versions, diffs } = versionsData;
  const order = versions.map((v) => v.id);
  const targetIdx = order.indexOf(targetId);
  if (targetIdx < 0) return [];

  const result: { name: string; introducedIn: string }[] = [];
  const seen = new Set<string>();

  for (let i = 0; i <= targetIdx; i++) {
    const v = versions[i];
    if (!v.classes) continue;
    for (const cls of v.classes) {
      if (!seen.has(cls.name)) {
        seen.add(cls.name);
        result.push({ name: cls.name, introducedIn: v.id });
      }
    }
  }

  return result;
}

function getNewClassNames(version: string): Set<string> {
  const diff = versionsData.diffs.find((d) => d.to === version);
  if (!diff) {
    const v = versionsData.versions.find((ver) => ver.id === version);
    return new Set(v?.classes?.map((c) => c.name) ?? []);
  }
  return new Set(diff.newClasses ?? []);
}

export function ArchDiagram({ version }: ArchDiagramProps) {
  const allClasses = collectClassesUpTo(version);
  const newClassNames = getNewClassNames(version);
  const versionData = versionsData.versions.find((v) => v.id === version);
  const tools = versionData?.tools ?? [];

  const reversed = [...allClasses].reverse();

  return (
    <div className="space-y-3">
      {reversed.map((cls, i) => {
        const isNew = newClassNames.has(cls.name);
        const colorClasses = getLayerColorClasses(cls.introducedIn);

        return (
          <div key={cls.name}>
            {i > 0 && (
              <div className="flex justify-center py-1">
                <motion.svg
                  width="24"
                  height="20"
                  viewBox="0 0 24 20"
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  transition={{ delay: i * 0.08 + 0.05 }}
                >
                  <motion.line
                    x1={12}
                    y1={0}
                    x2={12}
                    y2={14}
                    stroke="var(--color-text-secondary)"
                    strokeWidth={1.5}
                    initial={{ pathLength: 0 }}
                    animate={{ pathLength: 1 }}
                    transition={{ duration: 0.3, delay: i * 0.08 }}
                  />
                  <motion.polygon
                    points="7,12 12,19 17,12"
                    fill="var(--color-text-secondary)"
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    transition={{ delay: i * 0.08 + 0.2 }}
                  />
                </motion.svg>
              </div>
            )}
            <motion.div
            key={cls.name}
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: i * 0.08, duration: 0.3 }}
            className={cn(
              "rounded-lg border-2 px-4 py-3 transition-colors",
              isNew
                ? cn(colorClasses.border, colorClasses.bg)
                : "border-[#e8dcc8] bg-[#fbf7eb]"
            )}
          >
            <div className="flex items-center justify-between">
              <div>
                <span
                  className={cn(
                    "font-mono text-sm font-semibold",
                    isNew
                      ? "text-[#5a4a30]"
                      : "text-[#725d42]"
                  )}
                >
                  {cls.name}
                </span>
                <p
                  className={cn(
                    "mt-0.5 text-xs",
                    isNew
                      ? "text-[#9f927d]"
                      : "text-[#725d42]"
                  )}
                >
                  {CLASS_DESCRIPTIONS[cls.name] || ""}
                </p>
              </div>
              <div className="flex items-center gap-2">
                <span className="text-xs text-[#725d42]">
                  {cls.introducedIn}
                </span>
                {isNew && (
                  <span className="rounded-full bg-[#5a4a30] px-2 py-0.5 text-[10px] font-bold uppercase text-[#fbf7eb]">
                    NEW
                  </span>
                )}
              </div>
            </div>
          </motion.div>
          </div>
        );
      })}

      {allClasses.length === 0 && (
        <div className="rounded-lg border border-dashed border-[#e8dcc8] px-4 py-6 text-center text-sm text-[#725d42]">
          该版本中没有类（仅包含函数）
        </div>
      )}

      {tools.length > 0 && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: reversed.length * 0.08 + 0.1 }}
          className="flex flex-wrap gap-1.5 pt-2"
        >
          {tools.map((tool) => (
            <span
              key={tool}
              className="rounded-md bg-[#f6efe0] px-2 py-1 font-mono text-xs text-[#9f927d]"
            >
              {tool}
            </span>
          ))}
        </motion.div>
      )}
    </div>
  );
}
