# hoppinzq-module-agent-cli

命令行可视化 Agent。从 `hoppinzq-module-agent-web` 提取核心 agent 代码，去掉 Spring Boot / MySQL / wybuff 业务层，改为纯命令行调用。终端里用增强 ANSI 格式化（box-drawing 面板）可视化 agent 的思考、工具调用、结果、耗时。

## 运行

```bash
mvn compile
mvn exec:java -Dexec.mainClass="com.hoppinzq.agent.AgentCLI"
```

输入 `退出` 或 Ctrl+C 退出。

## 与 web 模块的区别

- 单会话：去掉了 `SessionContextHolder`（保留 stub 固定为 `cli-session`），不再用 `ConcurrentHashMap<sessionId, messages>`。
- 输出：`log.info()` → `CliRenderer` 的 ANSI box-drawing 面板（思考 / 工具调用 / 返回值 / AI 回复 / 错误 / token 统计）。
- 工具调用插桩：`invokeTool()` 前后用 `System.nanoTime()` 测耗时。
- 工具返回值 > 500 字符时面板中只显示前 200 + 末尾 100 + `[已截断至 N 字]`。

## 工具集合（16 个）

bash / read_file / write_file / edit_file / list_files / content_search / sub_agent / todo / load_skill / compact / task_create / task_update / task_list / task_get / background_run / check_background
