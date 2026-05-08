/**
 * ZAI 认证系统集成
 * API 文档: https://hoppinzq.com/zhangqi/zq-apps-list.html
 */

const ZAI_BASE = 'https://hoppinzq.com:3008/admin-api';
const LOGIN_URL = 'https://hoppinzq.com/zhangqi/zq-login.html';

// Token 存储的 key
const STORAGE_KEYS = {
  ACCESS_TOKEN: 'zai_access_token',
  REFRESH_TOKEN: 'zai_refresh_token',
  USER_ID: 'zai_user_id',
  EXPIRES_TIME: 'zai_expires_time',
  USER_INFO: 'zai_user_info',
} as const;

// 用户信息类型
export interface ZaiUserInfo {
  id: number;
  username: string;
  nickname: string;
  avatar: string;
  sex: number;
  remark: string;
  loginIp: string;
  loginDate: number;
}

// Token 刷新响应类型
interface TokenRefreshResponse {
  code: number;
  msg: string;
  data?: {
    accessToken: string;
    refreshToken: string;
    userId: number;
    expiresTime: string;
  };
}

// 通用 API 响应类型
interface ApiResponse<T = any> {
  code: number;
  msg: string;
  data: T;
}

// ==================== 工具函数 ====================

/**
 * 构建登录跳转 URL
 */
export function buildLoginUrl(redirectUrl?: string): string {
  const state = redirectUrl || window.location.href;
  return `${LOGIN_URL}?state=${encodeURIComponent(state)}`;
}

/**
 * 跳转到登录页
 */
export function redirectToLogin(redirectUrl?: string): void {
  window.location.href = buildLoginUrl(redirectUrl);
}

/**
 * 判断是否已登录
 */
export function isLoggedIn(): boolean {
  return !!localStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN);
}

/**
 * 清除所有认证信息
 */
export function clearAuthData(): void {
  Object.values(STORAGE_KEYS).forEach(key => localStorage.removeItem(key));
}

/**
 * 清除认证信息并跳转到登录页
 */
export function clearAuthAndRedirect(): void {
  clearAuthData();
  redirectToLogin();
}

// ==================== Token 管理 ====================

/**
 * 刷新访问令牌
 */
async function refreshAccessToken(): Promise<string> {
  const refreshToken = localStorage.getItem(STORAGE_KEYS.REFRESH_TOKEN);
  if (!refreshToken) {
    clearAuthAndRedirect();
    throw new Error('No refresh token');
  }

  try {
    const resp = await fetch(
      `${ZAI_BASE}/system/auth/refresh-token?refreshToken=${refreshToken}`,
      { method: 'POST', headers: { 'Content-Type': 'application/json' } }
    );

    if (!resp.ok) {
      clearAuthAndRedirect();
      throw new Error('Token refresh failed');
    }

    const result: TokenRefreshResponse = await resp.json();
    if (result.code === 0 && result.data) {
      localStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, result.data.accessToken);
      localStorage.setItem(STORAGE_KEYS.REFRESH_TOKEN, result.data.refreshToken);
      localStorage.setItem(STORAGE_KEYS.USER_ID, String(result.data.userId));
      localStorage.setItem(STORAGE_KEYS.EXPIRES_TIME, result.data.expiresTime);
      return result.data.accessToken;
    }

    clearAuthAndRedirect();
    throw new Error('Invalid refresh response');
  } catch (error) {
    clearAuthAndRedirect();
    throw error;
  }
}

/**
 * 检查 token 是否即将过期（提前 5 分钟刷新）
 */
export function isTokenExpiringSoon(): boolean {
  const expiresTime = localStorage.getItem(STORAGE_KEYS.EXPIRES_TIME);
  if (!expiresTime) return false;

  const expireDate = new Date(expiresTime).getTime();
  const now = Date.now();
  const fiveMinutes = 5 * 60 * 1000;

  return expireDate - now < fiveMinutes;
}

/**
 * 主动刷新 token（如果即将过期）
 */
export async function refreshTokenIfNeeded(): Promise<boolean> {
  if (isLoggedIn() && isTokenExpiringSoon()) {
    try {
      await refreshAccessToken();
      return true;
    } catch {
      return false;
    }
  }
  return isLoggedIn();
}

// ==================== 请求封装 ====================

/**
 * 带认证的 fetch 封装，自动处理 token 刷新
 */
export async function zaiFetch(url: string, options: RequestInit = {}): Promise<Response> {
  const accessToken = localStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN);
  const headers: HeadersInit = {
    'Content-Type': 'application/json',
    ...options.headers,
  };

  if (accessToken) {
    headers['Authorization'] = `Bearer ${accessToken}`;
  }

  let resp = await fetch(url, { ...options, headers });

  // Token 过期，自动刷新后重试
  if (resp.status === 401 || resp.status === 403) {
    try {
      const newToken = await refreshAccessToken();
      headers['Authorization'] = `Bearer ${newToken}`;
      resp = await fetch(url, { ...options, headers });
    } catch {
      // 刷新失败，已在 refreshAccessToken 中处理跳转
      throw new Error('Authentication failed');
    }
  }

  return resp;
}

// ==================== 回调处理 ====================

