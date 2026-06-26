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
] as const;

export function Header() {
  const t = useTranslations("nav");
  const pathname = usePathname();
  const locale = useLocale();
  const [mobileOpen, setMobileOpen] = useState(false);

  return (
    <header className="sticky top-0 z-50 border-b border-[#e8dcc8] bg-[#f8f8f0]/90 backdrop-blur-md">
      <div className="mx-auto flex h-14 max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8">
        <Link
          href={`/${locale}`}
          className="flex items-center gap-2 text-lg font-extrabold"
        >
          <span className="bg-gradient-to-r from-[#19c8b9] to-[#82d5bb] bg-clip-text text-transparent">
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
                "rounded-full px-3 py-1 text-sm font-semibold transition-colors",
                pathname.includes(item.href)
                  ? "bg-[#19c8b9] text-white"
                  : "text-[#8a7b66] hover:bg-[#f0e8d8] hover:text-[#794f27]"
              )}
            >
              {t(item.key)}
            </Link>
          ))}

          <a
            href="https://github.com/HOPPINZQ/hoppinai-agent"
            target="_blank"
            rel="noopener"
            className="text-[#8a7b66] transition-colors hover:text-[#794f27]"
            aria-label="GitHub"
          >
            <Github size={18} />
          </a>
        </nav>

        {/* Mobile hamburger */}
        <button
          onClick={() => setMobileOpen(!mobileOpen)}
          className="flex min-h-[44px] min-w-[44px] items-center justify-center rounded-full text-[#794f27] hover:bg-[#f0e8d8] md:hidden"
          aria-label="Menu"
        >
          {mobileOpen ? <X size={20} /> : <Menu size={20} />}
        </button>
      </div>

      {/* Mobile menu */}
      {mobileOpen && (
        <div className="border-t border-[#e8dcc8] bg-[#f8f8f0] p-4 md:hidden">
          {NAV_ITEMS.map((item) => (
            <Link
              key={item.key}
              href={`/${locale}${item.href}`}
              className="flex min-h-[44px] items-center rounded-xl px-3 text-sm font-semibold text-[#725d42] hover:bg-[#f0e8d8] hover:text-[#794f27]"
              onClick={() => setMobileOpen(false)}
            >
              {t(item.key)}
            </Link>
          ))}
          <div className="mt-3 flex items-center border-t border-[#e8dcc8] pt-3">
            <a
              href="https://github.com/HOPPINZQ/hoppinai-agent"
              target="_blank"
              rel="noopener"
              className="flex min-h-[44px] min-w-[44px] items-center justify-center rounded-full text-[#8a7b66] hover:bg-[#f0e8d8] hover:text-[#794f27]"
              aria-label="GitHub"
            >
              <Github size={18} />
            </a>
          </div>
        </div>
      )}
    </header>
  );
}
