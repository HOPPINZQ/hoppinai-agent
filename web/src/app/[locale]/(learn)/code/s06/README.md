# Agent06（上下文压缩智能体）

## 项目简介

Agent06 在 Agent05 的基础上引入了**上下文压缩（Context Compact）**机制。通过三层渐进式压缩策略，解决长对话中上下文窗口耗尽的问题，使智能体能够处理大型项目并支持无限会话。

## 核心特性

- **三层压缩策略**：Layer 1（微压缩）→ Layer 2（自动压缩）→ Layer 3（手动压缩），由轻到重渐进式压缩
- **微压缩（microCompact）**：每次 LLM 调用前自动执行，将旧工具结果替换为占位符 `[已执行: 工具名]`，保留最近 `KEEP_RECENT` 个完整结果
- **自动压缩（autoCompact）**：token 估算超过 `TOKEN_THRESHOLD` 时自动触发，保存完整对话到 `.transcripts/` 目录，LLM 生成摘要替换原始消息
- **手动压缩（compact 工具）**：用户主动调用，支持 `focus` 参数指定压缩重点
- **待办事项管理**：继承 Agent05 的 TodoManager，3 回合未更新自动催办
- **技能加载系统**：继承 Agent05 的 SkillLoader 两层注入架构
- **子智能体委托**：继承 Agent05 的 SubAgent 静态工具类
- **10 个工具**：bash、read_file、write_file、edit_file、list_files、content_search、sub_agent、todo、load_skill、compact

## 实现原理

### 静态字段桥接模式

Agent06 通过 `public static` 字段将依赖注入到 ToolDefinition 的静态工具定义中：

```java
public class Agent06 extends ZQAgent {
    public static ContextCompactor compactor;
    public static boolean manualCompactRequested = false;
    public static TodoManager todoManager;
    public static SkillLoader skillLoader;

    public Agent06(AnthropicClient client, String model, List<ToolDefinition> tools,
                   ContextCompactor compactor, SkillLoader skillLoader, TodoManager todoManager) {
        super(client, model, tools);
        Agent06.compactor = compactor;
        Agent06.skillLoader = skillLoader;
        Agent06.todoManager = todoManager;
    }
}
```

### 三层压缩策略

```
chatMessage() 调用链:
┌────────────────────────────────────────────────────┐
│  1. microCompact(new ArrayList<>(messageParams))   │
│     └─ 替换旧工具结果为 "[已执行: 工具名]"         │
│     └─ 保留最近 KEEP_RECENT 个完整结果             │
│                                                    │
│  2. estimateTokens(compactedParams) > THRESHOLD?  │
│     └─ YES → autoCompact()                        │
│              ├─ 保存到 .transcripts/*.jsonl        │
│              ├─ LLM 生成摘要                       │
│              └─ 摘要消息替换原始列表                │
│              └─ 同步更新 this.messageParams        │
│                                                    │
│  3. super.chatMessage(compactedParams)             │
└────────────────────────────────────────────────────┘

onToolExecution() 调用链:
┌────────────────────────────────────────────────────┐
│  1. Todo 提醒检查（roundsSinceTodo >= 3）          │
│                                                    │
│  2. microCompact(messageParams)                    │
│                                                    │
│  3. estimateTokens(messageParams) > THRESHOLD?    │
│     └─ YES → autoCompact() + 状态同步              │
│                                                    │
│  4. manualCompactRequested?                        │
│     └─ YES → autoCompact() + 重置标志              │
└────────────────────────────────────────────────────┘
```

### ContextCompactor 核心实现

```java
public class ContextCompactor {
    private final AnthropicClient client;
    private final String model;

    // Layer 1: 微压缩 - 替换旧工具结果为占位符
    public List<MessageParam> microCompact(List<MessageParam> messages) {
        // 1. 收集工具结果位置信息 + 工具ID→名称映射
        // 2. 若结果数 <= KEEP_RECENT，无需压缩
        // 3. 将 thresholdIndex 之前的工具结果替换为占位符
        // 4. 重建消息列表
    }

    // Layer 2/3: 完整压缩 - 保存对话 + 生成摘要
    public List<MessageParam> autoCompact(List<MessageParam> messages) {
        // 1. 保存完整对话到 .transcripts/transcript_<timestamp>.jsonl
        // 2. 截取消息文本（限制80000字符）
        // 3. 调用 LLM 生成摘要（已完成工作、当前状态、关键决策）
        // 4. 返回 [压缩提示+摘要, 助手确认] 两条消息
    }

    // Token 估算（静态方法）
    public static int estimateTokens(List<MessageParam> messages) {
        return OBJECT_MAPPER.writeValueAsString(messages).length() / 4;
    }
}
```

