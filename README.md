# 从0编写一个Claude Code

![Java](https://img.shields.io/badge/Java(添加mcp支持)-JDK17+-blue?style=flat&logo=openjdk&logoColor=white)
![Java](https://img.shields.io/badge/Java(不添加mcp支持)-JDK8+-blue?style=flat&logo=openjdk&logoColor=white)
![OpenAI](https://img.shields.io/badge/LLM-Claude-green?style=flat&logo=Claude&logoColor=white)
## 📚 关于API调用

你需要知道Claude风格的API调用👉 [访问在线文档](https://s.apifox.cn/apidoc/docs-site/3406967/doc-3090880)

没错，应用大模型的本质就是接口调用。不会？使用官方提供的anthropic JavaSDK。

### 为什么选择Claude风格的API？

- Claude的API风格跟OpenAI非常像，Claude系大模型同时支持FunctionCall和ToolCall
- 目前编程领域排名第一的大模型是Claude Sonnet 4

### 关于国内替代方案

不过Claude禁止国内使用了，我们有替代的国产大模型：智谱GLM-4.6
- 其代码能力已对齐Claude Sonnet 4
- 智谱API提供了Claude API的兼容
- 自然支持anthropic JavaSDK对大模型的调用，因为都是OpenAI协议

这，就是协议的力量！

### 立即体验

![快来试试吧](img/20fc9fce9356bbc3e280f5abe8573e5d.png)

> [🚀 速来拼好模，新用户免费2000万Tokens额度](https://www.bigmodel.cn/claude-code?ic=75JGQG0W9G)

# 从0编写一个Claude Code - 集成了AI智能体的IDE开发
不知道大家是否都在用集成了AI智能体的IDE开发呢？
比如常用的几个：
- 1、[Claude Code](https://www.aicodemirror.com/)
- 2、[Cursor](https://cursor.com/cn)
- 3、[Trae（字节）](https://www.trae.cn/)
- 4、[CodeBuddy（腾讯）](https://copilot.tencent.com/ide)  

我刚开始用的是Cursor，后面一直用Trae。
我的感受是非常nice，Trae对前端工程代码生成非常好，还能预览前端页面。
还可以配置提示词、MCP、知识库等，让AI通过Figma设计UI、绘制前端页面，
让AI通过云平台搭建运行环境，让AI编写云函数实现后端逻辑，让AI部署项目......太强了    
有没有想过，这是怎么实现的呢？其实很简单，调用操作系统接口就行，当然提示词、RAG、MCP等是LLM固有的能力，我们只需要配置就行。
其他的无非就是这几个功能：访问文件系统，编辑文件，搜索文件内容，调用终端，调用联网搜索接口。这些我们写代码就能实现。  
将上面的功能封装成提示词，functionCall，ToolCall或者MCP，就能为LLM所用。所以我们先来看看这些IDE自带了哪些工具：

<details>
<summary><b>Claude Code内置的工具列表如下</b></summary>

### 📁 文件操作工具

#### Read - 读取本地文件系统中的文件
- 支持读取图片、PDF、Jupyter笔记本等格式
- 可指定读取起始行和行数限制
- 返回带行号的内容

#### Write - 向本地文件系统写入文件
- 如果文件存在会覆盖
- 写入前必须先读取现有文件
- 支持写入各种格式文件

#### Edit - 在文件中进行精确的字符串替换
- 编辑前必须先读取文件
- 支持 replace_all 参数替换所有实例
- 必须提供唯一的匹配字符串

#### Glob - 快速文件模式匹配
- 支持通配符模式如 "**/*.js"
- 按修改时间排序返回文件路径
- 适合按文件名查找

#### Grep - 基于 ripgrep 的强大搜索工具
- 支持正则表达式语法
- 可按文件类型过滤
- 支持多行匹配和上下文显示  

I### 🛠️ 开发工具

#### Bash - 执行bash命令
- 支持持久化shell会话
- 可设置超时时间
- 支持后台运行

#### Task - 启动专门的代理处理复杂任务
- general-purpose: 通用代理，用于研究和多步任务
- statusline-setup: 配置状态行设置
- output-style-setup: 创建输出样式

#### TodoWrite - 创建和管理结构化任务列表
- 支持任务状态跟踪（pending/in_progress/completed）
- 帮助组织和跟踪复杂任务进度  

### 🌐 网络工具

#### WebFetch - 获取和分析网页内容
- 将HTML转换为markdown
- 使用AI模型处理内容
- 支持URL重定向

#### WebSearch - 搜索网络信息
- 提供最新信息
- 支持域名过滤
- 仅在美国地区可用  
 
### 📋 其他工具

#### NotebookEdit - 编辑Jupyter笔记本
- 替换单元格内容
- 支持插入/删除单元格
- 可指定代码或markdown类型

#### ExitPlanMode - 退出规划模式
- 在完成规划后使用
- 向用户展示执行计划

#### SlashCommand - 执行自定义斜杠命令
- 只能执行已定义的命令
- 支持带参数的命令  
</details>

<details>
<summary><b>Cursor内置的工具列表如下</b></summary>

### 📁 文件操作工具
#### read_file
- 定义：读取本地文件系统上的文件内容
- 功能：可以读取任何文件，支持指定行号范围，显示行号，返回文件完整内容
- 使用场景：查看代码文件、配置文件、文档等

#### search_replace
- 定义：在文件中进行精确的字符串替换
- 功能：支持单个替换或全部替换，保持原有格式，可以替换变量名、函数名等
- 使用场景：重构代码、修复bug、更新配置

#### MultiEdit
- 定义：在单个文件中进行多次编辑操作
- 功能：原子性操作，要么全部成功要么全部失败，支持批量修改
- 使用场景：复杂的代码重构、批量更新

#### write
- 定义：创建新文件或覆盖现有文件
- 功能：写入完整文件内容，支持创建任何类型的文件
- 使用场景：创建新组件、配置文件、脚本等

#### delete_file
- 定义：删除指定路径的文件
- 功能：安全删除，如果文件不存在会优雅处理
- 使用场景：清理临时文件、移除不需要的文件

### 🔍 搜索工具
#### codebase_search
- 定义：语义搜索工具，在代码库中查找相关功能
- 功能：理解代码含义，可以搜索功能而非精确文本
- 使用场景：查找特定功能实现、理解代码架构

#### grep
- 定义：基于ripgrep的强大文本搜索工具
- 功能：支持正则表达式、多行匹配、上下文显示、文件类型过滤
- 使用场景：精确搜索函数名、变量名、错误信息等

#### glob_file_search
- 定义：根据文件名模式搜索文件
- 功能：快速搜索匹配的文件路径，按修改时间排序
- 使用场景：查找特定类型的文件（如所有.js文件）

#### list_dir
- 定义：列出目录中的文件和子目录
- 功能：显示目录结构，支持忽略特定文件模式
- 使用场景：浏览项目结构、查看文件组织

### 💻 开发工具
#### run_terminal_cmd
- 定义：在用户系统上执行终端命令
- 功能：运行编译、测试、安装等命令，支持后台执行
- 使用场景：构建项目、运行测试、安装依赖

#### read_lints
- 定义：读取和显示代码检查错误
- 功能：获取linter诊断信息，支持指定文件或目录
- 使用场景：修复代码错误、提高代码质量

#### edit_notebook
- 定义：编辑Jupyter笔记本单元格
- 功能：修改现有单元格或创建新单元格，支持多种语言
- 使用场景：数据科学项目、交互式编程

### 🌐 网络工具
#### web_search
- 定义：搜索网络获取实时信息
- 功能：获取最新技术信息、文档、API更新等
- 使用场景：查找最新技术资料、解决技术问题

#### fetch_pull_request
- 定义：获取GitHub拉取请求的详细信息
- 功能：查看PR内容、代码差异、评论等
- 使用场景：代码审查、了解项目变更

### 📋 项目管理
#### todo_write
- 定义：创建和管理结构化任务列表
- 功能：跟踪进度、组织复杂任务、管理任务状态
- 使用场景：项目管理、任务分解、进度跟踪  
  
</details>


<details>
<summary><b>trae内置的工具列表如下</b></summary>

### 🔍 search_codebase - 代码库搜索工具
- 功能：使用自然语言描述搜索代码库中的相关代码
- 特点：使用专有的检索/嵌入模型套件，提供高质量的代码片段召回
- 支持：实时索引、跨多种编程语言、仅反映磁盘上代码库的当前状态

### 🔎 search_by_regex - 正则表达式搜索工具
- 功能：使用ripgrep命令在文件或目录中快速查找精确的文本模式匹配
- 特点：高效搜索，结果格式与ripgrep一致，可配置包含行号和内容
- 限制：结果上限为50个匹配项

### 📖 view_files - 文件查看工具
- 功能：可同时查看最多3个文件的内容
- 特点：批量模式查看，每次最多可查看250行，每文件最少200行
- 要求：必须使用绝对路径，优先使用搜索工具定位特定部分

### 📁 list_dir - 目录列表工具
- 功能：查看指定目录中的文件和子目录
- 特点：显示相对路径、文件类型（目录以/结尾）
- 要求：必须使用绝对路径，最大遍历深度为5

### ✍️ write_to_file - 文件写入工具
- 功能：创建新文件或重写现有文件
- 模式：
  - CREATE模式（rewrite=false）：仅用于创建新文件
  - REWRITE模式（rewrite=true）：仅用于重写现有文件
- 特点：自动创建父目录，必须使用绝对路径

### ✏️ update_file - 文件编辑工具
- 功能：编辑现有文件
- 规则：遵循搜索/替换规则设置old_str和new_str参数
- 特点：仅替换第一个匹配项，搜索部分应足够独特以确保唯一性

### ⚡ edit_file_fast_apply - 快速文件编辑工具
- 功能：编辑小于1000行的现有文件
- 规则：仅指定要编辑的特定代码行，不指定或写出未更改的代码
- 特点：使用特殊占位符表示未更改的代码

### 🔄 rename_file - 文件重命名工具
- 功能：移动或重命名现有文件
- 要求：必须使用绝对路径

### 🗑️ delete_file - 文件删除工具
- 功能：删除一个或多个文件
- 要求：必须使用绝对路径，确保文件存在

### ▶️ run_command - 命令执行工具
- 功能：在终端中执行命令
- 类型：web_server、long_running_process、short_running_process、other
- 特点：支持PowerShell环境，可设置工作目录，支持非阻塞命令

### 📊 check_command_status - 命令状态检查工具
- 功能：获取先前执行命令的状态
- 输出：当前状态、退出码、输出行、错误信息
- 特点：可设置输出优先级和字符数限制

### ⏹️ stop_command - 命令停止工具
- 功能：终止正在运行的命令
- 用途：需要重启命令或用户请求停止运行命令时使用

### 🔗 open_preview - 预览工具
- 功能：显示本地服务器的预览URL
- 要求：必须验证命令已成功执行，提供有效的完整预览URL

### 🌐 web_search - 网络搜索工具
- 功能：搜索互联网获取信息
- 特点：应谨慎使用，避免频繁搜索导致用户体验不佳和成本过高
- 选项：可设置语言限制和结果数量限制  

</details>

<details>

<summary><b>CodeBuddy内置的工具列表如下</b></summary>
### 📖 read_file - 读取本地文件系统的文件内容
- 用途：分析代码、查看配置文件、读取文本文件
- 参数：文件路径（必需），可选的起始行号和行数限制
- 输出：文件内容（支持部分读取）

### 📁 list_files - 列出指定目录中的文件和文件夹
- 用途：浏览项目结构，查看目录内容
- 参数：目录路径（必需），是否递归列出（可选）
- 输出：文件和文件夹列表

### 🔎 grep - 基于ripgrep的强大的搜索工具
- 用途：使用正则表达式搜索文件内容
- 参数：搜索目录、正则表达式模式、文件过滤模式
- 输出模式：匹配内容、匹配文件列表、匹配计数

### 📋 list_code_definition_names - 列出源代码文件中的顶层定义
- 用途：快速了解代码文件结构和内容
- 参数：目录路径
- 输出：类、函数、方法等定义的名称列表

### 📝 preview_markdown - 将Markdown文件转换为HTML并在浏览器中预览
- 用途：查看Markdown文件的渲染效果
- 参数：Markdown文件路径
- 输出：在浏览器中打开渲染后的HTML

### 🌐 openweb - 在浏览器中打开指定的网页地址
- 用途：预览网页或启动服务器后的页面
- 参数：有效的URL地址
- 要求：需要先启动可用的服务器

### 💻 code_apply - 在对话中直接显示代码块
- 用途：与用户讨论代码时直接展示代码内容
- 参数：响应内容（必需），文件路径（当有单个代码块时必需）
- 限制：仅在CHAT模式下可用

### ❓ ask_followup_question - 向用户提问以获取更多信息
- 用途：当需要澄清、更多细节或遇到歧义时使用
- 参数：问题内容（必需），可选选项数组
- 特点：支持提供2-5个选项供用户选择  

</details>

## Talk is cheap,show me the code!
经过总结和提炼，我们发现，这些工具的功能基本都围绕着文件操作、搜索、终端等方面展开。
那么我们就来一个一个实现（对应的示例见括号的类）：
（AIConstants类的apikey需要提前配置好）

- 1、AI对话
  - 对话 （[ChatAgent](src/main/java/com/hoppinzq/anthropic/ChatAgent.java)）
- 2、文件操作
  - 读取文件 （[ReadAgent](src/main/java/com/hoppinzq/anthropic/ReadAgent.java)）
  - 列出文件 （[FilesAgent](src/main/java/com/hoppinzq/anthropic/FilesAgent.java)）
  - 编辑文件 （[EditAgent](src/main/java/com/hoppinzq/anthropic/EditAgent.java)）
- 3、终端操作
  - 执行命令 （[BashAgent](src/main/java/com/hoppinzq/anthropic/BashAgent.java)）
- 4、搜索
  - 内容搜索 （[ContentSearchAgent](src/main/java/com/hoppinzq/anthropic/ContentSearchAgent.java)）
  - 联网搜索 （[WebSearchAgent](src/main/java/com/hoppinzq/anthropic/WebSearchAgent.java)）
- 5、其他
  - MCP支持 （[McpChatAgent](src/main/java/com/hoppinzq/anthropic/mcp/McpChatAgent.java)）
  - ReAct支持 （[ChatReActAgent](src/main/java/com/hoppinzq/anthropic/ChatReActAgent.java)）

