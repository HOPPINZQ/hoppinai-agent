import React from "react";
import { Link, useLocation } from "react-router-dom";
import { LayoutDashboard, MessageSquare, Settings, Bell, Search, User, Sword, History } from "lucide-react";
import { cn } from "@/lib/utils";

interface LayoutProps {
  children: React.ReactNode;
}

export function Layout({ children }: LayoutProps) {
  const location = useLocation();

  const navItems = [
    { name: "Dashboard", path: "/", icon: LayoutDashboard },
    { name: "AI Trading Chat", path: "/chat", icon: MessageSquare },
    { name: "AI Analysis History", path: "/history", icon: History },
    { name: "CSGO Inventory", path: "/csgo-inventory", icon: Sword },
    { name: "AI Purchase History", path: "/csgo-purchase-history", icon: History },
  ];

  return (
    <div className="flex h-screen overflow-hidden bg-[var(--bg)] text-[var(--text-primary)] font-sans">
      {/* Sidebar */}
      <aside className="w-64 flex-shrink-0 border-r border-[var(--border)] bg-[var(--surface)] flex flex-col">
        <div className="h-16 flex items-center px-6 border-b border-[var(--border)]">
          <div className="flex items-center gap-2 text-xl font-bold tracking-tight text-[var(--accent)]">
            <div className="w-8 h-8 rounded bg-[var(--accent)] flex items-center justify-center text-white">
              <span className="text-lg leading-none">AI</span>
            </div>
            StockMonitor
          </div>
        </div>
        
        <nav className="flex-1 py-6 px-3 space-y-1">
          {navItems.map((item) => {
            const isActive = location.pathname === item.path;
            return (
              <Link
                key={item.name}
                to={item.path}
                className={cn(
                  "flex items-center gap-3 px-3 py-2.5 rounded-lg transition-colors text-sm font-medium",
                  isActive
                    ? "bg-[var(--accent)]/10 text-[var(--accent)]"
                    : "text-[var(--text-secondary)] hover:bg-[var(--surface-hover)] hover:text-[var(--text-primary)]"
                )}
              >
                <item.icon className="w-5 h-5" />
                {item.name}
              </Link>
            );
          })}
        </nav>

        <div className="p-4 border-t border-[var(--border)]">
          <button className="flex items-center gap-3 px-3 py-2.5 w-full rounded-lg text-[var(--text-secondary)] hover:bg-[var(--surface-hover)] hover:text-[var(--text-primary)] transition-colors text-sm font-medium">
            <Settings className="w-5 h-5" />
            Settings
          </button>
        </div>
      </aside>

      {/* Main Content */}
      <div className="flex-1 flex flex-col overflow-hidden">
        {/* Top Header */}
        <header className="h-16 flex-shrink-0 border-b border-[var(--border)] bg-[var(--surface)]/50 backdrop-blur-md flex items-center justify-between px-6 z-10">
          <div className="flex items-center bg-[var(--surface)] border border-[var(--border)] rounded-full px-4 py-1.5 w-96 focus-within:border-[var(--accent)] transition-colors">
            <Search className="w-4 h-4 text-[var(--text-secondary)] mr-2" />
            <input 
              type="text" 
              placeholder="Search stocks, symbols, or ask AI..." 
              className="bg-transparent border-none outline-none text-sm w-full text-[var(--text-primary)] placeholder:text-[var(--text-secondary)]"
            />
          </div>
          
          <div className="flex items-center gap-4">
            <button className="p-2 rounded-full text-[var(--text-secondary)] hover:bg-[var(--surface-hover)] hover:text-[var(--text-primary)] transition-colors relative">
              <Bell className="w-5 h-5" />
              <span className="absolute top-1.5 right-1.5 w-2 h-2 rounded-full bg-[var(--accent)]"></span>
            </button>
            <div className="w-8 h-8 rounded-full bg-[var(--surface-hover)] border border-[var(--border)] flex items-center justify-center overflow-hidden cursor-pointer">
              <User className="w-5 h-5 text-[var(--text-secondary)]" />
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
