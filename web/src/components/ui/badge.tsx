import { cn } from "@/lib/utils";
import { getLayerColor } from "@/lib/constants";

interface BadgeProps {
  layer: "tools" | "planning" | "memory" | "concurrency" | "collaboration";
  children: React.ReactNode;
  className?: string;
}

/**
 * Pill badge filled with the layer's NookPhone color. Single source of
 * truth for layer color is LAYERS[].color in constants.ts.
 */
export function LayerBadge({ layer, children, className }: BadgeProps) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold whitespace-nowrap",
        className
      )}
      style={{
        backgroundColor: getLayerColor(layer),
        color: "#ffffff",
      }}
    >
      {children}
    </span>
  );
}

export function NewBadge({
  children,
  className,
}: {
  children?: React.ReactNode;
  className?: string;
}) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider whitespace-nowrap",
        className
      )}
      style={{
        backgroundColor: "#fc736d",
        color: "#ffffff",
      }}
    >
      {children || "New"}
    </span>
  );
}
