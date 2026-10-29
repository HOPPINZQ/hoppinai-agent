package com.hoppinzq.agent.capability.teams;

/**
 * teammate 线程上下文（ThreadLocal）。
 * <p>TeammateRunner 启动时把当前线程的 teammate 名字塞进来，
 * {@code send_message} 等工具据此区分"调用者是 lead 还是哪个 teammate"——
 * 原 Tools 里的静态 ThreadLocal 随特性一起收编进能力包。
 *
 * @author hoppinzq
 */
public final class TeammateContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TeammateContext() {
    }

    /** 标记当前线程所属的 teammate 名字 */
    public static void set(String name) {
        CURRENT.set(name);
    }

    /** 当前线程的 teammate 名字；lead 线程（未标记）返回 null */
    public static String current() {
        return CURRENT.get();
    }

    /** 清除标记（teammate 线程结束时调用，防止线程池复用串名） */
    public static void clear() {
        CURRENT.remove();
    }
}
