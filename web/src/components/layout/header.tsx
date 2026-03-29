"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useTranslations, useLocale } from "@/lib/i18n";
import { Github, Menu, X } from "lucide-react";
import { useState } from "react";
import { cn } from "@/lib/utils";

const NAV_ITEMS = [
  { key: "timeline", href: "/timeline" },
  { key: "layers", href: "/layers" },
  { key: "我的博客", href: "/blog" },
] as const;

export function Header() {
  const t = useTranslations("nav");
  const pathname = usePathname();
  const locale = useLocale();
  const [mobileOpen, setMobileOpen] = useState(false);

  return (
    <header className="sticky top-0 z-50 border-b border-white/[0.06] bg-[#06050f]/80 backdrop-blur-md">
      <div className="mx-auto flex h-14 max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8">
        <Link href={`/${locale}`} className="flex items-center gap-2 text-lg font-bold">
          <span className="bg-gradient-to-r from-violet-400 to-purple-400 bg-clip-text text-transparent">
            HoppinAI
          </span>
        </Link>

        {/* Desktop nav */}
        <nav className="hidden items-center gap-6 md:flex">
          {NAV_ITEMS.map((item) => (
            <Link
              key={item.key}
              href={`/${locale}${item.href}`}
              className={cn(
                "text-sm font-medium transition-colors",
                pathname.includes(item.href)
                  ? "text-white"
                  : "text-zinc-500 hover:text-zinc-200"
              )}
            >
              {t(item.key)}
            </Link>
          ))}

          <a
            href="https://gitee.com/hoppin/hoppinzq-agent"
            target="_blank"
            rel="noopener"
            className="text-zinc-500 transition-colors hover:text-zinc-300"
          >
            <Github size={18} />
          </a>
        </nav>

        {/* Mobile hamburger */}
        <button
          onClick={() => setMobileOpen(!mobileOpen)}
          className="flex min-h-[44px] min-w-[44px] items-center justify-center text-zinc-400 md:hidden"
        >
          {mobileOpen ? <X size={20} /> : <Menu size={20} />}
        </button>
      </div>

      {/* Mobile menu */}
      {mobileOpen && (
        <div className="border-t border-white/[0.06] bg-[#06050f] p-4 md:hidden">
          {NAV_ITEMS.map((item) => (
            <Link
              key={item.key}
              href={`/${locale}${item.href}`}
              className="flex min-h-[44px] items-center text-sm text-zinc-400 hover:text-white"
              onClick={() => setMobileOpen(false)}
            >
              {t(item.key)}
            </Link>
          ))}
          <div className="mt-3 flex items-center border-t border-white/[0.06] pt-3">
            <a
              href="https://gitee.com/hoppin/hoppinzq-agent"
              target="_blank"
              rel="noopener"
              className="flex min-h-[44px] min-w-[44px] items-center justify-center text-zinc-500 hover:text-zinc-300"
            >
              <Github size={18} />
            </a>
          </div>
        </div>
      )}
    </header>
  );
}
