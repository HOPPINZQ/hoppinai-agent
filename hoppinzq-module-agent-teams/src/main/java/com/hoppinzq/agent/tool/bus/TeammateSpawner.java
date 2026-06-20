package com.hoppinzq.agent.tool.bus;

import com.anthropic.client.AnthropicClient;
import com.hoppinzq.agent.tool.ToolDefinition;

import java.util.List;

/**
 * teammate 守护线程工厂。
 *
 * <p>静态方法 {@link #spawn} 构造一个 {@link TeammateRunner}，
 * 包到命名为 {@code teammate-<name>} 的守护线程里启动，
 * 并立即给 lead 发一条 {@code started} 消息，让 lead 知道这个 teammate 已经上线。
 *
 * @author hoppinzq
 */
public class TeammateSpawner {

    private TeammateSpawner() {
    }

    /**
     * 启动一个 teammate。
     *
     * @return 已启动的守护线程，调用方一般无需 join
     */
    public static Thread spawn(AnthropicClient client,
                               String model,
                               String name,
                               String role,
                               String prompt,
                               MessageBus bus,
                               List<ToolDefinition> teammateTools) {
        TeammateRunner runner = new TeammateRunner(client, model, name, role, prompt, bus, teammateTools);
        Thread thread = new Thread(runner, "teammate-" + name);
        thread.setDaemon(true);
        thread.start();
        // 立即通知 lead：我上线了
        MessageBus.send(name, "lead", "started", "message");
        return thread;
    }
}
