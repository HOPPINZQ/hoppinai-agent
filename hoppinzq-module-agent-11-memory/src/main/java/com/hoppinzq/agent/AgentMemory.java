package com.hoppinzq.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.memory.MemoryExtractor;
import com.hoppinzq.agent.tool.memory.MemorySelector;
import com.hoppinzq.agent.tool.memory.MemoryStore;

import java.util.ArrayList;
import java.util.List;

import static com.hoppinzq.agent.constant.AIConstants.*;

/**
 * 持久记忆示例（对应 Python 教程 s09_memory）。
 *
 * <p>跨会话记忆：每轮 LLM 调用前从 .memory/ 索引里挑相关条目注入 system prompt；
 * 每轮结束后用 LLM 抽取新记忆写入。重启后下次对话即可读到上次的记忆。
 *
 * <p>文件结构：
 * <pre>
 * {ROOT}/
 *   ├── .memory/
 *   │   ├── user_prefers_vim.md      (YAML frontmatter + 正文)
 *   │   └── ...
 *   └── MEMORY.md                    (索引，每条一行摘要)
 * </pre>
 *
 * <p><b>示例 prompt：</b>
 * <ul>
 *   <li>第一轮："我喜欢用 vim 编辑器" → 应在 .memory/ 落盘一条 user 偏好</li>
 *   <li>第二轮（重启后）："我喜欢用什么编辑器？" → 应从 .memory/ 读出 vim</li>
 * </ul>
 *
 * @author hoppinzq
 */
public class AgentMemory {

    public static void main(String[] args) {
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(API_KEY)
                .baseUrl(BASE_URL)
                .build();

        List<ToolDefinition> tools = new ArrayList<>();
        tools.add(ToolDefinition.BashDefinition);
        tools.add(ToolDefinition.ReadFileDefinition);
        tools.add(ToolDefinition.WriteFileDefinition);
        tools.add(ToolDefinition.EditFileDefinition);
        tools.add(ToolDefinition.ListFilesDefinition);

        ZQAgent agent = new ZQAgent(client, MODEL, tools);
        agent.setSystemPrompt(buildSystemPrompt());

        // 装配记忆三件套
        MemoryStore store = new MemoryStore(ROOT, client);
        agent.setMemoryStore(store);
        agent.setMemorySelector(new MemorySelector(store));
        agent.setMemoryExtractor(new MemoryExtractor(store));

        System.out.printf("\u001b[95m[memory]\u001b[0m 工作目录 %s，已存在 %d 条记忆%n",
                ROOT, store.fileCount());

        agent.run();
    }

    private static String buildSystemPrompt() {
        String osName = System.getProperty("os.name").toLowerCase();
        return String.format("""
                ## 角色
                你是一个具备跨会话持久记忆的开发助手。系统会自动：
                  - 每轮从 .memory/ 选相关记忆注入到这段系统提示后
                  - 每轮结束时把你新提供的事实写入 .memory/
                如果用户告诉你偏好、纠正你、或描述项目结构，请用陈述句表达，便于记忆抽取器落盘。

                ## 可用工具
                - bash / read_file / write_file / edit_file / list_files

                ## 环境
                - 操作系统：%s
                - 工作目录：%s""", osName, ROOT);
    }
}
