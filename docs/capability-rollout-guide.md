# 能力化推广指南（以 06-compact 为权威源）

本文档是把"插拔式能力架构"推广到其余教学模块的作业标准。
**权威源（reference implementation）= `hoppinzq-module-agent-06-compact`**，推广即让目标模块的内核与 06 逐字节一致，并把模块特性改造成能力包。

## 1. 内核同步（机械复制）

从 06 复制以下目录/文件到目标模块（包根 `src/main/java/com/hoppinzq/agent/`）：

| 来源（06） | 目标（各模块） | 说明 |
|---|---|---|
| `client/` 整目录 | 同路径 | LlmProvider（含 stream/onThinkingComplete）、OpenAI/AnthropicProvider（全能版：stream+think+多模态）、LlmMessage（ContentPart+reasoningSignature）、LlmRequest（thinking 字段） |
| `session/` 整目录 | 同路径 | MessageConverter（parts 降级+signature）、SessionBlock（signature）、SessionData（无 transcripts）、SessionManager（populate 回填末轮 thinking）、SessionStore、SessionMessage、TokenUsage |
| `capability/Capability.java` `AgentHook.java` `AgentCommand.java` | `capability/` | 能力契约三接口 |
| `base/ZQAgent.java` | 同路径 | 含 capability() 注册、钩子分发、命令收集；注意 06 版 tools 构造时拷贝为可变 ArrayList |
| `command/AgentCommandHandler.java` | 同路径 | 通用命令注册制（内置 /stats /usage /skills /exit + extraCommands） |
| `tool/ToolDefinition.java`、`tool/Tools.java` | 同路径 | **见第 2 节，不能盲目覆盖** |

### AIConstants 处理（不能整文件覆盖）

`constant/AIConstants.java` 每模块不同（MODULE_NAME）。逐模块做三件事：
1. 确认 `MODULE_NAME` 与目录名完全一致（如 `hoppinzq-module-agent-09-permission`）——**09/10/11/12/15/16/17/18/19/20/21 存在缺序号的旧值，必须修正**
2. 追加内核新常量：
   ```java
   //是否开启扩展思考（Claude thinking / DeepSeek reasoning），由 Provider 映射为各自协议参数
   public static final boolean THINK_ENABLED = false;
   //扩展思考 token 预算（仅 Anthropic 协议使用）
   public static final int THINK_BUDGET_TOKENS = 10000;
   ```
3. 特性常量暂留在 AIConstants 还是移入能力包，随第 3 节能力化一并处理（06 的标准做法：特性常量全部移入 `CompactConfig` 风格的能力自持配置类）

## 2. ToolDefinition / Tools 合并规则

06 的 `ToolDefinition.java` 含基座工具静态字段（Bash/ReadFile/EditFile/WriteFile/Glob/ContentSearch/SubAgent/Todo/Skills）。
各模块若**只有基座工具** → 直接覆盖。若有**模块特有工具静态字段**（如 07 的 Task 系列）→ 该字段是特性侵入，随能力化移入能力包的 `tools()` 返回值，然后从 ToolDefinition 中删除。`Tools.java` 中对应的静态执行方法同样移入能力包（能力包内私有方法），执行体里的静态回调钩子（`manualCompactHook` 模式）一律删除——工具与逻辑同包后直接调用。

`ToolDefinition` 静态字段对入口类的静态依赖（`Agent06.skillLoader` 模式）是既有全模块模式，**保留不动**（基座工具能力化是后续工作）。

## 3. 特性 → 能力包改造（核心工作）

参照 `capability/compact/` 的结构：

```
capability/<feature>/
  <Feature>Capability.java   ← 唯一入口，implements Capability
  FeatureConfig.java          ← 特性常量（自持配置，替代 AIConstants 中的特性常量）
  （其余原特性包的类，git mv 迁入 + 改包名 + 去静态钩子）
```

对照 06 的改造模式：

