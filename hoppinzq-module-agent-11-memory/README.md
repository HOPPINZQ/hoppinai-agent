# hoppinzq-module-agent-memory（持久记忆）

对应 Python 教程 `s09_memory`。跨会话记忆系统，让 agent 在重启后仍记得上次对话中的事实。

## 核心机制

```
每轮 LLM 调用前               每轮工具循环后
       │                              │
       ▼                              ▼
MemorySelector             MemoryExtractor
读 MEMORY.md 索引           从对话中抽事实
→ 调 LLM 选相关项           → 调 LLM 输出 TYPE|name|tags|body
→ 读 .memory/*.md 正文      → 写入 .memory/{type}_{name}.md
→ 注入 systemPrompt         → 更新 MEMORY.md 索引
                             → 文件数 ≥10 触发 consolidate
```

## 文件格式

`.memory/user_prefers_vim.md`：

```
---
type: user
name: prefers_vim
tags: [editor, vim]
created: 2026-06-19T10:15:30Z
---

用户偏好使用 vim 编辑器。
```

`MEMORY.md`：

```
# Memory Index

- [user] prefers_vim #editor #vim — 用户偏好使用 vim 编辑器
- [feedback] no_emoji #style — 不要在回复中使用 emoji
```

## 四类记忆

| type | 含义 |
|---|---|
| `user` | 用户偏好/身份 |
| `feedback` | 用户纠正过的错误做法 |
| `project` | 项目级约定（目录、技术栈、CI 等） |
| `reference` | 外部链接/路径 |

## 新增文件

- `tool/memory/MemoryRecord.java` — 数据类 + `toIndexLine()`
- `tool/memory/MemoryStore.java` — 读写文件、维护索引、YAML 解析、`askLlm` 公共方法
- `tool/memory/MemorySelector.java` — 每轮选记忆（调 1 次 LLM）
- `tool/memory/MemoryExtractor.java` — 每轮抽记忆（调 1 次 LLM）

## ZQAgent 本地副本修改点

1. 新增 `memoryStore / memorySelector / memoryExtractor` 三字段
2. `chatMessage` 改签为 `chatMessage(params, effectiveSystem)`，每轮允许传不同 system prompt
3. 每轮 while 入口：`selectAndFormat(userInput)` 追加到 systemPrompt 形成 `effectiveSystem`
4. 每轮工具循环退出后：`extractAndSave`，写盘后 `consolidateIfNeeded`

## 配置 & 运行

```bash
mvn -pl hoppinzq-module-agent-memory -am compile
mvn -pl hoppinzq-module-agent-memory exec:java -Dexec.mainClass=com.hoppinzq.agent.AgentMemory
```

## 场景测试

1. 第一次运行：
   - 输入 "我喜欢用 vim 编辑器"
   - 检查 `.memory/user_prefers_vim.md` 与 `MEMORY.md` 是否生成
2. 重启程序后：
   - 输入 "我喜欢用什么编辑器？"
   - 应看到 `[memory] 已注入相关记忆`，agent 从记忆读出 vim 回答
3. 持续交互 10+ 次：
   - 触发 `[memory] 文件数已到 N，建议 consolidate`

@hoppinzq
