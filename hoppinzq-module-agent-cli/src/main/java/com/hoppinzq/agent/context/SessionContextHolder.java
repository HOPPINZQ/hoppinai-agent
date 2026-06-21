package com.hoppinzq.agent.context;

public class SessionContextHolder {

    private static final InheritableThreadLocal<String> SESSION_ID = new InheritableThreadLocal<>();

    public static void set(String sessionId) {
        SESSION_ID.set(sessionId);
    }

    public static String get() {
        return SESSION_ID.get();
    }

    public static void clear() {
        SESSION_ID.remove();
    }
}
