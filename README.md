# HoppinZQ Agent
![Java](https://img.shields.io/badge/Java-JDK17+-blue?style=flat&logo=openjdk&logoColor=white)
![OpenAI](https://img.shields.io/badge/LLM-Claude-orange?style=flat&logo=Claude&logoColor=white)
> 从零开始，一步步构建一个功能完整的 AI Agent 框架

[演示网站点我](https://hoppinzq.com/agent/index.html)

## 项目简介

HoppinZQ Agent 是一个基于 Anthropic Claude API 构建的 AI Agent 演进式教学项目。项目通过 11 个递进式模块，从最简单的 Bash 执行器逐步演进到具备任务管理、上下文压缩、MCP 协议集成、ReAct 推理模式以及 Web 服务能力的完整 Agent 系统。

每个模块都是独立可运行的 Maven 子项目，继承自统一的父 POM，共享核心依赖。模块间通过功能递增的方式展示 Agent 架构的演进路径。

> 项目是完全参考开源项目 [learn-claude-code](https://github.com/shareAI-lab/learn-claude-code)。不过这个项目是基于Python，本项目：
> - 基于Java
> - 提供了Anthropic API的兼容
> - 额外添加了两个工具——list_file和search_content（基于ripgrep）
> - 重写了子智能体SubAgent、后台任务、Skills的逻辑
> - 添加MCP和ReAct的支持
> - 还开发一个web端的demo

## 📚 写在前面

你需要知道Anthropic风格的API调用👉 [访问在线文档](https://s.apifox.cn/apidoc/docs-site/3406967/doc-3090880)

没错，应用大模型的本质就是接口调用。不会？使用官方提供的anthropic JavaSDK。

### 为什么选择Claude风格的API？

- Claude的API风格跟OpenAI非常像，Claude系大模型同时支持FunctionCall和ToolCall
- 目前编程领域排名第一的大模型是 Claude 系列的大模型

### 关于国内替代方案

不过Claude禁止国内使用了，我们有替代的国产大模型：智谱GLM-5.0
- 其代码能力已对齐Claude系列大模型
- 智谱API提供了Anthropic API的兼容
- Deepseek也已支持Anthropic API的兼容

### 立即体验

![快来试试吧](img/20fc9fce9356bbc3e280f5abe8573e5d.png)

> [🚀 速来拼好模，新用户免费2000万Tokens额度](https://www.bigmodel.cn/claude-code?ic=75JGQG0W9G)

## 开始吧
### 核心特性

- **递进式架构**：从单一工具调用到多工具协同，逐步引入新能力
- **统一基类**：`ZQAgent` 提供标准化的 Agent 循环（用户输入 → LLM 推理 → 工具调用 → 结果返回 → 循环）
- **工具系统**：灵活的 `ToolDefinition` + Schema 定义，支持动态工具注册
- **上下文管理**：三层压缩策略（微压缩、自动压缩、手动压缩）
- **技能系统**：两层注入的 Skill 技能加载机制
- **任务管理**：基于 DAG 的任务图，支持依赖解析
- **后台执行**：守护线程后台任务 + 通知队列注入
- **MCP 协议**：支持 STDIO / SSE / Streamable HTTP 三种传输方式
- **ReAct 模式**：Thought → Action → Observation 推理循环
- **Web 服务**：Spring Boot 集成，会话管理 + REST API

## 模块总览

```
s01 ──→ s02 ──→ s03 ──→ s04 ──→ s05 ──→ s06 ──→ s07 ──→ s08
 │       │       │       │       │       │       │       │
 Bash   文件    Todo   SubAgent  Skill  压缩    任务    后台
 执行    操作    管理    子Agent   技能   管理    DAG    任务
                                                      │
                                                      ▼
s13 ◄─── s14 ◄── agent-web ────────────────────────────┘
 │        │         │
 MCP     ReAct   Web服务
 协议    推理    集成
```

| 模块 | 编号 | 核心能力 | 工具数 | 关键类 |
|------|------|----------|--------|--------|
| [agent-01](hoppinzq-module-agent-01/README.md) | s01 | Bash 执行器 | 1 | `ZQAgent`, `Tools`, `BashInput` |
| [agent-02](hoppinzq-module-agent-02/README.md) | s02 | 文件操作 + 内容搜索 | 7 | `Tools`, `EditFileInput`, `ContentSearchInput` |
| [agent-03](hoppinzq-module-agent-03/README.md) | s03 | Todo 待办管理 | 8 | `TodoManager`, `TodoInput`, `TodoItem` |
| [agent-04](hoppinzq-module-agent-04/README.md) | s04 | SubAgent 子 Agent | 9 | `SubAgent`, `SubAgentInput` |
| [agent-05](hoppinzq-module-agent-05/README.md) | s05 | Skill 技能系统 | 10 | `SkillLoader`, `LoadSkillInput` |
| [agent-06](hoppinzq-module-agent-06/README.md) | s06 | 上下文压缩 | 11 | `ContextCompactor`, `CompactInput` |
| [agent-07](hoppinzq-module-agent-07/README.md) | s07 | 任务管理 DAG | 14 | `TaskManager`, `TaskCreateInput` |
| [agent-08](hoppinzq-module-agent-08/README.md) | s08 | 后台任务执行 | 15 | `BackgroundManager`, `BackgroundRunInput` |
| [agent-13-mcp](hoppinzq-module-agent-13-mcp/README.md) | s13 | MCP 协议集成 | 6+MCP | `MCPAgent`, `McpLoader`, `McpSetting` |
| [agent-14-react](hoppinzq-module-agent-14-react/README.md) | s14 | ReAct 推理模式 | 7 | `Agent14`, `ReActInput` |
| [agent-web](hoppinzq-module-agent-web/README.md) | web | Web 服务集成 | 15+ | `WebZQAgent`, `AgentChatController` |

## 系统架构

```
┌─────────────────────────────────────────────────────────┐
│                     HoppinZQ Agent                      │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐              │
│  │ agent-01 │  │ agent-02 │  │ agent-03 │   核心演进    │
│  │  ~06     │→ │  ~06     │→ │  ~08     │   主线       │
│  │ Bash基础 │  │ 文件操作  │  │ 全功能   │              │
│  └──────────┘  └──────────┘  └──────────┘              │
│                                    │                     │
│                    ┌───────────────┼───────────────┐     │
│                    ▼               ▼               ▼     │
│              ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│              │  s13     │  │  s14     │  │  web     │   │
│              │ MCP协议  │  │ ReAct    │  │ Web服务  │   │
│              │ STDIO    │  │ 推理模式  │  │ Spring   │   │
│              │ SSE      │  │          │  │ MyBatis  │   │
│              │ HTTP     │  │          │  │ REST API │   │
│              └──────────┘  └──────────┘  └──────────┘   │
│                                                          │
├─────────────────────────────────────────────────────────┤
│                    共享基础设施                           │
│  ┌─────────────┐ ┌──────────────┐ ┌────────────────┐   │
│  │  ZQAgent    │ │ ToolSystem   │ │ ContextManager │   │
│  │  Agent循环  │ │ 工具定义执行  │ │ 上下文压缩     │   │
│  └─────────────┘ └──────────────┘ └────────────────┘   │
│  ┌─────────────┐ ┌──────────────┐ ┌────────────────┐   │
│  │ SkillLoader │ │ TaskManager  │ │ BackgroundMgr  │   │
│  │ 技能加载    │ │ 任务DAG      │ │ 后台任务       │   │
│  └─────────────┘ └──────────────┘ └────────────────┘   │
├─────────────────────────────────────────────────────────┤
│                    技术栈                                │
│  Java 17 │ Anthropic SDK 2.17 │ Jackson │ OkHttp │ SLF4J│
│  (Web)   │ Spring Boot 3.4 │ MyBatis-Plus │ MySQL     │
└─────────────────────────────────────────────────────────┘
```

## 快速开始

### 环境要求

- **JDK**: 17+
- **Maven**: 3.8+
- **Anthropic API Key**: 设置环境变量 `ANTHROPIC_API_KEY`

### 编译

```bash
# 编译全部模块
mvn clean compile

# 编译单个模块
mvn clean compile -pl hoppinzq-module-agent-01
```

### 运行

```bash
# 运行指定模块（以 agent-01 为例）
mvn exec:java -pl hoppinzq-module-agent-01 -Dexec.mainClass="com.hoppinzq.agent.Agent01"

# 运行 Web 模块
mvn spring-boot:run -pl hoppinzq-module-agent-web
```

### API Key 配置

所有模块通过 `AIConstants.java` 配置 API Key：

```java
public class AIConstants {
    public static final String API_KEY = System.getenv("ANTHROPIC_API_KEY");
    public static final String MODEL = "claude-sonnet-4-20250514";
}
```

s13 (MCP) 和 s14 (ReAct) 模块默认使用 DeepSeek 模型：

```java
// Agent13 / Agent14
public static final String MODEL = "deepseek-chat";
public static final String BASE_URL = "https://api.deepseek.com/anthropic";
```

## 项目结构

```
hoppinzq-agent/
├── pom.xml                              # 父 POM（统一依赖管理）
├── hoppinzq-module-agent-01/            # s01 - Bash 执行器
│   ├── src/main/java/.../agent/
│   │   ├── Agent01.java                 # 入口
│   │   ├── base/ZQAgent.java            # Agent 基类
│   │   ├── tool/Tools.java              # 工具定义
│   │   └── constant/AIConstants.java    # 常量配置
│   ├── README.md                        # 模块文档
│   └── s1.md                            # 教程文档
├── hoppinzq-module-agent-02/            # s02 - 文件操作
│   └── ...（新增 EditFile, ReadFile, WriteFile, ListFiles, ContentSearch）
├── hoppinzq-module-agent-03/            # s03 - Todo 管理
│   └── ...（新增 TodoManager, TodoInput, TodoItem）
├── hoppinzq-module-agent-04/            # s04 - SubAgent
│   └── ...（新增 SubAgent, SubAgentInput）
├── hoppinzq-module-agent-05/            # s05 - Skill 技能系统
│   └── ...（新增 SkillLoader, LoadSkillInput）
├── hoppinzq-module-agent-06/            # s06 - 上下文压缩
│   └── ...（新增 ContextCompactor, CompactInput）
├── hoppinzq-module-agent-07/            # s07 - 任务管理 DAG
│   └── ...（新增 TaskManager, TaskCreateInput 等）
├── hoppinzq-module-agent-08/            # s08 - 后台任务
│   └── ...（新增 BackgroundManager, BackgroundRunInput 等）
├── hoppinzq-module-agent-13-mcp/        # s13 - MCP 协议
│   └── ...（新增 MCPAgent, McpLoader, McpSetting）
├── hoppinzq-module-agent-14-react/      # s14 - ReAct 推理
│   └── ...（新增 ReActInput）
└── hoppinzq-module-agent-web/           # Web 服务集成
    └── ...（新增 WebZQAgent, Controller, Service, Mapper, Entity）
```

## 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 语言 | Java | 17 |
| AI SDK | Anthropic Java | 2.17.0 |
| 序列化 | Jackson | 2.15.2 |
| HTTP | OkHttp | 4.12.0 |
| 日志 | SLF4J | 2.0.17 |
| 代码简化 | Lombok | 1.18.30 |
| MCP SDK | io.modelcontextprotocol | 0.10.0 |
| Web 框架 | Spring Boot | 3.4.0 |
| ORM | MyBatis-Plus | 3.5.5 |
| 数据库 | MySQL | 8.0.33 |

## 模块演进路线

### 主线（s01 → s08）

每个后续模块继承前一个模块的全部能力，并新增一项核心功能：

```
s01  Bash 执行器
 └── s02  + 文件读写、编辑、搜索（5 个新工具）
      └── s03  + Todo 待办列表（TodoManager + 1 个新工具）
           └── s04  + SubAgent 子 Agent 上下文隔离（1 个新工具）
                └── s05  + Skill 技能两层注入（SkillLoader + 1 个新工具）
                     └── s06  + 三层上下文压缩（ContextCompactor + 1 个新工具）
                          └── s07  + DAG 任务管理（TaskManager + 4 个新工具）
                               └── s08  + 后台任务 + 通知注入（BackgroundManager + 2 个新工具）
```

### 分支（s13、s14、web）

- **s13 (MCP)**：独立分支，基于 s02 的工具集，引入 MCP 协议实现外部工具动态发现与调用
- **s14 (ReAct)**：独立分支，基于 s02 的工具集，实现 Thought → Action → Observation 推理模式
- **web**：集大成模块，基于 s08 全部能力 + s14 ReAct + Spring Boot Web 服务 + MyBatis-Plus 持久化

## 设计理念

### Agent 核心循环

所有模块共享同一核心循环模式：

```
用户输入 → 构建 System Prompt（含工具描述）→ LLM 推理
    ↓
LLM 返回文本 → 输出给用户
LLM 返回工具调用 → 执行工具 → 结果追加到上下文 → 再次调用 LLM（循环）
    ↓
达到最大轮次 或 用户终止 → 结束
```

### 两层技能注入

- **第一层（系统提示词）**：仅注入技能名称，约 100 tokens/技能，让 LLM 知道有哪些技能可用
- **第二层（工具结果）**：当 LLM 调用 `load_skill` 工具时，返回完整技能内容，约 2000 tokens/技能

### 三层上下文压缩

- **微压缩**：每次工具调用后自动移除中间过程细节
- **自动压缩**：上下文接近窗口限制时自动触发摘要压缩
- **手动压缩**：LLM 主动调用 `compact` 工具手动触发压缩

### DAG 任务管理

```
  parse ──→ transform ──→ test
               │
               ▼
              emit ──────→ validate
```

任务节点支持依赖关系（blockedBy / blocks），状态流转：`pending → in_progress → completed`

## 许可证

[MIT License](LICENSE)

## 作者

[hoppinzq](https://github.com/hoppinzq)
