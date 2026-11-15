"""Executable checks for the framework's design invariants.

Run with: python -m rsi_harness selftest
"""

from __future__ import annotations

import tempfile
from pathlib import Path

from .backends import DeterministicBackend
from .bootstrap import ensure_assets
from .executor import run_harness
from .library import Library
from .recursion import improve_task
from .scoring import assert_score_isolation, grade_all
from .store import LeakError, RunStore, assert_clean, atomic_text
from .tree import HarnessTree


def _tree_keys_are_posix() -> str:
    tree = HarnessTree({"system.md": "a", "scripts/solve.py": "b"})
    edited = tree.with_edits({"scripts\\solve.py": "c"})
    assert "scripts\\solve.py" not in edited.files, "backslash key survived"
    assert edited.files["scripts/solve.py"] == "c"
    return "backslash keys normalised, no split-brain paths"


def _surface_rejects_readonly() -> str:
    tree = HarnessTree({"system.md": "a"})
    try:
        tree.with_edits({"private/check.json": "leak"})
    except ValueError:
        return "edits outside the surface are rejected"
    raise AssertionError("readonly edit was accepted")


def _all_agent_surfaces_are_editable() -> str:
    """More than prompts: tools, skills, subagents and plugins must be editable."""
    from .tree import DEFAULT_SPEC, SURFACES

    probes = {
        "system_prompt": "system.md",
        "guardrails": "guardrails.md",
        "memory": "memory.md",
        "tool_definitions": "tools/definitions.json",
        "skills": "skills/demo/SKILL.md",
        "subagents": "subagents/reviewer.md",
        "plugins": "plugins/hooks.json",
        "scripts": "scripts/solve.py",
    }
    assert set(probes) == set(SURFACES), SURFACES
    for label, rel in probes.items():
        assert DEFAULT_SPEC.allows(rel), f"{label} ({rel}) is not editable"
    tree = HarnessTree({rel: "x" for rel in probes.values()})
    assert not tree.spec.validate(tree), tree.spec.validate(tree)
    assert not DEFAULT_SPEC.allows("private/check.json")
    return f"{len(probes)} surfaces editable; readonly paths still rejected"


def _leak_guard_trips() -> str:
    with tempfile.TemporaryDirectory() as td:
        root = Path(td)
        atomic_text(root / "note.md", "a normal trajectory file")
        assert_clean(root)
        atomic_text(root / "note.md", "the rubric says this deliverable is fine")
        try:
            assert_clean(root)
        except LeakError:
            return "banned phrase is blocked before evidence reaches a backend"
        raise AssertionError("banned phrase passed the guard")


def _cache_roundtrip() -> str:
    with tempfile.TemporaryDirectory() as td:
        store = RunStore(Path(td), tag="t", mode="parallel", k=1, seed=1)
        assert store.cached("abc") is None
        store.put_cache("abc", {"v": 1})
        assert store.cached("abc") == {"v": 1}
        return f"{store.cache_stats()} on a fresh namespace"


def _private_check_never_enters_workspace() -> str:
    with tempfile.TemporaryDirectory() as td:
        root = Path(td)
        base, tasks = ensure_assets(root / "assets")
        out = root / "exec"
        traj = run_harness(base, tasks[0].dir, out, timeout=20)
        assert traj.ok, traj.stderr or traj.error
        assert "total=" in traj.answer
        names = [p.relative_to(out / "workspace").as_posix()
                 for p in (out / "workspace").rglob("*") if p.is_file()]
        assert not any("check.json" in n or "private" in n.split("/") for n in names), names
        return f"{len(names)} workspace files, no private material"


