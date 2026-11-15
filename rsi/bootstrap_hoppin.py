"""Assets for the HoppinZQ Agent integration - all eight harness surfaces.

The harness tree is NOT just prompt text. It contains the same configurable
surfaces as the Harness^2 paper, expressed in the formats this repository
already understands:

  system.md / guardrails.md / memory.md   prompt sections (PromptAssembler style)
  tools/definitions.json                  OpenAI-compatible function schemas
  tools/guide.md                          tool usage policy
  skills/change-report/SKILL.md           SKILL.md with --- front matter ---
  subagents/reviewer.md                   subagent charter
  plugins/hooks.json                      hook + MCP server declarations
  domain/rules.md                         practitioner know-how
  scripts/solve.py                        offline stand-in for the tool loop

scripts/solve.py READS tools/definitions.json and SKILL.md at runtime. Those
files are therefore causal, not decorative: changing a tool policy or a skill's
output_fields changes the deliverable. That is what lets the recursion improve
something other than prose.

The starting tool manifest declares `skip_non_positive: true`, so the agent
drops deletions from the net-change report. The framework must discover that
from trajectories alone.

Asset version bumps regenerate the built-in files (existing extra files are
kept), because a schema change cannot be applied by "preserve everything".
"""

from __future__ import annotations

import csv
import io
import json
from pathlib import Path

from rsi_harness.recursion import Task
from rsi_harness.store import atomic_json, atomic_text
from rsi_harness.tree import HarnessTree

DOMAIN = "workspace-diff"
ASSET_VERSION = 2

TASK_MD = """# Workspace change report

Read `input/ledger.csv`. Each row is one changed file: `id`, a signed line
delta `amount` (negative means lines were deleted), and a `memo`.

Write `answer.txt` into the output directory with exactly two lines:

- `total=<amount>` - the net line delta across every changed file
- `rows=<id,id,...>` - the ids of every row included in that total

Every changed file must be represented, including deletions. Keep the output
plain text and machine readable.
"""

SYSTEM_MD = """## 角色

你是 HoppinZQ Agent，一个在工作区内完成开发任务的助手。系统提示由
PromptAssembler 按 context 条件装配；本段来自外部 RSI harness bundle，可被
自动优化，不写死在 Java 代码里。

先读懂任务与输入文件，再决定用哪些工具，最后按任务要求交付产物。
"""

GUARDRAILS_MD = """# Guardrails

- 只在工作区目录内读写文件。
- 不要编造输入文件里不存在的数据。
- 交付物要如实反映实际处理过的输入行。
- 遇到失败要如实记录，不要静默跳过。
"""

MEMORY_MD = """# Memory

此域暂无跨任务经验。RSI 会把已验证的教训追加到这里。
"""

TOOL_DEFINITIONS = {
    "schema": "openai-tools/1",
    "notes": "与 ToolDefinition(name, description, inputSchema) 对应的协议中立描述",
    "tools": [
        {
            "type": "function",
            "function": {
                "name": "read_file",
                "description": "读取工作区内指定路径的文件内容。",
                "parameters": {
                    "type": "object",
                    "properties": {"path": {"type": "string", "description": "相对工作区的路径"}},
                    "required": ["path"],
                },
            },
        },
        {
            "type": "function",
            "function": {
                "name": "write_file",
                "description": "把内容写入工作区内的指定路径。",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "path": {"type": "string"},
                        "content": {"type": "string"},
                    },
                    "required": ["path", "content"],
                },
            },
        },
        {
            "type": "function",
            "function": {
                "name": "list_files",
                "description": "列出工作区目录结构，确认输入与产物位置。",
                "parameters": {"type": "object", "properties": {}, "required": []},
            },
        },
        {
            "type": "function",
            "function": {
                "name": "content_search",
                "description": "在工作区内检索文本，定位需要修改的位置。",
                "parameters": {
                    "type": "object",
                    "properties": {"pattern": {"type": "string"}},
                    "required": ["pattern"],
                },
            },
        },
        {
            "type": "function",
            "function": {
                "name": "bash",
                "description": "执行一条本地命令并返回输出。",
                "parameters": {
                    "type": "object",
                    "properties": {"command": {"type": "string"}},
                    "required": ["command"],
                },
            },
        },
        {
            "type": "function",
            "function": {
                "name": "sum_changes",
                "description": "汇总变更清单，生成 total 与 rows 两行交付物。",
                "parameters": {
                    "type": "object",
                    "properties": {"source": {"type": "string", "description": "变更清单路径"}},
                    "required": ["source"],
                },
            },
        },
    ],
    "rsi_policy": {
        "id_column": "id",
        "amount_column": "amount",
        "output_fields_from_skill": "skills/change-report/SKILL.md",
        "skip_non_positive": True,
    },
}

