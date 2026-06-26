"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  LAYERS,
  VERSION_META,
  UNIMPLEMENTED_VERSIONS,
  getLayerColor,
} from "@/lib/constants";
import { useTranslations } from "@/lib/i18n";
import { cn } from "@/lib/utils";

export function Sidebar() {
  const pathname = usePathname();
  const locale = pathname.split("/")[1] || "zh";
  const t = useTranslations("sessions");
  const tLayer = useTranslations("layer_labels");

  return (
    <nav className="hidden w-56 shrink-0 md:block">
      <div className="sticky top-[calc(3.5rem+2rem)] mt-8 max-h-[calc(100vh-7.5rem)] space-y-5 overflow-y-auto pr-1 [scrollbar-width:thin]">
        {LAYERS.map((layer) => (
          <div key={layer.id}>
            <div className="flex items-center gap-1.5 pb-1.5">
              <span
                className="h-2.5 w-2.5 rounded-full"
                style={{ backgroundColor: getLayerColor(layer.id) }}
              />
              <span className="text-[11px] font-bold uppercase tracking-wider text-[#8a7b66]">
                {tLayer(layer.id)}
              </span>
            </div>
            <ul className="space-y-1">
              {layer.versions.map((vId) => {
                const meta = VERSION_META[vId];
                const isUnimplemented = UNIMPLEMENTED_VERSIONS.has(vId);
                const href = `/${locale}/${vId}`;
                const isActive =
                  pathname === href ||
                  pathname === `${href}/` ||
                  pathname.startsWith(`${href}/diff`);

                return (
                  <li key={vId}>
                    {isUnimplemented ? (
                      <span
                        className="block cursor-not-allowed select-none rounded-xl px-2.5 py-1.5 text-sm text-[#c4b89e]"
                        title="还没有实现"
                      >
                        <span className="font-mono text-xs">{vId}</span>
                        <span className="ml-1.5">{t(vId) || meta?.title}</span>
                        <span className="ml-1.5 text-[10px] opacity-70">
                          (WIP)
                        </span>
                      </span>
                    ) : (
                      <Link
                        href={href}
                        className={cn(
                          "block rounded-xl px-2.5 py-1.5 text-sm transition-colors",
                          isActive
                            ? "bg-[#b7c6e5] font-semibold text-[#3a3a5a]"
                            : "text-[#725d42] hover:bg-[#f0e8d8] hover:text-[#794f27]"
                        )}
                      >
                        <span className="font-mono text-xs">{vId}</span>
                        <span className="ml-1.5">
                          {t(vId) || meta?.title}
                        </span>
                      </Link>
                    )}
                  </li>
                );
              })}
            </ul>
          </div>
        ))}
      </div>
    </nav>
  );
}
