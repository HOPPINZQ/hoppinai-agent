"use client";

import { motion } from "framer-motion";
import { useTranslations } from "@/lib/i18n";
import { Card } from "@/components/ui/card";

interface WhatsNewProps {
  diff: {
    from: string;
    to: string;
    newClasses: string[];
    newFunctions: string[];
    newTools: string[];
    locDelta: number;
  } | null;
}

export function WhatsNew({ diff }: WhatsNewProps) {
  const t = useTranslations("version");
  const td = useTranslations("diff");

  if (!diff) {
    return null;
  }

  const hasContent =
    diff.newClasses.length > 0 ||
    diff.newTools.length > 0 ||
    diff.newFunctions.length > 0 ||
    diff.locDelta !== 0;

  if (!hasContent) {
    return null;
  }

  return (
    <div className="space-y-4"></div>
  );
}
