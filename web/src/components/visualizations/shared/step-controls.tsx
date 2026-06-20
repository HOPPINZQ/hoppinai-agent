"use client";

import { Play, Pause, SkipBack, SkipForward, RotateCcw } from "lucide-react";
import { cn } from "@/lib/utils";

interface StepControlsProps {
  currentStep: number;
  totalSteps: number;
  onPrev: () => void;
  onNext: () => void;
  onReset: () => void;
  isPlaying: boolean;
  onToggleAutoPlay: () => void;
  stepTitle: string;
  stepDescription: string;
  className?: string;
}

export function StepControls({
  currentStep,
  totalSteps,
  onPrev,
  onNext,
  onReset,
  isPlaying,
  onToggleAutoPlay,
  stepTitle,
  stepDescription,
  className,
}: StepControlsProps) {
  return (
    <div className={cn("space-y-3", className)}>
      {/* Annotation */}
      <div className="rounded-lg border border-[#889df0]/40 bg-[#e6eafb] px-4 py-3">
        <div className="mb-1 text-sm font-semibold text-[#889df0]">
          {stepTitle}
        </div>
        <div className="text-sm text-[#889df0]">
          {stepDescription}
        </div>
      </div>

      {/* Controls */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-1">
          <button
            onClick={onReset}
            className="rounded-md p-1.5 text-[#8a7b66] hover:bg-[#f6efe0] hover:text-[#794f27]"
            title="重置"
          >
            <RotateCcw size={16} />
          </button>
          <button
            onClick={onPrev}
            disabled={currentStep === 0}
            className="rounded-md p-1.5 text-[#8a7b66] hover:bg-[#f6efe0] hover:text-[#794f27] disabled:opacity-30"
            title="上一步"
          >
            <SkipBack size={16} />
          </button>
          <button
            onClick={onToggleAutoPlay}
            className="rounded-md p-1.5 text-[#8a7b66] hover:bg-[#f6efe0] hover:text-[#794f27]"
            title={isPlaying ? "暂停" : "自动播放"}
          >
            {isPlaying ? <Pause size={16} /> : <Play size={16} />}
          </button>
          <button
            onClick={onNext}
            disabled={currentStep === totalSteps - 1}
            className="rounded-md p-1.5 text-[#8a7b66] hover:bg-[#f6efe0] hover:text-[#794f27] disabled:opacity-30"
            title="下一步"
          >
            <SkipForward size={16} />
          </button>
        </div>

        {/* Step indicator */}
        <div className="flex items-center gap-2">
          <div className="flex gap-1">
            {Array.from({ length: totalSteps }, (_, i) => (
              <div
                key={i}
                className={cn(
                  "h-1.5 w-1.5 rounded-full transition-colors",
                  i === currentStep
                    ? "bg-[#889df0]"
                    : i < currentStep
                      ? "bg-[#889df0]/60"
                      : "bg-[#e8dcc8]"
                )}
              />
            ))}
          </div>
          <span className="font-mono text-xs text-[#725d42]">
            {currentStep + 1}/{totalSteps}
          </span>
        </div>
      </div>
    </div>
  );
}