| 原侵入点 | 能力化后 |
|---|---|
| 入口类覆写 `chatMessage` 前置逻辑（如 compactIfNeeded） | `hooks()` 返回 `beforeLlmCall` |
| 工具执行前检查（权限类） | `hooks()` 返回 `beforeToolCall`，返回非 null 文本即拦截 |
| 入口类注入 `AgentCommandHandler` 的 Runnable 命令 | `commands()` 返回 `AgentCommand` 列表 |
| 系统提示里的特性文案 | `promptFragments()` |
| 后台线程/调度器启动 | `onStart(agent)` / `onStop()`；宿主 Agent 引用用字段捕获 |
| 静态回调钩子（`XxxHook` 静态字段） | 删除；工具执行体直接调用能力内部方法 |
| 特性历史/元数据存进 SessionData | 能力自持存储（参照 `TranscriptStore`，写 `<ROOT>/.` 下自己的目录），SessionData 保持内核纯净 |
| AIConstants 特性常量 | `FeatureConfig` 常量类 |

入口类（AgentNN）瘦身为纯装配，参照 `Agent06.java`：
```java
FeatureCapability feature = new FeatureCapability(provider, MODEL, sessionManager);
AgentNN agent = new AgentNN(provider, MODEL, baseTools);
agent.capability(feature);
agent.setSystemPrompt(basePrompt + "\n\n" + String.join("\n", feature.promptFragments()));
```

### 钩子契约速查（capability/AgentHook.java）

```java
List<LlmMessage> beforeLlmCall(List<LlmMessage> messages)  // 返回新列表=替换上下文；原样返回=不改
void afterLlmResponse(LlmResponse response)
String beforeToolCall(LlmMessage.ToolCall call)            // 非 null=拦截，文本作为工具结果
void afterToolCall(String toolName, String result)
```
如模块确实需要"LLM 调用失败后恢复"挂载点（如 12-errorrecovery 的 prompt_too_long 响应式压缩），允许给 `AgentHook` 追加 default 方法 `onLlmError(Exception e)` 并在 ZQAgent 的 catch 块分发——这是唯一允许修改契约的场景，修改后须同步回 06 权威源。

## 4. 特例模块

- **01-baseloop**：无特性，仅内核同步（注意其 Tools/ToolDefinition 是最精简版，覆盖后确认编译）
- **02-tooluse**：其 AnthropicProvider 孤本（stream）已进内核，直接覆盖
- **22-multimodal**：其 Provider 多模态孤本已进内核，直接覆盖；剩余特性代码按第 3 节能力化
- **21-comprehensive / 24-harness**：特性全量累积模块，**本批仅做内核同步 + 编译适配**（保留其组合式接线，删除与新内核冲突的旧钩子），全量拆分为多个能力包留待后续批次

## 5. 强制约束

1. **编译门禁**：每个模块完成后 `mvn -q compile -pl <module目录名>` 必须零 ERROR 才算完成
2. **只动自己负责的模块目录**，不碰其他模块、不碰根 pom
3. **不做 git 提交**，文件操作即可（新文件不 add）
4. 注释风格与 06 一致：中文 Javadoc、说明"为什么"
5. **ANSI 转义陷阱**：Java 源里的颜色码是字面文本 `[90m`（反斜杠-u-0-0-1-b）。通过 Write/Edit 工具直接写会被 JSON 解码成真实 ESC 控制字符，**必须用 python 写入**（heredoc 单引号传脚本，`chr(92)+'u001b'` 构造），或干脆不新增带颜色的输出
6. 不引入新依赖（junit 缺失是既有问题，不要顺手修）
7. 能力包对内核的依赖只允许：`capability/` 契约、`client/`、`session/`、`tool/ToolDefinition`、`AIConstants.ROOT/MODEL`；内核（client/session/base/command）不得 import 任何能力包

## 6. 完成自检清单

- [ ] `client/`、`session/`、`capability/` 契约、`base/ZQAgent`、`command/AgentCommandHandler` 与 06 逐字节一致（`md5sum` 对比，AIConstants 除外）
- [ ] MODULE_NAME = 目录名
- [ ] 特性以 `capability/<feature>/` 存在，入口类仅装配
- [ ] `mvn -q compile -pl <模块>` 零 ERROR
- [ ] 内核无对能力包的反向 import：`grep -rn "capability\." src/main/java/com/hoppinzq/agent/{client,session,base,command}` 仅 ZQAgent 允许出现 `capability`（契约接口）
