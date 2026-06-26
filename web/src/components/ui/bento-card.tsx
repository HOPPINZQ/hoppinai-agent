import { Card } from "animal-island-ui";
import type { CardColor } from "animal-island-ui";
import { cn } from "@/lib/utils";

interface BentoCardProps {
  children: React.ReactNode;
  className?: string;
  /** NookPhone color name — fills the card body. */
  color?: CardColor;
  /** NookPhone pattern name — overlays a polka-dot wall-paper. */
  pattern?: CardColor;
}

/**
 * Cozy replacement for the old tilt/spotlight/ripple/particle BentoCard.
 * Delegates to animal-island-ui Card — same warm parchment, 20px radius,
 * and -2px hover float. All dark/glow/animation props are gone.
 *
 * This is the template for every other restyled primitive in src/components.
 */
export default function BentoCard({
  children,
  className,
  color,
  pattern,
}: BentoCardProps) {
  return (
    <Card
      color={color}
      pattern={pattern}
      className={cn("transition-transform duration-300", className)}
    >
      {children}
    </Card>
  );
}
