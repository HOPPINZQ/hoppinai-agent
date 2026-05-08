import { useEffect, useRef } from "react";
import { X } from "lucide-react";
import { buildIframeLoginUrl, type IframeLoginData } from "@/lib/zaiAuth";

interface LoginModalProps {
  isOpen: boolean;
  onClose: () => void;
  onLoginSuccess: (data: IframeLoginData) => void;
}

// 定义全局回调函数类型
declare global {
  interface Window {
    onZaiLoginSuccess: (data: IframeLoginData) => void;
  }
}

export function LoginModal({ isOpen, onClose, onLoginSuccess }: LoginModalProps) {
  const iframeRef = useRef<HTMLIFrameElement>(null);

  useEffect(() => {
    console.log('[LoginModal] isOpen changed:', isOpen);

    // 定义全局回调函数，供 ZAI 登录页调用
    window.onZaiLoginSuccess = (data: IframeLoginData) => {
      console.log('[LoginModal] onZaiLoginSuccess called with data:', data);
      // 通过 postMessage 发送给自己的消息监听器
      window.postMessage({ type: 'ZAI_LOGIN_SUCCESS', data }, window.location.origin);
    };

    // 监听来自自己的消息
    const handleMessage = (event: MessageEvent) => {
      // 只接受同源消息
      if (event.origin !== window.location.origin) return;

      console.log('[LoginModal] Received message:', event.data);

      if (event.data?.type === 'ZAI_LOGIN_SUCCESS') {
        console.log('[LoginModal] Login success!');
        onLoginSuccess(event.data.data);
        onClose();
      }
    };

    window.addEventListener('message', handleMessage);

    return () => {
      window.removeEventListener('message', handleMessage);
      // 清理全局函数
      delete window.onZaiLoginSuccess;
    };
  }, [onLoginSuccess, onClose]);

  // 重置 iframe src 当弹框关闭时
  useEffect(() => {
    if (!isOpen && iframeRef.current) {
      iframeRef.current.src = '';
    }
  }, [isOpen]);

  if (!isOpen) return null;

  console.log('[LoginModal] Rendering modal with URL:', buildIframeLoginUrl('onZaiLoginSuccess'));

  return (
    <div
      className="fixed inset-0 z-[99999] flex items-center justify-center bg-black/50 backdrop-blur-sm"
      onClick={onClose}
    >
      <div
        className="relative w-[450px] h-[600px] bg-white rounded-2xl overflow-hidden shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        {/* 关闭按钮 */}
        <button
          onClick={onClose}
          className="absolute top-4 right-4 z-10 w-8 h-8 flex items-center justify-center bg-black/10 hover:bg-black/20 rounded-full transition-colors"
          title="关闭"
        >
          <X className="w-4 h-4 text-gray-600" />
        </button>

        {/* 登录 iframe */}
        <iframe
          ref={iframeRef}
          src={buildIframeLoginUrl('onZaiLoginSuccess')}
          className="w-full h-full border-none"
          title="登录"
        />
      </div>
    </div>
  );
}
