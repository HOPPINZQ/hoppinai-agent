"""External grading. Loaded only by the ``judge`` command.

The private check file lives in ``assets/tasks/<id>/private/`` and is never
copied into an executor workspace or an evidence directory. Scores are written
to ``<namespace>/scores/`` after selection has already been committed; nothing
here is ever read by a proposer, composer, selector or reflector.
"""

from __future__ import annotations

import json
import re
from pathlib import Path

from .backends import ROWS_LINE, TOTAL_LINE
from .executor import run_harness
from .recursion import Task
from .store import RunStore, atomic_json, read_json
from .tree import HarnessTree


def _check(answer: str, expected_total: float, expected_ids: list[str]) -> dict:
    m_total = TOTAL_LINE.search(answer)
    m_rows = ROWS_LINE.search(answer)
    reported = float(m_total.group(1)) if m_total else None
    rows = {x.strip() for x in m_rows.group(1).split(",") if x.strip()} if m_rows else set()
    total_ok = reported is not None and abs(reported - expected_total) < 0.01
    rows_ok = rows == set(expected_ids)
    return {
        "delivered": bool(answer.strip()),
        "total_ok": total_ok,
        "rows_ok": rows_ok,
        "pass": bool(total_ok and rows_ok),
        "reported_total": reported,
        "rows_cited": len(rows),
    }


def grade_task(store: RunStore, task: Task) -> dict:
    private = task.dir / "private" / "check.json"
    if not private.is_file():
        raise FileNotFoundError(f"private check missing for {task.id}: {private}")
    expected = json.loads(private.read_text(encoding="utf-8"))

    adoption = read_json(store.task_dir(task.id) / "adoption.json", {})
    chosen_source = adoption.get("chosen_source", "") if isinstance(adoption, dict) else ""
    cold_answer = _read(store.path("cold", task.id, "answer.txt"))
    base_answer = _read(store.path("runs", task.id, "r1-base1", "answer.txt"))
    adopted_answer = _read(store.path("runs", task.id, chosen_source, "answer.txt"))

    cold = _check(cold_answer, expected["total"], expected["rows"]) if cold_answer else None
    baseline = _check(base_answer, expected["total"], expected["rows"])
    adopted = _check(adopted_answer, expected["total"], expected["rows"])
    out = {
        "task": task.id,
        "cold": cold,
        "baseline": baseline,
        "adopted": adopted,
        "improved": bool(adopted["pass"] and not baseline["pass"]),
        "improved_vs_cold": bool(adopted["pass"] and cold and not cold["pass"]),
        "chosen_source": chosen_source,
    }
    atomic_json(store.path("scores", task.id + ".json"), out)
    return out


def grade_all(store: RunStore, tasks: list[Task], *, base: HarnessTree | None = None,
              timeout: int = 30) -> dict:
    """Grade an existing run; optionally execute a cold, experience-free control.

    The cold control is a normal external measurement: it runs the untouched
    starting harness and writes only under ``cold/``, never into evidence.
    """
    if base is not None:
        for task in tasks:
            run_harness(base, task.dir, store.path("cold", task.id),
                        timeout=timeout, seed=store.seed)
    results = [grade_task(store, t) for t in tasks]
    summary = {
        "namespace": store.ns,
        "tasks": len(results),
        "cold_available": all(r["cold"] is not None for r in results),
        "cold_pass": sum(1 for r in results if r["cold"] and r["cold"]["pass"]),
        "baseline_pass": sum(1 for r in results if r["baseline"]["pass"]),
        "adopted_pass": sum(1 for r in results if r["adopted"]["pass"]),
        "improved": sum(1 for r in results if r["improved"]),
        "improved_vs_cold": sum(1 for r in results if r["improved_vs_cold"]),
        "results": results,
    }
    atomic_json(store.path("scores", "summary.json"), summary)
    return summary


def assert_score_isolation(store: RunStore, task_ids: list[str]) -> list[str]:
    """Post-hoc proof that no verdict reached an evidence directory."""
    violations: list[str] = []
    for task_id in task_ids:
        ctx_root = store.task_dir(task_id) / "context"
        if not ctx_root.is_dir():
            continue
        for p in ctx_root.rglob("*"):
            if not p.is_file():
                continue
            try:
                body = p.read_text(encoding="utf-8")
            except (OSError, UnicodeDecodeError):
                continue
            if re.search(r"check\.json|pass\"?\s*:\s*true|reported_total", body):
                violations.append(p.as_posix())
    return violations


def _read(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError):
        return ""