TOOLS_GUIDE_MD = """# Tool guidance

- `read_file` / `list_files` 用于确认输入与工作区结构。
- `content_search` 用于定位需要修改的位置。
- 修改文件前先读取，修改后复查产物。
- `sum_changes` 负责生成结构化交付物；它的参数契约在
  `tools/definitions.json` 中定义，输出字段由 `skills/change-report/SKILL.md`
  声明。
- 调用工具前先读工具定义，不要凭记忆猜参数。
"""

SKILL_MD = """---
name: change-report
description: 生成工作区变更净增行数报告
tags: diff,report,workspace
output_fields: total,rows
---

# 变更报告技能

## 目标
把变更清单转换成机器可读的两行交付物。

## 交付契约
- `total=<净增量>`：所有已计入行的 amount 代数和。
- `rows=<id,id,...>`：所有已计入行的 id，逗号分隔。
- 输出字段名由 front matter 的 `output_fields` 声明，顺序即输出顺序。

## 步骤
1. 用 `read_file` 读取输入清单，确认列名。
2. 调用 `sum_changes`，按工具定义里的 policy 处理每一行。
3. 复查产物的字段名、顺序与行数，再交付。
"""

SUBAGENT_MD = """# Reviewer subagent

触发时机：主 agent 准备交付 `answer.txt` 之前。

职责：
- 对照输入清单逐行核对交付物中列出的 id。
- 检查交付物字段名与 `skills/change-report/SKILL.md` 的声明一致。
- 只报告观察到的差异，不修改任何文件，也不接触评分信息。

输出：一段简短的自然语言差异说明。
"""

PLUGIN_HOOKS = {
    "hooks": [
        {
            "name": "pre-delivery-review",
            "trigger": "before_answer",
            "agent": "subagents/reviewer.md",
            "action": "review_deliverable",
        }
    ],
    "mcpServers": {},
}

DOMAIN_RULES_MD = """# Workspace diff rules

变更清单中 amount 可为负数，表示删除行。净增量是所有行（含删除行）的
代数和。交付物需列出每个已计入的 id，便于核对覆盖情况。
"""

SOLVE_PY = '''"""Offline stand-in for the agent tool loop.

Reads the tool manifest and the active skill from this harness tree on every
run, so tool definitions and skills are causal surfaces: editing either one
changes the deliverable.
"""
import csv
import json
import os
import re
from pathlib import Path

root = Path(__file__).resolve().parent.parent
manifest = json.loads((root / "tools" / "definitions.json").read_text(encoding="utf-8"))
policy = manifest.get("rsi_policy", {})

skill_rel = policy.get("output_fields_from_skill", "skills/change-report/SKILL.md")
skill_text = (root / skill_rel).read_text(encoding="utf-8")
front = re.search(r"^output_fields:\\s*(.+)$", skill_text, re.M)
fields = [x.strip() for x in front.group(1).split(",")] if front else ["total", "rows"]

src = Path(os.environ["RSI_INPUT"])
dst = Path(os.environ["RSI_OUTPUT"])
rows = list(csv.DictReader(src.read_text(encoding="utf-8").splitlines()))

id_col = policy.get("id_column", "id")
amount_col = policy.get("amount_column", "amount")
skip = bool(policy.get("skip_non_positive", False))

total = 0.0
used = []
for row in rows:
    amount = float(row[amount_col])
    if skip and amount <= 0:
        continue
    total += amount
    used.append(row[id_col])

rendered = {"total": f"{total:.2f}", "rows": ",".join(used)}
lines = [f"{name}={rendered[name]}" for name in fields if name in rendered]
dst.write_text("\\n".join(lines) + "\\n", encoding="utf-8")
print(f"wrote {dst.name}: " + " ".join(lines))
'''

