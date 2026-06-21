package com.hoppinzq.agent.tool.worktree;

import com.hoppinzq.agent.constant.AIConstants;
import com.hoppinzq.agent.tool.task.TaskManager;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 管理每个 teammate 的 git worktree。底层调用 {@link GitRunner} 跑 git 子进程。
 *
 * <p>典型用法：
 * <ol>
 *   <li>{@link #create(String, int)} 创建名为 {@code wt/<name>} 的分支 + 在 {@code .worktrees/<name>} 目录下挂一份 worktree</li>
 *   <li>{@link #bindTask(int, String)} 把 worktree 名字绑定到某个 task，AutoClaimer 认领时通过 WorktreeContext.set 切换 cwd</li>
 *   <li>{@link #remove(String, boolean)} 完成后销毁 worktree（discard=true 时连同分支一起删除）</li>
 * </ul>
 *
 * @author hoppinzq
 */
public class WorktreeManager {

    private final TaskManager taskManager;
    private final File worktreesDir;
    private final File repoRoot;

    public WorktreeManager(TaskManager tm) {
        this.taskManager = tm;
        this.worktreesDir = new File(AIConstants.WORKTREES_DIR);
        try { Files.createDirectories(worktreesDir.toPath()); } catch (Exception ignore) {}
        // repoRoot 是从 ROOT 向上找的 git 仓库根；找不到就退化到 ROOT（git 命令会报错，由调用方处理）
        File found = GitRunner.findRepoRoot(new File(AIConstants.ROOT));
        this.repoRoot = found != null ? found : new File(AIConstants.ROOT);
    }

    public File getWorktreesDir() { return worktreesDir; }
    public File getRepoRoot() { return repoRoot; }

    /**
     * 创建 worktree。
     * @return worktree 目录绝对路径；失败抛 RuntimeException。
     */
    public String create(String name, int taskId) throws Exception {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name 不能为空");
        }
        File targetDir = new File(worktreesDir, name);
        if (targetDir.exists()) {
            throw new IllegalStateException("worktree 目录已存在：" + targetDir);
        }
        Files.createDirectories(worktreesDir.toPath());
        String branch = "wt/" + name;
        String mainBranch = "main"; // 简化；不强制要求远端存在
        // 尝试 main，没有则 HEAD
        try {
            GitRunner.run(repoRoot, "worktree", "add", "-b", branch,
                    targetDir.getAbsolutePath(), mainBranch);
        } catch (Exception tryHead) {
            GitRunner.run(repoRoot, "worktree", "add", "-b", branch,
                    targetDir.getAbsolutePath(), "HEAD");
        }
        if (taskId > 0) {
            bindTask(taskId, name);
        }
        return targetDir.getAbsolutePath();
    }

    public void bindTask(int taskId, String name) {
        if (taskManager == null) return;
        // 简化：把 worktree 名字记到任务 description 末尾的元数据中
        // 真正绑定信息存在内存 Map 里更稳妥，但为教学简洁，复用 task 字段
        taskManager.setWorktree(taskId, name);
    }

    /**
     * 移除 worktree；discard=true 时连同分支一起删。
     */
    public String remove(String name, boolean discard) {
        try {
            File targetDir = new File(worktreesDir, name);
            if (discard) {
                try {
                    GitRunner.run(repoRoot, "worktree", "remove", "--force", targetDir.getAbsolutePath());
                } catch (Exception ignore) {
                    // 强制失败时直接物理删除目录
                    if (targetDir.exists()) deleteRecursively(targetDir);
                }
                try {
                    GitRunner.run(repoRoot, "branch", "-D", "wt/" + name);
                } catch (Exception ignore) {}
            } else {
                // detach：保留分支和目录内容，仅从 git worktree 索引里移除
                try {
                    GitRunner.run(repoRoot, "worktree", "remove", targetDir.getAbsolutePath());
                } catch (Exception ignore) {
                    // 留作孤儿
                }
            }
            return "已移除 worktree " + name + " (discard=" + discard + ")";
        } catch (Exception e) {
            return "remove_worktree 错误: " + e.getMessage();
        }
    }

    public String keep(String name) {
        // 教学：仅记录日志，不真正修改状态
        System.out.printf("\u001b[95m[worktree]\u001b[0m %s 标记为 keep（保留分支与目录）%n", name);
        return "worktree " + name + " 已保留";
    }

    public List<String> list() {
        List<String> out = new ArrayList<>();
        try {
            String raw = GitRunner.run(repoRoot, "worktree", "list", "--porcelain");
            for (String line : raw.split("\n")) {
                if (line.startsWith("worktree ")) {
                    out.add(line.substring("worktree ".length()).trim());
                }
            }
        } catch (Exception e) {
            out.add("git worktree list 失败：" + e.getMessage());
        }
        return out;
    }

    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) deleteRecursively(k);
        }
        // 不强求删除 .git 锁文件
        f.delete();
    }
}