### compact 工具定义

```java
public static ToolDefinition ContentCompactDefinition = new ToolDefinition(
    "compact",
    "手动触发对话压缩。当上下文过大时使用此工具...",
    createInputSchema(
        Map.of("focus", createProperty("string", "压缩重点（可选）")),
        List.of()
    ),
    CompactInput.class,
    Tools::compact
);
```

## 使用方法

### 编译运行

```bash
cd hoppinzq-module-agent-06
mvn clean compile
mvn exec:java -Dexec.mainClass="com.hoppinzq.agent.Agent06"
```

### API 配置

通过 `AIConstants.java` 统一管理：

| 参数 | 值 | 说明 |
|-----|---|------|
| API_KEY | （环境变量） | API 密钥 |
| BASE_URL | `https://hoppinzq.com:520/deepseek/anthropic` | 代理地址 |
| MODEL | `deepseek-chat` | 模型名称 |
| MAX_TOKENS | `12500` | 单次响应最大 token |
| TEMPERATURE | `0.7` | 生成温度 |
| TOKEN_THRESHOLD | `1000` | 自动压缩 token 阈值 |
| KEEP_RECENT | `3` | 微压缩保留最近结果数 |

### 交互示例

```
用户：请读取项目中所有的 Java 文件
智能体：[使用 read_file 读取多个文件]
      [微压缩自动触发，旧结果 → "[已执行: read_file]"]
      [保留最近 3 个完整结果]

用户：继续读取更多文件...
智能体：[token 超过阈值]
      [自动压缩已触发]
      [对话记录已保存: .transcripts/transcript_xxx.jsonl]
      [生成摘要：已完成读取核心文件...]
      [继续工作]

用户：使用 compact 工具手动压缩
智能体：[手动压缩执行]
      [对话记录已保存，摘要已生成]
```

## 项目结构

```
hoppinzq-module-agent-06/
├── src/main/java/com/hoppinzq/agent/
│   ├── Agent06.java                        # 上下文压缩智能体主类
│   ├── base/
│   │   └── ZQAgent.java                    # 智能体基类
│   │   └── SubAgent.java                   # 子智能体静态工具类
│   ├── constant/
│   │   └── AIConstants.java                # 配置常量
│   ├── tool/
│   │   ├── ToolDefinition.java             # 工具定义（10个静态工具）
│   │   ├── Tools.java                      # 工具实现
│   │   ├── schema/                         # 工具输入参数类
│   │   ├── compact/
│   │   │   └── ContextCompactor.java       # 三层压缩器实现
│   │   ├── manager/
│   │   │   └── TodoManager.java            # 待办事项管理器
│   │   └── skill/
│   │       └── SkillLoader.java            # 技能加载器
├── src/main/resources/skills/              # 技能文件目录
├── pom.xml
├── README.md
└── s6.md
```

## 技术栈

| 依赖 | 版本 | 用途 |
|-----|------|------|
| anthropic-java | 1.4.0 | Claude API SDK |
| lombok | 1.18.30 | 注解处理 |
| jackson-databind | 2.15.2 | JSON 序列化 |
| okhttp | 4.12.0 | HTTP 客户端 |
| slf4j-simple | 2.0.17 | 日志框架 |
| mcp | 0.10.0 | Model Context Protocol SDK |

## 设计亮点

- **渐进式压缩**：三层策略由轻到重，微压缩高频静默，自动压缩按需触发，手动压缩用户可控
- **双触发点保障**：`chatMessage()` 和 `onToolExecution()` 都有压缩检查，确保上下文不会膨胀
- **状态同步机制**：压缩返回新列表后，通过 `clear()` + `addAll()` 同步 ZQAgent 的 `messageParams`
- **容错设计**：压缩失败时返回原始消息列表，对话不中断；消息处理出错时跳过该消息继续
- **可追溯性**：完整对话保存为 JSONL 文件，可随时查看历史

## 扩展开发

- **调整压缩阈值**：修改 `AIConstants` 中的 `TOKEN_THRESHOLD` 和 `KEEP_RECENT`
- **自定义摘要策略**：修改 `ContextCompactor.autoCompact()` 中的摘要提示词
- **禁用自动压缩**：将 `TOKEN_THRESHOLD` 设为极大值
- **新增压缩层**：在 `onToolExecution()` 中添加新的压缩触发条件
