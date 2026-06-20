import { Card as LibraryCard } from "animal-island-ui";
import type { CardProps as LibraryCardProps } from "animal-island-ui";
import { cn } from "@/lib/utils";

/**
 * Thin wrapper around animal-island-ui Card so existing call sites
 * (`<Card className="...">`) keep working. Library Card already ships the
 * parchment background, 20px radius, brown body text, and a -2px hover float.
 *
 * Pass `color` or `pattern` directly via the spread props to use the
 * NookPhone palette or polka-dot wall-paper variants.
 */
type CardProps = LibraryCardProps & {
  className?: string;
};

export function Card({ className, children, ...props }: CardProps) {
  return (
    <LibraryCard className={className} {...props}>
      {children}
    </LibraryCard>
  );
}

export function CardHeader({
  className,
  children,
  ...props
}: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div className={cn("mb-4", className)} {...props}>
      {children}
    </div>
  );
}

export function CardTitle({
  className,
  children,
  ...props
}: React.HTMLAttributes<HTMLHeadingElement>) {
  return (
    <h3
      className={cn("text-lg font-bold text-[#794f27]", className)}
      {...props}
    >
      {children}
    </h3>
  );
}
