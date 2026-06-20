"use client";

import { motion } from "framer-motion";
import { cn } from "@/lib/utils";
import type { SimStep } from "@/types/agent-data";
import { User, Bot, Terminal, ArrowRight, AlertCircle } from "lucide-react";

interface SimulatorMessageProps {
  step: SimStep;
  index: number;
}

const TYPE_CONFIG: Record<
  string,
  { icon: typeof User; label: string; bgClass: string; borderClass: string }
> = {
  user_message: {
    icon: User,
    label: "User",
    bgClass: "bg-[#e6eafb]",
    borderClass: "border-[#e8dcc8]",
  },
  assistant_text: {
    icon: Bot,
    label: "Assistant",
    bgClass: "bg-[#fbf7eb]",
    borderClass: "border-[#e8dcc8]",
  },
  tool_call: {
    icon: Terminal,
    label: "Tool Call",
    bgClass: "bg-[#fde6d8]",
    borderClass: "border-[#e8dcc8]",
  },
  tool_result: {
    icon: ArrowRight,
    label: "Tool Result",
    bgClass: "bg-[#e0f0e0]",
    borderClass: "border-[#e8dcc8]",
  },
  system_event: {
    icon: AlertCircle,
    label: "System",
    bgClass: "bg-[#efe2fb]",
    borderClass: "border-[#e8dcc8]",
  },
};

export function SimulatorMessage({ step, index }: SimulatorMessageProps) {
  const config = TYPE_CONFIG[step.type] || TYPE_CONFIG.assistant_text;
  const Icon = config.icon;

  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.25 }}
      className={cn(
        "rounded-lg border p-3",
        config.bgClass,
        config.borderClass
      )}
    >
      <div className="mb-1.5 flex items-center gap-2">
        <Icon size={14} className="shrink-0 text-[var(--color-text-secondary)]" />
        <span className="text-xs font-medium text-[var(--color-text-secondary)]">
          {config.label}
          {step.toolName && (
            <span className="ml-1.5 font-mono text-[var(--color-text)]">
              {step.toolName}
            </span>
          )}
        </span>
      </div>

      {step.type === "tool_call" || step.type === "tool_result" ? (
        <pre className="overflow-x-auto whitespace-pre-wrap rounded bg-[#fbf7eb] p-2.5 font-mono text-xs leading-relaxed text-[#794f27]">
          {step.content || "(empty)"}
        </pre>
      ) : step.type === "system_event" ? (
        <pre className="overflow-x-auto whitespace-pre-wrap rounded bg-[#efe2fb] p-2.5 font-mono text-xs leading-relaxed text-[#b77dee]">
          {step.content}
        </pre>
      ) : (
        <p className="text-sm leading-relaxed">{step.content}</p>
      )}

      <p className="mt-2 text-xs italic text-[var(--color-text-secondary)]">
        {step.annotation}
      </p>
    </motion.div>
  );
}
