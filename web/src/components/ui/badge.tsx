import { cn } from "@/lib/utils";

const LAYER_COLORS = {
  tools:
    "bg-blue-500/15 text-blue-300 border border-blue-500/20",
  planning:
    "bg-violet-500/15 text-violet-300 border border-violet-500/20",
  memory:
    "bg-purple-500/15 text-purple-300 border border-purple-500/20",
  concurrency:
    "bg-amber-500/15 text-amber-300 border border-amber-500/20",
  collaboration:
    "bg-rose-500/15 text-rose-300 border border-rose-500/20",
} as const;

interface BadgeProps {
  layer: keyof typeof LAYER_COLORS;
  children: React.ReactNode;
  className?: string;
}

export function LayerBadge({ layer, children, className }: BadgeProps) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-md px-2 py-0.5 text-xs font-medium",
        LAYER_COLORS[layer],
        className
      )}
    >
      {children}
    </span>
  );
}

export function NewBadge({ children, className }: { children?: React.ReactNode; className?: string }) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium",
        "bg-gradient-to-r from-pink-500 to-rose-500 text-white",
        "shadow-sm shadow-pink-500/20",
        className
      )}
    >
      {children || "New"}
    </span>
  );
}
