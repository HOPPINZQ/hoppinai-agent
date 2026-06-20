package com.hoppinzq.agent.tool.cron;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static com.hoppinzq.agent.constant.AIConstants.MODULE_NAME;
import static com.hoppinzq.agent.constant.AIConstants.ROOT;

/**
 * 把 durable 任务持久化到 {@code <ROOT>/.scheduled_tasks.json}。
 * <p>
 * 进程启动时 load() → 把文件里所有任务塞回调度器；调度器每有变更后 save()。
 *
 * @author hoppinzq
 */
public final class CronStore {

    private static final ObjectMapper M = new ObjectMapper();
    private static final Path STORE = Paths.get(ROOT, ".scheduled_tasks.json");

    private CronStore() {}

    public static Path storePath() { return STORE; }

    public static List<CronJob> load() {
        if (!Files.exists(STORE)) {
            return new ArrayList<>();
        }
        try {
            byte[] bytes = Files.readAllBytes(STORE);
            if (bytes.length == 0) return new ArrayList<>();
            return M.readValue(bytes, new TypeReference<List<CronJob>>() {});
        } catch (IOException e) {
            System.err.println("[CronStore] 加载失败：" + e.getMessage());
            return new ArrayList<>();
        }
    }

    public static void save(List<CronJob> jobs) {
        try {
            Files.createDirectories(STORE.getParent());
            List<CronJob> durable = new ArrayList<>();
            for (CronJob j : jobs) {
                if (j.isDurable()) durable.add(j);
            }
            byte[] data = M.writerWithDefaultPrettyPrinter().writeValueAsBytes(durable);
            Files.write(STORE, data);
        } catch (IOException e) {
            System.err.println("[CronStore] 保存失败：" + e.getMessage());
        }
    }
}
