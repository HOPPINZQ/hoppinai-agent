"use client";

import { useRef, useMemo, createContext, useEffect } from "react";
import { motion, useMotionValue, useTransform } from "framer-motion";
import { useBentoMouse, useIsMobile } from "@/hooks/use-bento-mouse";
import { cn } from "@/lib/utils";
import type { MotionValue } from "framer-motion";

interface BentoGridContextType {
  mouseX: MotionValue<number>;
  mouseY: MotionValue<number>;
  isHovered: MotionValue<number>;
}

export const BentoGridContext = createContext<BentoGridContextType | null>(null);

interface BentoGridProps {
  children: React.ReactNode;
  className?: string;
  spotlightColor?: string;
  spotlightRadius?: number;
}

export default function BentoGrid({
  children,
  className,
  spotlightColor = "rgba(139, 92, 246, 0.06)",
  spotlightRadius = 500,
}: BentoGridProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const { mouseX, mouseY, isHovered } = useBentoMouse(containerRef);
  const isMobile = useIsMobile();

  const ctx = useMemo(
    () => ({ mouseX, mouseY, isHovered }),
    [mouseX, mouseY, isHovered]
  );

  // Use CSS variable for mouseY, derive spotlight from mouseX only
  const spotlightBg = useTransform(mouseX, (mx: number) => {
    if (mx < -500) return "none";
    return `radial-gradient(circle ${spotlightRadius}px at ${mx}px var(--grid-my, -1000px), ${spotlightColor}, transparent 40%)`;
  });

  // Update CSS variable for mouseY
  useEffect(() => {
    const unsubscribe = mouseY.on("change", (v) => {
      if (containerRef.current) {
        containerRef.current.style.setProperty("--grid-my", `${v}px`);
      }
    });
    return unsubscribe;
  }, [mouseY]);

  // Only show spotlight when hovered
  const spotlightOpacity = useTransform(isHovered, (v: number) => v);

  return (
    <BentoGridContext.Provider value={ctx}>
      <div ref={containerRef} className={cn("relative", className)}>
        {/* Section-level spotlight */}
        {!isMobile && (
          <motion.div
            className="absolute inset-0 pointer-events-none z-0 rounded-2xl"
            style={{
              background: spotlightBg,
              opacity: spotlightOpacity,
            }}
          />
        )}
        <div className="relative z-[1]">{children}</div>
      </div>
    </BentoGridContext.Provider>
  );
}
