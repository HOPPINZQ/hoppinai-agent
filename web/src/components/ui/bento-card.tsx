"use client";

import {
  useRef,
  useState,
  useCallback,
  useEffect,
} from "react";
import {
  motion,
  useMotionValue,
  useTransform,
  useSpring,
  AnimatePresence,
} from "framer-motion";
import { cn } from "@/lib/utils";
import { useIsMobile } from "@/hooks/use-bento-mouse";
import ParticleStars from "./particle-stars";

interface Ripple {
  id: number;
  x: number;
  y: number;
}

interface BentoCardProps {
  children: React.ReactNode;
  className?: string;
  glowColor?: string;
  enableTilt?: boolean;
  enableGlow?: boolean;
  enableParticles?: boolean;
  enableRipple?: boolean;
  spotlightColor?: string;
}

export default function BentoCard({
  children,
  className,
  glowColor = "139, 92, 246",
  enableTilt = true,
  enableGlow = true,
  enableParticles = true,
  enableRipple = true,
  spotlightColor,
}: BentoCardProps) {
  const cardRef = useRef<HTMLDivElement>(null);
  const isMobile = useIsMobile();
  const [ripples, setRipples] = useState<Ripple[]>([]);
  const [isCardHovered, setIsCardHovered] = useState(false);
  const rippleId = useRef(0);

  // Card-local mouse position
  const cardMouseX = useMotionValue(-1000);
  const cardMouseY = useMotionValue(-1000);

  // Track mouse relative to this card
  useEffect(() => {
    if (isMobile) return;
    const card = cardRef.current;
    if (!card) return;

    const onMove = (e: MouseEvent) => {
      const rect = card.getBoundingClientRect();
      cardMouseX.set(e.clientX - rect.left);
      cardMouseY.set(e.clientY - rect.top);
    };
    const onEnter = () => setIsCardHovered(true);
    const onLeave = () => {
      setIsCardHovered(false);
      cardMouseX.set(-1000);
      cardMouseY.set(-1000);
    };

    card.addEventListener("mousemove", onMove);
    card.addEventListener("mouseenter", onEnter);
    card.addEventListener("mouseleave", onLeave);
    return () => {
      card.removeEventListener("mousemove", onMove);
      card.removeEventListener("mouseenter", onEnter);
      card.removeEventListener("mouseleave", onLeave);
    };
  }, [isMobile, cardMouseX, cardMouseY]);

  // Tilt
  const tiltFactor = enableTilt && !isMobile ? 6 : 0;
  const rawRotateX = useTransform(cardMouseY, (v: number) => {
    if (!cardRef.current || v < -500) return 0;
    const h = cardRef.current.offsetHeight;
    return tiltFactor * (0.5 - v / h);
  });
  const rawRotateY = useTransform(cardMouseX, (v: number) => {
    if (!cardRef.current || v < -500) return 0;
    const w = cardRef.current.offsetWidth;
    return tiltFactor * (v / w - 0.5);
  });
  const rotateX = useSpring(rawRotateX, { stiffness: 200, damping: 25 });
  const rotateY = useSpring(rawRotateY, { stiffness: 200, damping: 25 });

  // Magnetism
  const magnetFactor = !isMobile ? 3 : 0;
  const magnetX = useSpring(
    useTransform(cardMouseX, (v: number) => {
      if (!cardRef.current || v < -500) return 0;
      const w = cardRef.current.offsetWidth;
      return magnetFactor * (v / w - 0.5);
    }),
    { stiffness: 150, damping: 20 }
  );
  const magnetY = useSpring(
    useTransform(cardMouseY, (v: number) => {
      if (!cardRef.current || v < -500) return 0;
      const h = cardRef.current.offsetHeight;
      return magnetFactor * (0.5 - v / h);
    }),
    { stiffness: 150, damping: 20 }
  );

  // Background gradient strings derived from individual transforms
  const sColor = spotlightColor ?? `rgba(${glowColor}, 0.08)`;

  const spotlightBg = useTransform(cardMouseX, (mx: number) => {
    if (mx < -500) return "none";
    return `radial-gradient(circle 250px at ${mx}px var(--bento-my, -1000px), ${sColor}, transparent 50%)`;
  });

  const borderGlowBg = useTransform(cardMouseX, (mx: number) => {
    if (mx < -500) return "none";
    return `radial-gradient(circle 180px at ${mx}px var(--bento-my, -1000px), rgba(${glowColor}, 0.5), rgba(${glowColor}, 0.15) 40%, transparent 60%)`;
  });

  // Update CSS variable for mouseY so we don't need useTransform with arrays
  useEffect(() => {
    const unsubscribe = cardMouseY.on("change", (v) => {
      if (cardRef.current) {
        cardRef.current.style.setProperty("--bento-my", `${v}px`);
      }
    });
    return unsubscribe;
  }, [cardMouseY]);

  // Click ripple
  const handleClick = useCallback(
    (e: React.MouseEvent) => {
      if (!enableRipple || isMobile) return;
      const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
      const id = rippleId.current++;
      setRipples((prev) => [...prev, { id, x: e.clientX - rect.left, y: e.clientY - rect.top }]);
      setTimeout(() => {
        setRipples((prev) => prev.filter((r) => r.id !== id));
      }, 700);
    },
    [enableRipple, isMobile]
  );

  // Mobile: render a simple static card
  if (isMobile) {
    return (
      <div
        className={cn(
          "relative overflow-hidden rounded-2xl border border-white/[0.06] bg-white/[0.03] backdrop-blur-sm",
          className
        )}
      >
        {children}
      </div>
    );
  }

  return (
    <motion.div
      ref={cardRef}
      className={cn(
        "relative overflow-hidden rounded-2xl cursor-pointer",
        "border border-white/[0.06] bg-white/[0.03] backdrop-blur-sm",
        "transition-colors duration-300 hover:border-white/[0.1]",
        className
      )}
      style={{
        rotateX: enableTilt ? rotateX : 0,
        rotateY: enableTilt ? rotateY : 0,
        transformPerspective: 800,
        x: magnetX,
        y: magnetY,
        willChange: "transform",
      }}
      onClick={handleClick}
    >
      {/* Inner spotlight following mouse */}
      {enableGlow && (
        <motion.div
          className="absolute inset-0 pointer-events-none z-[1] rounded-2xl"
          style={{ background: spotlightBg }}
        />
      )}

      {/* Border glow */}
      {enableGlow && (
        <motion.div
          className="absolute inset-0 pointer-events-none z-[2] rounded-2xl"
          style={{
            background: borderGlowBg,
            mask: "linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0)",
            WebkitMask: "linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0)",
            maskComposite: "exclude",
            WebkitMaskComposite: "xor",
            padding: "1px",
          }}
        />
      )}

      {/* Content */}
      <div className="relative z-[3]">{children}</div>

      {/* Particle stars */}
      {enableParticles && (
        <div className="absolute inset-0 z-[4] pointer-events-none overflow-hidden rounded-2xl">
          <ParticleStars visible={isCardHovered} color={`rgba(${glowColor}, 0.7)`} count={3} />
        </div>
      )}

      {/* Click ripples */}
      <AnimatePresence>
        {ripples.map((ripple) => (
          <motion.span
            key={ripple.id}
            className="absolute pointer-events-none z-[5] rounded-full"
            style={{
              left: ripple.x,
              top: ripple.y,
              width: 100,
              height: 100,
              marginLeft: -50,
              marginTop: -50,
              background: `radial-gradient(circle, rgba(${glowColor}, 0.3), transparent 70%)`,
            }}
            initial={{ scale: 0, opacity: 0.5 }}
            animate={{ scale: 3, opacity: 0 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.6, ease: "easeOut" }}
          />
        ))}
      </AnimatePresence>
    </motion.div>
  );
}
