import { Button as LibraryButton } from "animal-island-ui";
import type { ButtonProps as LibraryButtonProps } from "animal-island-ui";

/**
 * Re-export of animal-island-ui Button. RSC-safe (no hooks, no DOM-only
 * APIs) so it can be imported from server components.
 *
 * The library Button supports 5 types × 3 sizes; primary/danger-primary get
 * the 3D pixel shadow. See AI_USAGE.md in the package for the full prop
 * reference.
 */
export type { LibraryButtonProps };
export const Button = LibraryButton;
