import Link from "next/link";
import {
  LEARNING_PATH,
  VERSION_META,
  LAYERS,
  UNIMPLEMENTED_VERSIONS,
  getLayerColor,
} from "@/lib/constants";
import { LayerBadge } from "@/components/ui/badge";
import versionsData from "@/data/generated/versions.json";
import { VersionDetailClient } from "./client";
import { getTranslations } from "@/lib/i18n-server";
import { loadCodeTree } from "@/lib/load-code-tree";

export function generateStaticParams() {
  return LEARNING_PATH.map((version) => ({ version }));
}

export default async function VersionPage({
  params,
}: {
  params: Promise<{ locale: string; version: string }>;
}) {
  const { locale, version } = await params;

  const versionData = versionsData.versions.find((v) => v.id === version);
  const meta = VERSION_META[version];
  const diff = versionsData.diffs.find((d) => d.to === version) ?? null;

  if (!versionData || !meta) {
    return (
      <div className="py-20 text-center">
        <h1 className="text-2xl font-bold text-[#794f27]">Version not found</h1>
        <p className="mt-2 text-[#9f927d]">{version}</p>
      </div>
    );
  }

  if (UNIMPLEMENTED_VERSIONS.has(version)) {
    return (
      <div className="mx-auto mt-4 max-w-6xl py-20 text-center">
        <div className="inline-flex items-center gap-2 rounded-full border border-[#e8dcc8] bg-[#fbf7eb] px-4 py-1.5 text-sm font-semibold text-[#725d42]">
          <span className="font-mono">{version}</span>
          <span>&mdash;</span>
          <span>{meta.title}</span>
        </div>
        <h1 className="mt-6 text-3xl font-extrabold text-[#794f27]">
          HoppinAI 施工中
        </h1>
        <p className="mt-3 text-lg text-[#8a7b66]">
          还没有用 Java 实现，敬请期待!
        </p>
        <p className="mt-1 text-sm text-[#9f927d]">
          {meta.subtitle} &middot; {meta.coreAddition}
        </p>
        <Link
          href={`/${locale}/layers`}
          className="mt-8 inline-flex items-center gap-2 rounded-full bg-[#19c8b9] px-5 py-2.5 text-sm font-semibold text-white transition-transform hover:-translate-y-0.5"
        >
          &larr; 返回查看其他资料
        </Link>
      </div>
    );
  }

  const t = getTranslations(locale, "version");
  const tSession = getTranslations(locale, "sessions");
  const tLayer = getTranslations(locale, "layer_labels");
  const layer = LAYERS.find((l) => l.id === meta.layer);

  const codeTree = loadCodeTree(version);

  const pathIndex = LEARNING_PATH.indexOf(version as typeof LEARNING_PATH[number]);
  let prevVersion = pathIndex > 0 ? LEARNING_PATH[pathIndex - 1] : null;
  let nextVersion =
    pathIndex < LEARNING_PATH.length - 1
      ? LEARNING_PATH[pathIndex + 1]
      : null;

  while (prevVersion && UNIMPLEMENTED_VERSIONS.has(prevVersion)) {
    const prevIdx = LEARNING_PATH.indexOf(prevVersion);
    prevVersion = prevIdx > 0 ? LEARNING_PATH[prevIdx - 1] : null;
  }

  while (nextVersion && UNIMPLEMENTED_VERSIONS.has(nextVersion)) {
    const nextIdx = LEARNING_PATH.indexOf(nextVersion);
    nextVersion = nextIdx < LEARNING_PATH.length - 1 ? LEARNING_PATH[nextIdx + 1] : null;
  }

  return (
    <div className="mx-auto mt-4 max-w-6xl space-y-10">
      {/* Header */}
      <header className="space-y-3">
        <div className="flex flex-wrap items-center gap-3">
          <span
            className="rounded-2xl border px-3 py-1 font-mono text-lg font-bold"
            style={{
              borderColor: getLayerColor(meta.layer),
              color: getLayerColor(meta.layer),
              backgroundColor: "#fbf7eb",
            }}
          >
            {version}
          </span>
          <h1 className="text-2xl font-extrabold text-[#794f27] sm:text-3xl">
            {tSession(version) || meta.title}
          </h1>
          {layer && (
            <LayerBadge layer={meta.layer}>{tLayer(layer.id)}</LayerBadge>
          )}
        </div>
        <p className="text-lg text-[#8a7b66]">{meta.subtitle}</p>
        <div className="flex flex-wrap items-center gap-4 text-sm text-[#8a7b66]">
          <span className="font-mono font-semibold text-[#725d42]">
            {versionData.loc} LOC
          </span>
          <span>
            {versionData.tools.length} {t("tools")}
          </span>
          {meta.coreAddition && (
            <span className="rounded-full border border-[#e8dcc8] bg-[#fbf7eb] px-2.5 py-0.5 text-xs font-semibold text-[#725d42]">
              {meta.coreAddition}
            </span>
          )}
        </div>
        {meta.keyInsight && (
          <blockquote
            className="rounded-r-xl border-l-4 pl-4 text-sm italic text-[#725d42]"
            style={{ borderColor: getLayerColor(meta.layer) }}
          >
            {meta.keyInsight}
          </blockquote>
        )}
      </header>

      {/* Client-rendered interactive sections */}
      <VersionDetailClient
        version={version}
        diff={diff}
        source={versionData.source}
        filename={versionData.filename}
        githubUrl={versionData.githubUrl}
        downloadUrl={versionData.downloadUrl}
        tree={codeTree}
      />

      {/* Prev / Next navigation */}
      <nav className="flex items-center justify-between border-t border-[#e8dcc8] pt-6">
        {prevVersion ? (
          <Link
            href={`/${locale}/${prevVersion}`}
            className="group flex items-center gap-2 text-sm text-[#8a7b66] transition-colors hover:text-[#794f27]"
          >
            <span className="transition-transform group-hover:-translate-x-1">
              &larr;
            </span>
            <div>
              <div className="text-xs text-[#9f927d]">{t("prev")}</div>
              <div className="font-semibold">
                {prevVersion} -{" "}
                {tSession(prevVersion) || VERSION_META[prevVersion]?.title}
              </div>
            </div>
          </Link>
        ) : (
          <div />
        )}
        {nextVersion ? (
          <Link
            href={`/${locale}/${nextVersion}`}
            className="group flex items-center gap-2 text-right text-sm text-[#8a7b66] transition-colors hover:text-[#794f27]"
          >
            <div>
              <div className="text-xs text-[#9f927d]">{t("next")}</div>
              <div className="font-semibold">
                {tSession(nextVersion) || VERSION_META[nextVersion]?.title} -{" "}
                {nextVersion}
              </div>
            </div>
            <span className="transition-transform group-hover:translate-x-1">
              &rarr;
            </span>
          </Link>
        ) : (
          <div />
        )}
      </nav>
    </div>
  );
}
