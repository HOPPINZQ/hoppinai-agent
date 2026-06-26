"use client";

import { useRef, useEffect, useState } from "react";
import { motion, useInView, type Easing } from "framer-motion";
import { cn } from "@/lib/utils";

interface ScrollRevealProps {
  children: React.ReactNode;
  className?: string;
  /** Animation type */
  animation?: "fade-up" | "fade-left" | "fade-right" | "fade-scale" | "blur";
  /** Duration in seconds */
  duration?: number;
  /** Framer-motion ease (cubic-bezier array or named ease) */
  ease?: Easing;
  /** Stagger delay between children (in seconds). 0 = no stagger */
  stagger?: number;
  /** If true, direct children animate individually with stagger */
  staggerChildren?: boolean;
  /** Delay before starting (seconds) */
  delay?: number;
}

const ANIMATIONS: Record<
  NonNullable<ScrollRevealProps["animation"]>,
  { from: Record<string, number | string>; to: Record<string, number | string> }
> = {
  "fade-up": { from: { opacity: 0, y: 30 }, to: { opacity: 1, y: 0 } },
  "fade-left": { from: { opacity: 0, x: -50 }, to: { opacity: 1, x: 0 } },
  "fade-right": { from: { opacity: 0, x: 50 }, to: { opacity: 1, x: 0 } },
  "fade-scale": { from: { opacity: 0, scale: 0.95, y: 20 }, to: { opacity: 1, scale: 1, y: 0 } },
  "blur": { from: { opacity: 0, filter: "blur(10px)", y: 10 }, to: { opacity: 1, filter: "blur(0px)", y: 0 } },
};

/**
 * Slim framer-motion replacement for the old gsap-powered ScrollReveal.
 * Preserves the same prop surface (animation / duration / stagger /
 * staggerChildren / delay) so call sites don't need to change.
 *
 * Uses `useInView` to play once when the container enters the viewport.
 * When `staggerChildren`, each direct child is wrapped in its own motion.div
 * with an incremental delay.
 */
export default function ScrollReveal({
  children,
  className,
  animation = "fade-up",
  duration = 0.6,
  ease = [0.4, 0, 0.2, 1],
  stagger = 0.1,
  staggerChildren = false,
  delay = 0,
}: ScrollRevealProps) {
  const ref = useRef<HTMLDivElement>(null);
  const inView = useInView(ref, { once: true, amount: 0.2 });
  const [mounted, setMounted] = useState(false);
  useEffect(() => setMounted(true), []);

  const variant = ANIMATIONS[animation];
  const easeProp = ease;

  if (!staggerChildren) {
    return (
      <motion.div
        ref={ref}
        className={cn("will-change-transform", className)}
        initial={variant.from}
        animate={inView ? { ...variant.to, transition: { duration, ease: easeProp, delay } } : variant.from}
      >
        {children}
      </motion.div>
    );
  }

  // staggerChildren: wrap each direct child in its own motion element
  const childArray = Array.isArray(children) ? children : [children];
  return (
    <motion.div
      ref={ref}
      className={cn("will-change-transform", className)}
      initial="hidden"
      animate={inView ? "show" : "hidden"}
      variants={{
        hidden: {},
        show: { transition: { staggerChildren: stagger, delayChildren: delay } },
      }}
    >
      {mounted &&
        childArray.map((child, i) => (
          <motion.div
            key={i}
            variants={{
              hidden: variant.from,
              show: { ...variant.to, transition: { duration, ease: easeProp } },
            }}
          >
            {child}
          </motion.div>
        ))}
    </motion.div>
  );
}
