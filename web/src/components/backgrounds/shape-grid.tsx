"use client";

import { useEffect, useState, useRef } from "react";
import { motion } from "framer-motion";
import { cn } from "@/lib/utils";

interface ShapeGridProps {
  className?: string;
  dotSize?: number;
  gap?: number;
  color?: string;
  opacity?: number;
  animated?: boolean;
  mouseInteractionEnabled?: boolean;
  mouseRadius?: number;
}

export function ShapeGrid({
  className,
  dotSize = 2,
  gap = 24,
  color = "currentColor",
  opacity = 0.15,
  animated = true,
  mouseInteractionEnabled = true,
  mouseRadius = 150,
}: ShapeGridProps) {
  const [shapes, setShapes] = useState<Array<{ id: number; x: number; y: number; delay: number }>>([]);
  const [dimensions, setDimensions] = useState({ width: 0, height: 0 });
  const [mousePosition, setMousePosition] = useState({ x: -1000, y: -1000 });
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const updateDimensions = () => {
      if (containerRef.current) {
        setDimensions({
          width: containerRef.current.offsetWidth,
          height: containerRef.current.offsetHeight,
        });
      }
    };

    updateDimensions();
    window.addEventListener("resize", updateDimensions);

    const timer = setTimeout(updateDimensions, 100);

    return () => {
      window.removeEventListener("resize", updateDimensions);
      clearTimeout(timer);
    };
  }, []);

  useEffect(() => {
    if (dimensions.width === 0 || dimensions.height === 0) return;

    const cols = Math.ceil(dimensions.width / gap);
    const rows = Math.ceil(dimensions.height / gap);
    const newShapes: Array<{ id: number; x: number; y: number; delay: number }> = [];

    for (let i = 0; i < cols; i++) {
      for (let j = 0; j < rows; j++) {
        newShapes.push({
          id: i * rows + j,
          x: i * gap,
          y: j * gap,
          delay: (i + j) * 0.03,
        });
      }
    }

    setShapes(newShapes);
  }, [dimensions, gap]);

  useEffect(() => {
    if (!mouseInteractionEnabled) return;

    const handleMouseMove = (e: MouseEvent) => {
      if (containerRef.current) {
        const rect = containerRef.current.getBoundingClientRect();
        setMousePosition({
          x: e.clientX - rect.left,
          y: e.clientY - rect.top,
        });
      }
    };

    const handleMouseLeave = () => {
      setMousePosition({ x: -1000, y: -1000 });
    };

    window.addEventListener("mousemove", handleMouseMove);
    window.addEventListener("mouseout", handleMouseLeave);

    return () => {
      window.removeEventListener("mousemove", handleMouseMove);
      window.removeEventListener("mouseout", handleMouseLeave);
    };
  }, [mouseInteractionEnabled]);

  const getDistanceFromMouse = (x: number, y: number) => {
    const dx = x - mousePosition.x;
    const dy = y - mousePosition.y;
    return Math.sqrt(dx * dx + dy * dy);
  };

  const getShapeScale = (x: number, y: number) => {
    if (!mouseInteractionEnabled) return 1;
    const distance = getDistanceFromMouse(x, y);
    if (distance < mouseRadius) {
      return 1 + (1 - distance / mouseRadius) * 1.5;
    }
    return 1;
  };

  const getShapeOpacity = (x: number, y: number) => {
    if (!mouseInteractionEnabled) return opacity;
    const distance = getDistanceFromMouse(x, y);
    if (distance < mouseRadius) {
      return opacity + (1 - distance / mouseRadius) * 0.3;
    }
    return opacity;
  };

  return (
    <div
      ref={containerRef}
      className={cn("fixed inset-0 pointer-events-none -z-10 overflow-hidden", className)}
      style={{ color }}
    >
      <svg
        width={dimensions.width || "100%"}
        height={dimensions.height || "100%"}
        className="w-full h-full"
        preserveAspectRatio="xMidYMid slice"
      >
        {shapes.slice(0, 500).map((shape) => {
          const scale = getShapeScale(shape.x, shape.y);
          const shapeOpacity = getShapeOpacity(shape.x, shape.y);

          return (
            <motion.circle
              key={shape.id}
              cx={shape.x}
              cy={shape.y}
              r={dotSize}
              fill="currentColor"
              initial={animated ? { scale: 0, opacity: 0 } : false}
              animate={
                animated
                  ? {
                      scale: mouseInteractionEnabled ? [0.5, 1, scale, 1] : [0, 1, 0.8, 1],
                      opacity: [0, shapeOpacity, shapeOpacity * 0.7, shapeOpacity],
                    }
                  : { scale: 1, opacity: shapeOpacity }
              }
              transition={
                animated
                  ? {
                      duration: 2.5,
                      delay: shape.delay,
                      repeat: Infinity,
                      repeatDelay: 4,
                      times: [0, 0.3, 0.6, 1],
                      ease: "easeInOut",
                    }
                  : {}
              }
            />
          );
        })}
      </svg>
    </div>
  );
}
