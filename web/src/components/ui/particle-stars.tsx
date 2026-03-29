"use client";

import { useMemo } from "react";
import { motion, AnimatePresence } from "framer-motion";

interface ParticleStarsProps {
  visible: boolean;
  color?: string;
  count?: number;
}

const ParticleStars = ({ visible, color = "rgba(139, 92, 246, 0.8)", count = 4 }: ParticleStarsProps) => {
  const particles = useMemo(() => {
    return Array.from({ length: count }, (_, i) => ({
      id: i,
      x: 10 + Math.random() * 80,
      y: 10 + Math.random() * 80,
      size: 2 + Math.random() * 2,
      delay: i * 0.3,
      duration: 1.5 + Math.random() * 1,
    }));
  }, [count]);

  return (
    <AnimatePresence>
      {visible && (
        <>
          {particles.map((p) => (
            <motion.span
              key={p.id}
              className="absolute rounded-full pointer-events-none"
              style={{
                left: `${p.x}%`,
                top: `${p.y}%`,
                width: p.size,
                height: p.size,
                backgroundColor: color,
                boxShadow: `0 0 ${p.size * 3}px ${color}, 0 0 ${p.size * 6}px ${color}`,
              }}
              initial={{ scale: 0, opacity: 0 }}
              animate={{
                scale: [0, 1.2, 0.6, 1, 0],
                opacity: [0, 1, 0.6, 0.8, 0],
              }}
              exit={{ scale: 0, opacity: 0 }}
              transition={{
                duration: p.duration,
                delay: p.delay,
                repeat: Infinity,
                repeatDelay: 1,
                ease: "easeInOut",
              }}
            />
          ))}
        </>
      )}
    </AnimatePresence>
  );
};

export default ParticleStars;
