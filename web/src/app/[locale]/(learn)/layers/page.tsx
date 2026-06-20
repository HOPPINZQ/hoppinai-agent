"use client";

import Link from "next/link";
import { useTranslations, useLocale } from "@/lib/i18n";
import {
  LAYERS,
  VERSION_META,
  UNIMPLEMENTED_VERSIONS,
  getLayerColor,
} from "@/lib/constants";
import { Card } from "@/components/ui/card";
import { LayerBadge } from "@/components/ui/badge";
import { ChevronRight } from "lucide-react";
import type { VersionIndex } from "@/types/agent-data";
import versionData from "@/data/generated/versions.json";

const data = versionData as VersionIndex;

export default function LayersPage() {
  const t = useTranslations("layers");
  const locale = useLocale();

  return (
    <div className="py-4">
      <div className="mb-10">
        <h1 className="text-3xl font-extrabold text-[#794f27]">{t("title")}</h1>
        <p className="mt-2 text-[#8a7b66]">{t("subtitle")}</p>
      </div>

      <div className="space-y-6">
        {LAYERS.map((layer, index) => {
          const versionInfos = layer.versions.map((vId) => {
            const info = data.versions.find((v) => v.id === vId);
            const meta = VERSION_META[vId];
            return { id: vId, info, meta };
          });
          const layerColor = getLayerColor(layer.id);

          return (
            <div
              key={layer.id}
              className="overflow-hidden rounded-2xl border border-[#e8dcc8] bg-[#fbf7eb]"
              style={{ borderLeft: `6px solid ${layerColor}` }}
            >
              {/* Layer header */}
              <div className="flex items-center gap-3 px-6 py-4">
                <div
                  className="h-3 w-3 rounded-full"
                  style={{ backgroundColor: layerColor }}
                />
                <div>
                  <h2 className="text-xl font-extrabold text-[#794f27]">
                    <span className="text-[#9f927d]">L{index + 1}</span>{" "}
                    {layer.label}
                  </h2>
                  <p className="mt-1 text-sm text-[#8a7b66]">{t(layer.id)}</p>
                </div>
              </div>

              {/* Version cards within this layer */}
              <div className="border-t border-[#e8dcc8] bg-[#f8f8f0] px-6 py-4">
                <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
                  {versionInfos.map(({ id, info, meta }) => {
                    const isUnimplemented = UNIMPLEMENTED_VERSIONS.has(id);

                    return isUnimplemented ? (
                      <div
                        key={id}
                        className="group cursor-not-allowed opacity-60"
                      >
                        <Card>
                          <div className="flex items-start justify-between">
                            <div className="min-w-0 flex-1">
                              <div className="flex items-center gap-2">
                                <span className="font-mono text-xs text-[#9f927d]">
                                  {id}
                                </span>
                                <LayerBadge layer={layer.id}>
                                  {layer.id}
                                </LayerBadge>
                                <span className="rounded-full bg-[#f0e8d8] px-2 py-0.5 text-[10px] font-bold text-[#8a7b66]">
                                  WIP
                                </span>
                              </div>
                              <h3 className="mt-1 font-bold text-[#794f27]">
                                {meta?.title || id}
                              </h3>
                              {meta?.subtitle && (
                                <p className="mt-0.5 text-xs text-[#8a7b66]">
                                  {meta.subtitle}
                                </p>
                              )}
                            </div>
                          </div>
                          <div className="mt-3 flex items-center gap-4 text-xs text-[#8a7b66]">
                            <span>{info?.loc ?? "?"} LOC</span>
                            <span>{info?.tools.length ?? "?"} tools</span>
                          </div>
                          {meta?.keyInsight && (
                            <p className="mt-2 line-clamp-2 text-xs leading-relaxed text-[#725d42]">
                              {meta.keyInsight}
                            </p>
                          )}
                        </Card>
                      </div>
                    ) : (
                      <Link key={id} href={`/${locale}/${id}`} className="group">
                        <Card className="transition-transform group-hover:-translate-y-1">
                          <div className="flex items-start justify-between">
                            <div className="min-w-0 flex-1">
                              <div className="flex items-center gap-2">
                                <span className="font-mono text-xs text-[#9f927d]">
                                  {id}
                                </span>
                                <LayerBadge layer={layer.id}>
                                  {layer.id}
                                </LayerBadge>
                              </div>
                              <h3 className="mt-1 font-bold text-[#794f27]">
                                {meta?.title || id}
                              </h3>
                              {meta?.subtitle && (
                                <p className="mt-0.5 text-xs text-[#8a7b66]">
                                  {meta.subtitle}
                                </p>
                              )}
                            </div>
                            <ChevronRight
                              size={16}
                              className="mt-1 shrink-0 text-[#c4b89e] transition-colors group-hover:text-[#19c8b9]"
                            />
                          </div>
                          <div className="mt-3 flex items-center gap-4 text-xs text-[#8a7b66]">
                            <span>{info?.loc ?? "?"} LOC</span>
                            <span>{info?.tools.length ?? "?"} tools</span>
                          </div>
                          {meta?.keyInsight && (
                            <p className="mt-2 line-clamp-2 text-xs leading-relaxed text-[#725d42]">
                              {meta.keyInsight}
                            </p>
                          )}
                        </Card>
                      </Link>
                    );
                  })}
                </div>
              </div>

              {/* Composition indicator */}
              {index < LAYERS.length - 1 && (
                <div className="flex items-center justify-center py-1 text-[#c4b89e]">
                  <svg
                    width="20"
                    height="12"
                    viewBox="0 0 20 12"
                    fill="none"
                    className="text-current"
                  >
                    <path
                      d="M10 0 L10 12 M5 7 L10 12 L15 7"
                      stroke="currentColor"
                      strokeWidth="1.5"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    />
                  </svg>
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
