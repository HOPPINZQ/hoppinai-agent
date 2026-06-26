package com.hoppinzq.agent.session;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.hoppinzq.agent.constant.AIConstants.OBJECT_MAPPER;
import static com.hoppinzq.agent.constant.AIConstants.ROOT;

/**
 * 会话文件持久化。负责 {@code .sessions/<sessionId>.json} 的读写。
 * <p>仅关心文件 IO 与 JSON 序列化，不耦合 agent / SDK 类型，便于复用与测试。
 * <ul>
 *   <li>写入采用「临时文件 +原子 rename」避免崩溃导致半截文件</li>
 *   <li>sessionId 仅允许字母数字与少量安全字符，杜绝路径穿越</li>
 * </ul>
 *
 * @author hoppinzq
 */
public class SessionStore {

    private static final String SESSION_DIR = ".sessions";
    private static final String SUFFIX = ".json";

    private final Path dir;
    private final ObjectMapper mapper;

    public SessionStore() {
        this(Paths.get(ROOT, SESSION_DIR), OBJECT_MAPPER);
    }

    public SessionStore(Path dir, ObjectMapper mapper) {
        this.dir = dir;
        this.mapper = mapper;
    }

    /** 确保会话目录存在。 */
    private void ensureDir() {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("无法创建会话目录: " + dir.toAbsolutePath(), e);
        }
    }

    /** 校验 sessionId 安全性，并返回对应的会话文件路径。 */
    public Path pathOf(String sessionId) {
        if (sessionId == null || !sessionId.matches("[A-Za-z0-9_\\-:.]+")) {
            throw new IllegalArgumentException("非法 sessionId: " + sessionId);
        }
        return dir.resolve(sessionId + SUFFIX);
    }

    /** 保存（覆盖）指定会话的消息列表。 */
    public void save(String sessionId, List<SessionMessage> messages) {
        ensureDir();
        Path target = pathOf(sessionId);
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            mapper.writeValue(tmp.toFile(), messages);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ame) {
                // 跨文件系统时退化为普通覆盖
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new RuntimeException("保存会话失败: " + sessionId, e);
        }
    }

    /** 加载指定会话；不存在则返回空列表。 */
    public List<SessionMessage> load(String sessionId) {
        Path file = pathOf(sessionId);
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        try {
            List<SessionMessage> list = mapper.readValue(file.toFile(),
                    new TypeReference<List<SessionMessage>>() {});
            return list == null ? new ArrayList<>() : list;
        } catch (IOException e) {
            throw new RuntimeException("加载会话失败: " + sessionId, e);
        }
    }

    /** 是否存在该会话。 */
    public boolean exists(String sessionId) {
        return Files.exists(pathOf(sessionId));
    }

    /** 列出所有已存在的 sessionId（不含后缀）。 */
    public List<String> listIds() {
        if (!Files.exists(dir)) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(Files::isRegularFile)
                    .forEach(p -> {
                        String name = p.getFileName().toString();
                        if (name.endsWith(SUFFIX)) {
                            ids.add(name.substring(0, name.length() - SUFFIX.length()));
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException("列出会话失败", e);
        }
        ids.sort(java.util.Comparator.reverseOrder());
        return ids;
    }
}
