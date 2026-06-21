package com.hoppinzq.agent.tool.bus;

import com.anthropic.client.AnthropicClient;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.protocol.ProtocolRegistry;

import java.util.List;

/**
 * 把一个 {@link TeammateRunner} 包成 daemon 线程并启动。
 *
 * @author hoppinzq
 */
public class TeammateSpawner {

    /**
     * 启动一个 teammate 线程。
     *
     * @return 已启动的 daemon 线程
     */
    public static Thread spawn(AnthropicClient client, String model, String name, String role,
                               String initialPrompt, MessageBus bus,
                               List<ToolDefinition> teammateTools,
                               ProtocolRegistry protocolRegistry) {
        TeammateRunner runner = new TeammateRunner(client, model, name, role, initialPrompt, bus,
                teammateTools, protocolRegistry);
        Thread t = new Thread(runner, "teammate-" + name);
        t.setDaemon(true);
        t.start();
        return t;
    }
}
