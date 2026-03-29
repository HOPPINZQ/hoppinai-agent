import Link from "next/link";
import { LEARNING_PATH, VERSION_META, LAYERS, UNIMPLEMENTED_VERSIONS } from "@/lib/constants";
import { LayerBadge } from "@/components/ui/badge";
import versionsData from "@/data/generated/versions.json";
import { VersionDetailClient } from "./client";
import { getTranslations } from "@/lib/i18n-server";
import { loadCodeTree } from "@/lib/load-code-tree";

const LAYER_ACCENT: Record<string, string> = {
  tools: "border-blue-500/30",
  planning: "border-violet-500/30",
  memory: "border-purple-500/30",
  concurrency: "border-amber-500/30",
  collaboration: "border-rose-500/30",
};

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
        <h1 className="text-2xl font-bold">Version not found</h1>
        <p className="mt-2 text-zinc-500">{version}</p>
      </div>
    );
  }

  if (UNIMPLEMENTED_VERSIONS.has(version)) {
    return (
      <div className="mx-auto max-w-6xl mt-4 py-20 text-center">
        <div className="inline-flex items-center gap-2 rounded-full border border-white/[0.06] bg-white/[0.03] px-4 py-1.5 text-sm font-medium text-zinc-500">
          <span className="font-mono">{version}</span>
          <span>&mdash;</span>
          <span>{meta.title}</span>
        </div>
        <h1 className="mt-6 text-3xl font-bold text-zinc-100">
          HoppinAI施工中
        </h1>
        <p className="mt-3 text-lg text-zinc-500">
          还没有用Java实现，敬请期待!
        </p>
        <p className="mt-1 text-sm text-zinc-600">
          {meta.subtitle} &middot; {meta.coreAddition}
        </p>
        <Link
          href={`/${locale}/layers`}
          className="mt-8 inline-flex items-center gap-2 rounded-lg bg-white/10 px-5 py-2.5 text-sm font-medium text-zinc-300 transition-colors hover:bg-white/15 hover:text-white"
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
    <div className="mx-auto max-w-6xl space-y-10 mt-4">
      {/* Header */}
      <header className="space-y-3">
        <div className="flex flex-wrap items-center gap-3">
          <span className="rounded-lg bg-white/[0.05] border border-white/[0.08] px-3 py-1 font-mono text-lg font-bold text-zinc-200">
            {version}
          </span>
          <h1 className="text-2xl font-bold text-zinc-100 sm:text-3xl">{tSession(version) || meta.title}</h1>
          {layer && (
            <LayerBadge layer={meta.layer}>{tLayer(layer.id)}</LayerBadge>
          )}
        </div>
        <p className="text-lg text-zinc-500">
          {meta.subtitle}
        </p>
        <div className="flex flex-wrap items-center gap-4 text-sm text-zinc-500">
          <span className="font-mono">{versionData.loc} LOC</span>
          <span>{versionData.tools.length} {t("tools")}</span>
          {meta.coreAddition && (
            <span className="rounded-full border border-white/[0.08] bg-white/[0.03] px-2.5 py-0.5 text-xs text-zinc-400">
              {meta.coreAddition}
            </span>
          )}
        </div>
        {meta.keyInsight && (
          <blockquote className={cn(
            "border-l-4 pl-4 text-sm italic text-zinc-400",
            LAYER_ACCENT[meta.layer]
          )}>
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
      <nav className="flex items-center justify-between border-t border-white/[0.06] pt-6">
        {prevVersion ? (
          <Link
            href={`/${locale}/${prevVersion}`}
            className="group flex items-center gap-2 text-sm text-zinc-500 transition-colors hover:text-white"
          >
            <span className="transition-transform group-hover:-translate-x-1">
              &larr;
            </span>
            <div>
              <div className="text-xs text-zinc-600">{t("prev")}</div>
              <div className="font-medium">
                {prevVersion} - {tSession(prevVersion) || VERSION_META[prevVersion]?.title}
              </div>
            </div>
          </Link>
        ) : (
          <div />
        )}
        {nextVersion ? (
          <Link
            href={`/${locale}/${nextVersion}`}
            className="group flex items-center gap-2 text-right text-sm text-zinc-500 transition-colors hover:text-white"
          >
            <div>
              <div className="text-xs text-zinc-600">{t("next")}</div>
              <div className="font-medium">
                {tSession(nextVersion) || VERSION_META[nextVersion]?.title} - {nextVersion}
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

function cn(...inputs: (string | false | null | undefined)[]) {
  return inputs.filter(Boolean).join(" ");
}
