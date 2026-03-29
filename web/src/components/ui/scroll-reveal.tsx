"use client";

import { useRef, useEffect } from "react";
import gsap from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";
import { cn } from "@/lib/utils";

gsap.registerPlugin(ScrollTrigger);

interface ScrollRevealProps {
  children: React.ReactNode;
  className?: string;
  /** Animation type */
  animation?: "fade-up" | "fade-left" | "fade-right" | "fade-scale" | "blur";
  /** Duration in seconds */
  duration?: number;
  /** GSAP ease */
  ease?: string;
  /** ScrollTrigger start position */
  scrollStart?: string;
  /** ScrollTrigger end position */
  scrollEnd?: string;
  /** Stagger delay between children (in seconds). 0 = no stagger, only animate as one block */
  stagger?: number;
  /** If true, direct children will animate individually with stagger */
  staggerChildren?: boolean;
  /** Delay before starting (seconds) */
  delay?: number;
}

export default function ScrollReveal({
  children,
  className,
  animation = "fade-up",
  duration = 0.8,
  ease = "power3.out",
  scrollStart = "top 88%",
  scrollEnd = "bottom 20%",
  stagger = 0.1,
  staggerChildren = false,
  delay = 0,
}: ScrollRevealProps) {
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;

    const targets = staggerChildren ? el.children : [el];
    const fromVars: gsap.TweenVars = {};
    const toVars: gsap.TweenVars = {
      duration,
      ease,
      delay,
      opacity: 1,
      x: 0,
      y: 0,
      scale: 1,
      filter: "blur(0px)",
    };

    switch (animation) {
      case "fade-up":
        fromVars.opacity = 0;
        fromVars.y = 50;
        fromVars.x = 0;
        break;
      case "fade-left":
        fromVars.opacity = 0;
        fromVars.x = -60;
        fromVars.y = 0;
        break;
      case "fade-right":
        fromVars.opacity = 0;
        fromVars.x = 60;
        fromVars.y = 0;
        break;
      case "fade-scale":
        fromVars.opacity = 0;
        fromVars.scale = 0.9;
        fromVars.y = 30;
        break;
      case "blur":
        fromVars.opacity = 0;
        fromVars.filter = "blur(12px)";
        fromVars.y = 20;
        break;
    }

    const ctx = gsap.context(() => {
      gsap.fromTo(targets, fromVars, {
        ...toVars,
        stagger: staggerChildren ? stagger : 0,
        scrollTrigger: {
          trigger: el,
          start: scrollStart,
          end: scrollEnd,
          toggleActions: "play none none none",
        },
      });
    }, el);

    return () => ctx.revert();
  }, [animation, duration, ease, scrollStart, scrollEnd, stagger, staggerChildren, delay]);

  return (
    <div ref={containerRef} className={cn("will-change-transform", className)}>
      {children}
    </div>
  );
}
