"""Built-in starting harness and task set.

The starting harness has one real defect: its script drops negative rows. The
framework must discover that from trajectories alone. The private check files
are generated here but are only ever opened by ``judge``.
"""

from __future__ import annotations

import csv
import io
import json
from pathlib import Path

from .recursion import Task
from .store import atomic_json, atomic_text
from .tree import HarnessTree

DOMAIN = "ledger"

TASK_MD = """# Ledger reconciliation

Read `input/ledger.csv` and write `answer.txt` into the output directory.

The deliverable must contain exactly two lines:

- `total=<amount>` - the net total across every row in the file
- `rows=<id,id,...>` - the ids of every row included in that total

Amounts may be positive or negative. Every row in the input must be represented.
Keep the deliverable plain text and machine readable.
"""

SYSTEM_MD = """# System

You are a careful analyst. Read the task file and the public source file before
acting. Produce the deliverable exactly as the task describes, then stop.
"""

GUARDRAILS_MD = """# Guardrails

- Work only inside the workspace you were given.
- Do not invent values that are absent from the source file.
- Report what you actually did, not what you intended to do.
"""

MEMORY_MD = """# Memory

No prior experience recorded for this domain yet.
"""

TOOLS_GUIDE_MD = """# Tool guidance

Use `scripts/solve.py` to build the deliverable. It reads `RSI_INPUT` and writes
to `RSI_OUTPUT`. Inspect its output before finishing.
"""

DOMAIN_RULES_MD = """# Ledger rules

A ledger row has an id and a signed amount. Write the net total in two decimal
places and list the ids you included.
"""

SOLVE_PY = '''"""Build the ledger deliverable. Part of the editable harness."""
import csv
import os
from pathlib import Path

src = Path(os.environ["RSI_INPUT"])
dst = Path(os.environ["RSI_OUTPUT"])

rows = list(csv.DictReader(src.read_text(encoding="utf-8").splitlines()))
total = 0.0
used = []
for row in rows:
    amount = float(row["amount"])
    if amount > 0:
        total += amount
        used.append(row["id"])

dst.write_text(
    f"total={total:.2f}\\nrows={','.join(used)}\\n",
    encoding="utf-8",
)
print(f"wrote {dst.name}: total={total:.2f}, rows={len(used)}")
'''

LEDGERS = {
    "ledger-1": [
        ("L1", "120.50", "invoice"),
        ("L2", "-35.25", "credit"),
        ("L3", "20.00", "invoice"),
        ("L4", "60.00", "invoice"),
    ],
    "ledger-2": [
        ("M1", "300.00", "invoice"),
        ("M2", "-18.75", "refund"),
        ("M3", "42.50", "invoice"),
        ("M4", "-11.25", "credit"),
        ("M5", "7.00", "fee"),
    ],
}


def _csv_text(rows: list[tuple[str, str, str]]) -> str:
    buf = io.StringIO(newline="")
    w = csv.writer(buf, lineterminator="\n")
    w.writerow(["id", "amount", "memo"])
    w.writerows(rows)
    return buf.getvalue()


def ensure_assets(root: Path | str, *, reset: bool = False) -> tuple[HarnessTree, list[Task]]:
    """Create the built-in assets once; never overwrite local edits unless reset=True.

    Users customise tasks and the starting harness by editing files under
    ``assets/``. Rewriting them on every command would silently discard that
    work, so existing files are preserved by default.
    """
    root = Path(root)
    base_dir = root / "harness"
    base_dir.mkdir(parents=True, exist_ok=True)

    def put(path: Path, text: str) -> None:
        if reset or not path.is_file():
            atomic_text(path, text)

    files = {
        "system.md": SYSTEM_MD,
        "guardrails.md": GUARDRAILS_MD,
        "memory.md": MEMORY_MD,
        "tools/guide.md": TOOLS_GUIDE_MD,
        "domain/rules.md": DOMAIN_RULES_MD,
        "scripts/solve.py": SOLVE_PY,
    }
    for rel, text in files.items():
        put(base_dir / rel, text)

    tasks: list[Task] = []
    for task_id, rows in LEDGERS.items():
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
