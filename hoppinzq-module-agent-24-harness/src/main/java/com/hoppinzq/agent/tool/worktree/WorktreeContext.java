package com.hoppinzq.agent.tool.worktree;

import com.hoppinzq.agent.constant.AIConstants;

import java.io.File;

/**
 * Teammate 线程的 worktree 上下文（ThreadLocal）。
 * <p>当 teammate 认领一个绑定了 worktree 的 task 时，会把 worktree 路径塞进当前线程，
 * 之后该线程内的 bash / read_file / write_file / edit_file / glob 都会以 worktree 路径
 * 作为 cwd，互不干扰。
 *
 * @author hoppinzq
 */
public final class WorktreeContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private WorktreeContext() {}

    public static void set(String path) { CURRENT.set(path); }
    public static void clear() { CURRENT.remove(); }
    public static String current() { return CURRENT.get(); }

    /** 当前生效的工作目录：若线程有 worktree 则用它，否则用 ROOT。 */
    public static File effectiveRoot() {
        String p = CURRENT.get();
        if (p == null || p.isBlank()) {
            return new File(AIConstants.ROOT);
        }
        return new File(p);
    }
}
