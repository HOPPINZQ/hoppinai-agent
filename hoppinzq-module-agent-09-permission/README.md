# hoppinzq-module-agent-permission（权限系统）

对应 Python 教程 `s03_permission`。在 module-02 工具循环的基础上，插入**三重权限闸门**，所有工具执行前必须先过闸门。

## 核心机制

```
工具调用请求
   │
   ├─ 闸门 1: denyList（危险命令黑名单）        → 命中 → DENY
   │
   ├─ 闸门 2: permissionRules（路径越界等规则）  → 命中 → DENY
   │
   └─ 闸门 3: askUser（破坏性命令交互式确认）    → y/N → ALLOW/DENY
   │
   全部通过 → 执行工具
```

| 闸门 | 命中示例 | 行为 |
|---|---|---|
| denyList | `rm -rf /`、`sudo xxx`、`format C:`、`del /f /s /q C:\` | 直接拒绝 |
| rules | `write_file` 路径越出 ROOT | 直接拒绝 |
| askUser | `rm xxx`、`del xxx`、`git push --force` | `y/N` 确认 |

## 新增文件

- `tool/permission/Decision.java` — 决策类型 + 拒绝原因
- `tool/permission/PermissionRules.java` — 静态黑名单 + 路径越界规则
- `tool/permission/PermissionChecker.java` — 三闸门流水线

## ZQAgent 本地副本修改点

1. 新增 `permissionChecker` 字段 + setter
2. 工具执行前调用 `checkPermission(toolName, input)`：
   - `DENY` → 构造 `isError=true` 的 ToolResult 把拒绝原因回灌给模型
   - `ASK` 阶段已在 `PermissionChecker.askUser` 内通过 Scanner 完成确认

## 配置 & 运行

编辑 `src/main/java/com/hoppinzq/agent/constant/AIConstants.java`，填好 `API_KEY` / `BASE_URL` / `MODEL`。

```bash
mvn -pl hoppinzq-module-agent-permission -am compile
mvn -pl hoppinzq-module-agent-permission exec:java -Dexec.mainClass=com.hoppinzq.agent.AgentPermission
```

## 场景测试

| Prompt | 期望 |
|---|---|
| 请执行 `rm -rf /tmp/x` | denyList 拦截 |
| 请执行 `sudo apt update` | denyList 拦截 |
| 把内容写到 `../escape.txt` | 路径越界规则拦截 |
| 用 del 删除 old.log | 弹出 `[权限确认]` 提示 |
| 读 README.md | 正常执行 |

@hoppinzq
