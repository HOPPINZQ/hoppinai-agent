# 上游 learn-claude-code 修复移植说明

> 2026-11-02 | 对上游参考仓库 `D:\project\ai\learn-claude-code`（Python 教程原版，s01-s17，159 个非合并提交）的
> git 修复史做了完整挖掘，把适用的修复移植到 Java 版。影响 6 个模块 + 全部 25 个模块的 Tools.java。

---

## 1. 上游修复史挖掘结论

对 159 个提交逐一比对，**适用且已移植 6 项**、**已领先覆盖 4 项**、**不适用 5 项**（详见附录）。
上游最高价值的修复集中在 **s08 上下文压缩**（6 个独立修复）——这正是我们 06-compact 参照的章节。

## 2. 已移植的修复

### 2.1 snip_compact 迟滞（上游 b057622）→ 06/21/24

- **上游问题**：`len > 50` 触发、裁到 47 条——两三轮对话后再次触发，反复压缩抖动
- **修复**：触发 `MAX_MESSAGES=50`、裁到新增常量 `SNIP_TARGET_MESSAGES=30`（保留头 3 + 尾 26 + 占位符）
- 改动：`ContextCompactor.snipCompact` 的 `tailStart` 计算；常量入 `CompactConfig`（06）/`AIConstants`（21/24）

### 2.2 micro_compact 保留本轮完整批次（上游 b5bd0dd + b6b5f33）→ 06/21/24

- **上游问题**：按"全局倒数 KEEP_RECENT 条"保留工具结果——若一轮产生 5 个 tool_result 而 KEEP_RECENT=3，
  **本轮的最后 2 个结果会被替换成 `[已执行: xxx]` 占位符**，模型直接丢失当前工作上下文
- **修复**：microCompact 重写——最近一条 assistant 之后的 tool_result 属于"本轮批次"，**整批保留**；
  只对更早的旧结果做 KEEP_RECENT 淘汰
- 改动：`ContextCompactor.microCompact` 整体替换（三模块同步）

### 2.3 压缩后保留当前用户请求（上游 7226f55，#603）→ 06/21/24

- **上游问题**：auto_compact 生成摘要后，当前用户请求随历史一起被压缩掉——模型忘记正在做的任务
- **修复**：
  - `autoCompact` 步骤 4 的摘要消息头嵌入 `[当前用户请求]`（有请求时），
    消息格式变为 `[对话已压缩。完整记录: ...] + [当前用户请求] + [对话摘要（仅供参考）]`
  - `compactIfNeeded`（06）末尾统一保护：压缩手术（snip/micro）可能裁掉用户最近输入，
    新历史若已不含该请求则补一条 `[当前用户请求] ...`
  - 新增 `latestUserRequest()` helper（跳过 tool 结果与 `[...]` 占位符）
- 注：21/24 的压缩器是老版（无 compactIfNeeded 编排），autoCompact 内嵌已覆盖其唯一压缩路径

### 2.4 读文件强制 UTF-8（上游 199402f）→ 22 个有 readFile 的模块

- **上游问题**：`read_text()` 不带编码，Windows 默认 GBK 解码 UTF-8 文件乱码
- **Java 原状**：写侧 `getBytes(StandardCharsets.UTF_8)`、读侧 `new String(bytes)` 平台默认编码——**读写不对称**
- **修复**：`readFile`/`editFile` 的 `new String(...)` 全部补 `StandardCharsets.UTF_8`（22 个模块，
  01/22 无 readFile 工具无需改）

### 2.5 glob 结果上限（上游 61c3e64）→ 14 个有 glob 的模块

- **上游问题**：`**` 递归匹配无上限，node_modules 场景一次返回几十万条撑爆上下文
- **Java 原状**：已支持 `**` 递归 + sorted ✓（比上游修复前强），但无条数上限
- **修复**：glob 拼接循环加 200 条上限，超出追加 `... (匹配过多已截断，请收窄 pattern)`

### 2.6 TaskManager 循环依赖检测（上游 711249e）→ 19/20

- **上游问题**：任务 ID 由运行时生成，模型 update_task 时可能无意建出 A→B→A 的环，两个任务互相阻塞永无 ready
- **修复**：`updateTask` 的 addBlockedBy 处理前做环检测（`dependsOnTask` 传递依赖遍历），
  成环返回错误文案；两阶段建依赖（先建节点后加依赖）Java 版本已是现状 ✓

## 3. 已领先覆盖的上游修复（无需动作）

| 上游修复 | Java 现状 |
|---|---|
| d5cba86 识别 Anthropic 溢出文案 `prompt is too long` | 12-errorrecovery 的 `ErrorClassifier` 已同时匹配三种文案 ✓ |
| 168fff8 空 tool_use 响应死循环 | `hasPendingToolCalls` 双判据 + 空 results break，比上游修复更严谨 ✓ |
| b4e7269 动态 system prompt（tools/workspace 从 context 生成） | 15-sysprompt 的 PromptCapability 已动态装配 ✓ |
| 94ca468 OS 感知 shell 提示 | 上一轮已移植（ZQAgent.environmentPrompt）✓ |

## 4. 不适用的上游修复

| 上游修复 | 原因 |
|---|---|
| 85ee4f1 CVE-2026-67213 | 仅 web/（npm 依赖），Java 不涉及 |
| ce8f9f1 Windows 进程清理（killpg/fcntl） | Python 特有问题（依赖 POSIX API）；Java `destroyForcibly()` 天然跨平台 |
| cce031e 子进程解码容错 errors="replace" | Java `InputStreamReader` 非法字节默认替换 U+FFFD，行为等价 |
| 7b76e25 Windows UnicodeDecodeError | 同上，编码选择已处理 |
| 0dcafa2 系列权限正则加固（shlex 词边界解析） | 有价值但复杂（完整 shell 词法分析）；Java 01 的 contains 黑名单是"宁可误报"的底线定位，词边界版列入后续（见 §6） |

## 5. 改动文件与验证

| 文件 | 范围 | 修复 |
|---|---|---|
| `capability/compact/ContextCompactor.java`（06） | 1 | 2.1 + 2.2 + 2.3 + 保护块 |
| `tool/compact/ContextCompactor.java`（21/24） | 2 | 2.1 + 2.2 + 2.3（autoCompact 内嵌版） |
| `capability/compact/CompactConfig.java`（06）+ `AIConstants.java`（21/24） | 3 | `SNIP_TARGET_MESSAGES = 30` |
| `tool/Tools.java` | 22（UTF-8）+ 14（glob） | 2.4 + 2.5 |
| `capability/task/TaskManager.java`（19/20） | 2 | 2.6 |

**验证**：全量 `mvn -o compile` 零错误（28 个 Maven 模块）；`bin/check-kernel-sync.sh` `[OK]`。

## 6. 后续建议

1. **权限正则词边界版**（上游 1293797 完整 shlex 方案）：移植到 09-permission 的
   `PermissionConfig`，替换 01 黑名单的 contains 匹配——防漏报（`rm  -rf /` 双空格变体）
2. **worktree 自动认领联动**（上游 43beba5）：`idle_poll` 返回 `(result, claimed_task_id)`，
   认领带 worktree 的任务后更新线程 cwd——正是 Icode 任务 #28 的上游蓝本，实现时参照
3. **s15 背景任务生命周期加固**（上游 078eca7/98f8ff7：原子投递 + 生命周期）：08-background
   的 BackgroundManager 值得对照移植，列入待办
4. **上游仓库同步机制**：建议定期（如每月）重跑本次的 git log 挖掘流程，
   `git log --oneline --no-merges` 增量比对即可