DATASETS = {
    "change-1": [
        ("C1", "120", "src/Main.java added"),
        ("C2", "-35", "src/Old.java removed"),
        ("C3", "20", "README.md added"),
        ("C4", "60", "src/Util.java added"),
    ],
    "change-2": [
        ("D1", "300", "api/Handler.java added"),
        ("D2", "-18", "api/Legacy.java removed"),
        ("D3", "42", "tests/HandlerTest.java added"),
        ("D4", "-11", "docs/old.md removed"),
        ("D5", "7", "pom.xml added"),
    ],
}


def _csv_text(rows: list[tuple[str, str, str]]) -> str:
    buf = io.StringIO(newline="")
    writer = csv.writer(buf, lineterminator="\n")
    writer.writerow(["id", "amount", "memo"])
    writer.writerows(rows)
    return buf.getvalue()


def ensure_assets(root: Path | str, *, reset: bool = False) -> tuple[HarnessTree, list[Task]]:
    """Create all eight built-in surfaces once; preserve local edits by default.

    A version bump regenerates the built-in files (a schema change cannot be
    applied by preserving everything), but never deletes extra user files.
    """
    root = Path(root)
    base_dir = root / "harness"
    base_dir.mkdir(parents=True, exist_ok=True)

    marker = root / ".asset-version"
    recorded = marker.read_text(encoding="utf-8").strip() if marker.is_file() else ""
    if recorded != str(ASSET_VERSION):
        reset = True
        atomic_text(marker, str(ASSET_VERSION) + "\n")

    def put(path: Path, text: str) -> None:
        if reset or not path.is_file():
            atomic_text(path, text)

    files = {
        "system.md": SYSTEM_MD,
        "guardrails.md": GUARDRAILS_MD,
        "memory.md": MEMORY_MD,
        "tools/definitions.json": json.dumps(
            TOOL_DEFINITIONS, indent=2, sort_keys=True, ensure_ascii=False) + "\n",
        "tools/guide.md": TOOLS_GUIDE_MD,
        "skills/change-report/SKILL.md": SKILL_MD,
        "subagents/reviewer.md": SUBAGENT_MD,
        "plugins/hooks.json": json.dumps(
            PLUGIN_HOOKS, indent=2, sort_keys=True, ensure_ascii=False) + "\n",
        "domain/rules.md": DOMAIN_RULES_MD,
        "scripts/solve.py": SOLVE_PY,
    }
    for rel, text in files.items():
        put(base_dir / rel, text)

    tasks: list[Task] = []
    for task_id, rows in DATASETS.items():
        tdir = root / "tasks" / task_id
        (tdir / "input").mkdir(parents=True, exist_ok=True)
        (tdir / "private").mkdir(parents=True, exist_ok=True)
        put(tdir / "task.md", TASK_MD)
        put(tdir / "input" / "ledger.csv", _csv_text(rows))
        check = tdir / "private" / "check.json"
        if reset or not check.is_file():
            atomic_json(check, {
                "total": round(sum(float(r[1]) for r in rows), 2),
                "rows": [r[0] for r in rows],
            })
        tasks.append(Task(id=task_id, dir=tdir, domain=DOMAIN))
    return HarnessTree.read(base_dir), tasks
