"use client";

import { useMemo } from "react";
import Link from "next/link";
import { useLocale } from "@/lib/i18n";
import { VERSION_META } from "@/lib/constants";
import { Card, CardHeader, CardTitle } from "@/components/ui/card";
import { LayerBadge } from "@/components/ui/badge";
import { CodeDiff } from "@/components/diff/code-diff";
import {
  ArrowLeft,
  Plus,
  Minus,
  FileCode,
  Wrench,
  Box,
  FunctionSquare,
} from "lucide-react";
import type { VersionIndex } from "@/types/agent-data";
import versionData from "@/data/generated/versions.json";

const data = versionData as VersionIndex;

interface DiffPageContentProps {
  version: string;
}

export function DiffPageContent({ version }: DiffPageContentProps) {
  const locale = useLocale();
  const meta = VERSION_META[version];

  const { currentVersion, prevVersion, diff } = useMemo(() => {
    const current = data.versions.find((v) => v.id === version);
    const prevId = meta?.prevVersion;
    const prev = prevId ? data.versions.find((v) => v.id === prevId) : null;
    const d = data.diffs.find((d) => d.to === version);
    return { currentVersion: current, prevVersion: prev, diff: d };
  }, [version, meta]);

  if (!meta || !currentVersion) {
    return (
      <div className="py-12 text-center">
        <p className="text-[#8a7b66]">Version not found.</p>
        <Link
          href={`/${locale}/timeline`}
          className="mt-4 inline-block text-sm font-semibold text-[#11a89b] underline decoration-[#82d5bb] underline-offset-2"
        >
          Back to timeline
        </Link>
      </div>
    );
  }

  if (!prevVersion || !diff) {
    return (
      <div className="py-12">
        <Link
          href={`/${locale}/${version}`}
          className="mb-6 inline-flex items-center gap-1 text-sm font-semibold text-[#8a7b66] hover:text-[#794f27]"
        >
          <ArrowLeft size={14} />
          Back to {meta.title}
        </Link>
        <h1 className="text-3xl font-extrabold text-[#794f27]">
          {meta.title}
        </h1>
        <p className="mt-4 text-[#8a7b66]">
          This is the first version -- there is no previous version to compare
          against.
        </p>
      </div>
    );
  }

  const prevMeta = VERSION_META[prevVersion.id];

  return (
    <div className="py-4">
      <Link
        href={`/${locale}/${version}`}
        className="mb-6 inline-flex items-center gap-1 text-sm font-semibold text-[#8a7b66] hover:text-[#794f27]"
      >
        <ArrowLeft size={14} />
        Back to {meta.title}
      </Link>

      {/* Header */}
      <div className="mb-8">
        <h1 className="text-3xl font-extrabold text-[#794f27]">
          {prevMeta?.title || prevVersion.id} → {meta.title}
        </h1>
        <p className="mt-2 text-[#8a7b66]">
          {prevVersion.id} ({prevVersion.loc} LOC) → {version} (
          {currentVersion.loc} LOC)
        </p>
      </div>

      {/* Structural Diff */}
      <div className="mb-8 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Card>
          <CardHeader>
            <div className="flex items-center gap-2 text-[#8a7b66]">
              <FileCode size={16} />
              <span className="text-sm font-semibold">LOC Delta</span>
            </div>
          </CardHeader>
          <CardTitle>
            <span
              className={
                diff.locDelta >= 0 ? "text-[#5a9e1e]" : "text-[#c44a4a]"
              }
            >
              {diff.locDelta >= 0 ? "+" : ""}
              {diff.locDelta}
            </span>
            <span className="ml-2 text-sm font-normal text-[#9f927d]">
              lines
            </span>
          </CardTitle>
        </Card>

        <Card>
          <CardHeader>
            <div className="flex items-center gap-2 text-[#8a7b66]">
              <Wrench size={16} />
              <span className="text-sm font-semibold">New Tools</span>
            </div>
          </CardHeader>
          <CardTitle>
            <span className="text-[#889df0]">{diff.newTools.length}</span>
          </CardTitle>
          {diff.newTools.length > 0 && (
            <div className="mt-2 flex flex-wrap gap-1">
              {diff.newTools.map((tool) => (
                <span
                  key={tool}
                  className="rounded-full bg-[#e6eafb] px-2 py-0.5 text-xs font-semibold text-[#3a4a8a]"
                >
                  {tool}
                </span>
              ))}
            </div>
          )}
        </Card>

        <Card>
          <CardHeader>
            <div className="flex items-center gap-2 text-[#8a7b66]">
              <Box size={16} />
              <span className="text-sm font-semibold">New Classes</span>
            </div>
          </CardHeader>
          <CardTitle>
            <span className="text-[#b77dee]">{diff.newClasses.length}</span>
          </CardTitle>
          {diff.newClasses.length > 0 && (
            <div className="mt-2 flex flex-wrap gap-1">
              {diff.newClasses.map((cls) => (
                <span
                  key={cls}
                  className="rounded-full bg-[#efe2fb] px-2 py-0.5 text-xs font-semibold text-[#5a2a8a]"
                >
                  {cls}
                </span>
              ))}
            </div>
          )}
        </Card>

        <Card>
          <CardHeader>
            <div className="flex items-center gap-2 text-[#8a7b66]">
              <FunctionSquare size={16} />
              <span className="text-sm font-semibold">New Functions</span>
            </div>
          </CardHeader>
          <CardTitle>
            <span className="text-[#e59266]">{diff.newFunctions.length}</span>
          </CardTitle>
          {diff.newFunctions.length > 0 && (
            <div className="mt-2 flex flex-wrap gap-1">
              {diff.newFunctions.map((fn) => (
                <span
                  key={fn}
                  className="rounded-full bg-[#fde6d8] px-2 py-0.5 text-xs font-semibold text-[#8a4a20]"
                >
                  {fn}
                </span>
              ))}
            </div>
          )}
        </Card>
      </div>

      {/* Version Info Comparison */}
      <div className="mb-8 grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Card className="border-l-4 border-l-[#fc736d]">
          <CardHeader>
            <CardTitle>{prevMeta?.title || prevVersion.id}</CardTitle>
            <p className="text-sm text-[#8a7b66]">{prevMeta?.subtitle}</p>
          </CardHeader>
          <div className="space-y-1 text-sm text-[#725d42]">
            <p>{prevVersion.loc} LOC</p>
            <p>
              {prevVersion.tools.length} tools: {prevVersion.tools.join(", ")}
            </p>
            <LayerBadge layer={prevVersion.layer}>
              {prevVersion.layer}
            </LayerBadge>
          </div>
        </Card>
        <Card className="border-l-4 border-l-[#8ac68a]">
          <CardHeader>
            <CardTitle>{meta.title}</CardTitle>
            <p className="text-sm text-[#8a7b66]">{meta.subtitle}</p>
          </CardHeader>
          <div className="space-y-1 text-sm text-[#725d42]">
            <p>{currentVersion.loc} LOC</p>
            <p>
              {currentVersion.tools.length} tools:{" "}
              {currentVersion.tools.join(", ")}
            </p>
            <LayerBadge layer={currentVersion.layer}>
              {currentVersion.layer}
            </LayerBadge>
          </div>
        </Card>
      </div>

      {/* Code Diff */}
      <div>
        <h2 className="mb-4 text-xl font-bold text-[#794f27]">
          Source Code Diff
        </h2>
        <CodeDiff
          oldSource={prevVersion.source}
          newSource={currentVersion.source}
          oldLabel={`${prevVersion.id} (${prevVersion.filename})`}
          newLabel={`${version} (${currentVersion.filename})`}
        />
      </div>
    </div>
  );
}
