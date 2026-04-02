# HoppinZQ Agent
use[![zread](https://img.shields.io/badge/Ask_Zread-_.svg?style=flat&color=00b0aa&labelColor=000000&logo=data%3Aimage%2Fsvg%2Bxml%3Bbase64%2CPHN2ZyB3aWR0aD0iMTYiIGhlaWdodD0iMTYiIHZpZXdCb3g9IjAgMCAxNiAxNiIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPHBhdGggZD0iTTQuOTYxNTYgMS42MDAxSDIuMjQxNTZDMS44ODgxIDEuNjAwMSAxLjYwMTU2IDEuODg2NjQgMS42MDE1NiAyLjI0MDFWNC45NjAxQzEuNjAxNTYgNS4zMTM1NiAxLjg4ODEgNS42MDAxIDIuMjQxNTYgNS42MDAxSDQuOTYxNTZDNS4zMTUwMiA1LjYwMDEgNS42MDE1NiA1LjMxMzU2IDUuNjAxNTYgNC45NjAxVjIuMjQwMUM1LjYwMTU2IDEuODg2NjQgNS4zMTUwMiAxLjYwMDEgNC45NjE1NiAxLjYwMDFaIiBmaWxsPSIjZmZmIi8%2BCjxwYXRoIGQ9Ik00Ljk2MTU2IDEwLjM5OTlIMi4yNDE1NkMxLjg4ODEgMTAuMzk5OSAxLjYwMTU2IDEwLjY4NjQgMS42MDE1NiAxMS4wMzk5VjEzLjc1OTlDMS42MDE1NiAxNC4xMTM0IDEuODg4MSAxNC4zOTk5IDIuMjQxNTYgMTQuMzk5OUg0Ljk2MTU2QzUuMzE1MDIgMTQuMzk5OSA1LjYwMTU2IDE0LjExMzQgNS42MDE1NiAxMy43NTk5VjExLjAzOTlDNS42MDE1NiAxMC42ODY0IDUuMzE1MDIgMTAuMzk5OSA0Ljk2MTU2IDEwLjM5OTlaIiBmaWxsPSIjZmZmIi8%2BCjxwYXRoIGQ9Ik0xMy43NTg0IDEuNjAwMUgxMS4wMzg0QzEwLjY4NSAxLjYwMDEgMTAuMzk4NCAxLjg4NjY0IDEwLjM5ODQgMi4yNDAxVjQuOTYwMUMxMC4zOTg0IDUuMzEzNTYgMTAuNjg1IDUuNjAwMSAxMS4wMzg0IDUuNjAwMUgxMy43NTg0QzE0LjExMTkgNS42MDAxIDE0LjM5ODQgNS4zMTM1NiAxNC4zOTg0IDQuOTYwMVYyLjI0MDFDMTQuMzk4NCAxLjg4NjY0IDE0LjExMTkgMS42MDAxIDEzLjc1ODQgMS42MDAxWiIgZmlsbD0iI2ZmZiIvPgo8cGF0aCBkPSJNNCAxMkwxMiA0TDQgMTJaIiBmaWxsPSIjZmZmIi8%2BCjxwYXRoIGQ9Ik00IDEyTDEyIDQiIHN0cm9rZT0iI2ZmZiIgc3Ryb2tlLXdpZHRoPSIxLjUiIHN0cm9rZS1saW5lY2FwPSJyb3VuZCIvPgo8L3N2Zz4K&logoColor=ffffff)](https://zread.ai/HOPPINZQ/hoppinai-agent)
  

【 [英文文档](README_EN.md) 】 - 【 [中文文档](README.md) 】
![Java](https://img.shields.io/badge/Java-JDK17+-blue?style=flat&logo=openjdk&logoColor=white)
![OpenAI](https://img.shields.io/badge/LLM-Claude-orange?style=flat&logo=Claude&logoColor=white)
> Build a fully functional AI Agent framework from scratch, step by step

[Live Demo](https://hoppinzq.com/agent/index.html)

## Project Overview

HoppinZQ Agent is an evolutionary teaching project for building AI Agents based on the Anthropic Claude API. Through 11 progressive modules, it evolves from a simple Bash executor into a complete Agent system with task management, context compression, MCP protocol integration, ReAct reasoning, and Web service capabilities.

Each module is an independently runnable Maven sub-project that inherits from a unified parent POM and shares core dependencies. Modules demonstrate the evolutionary path of Agent architecture through incremental feature additions.

> This project is fully inspired by the open-source project [learn-claude-code](https://github.com/shareAI-lab/learn-claude-code). However, that project is based on Python, while this project:
> - Is built with Java
> - Provides Anthropic API compatibility
> - Adds two extra tools — `list_file` and `search_content` (based on ripgrep)
> - Rewrites the logic for SubAgent, background tasks, and Skills
> - Adds MCP and ReAct support
> - Includes a web-based demo

## 📚 Prerequisites

You need to understand Anthropic-style API calls. 👉 [View Online Documentation](https://s.apifox.cn/apidoc/docs-site/3406967/doc-3090880)

At its core, using large language models is just API calls. Not sure how? Use the official Anthropic Java SDK.

### Why Choose Claude-style API?

- Claude's API style is very similar to OpenAI's, and Claude-series models support both FunctionCall and ToolCall
- Claude-series models currently rank #1 in coding benchmarks

### Alternative Solutions for China Users

Claude is not available in China, but we have domestic alternatives: Zhipu GLM-5.0
- Its coding capabilities are on par with Claude-series models
- Zhipu API provides Anthropic API compatibility
- DeepSeek also supports Anthropic API compatibility

### Try It Now

![Try it now](img/BigmodelPoster.png)

> [🚀 Sign up for Zhipu BigModel — New users get 20 million free Tokens](https://www.bigmodel.cn/claude-code?ic=75JGQG0W9G)

## Getting Started
### Core Features

- **Progressive Architecture**: From single tool calls to multi-tool collaboration, gradually introducing new capabilities
- **Unified Base Class**: `ZQAgent` provides a standardized Agent loop (User Input → LLM Inference → Tool Call → Result Return → Loop)
- **Tool System**: Flexible `ToolDefinition` + Schema definitions with dynamic tool registration
- **Context Management**: Three-layer compression strategy (micro, automatic, manual)
- **Skill System**: Two-layer injection Skill loading mechanism
- **Task Management**: DAG-based task graph with dependency resolution
- **Background Execution**: Daemon thread background tasks + notification queue injection
- **MCP Protocol**: Supports STDIO / SSE / Streamable HTTP transports
- **ReAct Mode**: Thought → Action → Observation reasoning loop
- **Web Service**: Spring Boot integration with session management + REST API

## Module Overview

```
s01 ──→ s02 ──→ s03 ──→ s04 ──→ s05 ──→ s06 ──→ s07 ──→ s08
 │       │       │       │       │       │       │       │
 Bash   File    Todo   SubAgent  Skill  Context  Task   Background
 Exec   Ops     Mgr    SubAgent  Skill  Compress  DAG    Tasks
                                                      │
                                                      ▼
s13 ◄─── s14 ◄── agent-web ────────────────────────────┘
 │        │         │
 MCP     ReAct   Web Service
 Protocol Reasoning Integration
```

| Module | ID | Core Capability | Tools | Key Classes |
|--------|----|-----------------|-------|-------------|
| [agent-01](hoppinzq-module-agent-01/README.md) | s01 | Bash Executor | 1 | `ZQAgent`, `Tools`, `BashInput` |
| [agent-02](hoppinzq-module-agent-02/README.md) | s02 | File Ops + Content Search | 7 | `Tools`, `EditFileInput`, `ContentSearchInput` |
| [agent-03](hoppinzq-module-agent-03/README.md) | s03 | Todo Management | 8 | `TodoManager`, `TodoInput`, `TodoItem` |
| [agent-04](hoppinzq-module-agent-04/README.md) | s04 | SubAgent | 9 | `SubAgent`, `SubAgentInput` |
| [agent-05](hoppinzq-module-agent-05/README.md) | s05 | Skill System | 10 | `SkillLoader`, `LoadSkillInput` |
| [agent-06](hoppinzq-module-agent-06/README.md) | s06 | Context Compression | 11 | `ContextCompactor`, `CompactInput` |
| [agent-07](hoppinzq-module-agent-07/README.md) | s07 | Task Management DAG | 14 | `TaskManager`, `TaskCreateInput` |
| [agent-08](hoppinzq-module-agent-08/README.md) | s08 | Background Task Execution | 15 | `BackgroundManager`, `BackgroundRunInput` |
| [agent-13-mcp](hoppinzq-module-agent-13-mcp/README.md) | s13 | MCP Protocol Integration | 6+MCP | `MCPAgent`, `McpLoader`, `McpSetting` |
| [agent-14-react](hoppinzq-module-agent-14-react/README.md) | s14 | ReAct Reasoning Mode | 7 | `Agent14`, `ReActInput` |
| [agent-web](hoppinzq-module-agent-web/README.md) | web | Web Service Integration | 15+ | `WebZQAgent`, `AgentChatController` |

## System Architecture

```
┌─────────────────────────────────────────────────────────┐
│                     HoppinZQ Agent                      │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐              │
│  │ agent-01 │  │ agent-02 │  │ agent-03 │   Core       │
│  │  ~06     │→ │  ~06     │→ │  ~08     │   Evolution  │
│  │ Bash     │  │ File Ops │  │ Full     │   Mainline   │
│  └──────────┘  └──────────┘  └──────────┘              │
│                                    │                     │
│                    ┌───────────────┼───────────────┐     │
│                    ▼               ▼               ▼     │
│              ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│              │  s13     │  │  s14     │  │  web     │   │
│              │ MCP      │  │ ReAct    │  │ Web      │   │
│              │ STDIO    │  │ Reasoning│  │ Spring   │   │
│              │ SSE      │  │          │  │ MyBatis  │   │
│              │ HTTP     │  │          │  │ REST API │   │
│              └──────────┘  └──────────┘  └──────────┘   │
│                                                          │
├─────────────────────────────────────────────────────────┤
│                  Shared Infrastructure                   │
│  ┌─────────────┐ ┌──────────────┐ ┌────────────────┐   │
│  │  ZQAgent    │ │ ToolSystem   │ │ ContextManager │   │
│  │  Agent Loop │ │ Tool Def/Exec│ │ Context Compress│  │
│  └─────────────┘ └──────────────┘ └────────────────┘   │
│  ┌─────────────┐ ┌──────────────┐ ┌────────────────┐   │
│  │ SkillLoader │ │ TaskManager  │ │ BackgroundMgr  │   │
│  │ Skill Load  │ │ Task DAG     │ │ Background Task│   │
│  └─────────────┘ └──────────────┘ └────────────────┘   │
├─────────────────────────────────────────────────────────┤
│                    Tech Stack                            │
│  Java 17 │ Anthropic SDK 2.17 │ Jackson │ OkHttp │ SLF4J│
│  (Web)   │ Spring Boot 3.4 │ MyBatis-Plus │ MySQL     │
└─────────────────────────────────────────────────────────┘
```

## Quick Start

### Requirements

- **JDK**: 17+
- **Maven**: 3.8+
- **AIConstants**: 
     ```text
    // anthropic url
    public static final String BASE_URL = "https://api.deepseek.com/anthropic";
    // API KEY
    public static final String API_KEY = "sk-xxxxxxxxx";
    // model
    public static final String MODEL = "deepseek-chat";
    ```

### Build

```bash
# Build all modules
mvn clean compile

# Build a single module
mvn clean compile -pl hoppinzq-module-agent-01
```

### Run

```bash
# Run a specific module (e.g., agent-01)
mvn exec:java -pl hoppinzq-module-agent-01 -Dexec.mainClass="com.hoppinzq.agent.Agent01"

# Run Web module
mvn spring-boot:run -pl hoppinzq-module-agent-web
```

### API Key Configuration

All modules configure the API Key through `AIConstants.java`:

```text
// anthropic url
public static final String BASE_URL = "https://api.deepseek.com/anthropic";
// API KEY
public static final String API_KEY = "sk-xxxxxxxxx";
// model
public static final String MODEL = "deepseek-chat";
```

## Project Structure

```
hoppinzq-agent/
├── pom.xml                              # Parent POM (unified dependency management)
├── hoppinzq-module-agent-01/            # s01 - Bash Executor
│   ├── src/main/java/.../agent/
│   │   ├── Agent01.java                 # Entry point
│   │   ├── base/ZQAgent.java            # Agent base class
│   │   ├── tool/Tools.java              # Tool definitions
│   │   └── constant/AIConstants.java    # Constants config
│   ├── README.md                        # Module documentation
│   └── s1.md                            # Tutorial document
├── hoppinzq-module-agent-02/            # s02 - File Operations
│   └── ... (adds EditFile, ReadFile, WriteFile, ListFiles, ContentSearch)
├── hoppinzq-module-agent-03/            # s03 - Todo Management
│   └── ... (adds TodoManager, TodoInput, TodoItem)
├── hoppinzq-module-agent-04/            # s04 - SubAgent
│   └── ... (adds SubAgent, SubAgentInput)
├── hoppinzq-module-agent-05/            # s05 - Skill System
│   └── ... (adds SkillLoader, LoadSkillInput)
├── hoppinzq-module-agent-06/            # s06 - Context Compression
│   └── ... (adds ContextCompactor, CompactInput)
├── hoppinzq-module-agent-07/            # s07 - Task Management DAG
│   └── ... (adds TaskManager, TaskCreateInput, etc.)
├── hoppinzq-module-agent-08/            # s08 - Background Tasks
│   └── ... (adds BackgroundManager, BackgroundRunInput, etc.)
├── hoppinzq-module-agent-13-mcp/        # s13 - MCP Protocol
│   └── ... (adds MCPAgent, McpLoader, McpSetting)
├── hoppinzq-module-agent-14-react/      # s14 - ReAct Reasoning
│   └── ... (adds ReActInput)
└── hoppinzq-module-agent-web/           # Web Service Integration
    └── ... (adds WebZQAgent, Controller, Service, Mapper, Entity)
```

## Tech Stack

| Category | Technology | Version |
|----------|-----------|---------|
| Language | Java | 17 |
| AI SDK | Anthropic Java | 2.17.0 |
| Serialization | Jackson | 2.15.2 |
| HTTP | OkHttp | 4.12.0 |
| Logging | SLF4J | 2.0.17 |
| Boilerplate | Lombok | 1.18.30 |
| MCP SDK | io.modelcontextprotocol | 0.10.0 |
| Web Framework | Spring Boot | 3.4.0 |
| ORM | MyBatis-Plus | 3.5.5 |
| Database | MySQL | 8.0.33 |

## Module Evolution Path

### Mainline (s01 → s08)

Each subsequent module inherits all capabilities from the previous one and adds one core feature:

```
s01  Bash Executor
 └── s02  + File read/write, edit, search (5 new tools)
      └── s03  + Todo list management (TodoManager + 1 new tool)
           └── s04  + SubAgent context isolation (1 new tool)
                └── s05  + Skill two-layer injection (SkillLoader + 1 new tool)
                     └── s06  + Three-layer context compression (ContextCompactor + 1 new tool)
                          └── s07  + DAG task management (TaskManager + 4 new tools)
                               └── s08  + Background tasks + notification injection (BackgroundManager + 2 new tools)
```

### Branches (s13, s14, web)

- **s13 (MCP)**: Independent branch based on s02's toolset, introducing MCP protocol for dynamic external tool discovery and invocation
- **s14 (ReAct)**: Independent branch based on s02's toolset, implementing Thought → Action → Observation reasoning pattern
- **web**: Comprehensive module combining all s08 capabilities + s14 ReAct + Spring Boot Web service + MyBatis-Plus persistence

## Design Philosophy

### Agent Core Loop

All modules share the same core loop pattern:

```
User Input → Build System Prompt (with tool descriptions) → LLM Inference
    ↓
LLM returns text → Output to user
LLM returns tool call → Execute tool → Append result to context → Call LLM again (loop)
    ↓
Max rounds reached OR User terminates → End
```

### Two-Layer Skill Injection

- **Layer 1 (System Prompt)**: Only injects skill names, ~100 tokens/skill, letting the LLM know which skills are available
- **Layer 2 (Tool Result)**: When the LLM calls the `load_skill` tool, returns the full skill content, ~2000 tokens/skill

### Three-Layer Context Compression

- **Micro Compression**: Automatically removes intermediate process details after each tool call
- **Auto Compression**: Automatically triggers summary compression when context approaches window limits
- **Manual Compression**: LLM proactively calls the `compact` tool to manually trigger compression

### DAG Task Management

```
  parse ──→ transform ──→ test
               │
               ▼
              emit ──────→ validate
```

Task nodes support dependency relationships (blockedBy / blocks), with state transitions: `pending → in_progress → completed`

## License

[MIT License](LICENSE)

## Author

[hoppinzq](https://github.com/hoppinzq)
