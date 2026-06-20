import React, { useState, useCallback, useEffect, useRef } from "react";
import { Link, useLocation } from "react-router-dom";
import { LayoutDashboard, MessageSquare, Settings, Bell, Search, User, Crosshair, Clock, LogOut, ChevronDown, Sun, Moon, Leaf } from "lucide-react";
import { cn } from "@/lib/utils";
import { useTheme } from "@/lib/useTheme";
import { useAuth, useIframeLogin, getCachedUserInfo, isLoggedIn, redirectToLogin, handleAuthCallback, getUserInfo, type IframeLoginData } from "@/lib/zaiAuth";
import { LoginModal } from "@/components/LoginModal";

interface LayoutProps {
  children: React.ReactNode;
}

export function Layout({ children }: LayoutProps) {
  const location = useLocation();
  const { theme, setTheme, resolvedTheme } = useTheme();

  // 认证状态
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [userInfo, setUserInfo] = useState<ReturnType<typeof getCachedUserInfo>>(null);
  const { isOpen: isLoginModalOpen, openLogin, closeLogin } = useIframeLogin();

  // 登录菜单下拉状态
  const [showLoginMenu, setShowLoginMenu] = useState(false);
  const loginMenuRef = useRef<HTMLDivElement>(null);

  // 初始化认证状态
  useEffect(() => {
    const initAuth = async () => {
      // 先处理 URL 回调（跳转登录方式）
      const isNewLogin = handleAuthCallback();

      // 更新认证状态
      const authenticated = isLoggedIn();
      setIsAuthenticated(authenticated);

      // 如果是新登录或已登录但没有缓存的用户信息，则获取用户信息
      if (isNewLogin || (authenticated && !getCachedUserInfo())) {
        try {
          const info = await getUserInfo();
          setUserInfo(info);
        } catch (error) {
          console.error('[Layout] Failed to get user info:', error);
          // 获取用户信息失败，可能 token 无效
          setIsAuthenticated(false);
        }
      } else {
        setUserInfo(getCachedUserInfo());
      }
    };

    initAuth();
  }, []);

  // 点击外部关闭登录菜单
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (loginMenuRef.current && !loginMenuRef.current.contains(event.target as Node)) {
        setShowLoginMenu(false);
      }
    };

    if (showLoginMenu) {
      document.addEventListener('mousedown', handleClickOutside);
    }

    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [showLoginMenu]);

  // 处理登录成功
  const handleLoginSuccess = useCallback(() => {
    setIsAuthenticated(true);
    setUserInfo(getCachedUserInfo());
    setShowLoginMenu(false);
  }, []);

  // 处理登出
  const handleLogout = useCallback(() => {
    setIsAuthenticated(false);
    setUserInfo(null);
    setShowLoginMenu(false);
    // 清除认证信息并跳转到登录页
    localStorage.removeItem('zai_access_token');
    localStorage.removeItem('zai_refresh_token');
    localStorage.removeItem('zai_user_id');
    localStorage.removeItem('zai_expires_time');
    localStorage.removeItem('zai_user_info');
  }, []);

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
            <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-[var(--accent)] to-[var(--accent-dim)] flex items-center justify-center shadow-lg shadow-[var(--accent)]/20">
              <Leaf className="w-5 h-5 text-white" />
            </div>
            <div>
              <div className="text-[15px] font-extrabold tracking-wide text-[var(--text-primary)] font-display">
                岛屿集市
              </div>
              <div className="text-[9px] uppercase tracking-[0.2em] text-[var(--accent)] font-bold">
                Island Hub
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
                  "flex items-center gap-3 px-3 py-2.5 rounded-xl transition-all text-[13px] font-bold relative group",
                  isActive
                    ? "bg-[var(--accent)]/15 text-[var(--accent)]"
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
          <Link
            to="/settings"
            className="flex items-center gap-3 px-3 py-2.5 w-full rounded-lg text-[var(--text-secondary)] hover:bg-[var(--surface-hover)] hover:text-[var(--text-primary)] transition-all text-[13px] font-medium"
          >
            <Settings className="w-[18px] h-[18px]" />
            设置
          </Link>
        </div>
      </aside>

      {/* Main Content */}
      <div className="flex-1 flex flex-col overflow-hidden relative">
        {/* Top Header */}
        <header className="h-14 flex-shrink-0 border-b border-[var(--border)] bg-[var(--surface)]/90 backdrop-blur-xl flex items-center justify-between px-6 z-10">
          <div className="flex items-center bg-[var(--bg)] border-[2px] border-[var(--border)] rounded-full px-4 py-1.5 w-80 focus-within:border-[#ffcc00] focus-within:shadow-[0_0_0_3px_rgba(255,204,0,0.15)] transition-all">
            <Search className="w-4 h-4 text-[var(--text-muted)] mr-2" />
            <input
              type="text"
              placeholder="搜索皮肤、武器..."
              className="bg-transparent border-none outline-none text-[13px] font-semibold w-full text-[var(--text-primary)] placeholder:text-[var(--text-muted)] placeholder:font-medium"
            />
          </div>

          <div className="flex items-center gap-3">
            <button
              onClick={() => setTheme(resolvedTheme === "dark" ? "light" : "dark")}
              className="p-2 rounded-lg text-[var(--text-secondary)] hover:bg-[var(--surface-hover)] hover:text-[var(--text-primary)] transition-colors"
              title={resolvedTheme === "dark" ? "切换到浅色模式" : "切换到深色模式"}
            >
              {resolvedTheme === "dark" ? <Sun className="w-[18px] h-[18px]" /> : <Moon className="w-[18px] h-[18px]" />}
            </button>
            <button className="p-2 rounded-lg text-[var(--text-secondary)] hover:bg-[var(--surface-hover)] hover:text-[var(--text-primary)] transition-colors relative">
              <Bell className="w-[18px] h-[18px]" />
              <span className="absolute top-1 right-1 w-2 h-2 rounded-full bg-[var(--accent)]"></span>
            </button>

            {/* 登录/用户信息区域 */}
            <div className="relative" ref={loginMenuRef}>
              {isAuthenticated && userInfo ? (
                <div className="flex items-center gap-2">
                  <div className="flex items-center gap-2 px-2 py-1 rounded-lg bg-[var(--surface-hover)]/50">
                    {userInfo.avatar ? (
                      <img
                        src={userInfo.avatar}
                        alt={userInfo.nickname}
                        className="w-6 h-6 rounded-full object-cover"
                      />
                    ) : (
                      <div className="w-6 h-6 rounded-full bg-[var(--accent)]/20 flex items-center justify-center">
                        <User className="w-3 h-3 text-[var(--accent)]" />
                      </div>
                    )}
                    <span className="text-[12px] text-[var(--text-secondary)] max-w-[100px] truncate">
                      {userInfo.nickname || userInfo.username}
                    </span>
                    <button
                      onClick={handleLogout}
                      className="p-1 text-[var(--text-muted)] hover:text-[var(--down)] transition-colors"
                      title="退出登录"
                    >
                      <LogOut className="w-3 h-3" />
                    </button>
                  </div>
                </div>
              ) : (
                <button
                  onClick={() => setShowLoginMenu(!showLoginMenu)}
                  className="w-8 h-8 rounded-lg bg-gradient-to-br from-[var(--accent)] to-[var(--accent-dim)] flex items-center justify-center overflow-hidden hover:shadow-lg hover:shadow-[var(--accent)]/20 transition-all"
                  title="登录"
                >
                  <User className="w-4 h-4 text-[var(--bg)]" />
                </button>
              )}

              {/* 登录方式选择菜单 */}
              {showLoginMenu && !isAuthenticated && (
                <div className="absolute right-0 top-full mt-2 w-48 bg-[var(--surface)] border border-[var(--border)] rounded-lg shadow-xl overflow-hidden z-50">
                  <div className="p-2 space-y-1">
                    <button
                      onClick={() => {
                        console.log('[Layout] iframe login selected');
                        setShowLoginMenu(false);
                        openLogin();
                      }}
                      className="w-full flex items-center gap-3 px-3 py-2 rounded-lg text-left text-[13px] text-[var(--text-secondary)] hover:bg-[var(--surface-hover)] hover:text-[var(--text-primary)] transition-colors"
                    >
                      <User className="w-4 h-4 text-[var(--accent)]" />
                      <div>
                        <div className="font-medium">页内登录</div>
                        <div className="text-[11px] text-[var(--text-muted)]">在弹窗中登录，不跳转</div>
                      </div>
                    </button>
                    <button
                      onClick={() => {
                        console.log('[Layout] redirect login selected');
                        setShowLoginMenu(false);
                        redirectToLogin();
                      }}
                      className="w-full flex items-center gap-3 px-3 py-2 rounded-lg text-left text-[13px] text-[var(--text-secondary)] hover:bg-[var(--surface-hover)] hover:text-[var(--text-primary)] transition-colors"
                    >
                      <User className="w-4 h-4 text-[var(--accent)]" />
                      <div>
                        <div className="font-medium">跳转登录</div>
                        <div className="text-[11px] text-[var(--text-muted)]">跳转到 ZAI 认证页</div>
                      </div>
                    </button>
                  </div>
                </div>
              )}
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

      {/* 登录弹框 */}
      <LoginModal
        isOpen={isLoginModalOpen}
        onClose={closeLogin}
        onLoginSuccess={handleLoginSuccess}
      />
    </div>
  );
}