def _recursion_budget_blindness_resume_grading() -> str:
    with tempfile.TemporaryDirectory() as td:
        root = Path(td)
        base, tasks = ensure_assets(root / "assets")
        task = tasks[0]
        budgets: dict[str, dict] = {}
        pstore = None
        for mode in ("parallel", "sequential"):
            store = RunStore(root / "runs", tag="t", mode=mode, k=2, seed=3)
            backend = DeterministicBackend()
            library = Library(store.path("library"))
            res = improve_task(store=store, task=task, start=base, backend=backend,
                               library=library, mode=mode, k=2, seed=3, timeout=20,
                               base_harness=base)
            assert res["adopted_row_coverage"] == 1.0, res
            budgets[mode] = res["counters"]
            ctx = store.task_dir(task.id) / "selection" / "context"
            assert not (ctx / "manifest.json").exists(), "provenance leaked into blind context"
            labels = sorted(p.stem for p in (ctx / "blind").glob("*.txt"))
            assert labels == ["A", "B"], labels
            assert not assert_score_isolation(store, [task.id])
            if mode == "parallel":
                pstore = store
                again = improve_task(store=store, task=task, start=base, backend=backend,
                                     library=library, mode=mode, k=2, seed=3, timeout=20,
                                     base_harness=base)
                assert again["resumed"] is True, "second identical call did not resume"
        assert budgets["parallel"]["proposals"] == budgets["sequential"]["proposals"] == 4
        assert budgets["parallel"]["compositions"] == budgets["sequential"]["compositions"] == 2
        assert pstore is not None
        summary = grade_all(pstore, [task], base=base, timeout=20)
        assert summary["cold_pass"] == 0, summary
        assert summary["adopted_pass"] == 1, summary
        assert summary["improved_vs_cold"] == 1, summary
        assert not assert_score_isolation(pstore, [task.id])
        return ("2k proposals / k finals in both modes, blind labels A/B, "
                "resume hit, cold 0/1 -> adopted 1/1")


def _openai_compatible_contract() -> str:
    """Verify the pluggable backend without a key, a network or a live server."""
    import json
    from unittest.mock import patch

    from .backends import OpenAICompatBackend

    captured: dict = {}

    class Response:
        def __init__(self, text: str) -> None:
            self.text = text

        def __enter__(self) -> "Response":
            return self

        def __exit__(self, *_args) -> bool:
            return False

        def read(self) -> bytes:
            return self.text.encode("utf-8")

    def fake_urlopen(req, timeout=None):
        body = json.loads(req.data.decode("utf-8"))
        captured["body"] = body
        captured["headers"] = dict(req.headers)
        instruction = body["messages"][0]["content"]
        content = '{"label": "A"}' if "Pick the best" in instruction else '{"keep_base": true}'
        return Response(json.dumps({"choices": [{"message": {"content": content}}]}))

    backend = OpenAICompatBackend(model="mock", base_url="http://example.invalid/v1",
                                  api_key="test")
    with patch("rsi_harness.backends.urllib.request.urlopen", side_effect=fake_urlopen):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            (root / "blind").mkdir()
            atomic_text(root / "blind" / "A.txt", "total=1.00\nrows=X\n")
            (root / "source").mkdir()
            atomic_text(root / "source" / "ledger.csv", "id,amount\nX,1.00\n")
            assert backend.select(root, ["A", "B"]) == "A"
            prop = backend.propose("INV", root, HarnessTree({"system.md": "a"}))
            assert prop.keep_base is True
    assert captured["body"]["model"] == "mock"
    assert captured["headers"]["Authorization"] == "Bearer test"
    assert captured["body"]["temperature"] == 0
    return "request shape, auth header and JSON parsing verified offline"


CHECKS = [
    ("harness tree uses posix keys", _tree_keys_are_posix),
    ("editable surface rejects readonly paths", _surface_rejects_readonly),
    ("all 8 agent surfaces are editable", _all_agent_surfaces_are_editable),
    ("leak guard blocks grading language", _leak_guard_trips),
    ("content-addressed cache roundtrip", _cache_roundtrip),
    ("private check never enters workspace", _private_check_never_enters_workspace),
    ("openai-compatible backend contract", _openai_compatible_contract),
    ("recursion, blindness, resume, external grading", _recursion_budget_blindness_resume_grading),
]


def run_checks() -> list[tuple[str, bool, str]]:
    results: list[tuple[str, bool, str]] = []
    for name, fn in CHECKS:
        try:
            detail = fn()
            results.append((name, True, detail))
        except Exception as exc:
            results.append((name, False, f"{type(exc).__name__}: {exc}"))
    return results
