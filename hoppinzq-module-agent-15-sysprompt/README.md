# hoppinzq-module-agent-sysprompt（动态系统提示词）

对应 Python 教程 `s10_system_prompt`。系统提示词不再写死在 `buildSystemPrompt()` 里，
改由 `PromptAssembler` 在每轮 LLM 调用前按 `AgentContext` 动态装配。

## 4 个 section

| section | 触发条件 |
|---|---|
| `identity` | 永远挂 |
| `tools` | 当前 tools 列表非空 |
| `workspace` | 永远挂 |
| `memory` | **仅当 MEMORY.md 存在时挂**（读取索引摘要） |

context hash 相同时走缓存，避免重复渲染。

## 新增文件

- `tool/prompt/AgentContext.java` — workspace / os / tools / memoryEnabled / userPrompt
- `tool/prompt/PromptAssembler.java` — Section 注册表 + `get(ctx)` 带 hash 缓存

## ZQAgent 本地副本修改点

1. **去掉** `setSystemPrompt` 字段 —— 不再一次性赋值
2. 新增 `promptAssembler` 字段
3. 每轮 while 入口：`buildContext(userInput)` 检测 MEMORY.md 是否存在 + 取当前工具名
4. `chatMessage(params, systemPrompt)` 接受动态 system

## 配置 & 运行

```bash
mvn -pl hoppinzq-module-agent-sysprompt -am compile
mvn -pl hoppinzq-module-agent-sysprompt exec:java -Dexec.mainClass=com.hoppinzq.agent.AgentSysPrompt
```

## 场景测试

```text
1. 启动 → 默认无 MEMORY.md → 系统提示只含 identity + tools + workspace
2. 让 agent 写一个 MEMORY.md（write_file 工具）
3. 下一轮 agent 调用 → system 提示自动追加 memory 段（含 MEMORY.md 摘要）
4. 用 bash 删掉 MEMORY.md → 再下一轮 → memory 段消失
```

也可手工创建：

```bash
echo "# Memory Index" > hoppinzq-module-agent-sysprompt/MEMORY.md
```

观察下一轮 agent 的系统提示是否自动挂上 memory 段。

@hoppinzq
