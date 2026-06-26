"use client";

import { useState, useMemo } from "react";
import { useLocale, useTranslations } from "@/lib/i18n";
import { LEARNING_PATH, VERSION_META } from "@/lib/constants";
import { Card, CardHeader, CardTitle } from "@/components/ui/card";
import { LayerBadge } from "@/components/ui/badge";
import { CodeDiff } from "@/components/diff/code-diff";
import { ArchDiagram } from "@/components/architecture/arch-diagram";
import {
  ArrowRight,
  FileCode,
  Wrench,
  Box,
  FunctionSquare,
} from "lucide-react";
import type { VersionIndex } from "@/types/agent-data";
import versionData from "@/data/generated/versions.json";

const data = versionData as VersionIndex;

export default function ComparePage() {
  const t = useTranslations("compare");
  const locale = useLocale();
  const [versionA, setVersionA] = useState<string>("");
  const [versionB, setVersionB] = useState<string>("");

  const infoA = useMemo(
    () => data.versions.find((v) => v.id === versionA),
    [versionA]
  );
  const infoB = useMemo(
    () => data.versions.find((v) => v.id === versionB),
    [versionB]
  );
  const metaA = versionA ? VERSION_META[versionA] : null;
  const metaB = versionB ? VERSION_META[versionB] : null;

  const comparison = useMemo(() => {
    if (!infoA || !infoB) return null;
    const toolsA = new Set(infoA.tools);
    const toolsB = new Set(infoB.tools);
    const onlyA = infoA.tools.filter((t) => !toolsB.has(t));
    const onlyB = infoB.tools.filter((t) => !toolsA.has(t));
    const shared = infoA.tools.filter((t) => toolsB.has(t));

    const classesA = new Set(infoA.classes.map((c) => c.name));
    const newClasses = infoB.classes
      .map((c) => c.name)
      .filter((c) => !classesA.has(c));

    const funcsA = new Set(infoA.functions.map((f) => f.name));
    const newFunctions = infoB.functions
      .map((f) => f.name)
      .filter((f) => !funcsA.has(f));

    return {
      locDelta: infoB.loc - infoA.loc,
      toolsOnlyA: onlyA,
      toolsOnlyB: onlyB,
      toolsShared: shared,
      newClasses,
      newFunctions,
    };
  }, [infoA, infoB]);

  return (
    <div className="py-4">
      <div className="mb-8">
        <h1 className="text-3xl font-extrabold text-[#794f27]">
          {t("title")}
        </h1>
        <p className="mt-2 text-[#8a7b66]">{t("subtitle")}</p>
      </div>

      {/* Selectors */}
      <div className="mb-8 flex flex-col items-start gap-4 sm:flex-row sm:items-center">
        <div className="flex-1">
          <label className="mb-1 block text-sm font-semibold text-[#725d42]">
            {t("select_a")}
          </label>
          <select
            value={versionA}
            onChange={(e) => setVersionA(e.target.value)}
            className="w-full rounded-2xl border-2 border-[#c4b89e] bg-[#fbf7eb] px-3 py-2 text-sm font-medium text-[#725d42] outline-none focus:border-[#ffcc00]"
          >
            <option value="">-- select --</option>
            {LEARNING_PATH.map((v) => (
              <option key={v} value={v}>
                {v} - {VERSION_META[v]?.title}
              </option>
            ))}
          </select>
        </div>

        <ArrowRight
          size={20}
          className="mt-5 hidden text-[#9f927d] sm:block"
        />

        <div className="flex-1">
          <label className="mb-1 block text-sm font-semibold text-[#725d42]">
            {t("select_b")}
          </label>
          <select
            value={versionB}
            onChange={(e) => setVersionB(e.target.value)}
            className="w-full rounded-2xl border-2 border-[#c4b89e] bg-[#fbf7eb] px-3 py-2 text-sm font-medium text-[#725d42] outline-none focus:border-[#ffcc00]"
          >
            <option value="">-- select --</option>
            {LEARNING_PATH.map((v) => (
              <option key={v} value={v}>
                {v} - {VERSION_META[v]?.title}
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* Results */}
      {infoA && infoB && comparison && (
        <div className="space-y-8">
          {/* Side-by-side version info */}
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Card>
              <CardHeader>
                <CardTitle>{metaA?.title || versionA}</CardTitle>
                <p className="text-sm text-[#8a7b66]">{metaA?.subtitle}</p>
              </CardHeader>
              <div className="space-y-2 text-sm text-[#725d42]">
                <p>{infoA.loc} LOC</p>
                <p>{infoA.tools.length} tools</p>
                {metaA && (
                  <LayerBadge layer={metaA.layer}>{metaA.layer}</LayerBadge>
                )}
              </div>
            </Card>
            <Card>
              <CardHeader>
                <CardTitle>{metaB?.title || versionB}</CardTitle>
                <p className="text-sm text-[#8a7b66]">{metaB?.subtitle}</p>
              </CardHeader>
              <div className="space-y-2 text-sm text-[#725d42]">
                <p>{infoB.loc} LOC</p>
                <p>{infoB.tools.length} tools</p>
                {metaB && (
                  <LayerBadge layer={metaB.layer}>{metaB.layer}</LayerBadge>
                )}
              </div>
            </Card>
          </div>

          {/* Side-by-side Architecture Diagrams */}
          <div>
            <h2 className="mb-4 text-xl font-bold text-[#794f27]">
              {t("architecture")}
            </h2>
            <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
              <div>
                <h3 className="mb-3 text-sm font-semibold text-[#8a7b66]">
                  {metaA?.title || versionA}
                </h3>
                <ArchDiagram version={versionA} />
              </div>
              <div>
                <h3 className="mb-3 text-sm font-semibold text-[#8a7b66]">
                  {metaB?.title || versionB}
                </h3>
                <ArchDiagram version={versionB} />
              </div>
            </div>
          </div>

          {/* Structural diff */}
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <Card>
              <CardHeader>
                <div className="flex items-center gap-2 text-[#8a7b66]">
                  <FileCode size={16} />
                  <span className="text-sm font-semibold">
                    {t("loc_delta")}
                  </span>
                </div>
              </CardHeader>
              <CardTitle>
                <span
                  className={
                    comparison.locDelta >= 0
                      ? "text-[#5a9e1e]"
                      : "text-[#c44a4a]"
                  }
                >
                  {comparison.locDelta >= 0 ? "+" : ""}
                  {comparison.locDelta}
                </span>
                <span className="ml-2 text-sm font-normal text-[#9f927d]">
                  {t("lines")}
                </span>
              </CardTitle>
            </Card>

            <Card>
              <CardHeader>
                <div className="flex items-center gap-2 text-[#8a7b66]">
                  <Wrench size={16} />
                  <span className="text-sm font-semibold">
                    {t("new_tools_in_b")}
                  </span>
                </div>
              </CardHeader>
              <CardTitle>
                <span className="text-[#889df0]">
                  {comparison.toolsOnlyB.length}
                </span>
              </CardTitle>
              {comparison.toolsOnlyB.length > 0 && (
                <div className="mt-2 flex flex-wrap gap-1">
                  {comparison.toolsOnlyB.map((tool) => (
                    <span
                      key={tool}
                      className="rounded-full bg-[#e6eafb] px-1.5 py-0.5 text-xs font-semibold text-[#3a4a8a]"
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
                  <span className="text-sm font-semibold">
                    {t("new_classes_in_b")}
                  </span>
                </div>
              </CardHeader>
              <CardTitle>
                <span className="text-[#b77dee]">
                  {comparison.newClasses.length}
                </span>
              </CardTitle>
              {comparison.newClasses.length > 0 && (
                <div className="mt-2 flex flex-wrap gap-1">
                  {comparison.newClasses.map((cls) => (
                    <span
                      key={cls}
                      className="rounded-full bg-[#efe2fb] px-1.5 py-0.5 text-xs font-semibold text-[#5a2a8a]"
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
                  <span className="text-sm font-semibold">
                    {t("new_functions_in_b")}
                  </span>
                </div>
              </CardHeader>
              <CardTitle>
                <span className="text-[#e59266]">
                  {comparison.newFunctions.length}
                </span>
              </CardTitle>
              {comparison.newFunctions.length > 0 && (
                <div className="mt-2 flex flex-wrap gap-1">
                  {comparison.newFunctions.map((fn) => (
                    <span
                      key={fn}
                      className="rounded-full bg-[#fde6d8] px-1.5 py-0.5 text-xs font-semibold text-[#8a4a20]"
                    >
                      {fn}
                    </span>
                  ))}
                </div>
              )}
            </Card>
          </div>

          {/* Tool comparison */}
          <Card>
            <CardHeader>
              <CardTitle>{t("tool_comparison")}</CardTitle>
            </CardHeader>
            <div className="grid grid-cols-1 gap-6 sm:grid-cols-3">
              <div>
                <h4 className="mb-2 text-sm font-semibold text-[#725d42]">
                  {t("only_in")} {metaA?.title || versionA}
                </h4>
                {comparison.toolsOnlyA.length === 0 ? (
                  <p className="text-xs text-[#9f927d]">{t("none")}</p>
                ) : (
                  <div className="flex flex-wrap gap-1">
                    {comparison.toolsOnlyA.map((tool) => (
                      <span
                        key={tool}
                        className="rounded-full bg-[#fde2e0] px-1.5 py-0.5 text-xs font-semibold text-[#8a3a3a]"
                      >
                        {tool}
                      </span>
                    ))}
                  </div>
                )}
              </div>
              <div>
                <h4 className="mb-2 text-sm font-semibold text-[#725d42]">
                  {t("shared")}
                </h4>
                {comparison.toolsShared.length === 0 ? (
                  <p className="text-xs text-[#9f927d]">{t("none")}</p>
                ) : (
                  <div className="flex flex-wrap gap-1">
                    {comparison.toolsShared.map((tool) => (
                      <span
                        key={tool}
                        className="rounded-full bg-[#f0e8d8] px-1.5 py-0.5 text-xs font-semibold text-[#6a5a40]"
                      >
                        {tool}
                      </span>
                    ))}
                  </div>
                )}
              </div>
              <div>
                <h4 className="mb-2 text-sm font-semibold text-[#725d42]">
                  {t("only_in")} {metaB?.title || versionB}
                </h4>
                {comparison.toolsOnlyB.length === 0 ? (
                  <p className="text-xs text-[#9f927d]">{t("none")}</p>
                ) : (
                  <div className="flex flex-wrap gap-1">
                    {comparison.toolsOnlyB.map((tool) => (
                      <span
                        key={tool}
                        className="rounded-full bg-[#e0f0e0] px-1.5 py-0.5 text-xs font-semibold text-[#3a6a3a]"
                      >
                        {tool}
                      </span>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </Card>

          {/* Code Diff */}
          <div>
            <h2 className="mb-4 text-xl font-bold text-[#794f27]">
              {t("source_diff")}
            </h2>
            <CodeDiff
              oldSource={infoA.source}
              newSource={infoB.source}
              oldLabel={`${infoA.id} (${infoA.filename})`}
              newLabel={`${infoB.id} (${infoB.filename})`}
            />
          </div>
        </div>
      )}

      {/* Empty state */}
      {(!versionA || !versionB) && (
        <div className="rounded-2xl border-2 border-dashed border-[#d4c9b4] p-12 text-center">
          <p className="text-[#8a7b66]">{t("empty_hint")}</p>
        </div>
      )}
    </div>
  );
}
