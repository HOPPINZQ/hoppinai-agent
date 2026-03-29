import React from "react";
import { Link, useLocation } from "react-router-dom";
import { LayoutDashboard, MessageSquare, Settings, Bell, Search, User, Crosshair, Clock } from "lucide-react";
import { cn } from "@/lib/utils";

interface LayoutProps {
  children: React.ReactNode;
}

export function Layout({ children }: LayoutProps) {
  const location = useLocation();

  const navItems = [
    { name: "仪表盘", path: "/", icon: LayoutDashboard },
    { name: "AI 交易助手", path: "/chat", icon: MessageSquare },
    { name: "AI 分析历史", path: "/history", icon: Clock },
    { name: "CSGO 库存", path: "/csgo-inventory", icon: Crosshair },
    { name: "AI 购买历史", path: "/csgo-purchase-history", icon: Clock },
  ];

  return (
    <div className="flex h-screen overflow-hidden bg-[var(--bg)] text-[var(--text-primary)] font-sans">
      {/* Sidebar */}
      <aside className="w-[260px] flex-shrink-0 border-r border-[var(--border)] bg-[var(--surface)] flex flex-col relative">
        {/* Top accent line */}
        <div className="h-[2px] bg-gradient-to-r from-transparent via-[var(--accent)]/60 to-transparent" />

        {/* Logo */}
        <div className="h-16 flex items-center px-5 border-b border-[var(--border)]">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-lg bg-gradient-to-br from-[var(--accent)] to-[var(--accent-dim)] flex items-center justify-center shadow-lg shadow-[var(--accent)]/15">
              <Crosshair className="w-5 h-5 text-[var(--bg)]" />
            </div>
            <div>
              <div className="text-[15px] font-bold tracking-wide text-[var(--text-primary)] font-display">
                CSGO TRADE
              </div>
              <div className="text-[9px] uppercase tracking-[0.2em] text-[var(--accent)] font-medium">
                Market Hub
              </div>
            </div>
          </div>
        </div>

        {/* Navigation */}
        <nav className="flex-1 py-4 px-3 space-y-0.5">
          {navItems.map((item) => {
            const isActive = location.pathname === item.path;
            return (
              <Link
                key={item.name}
                to={item.path}
                className={cn(
                  "flex items-center gap-3 px-3 py-2.5 rounded-lg transition-all text-[13px] font-medium relative group",
                  isActive
                    ? "bg-[var(--accent)]/10 text-[var(--accent)]"
                    : "text-[var(--text-secondary)] hover:bg-[var(--surface-hover)] hover:text-[var(--text-primary)]"
                )}
              >
                {isActive && (
                  <div className="absolute left-0 top-1/2 -translate-y-1/2 w-[3px] h-5 rounded-r-full bg-[var(--accent)]" />
                )}
                <item.icon className={cn("w-[18px] h-[18px]", isActive && "text-[var(--accent)]")} />
                {item.name}
              </Link>
            );
          })}
        </nav>

        {/* Bottom */}
        <div className="p-3 border-t border-[var(--border)]">
          <button className="flex items-center gap-3 px-3 py-2.5 w-full rounded-lg text-[var(--text-secondary)] hover:bg-[var(--surface-hover)] hover:text-[var(--text-primary)] transition-all text-[13px] font-medium">
            <Settings className="w-[18px] h-[18px]" />
            设置
          </button>
        </div>
      </aside>

      {/* Main Content */}
      <div className="flex-1 flex flex-col overflow-hidden relative">
        {/* Top Header */}
        <header className="h-14 flex-shrink-0 border-b border-[var(--border)] bg-[var(--surface)]/90 backdrop-blur-xl flex items-center justify-between px-6 z-10">
          <div className="flex items-center bg-[var(--bg)] border border-[var(--border)] rounded-lg px-3.5 py-1.5 w-80 focus-within:border-[var(--accent)]/40 transition-all">
            <Search className="w-4 h-4 text-[var(--text-muted)] mr-2" />
            <input
              type="text"
              placeholder="搜索皮肤、武器..."
              className="bg-transparent border-none outline-none text-[13px] w-full text-[var(--text-primary)] placeholder:text-[var(--text-muted)]"
            />
          </div>

          <div className="flex items-center gap-3">
            <button className="p-2 rounded-lg text-[var(--text-secondary)] hover:bg-[var(--surface-hover)] hover:text-[var(--text-primary)] transition-colors relative">
              <Bell className="w-[18px] h-[18px]" />
              <span className="absolute top-1 right-1 w-2 h-2 rounded-full bg-[var(--accent)]"></span>
            </button>
            <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-[var(--accent)] to-[var(--accent-dim)] flex items-center justify-center overflow-hidden cursor-pointer">
              <User className="w-4 h-4 text-[var(--bg)]" />
            </div>
          </div>
        </header>

        {/* Page Content */}
        <main className="flex-1 overflow-y-auto p-6">
          <div className="max-w-7xl mx-auto">
            {children}
          </div>
        </main>
      </div>
    </div>
  );
}
