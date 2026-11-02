# bash 工具加固与系统提示改进说明

> 2026-11-02 | 参照 Python 教程 s01（`hoppinzq-module-agent-01-baseloop/src/main/resources/code.py`）对 Java 版的对标审查结论与整改记录。
> 影响范围：**全部 25 个教学模块**（`tool/Tools.java` 的 executeBash 方法 + 内核 `base/ZQAgent.java` 的系统提示）。

---

## 1. 背景

Python s01（154 行）是本工程 Java 版的参照源码。对标审查发现 7 处 Java 版可借鉴点，其中 2 处为 bug 级缺陷，已全部修复并同步到全部模块。

## 2. 改动清单

### P0-1 bash 命令无超时 → Agent 永久挂死（bug 修复）

- **Python**：`subprocess.run(..., timeout=120)` + `TimeoutExpired` 分类处理
- **Java 原状**：`process.waitFor()` 不带超时，一条交互式命令（`ping`、无参 `python`）即永久卡死
- **修复**：`waitFor(120, TimeUnit.SECONDS)` + 超时 `destroyForcibly()`，返回 `Error: Timeout (120s)`
- 新增常量 `BASH_TIMEOUT_SECONDS = 120`（`tool/Tools.java`）

### P0-2 stderr 读取顺序死锁风险（bug 修复）

- **Python**：`subprocess.run` 内部 `communicate()` 并发读双流，天然无此问题
- **Java 原状**：先顺序读完 stdout 再读 stderr——子进程向 stderr 写满管道缓冲区（约 64KB）时，
  子进程阻塞在写、我们阻塞在读，互相等死
- **修复**：`processBuilder.redirectErrorStream(true)` 合并单流顺序读取；同时读取循环持续排空管道
  （超限后只排空不拼接），保证 `waitFor` 不因缓冲区满挂起

### P1-1 危险命令黑名单（安全护栏）

- **Python**：`rm -rf /`、`sudo`、`shutdown`、`reboot`、`> /dev/` 五条黑名单在 bash 工具内直接拦截
- **Java 原状**：01 裸奔（权限拦截在 09 模块的 capability，01 不注册即无任何保护）
- **修复**：新增 `DANGEROUS_COMMANDS` 黑名单，命中返回 `Error: Dangerous command blocked`。
  定位为"最后一道底线"，与 09-permission 的可配置规则互补，不冲突

### P1-2 输出截断 + 空输出兜底

- **Python**：`out[:50000]` 截断 + 空输出返回 `(no output)`
- **Java 原状**：无截断（大文件输出直接进上下文，01 无 compact 兜底）；空输出返回空串，模型易迷惑
- **修复**：`BASH_MAX_OUTPUT_CHARS = 50_000` 截断（附截断提示）；空输出返回 `(no output)`

### P2 环境感知系统提示（内核一处改动，全模块生效）

- **Python**：`ENVIRONMENT_PROMPT` —— Windows 上告知"bash 走 cmd.exe，用 cmd 语法不是 Unix Bash/PowerShell"，
  并把 cwd 写进 SYSTEM，附 `Act, don't explain.`
- **Java 原状**：系统提示只有模块自拟内容，无运行环境说明——模型在 Windows 上高频输出 `ls`/`rm` 等 Unix 命令
- **修复**：内核 `ZQAgent` 新增 `environmentPrompt()`，在 `chatMessage` 构造请求时**追加**到模块系统提示之后：

  ```
  [运行环境] 工作目录: <ROOT>。bash 工具在 Windows 上通过 cmd.exe 执行：请使用 cmd 语法，
  而不是 Unix Bash 或 PowerShell 语法 优先直接行动解决问题（Act, don't explain）。
  ```

  放内核而非 25 个入口类的原因：环境信息属于运行时事实（与 Python 把它烘进 SYSTEM 同理），
  一处改动全模块生效，避免 25 份提示词模板逐一修补。

### 顺手修正

- 空命令校验（`command` 为空直接报错，不启动进程）
- `InterruptedException` 恢复中断标志后返回 `Error: 执行被中断`
- 22-multimodal 的 Tools.java 缺 `OBJECT_MAPPER` 静态导入（新实现依赖），已补

## 3. 不采纳的参考点

| Python 写法 | 不采纳原因 |
|---|---|
| Readline 零宽包裹（`\001\033[..m\002`） | Java `Scanner` 不经过 readline，无此问题 |
| `errors="replace"` 解码容错 | Java 已按 cmd=GBK / bash=UTF-8 精确选编码，比 Python 的通用容错更准 |
| 危险命令黑名单放系统提示说明 | 拦截逻辑留在工具执行体，提示词只由 capability 的 promptFragments 贡献（09-permission 已有） |

## 4. 改动文件与同步范围

| 文件 | 范围 | 内容 |
|---|---|---|
| `tool/Tools.java` | 全部 25 个模块 | executeBash 整体替换（括号配平法逐模块替换，含 6 个微变体模块）+ 3 个新常量 |
| `base/ZQAgent.java` | 全部 25 个模块（内核同步） | 新增 `environmentPrompt()`，`chatMessage` 追加环境提示 |
| `tool/Tools.java`（22-multimodal） | 单模块 | 补 `OBJECT_MAPPER` 静态导入 |

同步注意：`tool/Tools.java` **不在**内核同步清单（各模块工具面不同），本轮 bash 方法是逐模块替换的；
后续若再改 executeBash，需重复此同步动作（或将其抽为独立能力包，见"后续建议"）。

## 5. 验证

- 全量 `mvn -o compile` 零错误（28 个 Maven 模块）
- `bin/check-kernel-sync.sh` 输出 `[OK]`（内核含本轮 ZQAgent 改动，24 个模块逐字节一致）
- 未做运行时验证（需真实 LLM 对话）：建议冒烟用例——`ping localhost`（验证超时）、`dir`（验证 Windows 提示词效果）、`echo 大文本`（验证截断）

## 6. 后续建议

1. **executeBash 能力化**：bash 工具含安全策略（黑名单/超时/截断），本质是能力而非内核——
   可抽为 `capability/base-tools/`，与"基座工具能力化"任务（Icode #31）合并处理
2. **黑名单可配置**：`DANGEROUS_COMMANDS` 硬编码在 Tools 里，后续并入 09-permission 的
   `PermissionConfig` 由规则文件驱动
3. **超时/截断常量入配置**：`BASH_TIMEOUT_SECONDS`/`BASH_MAX_OUTPUT_CHARS` 目前硬编码，
   可挪到 AIConstants 或未来能力配置