/**
 * 处理 URL 回调（页面跳转方式）
 * @returns 是否成功处理回调
 */
export function handleAuthCallback(): boolean {
  const params = new URLSearchParams(window.location.search);
  const accessToken = params.get('accessToken');
  const userId = params.get('userId');
  const refreshToken = params.get('refreshToken');
  const expiresTime = params.get('expiresTime');

  if (accessToken && userId) {
    saveAuthTokens(accessToken, userId, refreshToken, expiresTime);
    // 清理 URL 中的认证参数
    const cleanUrl = window.location.origin + window.location.pathname;
    window.history.replaceState({}, document.title, cleanUrl);
    return true;
  }
  return false;
}

// ==================== iframe 登录 ====================

/**
 * iframe 登录回调数据类型
 */
export interface IframeLoginData {
  userId: string;
  accessToken: string;
  refreshToken: string;
  expiresTime?: string;
}

/**
 * 保存认证 tokens
 */
function saveAuthTokens(
  accessToken: string,
  userId: string,
  refreshToken?: string | null,
  expiresTime?: string | null
): void {
  localStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, accessToken);
  localStorage.setItem(STORAGE_KEYS.USER_ID, userId);
  if (refreshToken) {
    localStorage.setItem(STORAGE_KEYS.REFRESH_TOKEN, refreshToken);
  }
  if (expiresTime) {
    localStorage.setItem(STORAGE_KEYS.EXPIRES_TIME, expiresTime);
  }
}

/**
 * 构建 iframe 登录 URL
 * @param callbackName 父页面回调函数名，默认 onZaiLoginSuccess
 */
export function buildIframeLoginUrl(callbackName: string = 'onZaiLoginSuccess'): string {
  return `${LOGIN_URL}?state=iframe_${callbackName}`;
}

/**
 * 处理 iframe 登录成功回调
 * 在 iframe 内调用，通过 postMessage 通知父页面
 */
export function handleIframeLoginSuccess(data: IframeLoginData): void {
  saveAuthTokens(data.accessToken, data.userId, data.refreshToken, data.expiresTime);

  // 通过 postMessage 通知父页面
  window.parent.postMessage(
    { type: 'ZAI_LOGIN_SUCCESS', data },
    '*'
  );
}

// ==================== 用户信息 ====================

/**
 * 获取用户信息
 */
export async function getUserInfo(): Promise<ZaiUserInfo> {
  const resp = await zaiFetch(`${ZAI_BASE}/system/auth/get-login-info`);
  const result: ApiResponse<ZaiUserInfo> = await resp.json();

  if (result.code === 0 && result.data) {
    localStorage.setItem(STORAGE_KEYS.USER_INFO, JSON.stringify(result.data));
    return result.data;
  }

  throw new Error(result.msg || '获取用户信息失败');
}

/**
 * 获取缓存的用户信息
 */
export function getCachedUserInfo(): ZaiUserInfo | null {
  const cached = localStorage.getItem(STORAGE_KEYS.USER_INFO);
  return cached ? JSON.parse(cached) : null;
}

/**
 * 登出
 */
export async function logout(): Promise<void> {
  try {
    await zaiFetch(`${ZAI_BASE}/system/auth/logout`);
  } finally {
    clearAuthAndRedirect();
  }
}

// ==================== React Hook ====================

import { useState, useEffect, useCallback } from 'react';

/**
 * 认证状态 Hook
 */
export function useAuth() {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [userInfo, setUserInfo] = useState<ZaiUserInfo | null>(null);
  const [loading, setLoading] = useState(true);

  const loadUserInfo = useCallback(async () => {
    try {
      const info = await getUserInfo();
      setUserInfo(info);
      return info;
    } catch {
      return null;
    }
  }, []);

  useEffect(() => {
    const initAuth = async () => {
      setLoading(true);

      // 先处理可能的 URL 回调
      const isNewLogin = handleAuthCallback();

      // 检查是否已登录
      if (isLoggedIn()) {
        // 如果是新登录或没有缓存的用户信息，则获取用户信息
        if (isNewLogin || !getCachedUserInfo()) {
          await loadUserInfo();
        } else {
          setUserInfo(getCachedUserInfo());
        }
        setIsAuthenticated(true);
      } else {
        setIsAuthenticated(false);
        setUserInfo(null);
      }

      setLoading(false);
    };

    initAuth();
  }, [loadUserInfo]);

  return {
    isAuthenticated,
    userInfo,
    loading,
    login: () => redirectToLogin(),
    logout,
    refreshUserInfo: async () => {
      const info = await getUserInfo();
      setUserInfo(info);
      return info;
    },
    loadUserInfo,
  };
}

/**
 * iframe 登录弹框 Hook
 */
export function useIframeLogin() {
  const [isOpen, setIsOpen] = useState(false);

  const openLogin = useCallback(() => {
    console.log('[useIframeLogin] Opening login modal');
    setIsOpen(true);
  }, []);

  const closeLogin = useCallback(() => {
    console.log('[useIframeLogin] Closing login modal');
    setIsOpen(false);
  }, []);

  return {
    isOpen,
    openLogin,
    closeLogin,
  };
}
