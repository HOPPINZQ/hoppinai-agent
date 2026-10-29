package com.hoppinzq.agent.tool.protocol;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 协议状态注册表（单例风格：每个 agent 持有一个实例）。
 * <p>
 * 用 {@link ConcurrentHashMap} 保存 requestId -&gt; {@link ProtocolState}，所有方法线程安全。
 *
 * @author hoppinzq
 */
public class ProtocolRegistry {

    private final ConcurrentMap<String, ProtocolState> states = new ConcurrentHashMap<>();

    /** 返回 8 字符短 UUID 作为新的 requestId。 */
    public String newRequestId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 创建一个新的 {@link ProtocolState}（status=pending）并登记。
     *
     * @param type    协议类型，如 shutdown_request / plan_approval_request
     * @param sender  发起方名字（如 lead）
     * @param target  目标方名字（如 alice）
     * @param payload 负载（可为 null）
     */
    public ProtocolState create(String type, String sender, String target, java.util.Map<String, Object> payload) {
        String id = newRequestId();
        ProtocolState state = ProtocolState.builder()
                .requestId(id)
                .type(type)
                .sender(sender)
                .target(target)
                .status("pending")
                .payload(payload)
                .createdAt(System.currentTimeMillis())
                .build();
        states.put(id, state);
        return state;
    }

    /** 直接用给定 id 登记一个 pending 状态（teammate 发起的请求由 lead 这边登记时使用）。 */
    public ProtocolState register(String requestId, String type, String sender, String target,
                                   java.util.Map<String, Object> payload) {
        ProtocolState state = ProtocolState.builder()
                .requestId(requestId)
                .type(type)
                .sender(sender)
                .target(target)
                .status("pending")
                .payload(payload)
                .createdAt(System.currentTimeMillis())
                .build();
        states.put(requestId, state);
        return state;
    }

    /** 返回仍处于 pending / responded 状态的请求；其它情况返回 empty。 */
    public Optional<ProtocolState> getPending(String id) {
        if (id == null) return Optional.empty();
        ProtocolState s = states.get(id);
        if (s == null) return Optional.empty();
        if ("pending".equals(s.getStatus()) || "responded".equals(s.getStatus())) {
            return Optional.of(s);
        }
        return Optional.empty();
    }

    public Optional<ProtocolState> get(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(states.get(id));
    }

    /** 把指定 id 的状态更新为 approved / rejected / responded。 */
    public void markResponded(String id, String status) {
        ProtocolState s = states.get(id);
        if (s != null) {
            s.setStatus(status);
        }
    }

    public Collection<ProtocolState> all() {
        return states.values();
    }
}
