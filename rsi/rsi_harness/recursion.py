"""Task-level recursion: baseline -> propose -> probe -> compose -> blind select.

Both budget modes cost the same: 2k proposals, 2k probes and k final versions.
``parallel`` spends it in one round of width (k INV + k DOM on shared evidence);
``sequential`` spends it in k rounds of depth, each round editing the previous
checkpoint and accumulating evidence.
"""

from __future__ import annotations

import hashlib
import random
import shutil
import string
from dataclasses import dataclass
from pathlib import Path

from .backends import Backend, Proposal, artefact_quality
from .executor import Trajectory, run_harness
from .library import Experience, Library
from .store import RunStore, assert_clean, atomic_json, atomic_text, digest_of
from .tree import HarnessTree

CRITERIA = """Choose the deliverable that best satisfies the task, in this order:

1. Was a deliverable produced at all?
2. Does every figure agree with the public source file?
3. Is the deliverable internally coherent?
4. Can it be used as-is, without repair?
5. Is anything left unfinished?

The files are anonymous. You do not know which configuration produced which one.
"""

PROPOSAL_NOTE = """Evidence directory. Read the files on disk.

`task.md` states the work. `source/` is the public input. `base/` holds recorded
trajectories of the current harness. `current_harness/` is the editable file
tree. `pool/`, when present, holds diffs and trial results of other candidates.
`notes/library.md` carries lessons from earlier tasks in this domain.

Every edit must be traceable to a concrete observation in these files. When the
evidence does not support a change, keep the current harness unchanged.
"""


@dataclass
class Task:
    id: str
    dir: Path
    domain: str

    @property
    def source(self) -> str:
        return (self.dir / "input" / "ledger.csv").read_text(encoding="utf-8")


def _clean_dir(path: Path) -> Path:
    if path.exists():
        shutil.rmtree(path)
    path.mkdir(parents=True)
    return path


def _write_traj(dst: Path, traj: Trajectory) -> None:
    dst.mkdir(parents=True, exist_ok=True)
    atomic_json(dst / "trajectory.json", traj.to_dict())
    atomic_text(dst / "stdout.txt", traj.stdout)
    atomic_text(dst / "stderr.txt", traj.stderr)
    atomic_text(dst / "answer.txt", traj.answer)


def _build_context(
    ctx: Path,
    *,
    task: Task,
    current: HarnessTree,
    baselines: list[tuple[str, Trajectory]],
    notes: str,
    focus: dict,
    pool: list[dict] | None = None,
    trajectory_root: Path | None = None,
) -> None:
    _clean_dir(ctx)
    atomic_text(ctx / "task.md", (task.dir / "task.md").read_text(encoding="utf-8"))
    atomic_text(ctx / "README-evidence.md", PROPOSAL_NOTE)
    (ctx / "source").mkdir()
    atomic_text(ctx / "source" / "ledger.csv", task.source)
    (ctx / "base").mkdir()
    for label, traj in baselines:
        d = ctx / "base" / label
        atomic_text(d / "answer.txt", traj.answer)
        atomic_text(d / "stdout.txt", traj.stdout)
        atomic_json(d / "trajectory.json", traj.to_dict())
    current.write(ctx / "current_harness")
    if notes:
        atomic_text(ctx / "notes" / "library.md", notes)
    atomic_json(ctx / "focus.json", focus)
    if pool:
        (ctx / "pool").mkdir()
        for item in pool:
            d = ctx / "pool" / item["label"]
            atomic_text(d / "diff.patch", item["diff"])
            atomic_text(d / "answer.txt", item["traj"].answer)
            atomic_json(d / "trajectory.json", item["traj"].to_dict())
    if trajectory_root:
        atomic_text(ctx / "runs_root.txt", trajectory_root.as_posix())


def _apply(current: HarnessTree, edits: dict[str, str]) -> tuple[HarnessTree, bool]:
    if not edits:
        return current, False
    try:
        return current.with_edits(edits), True
    except ValueError:
        return current, False


def improve_task(
    *,
    store: RunStore,
    task: Task,
    start: HarnessTree,
    backend: Backend,
    library: Library,
    mode: str = "parallel",
    k: int = 2,
    seed: int = 7,
    timeout: int = 30,
    base_harness: HarnessTree | None = None,
) -> dict:
    if mode not in ("parallel", "sequential"):
        raise ValueError(f"unknown mode: {mode}")
    if k < 1:
        raise ValueError("k must be >= 1")

    # The resume key must not depend on `start`: that tree carries experience
    # injected from earlier tasks, so it changes as the library grows. Anchoring
    # on the untouched base keeps "same command, same result" true on a rerun.
    anchor = (base_harness or start).digest()
    backend_id = backend.name + ":" + str(getattr(backend, "model", ""))
    inputs = digest_of(task.id, digest_of(task.source), anchor,
                       mode, k, seed, backend_id)
    resumed = store.stage_done(task.id, "adoption", inputs)
    if resumed is not None:
        return {**dict(resumed), "resumed": True}

    cache_before = store.cache_stats()
    rng = random.Random(int(
        hashlib.sha256(f"{task.id}|{k}|{mode}|{seed}".encode()).hexdigest()[:8], 16
    ))
    rounds = k if mode == "sequential" else 1
    per_scope = 1 if mode == "sequential" else k
    finals_per_round = 1 if mode == "sequential" else k
    notes = library.note_text(task.domain)

    counters = {"executions": 0, "proposals": 0, "probes": 0,
                "compositions": 0, "selections": 1, "rejected_edits": 0}
    current = start
    baselines: list[tuple[str, Trajectory]] = []
    finals: list[tuple[str, HarnessTree, Trajectory]] = []

    for r in range(rounds):
        n_base = k if r == 0 else 1
        for i in range(n_base):
            label = f"r{r + 1}-base{i + 1}"
            out = store.path("runs", task.id, label)
            traj = run_harness(current, task.dir, out, timeout=timeout, seed=seed + i)
            counters["executions"] += 1
            _write_traj(out, traj)
            baselines.append((label, traj))

        proposals: list[tuple[str, Proposal]] = []
        for scope in ("INV", "DOM"):
            for i in range(per_scope):
                label = f"r{r + 1}-{scope.lower()}{i + 1}"
                ctx = store.context_dir(task.id, "propose", label)
                focus = {"scope": scope, "variant": i + r, "round": r + 1, "mode": mode}
                _build_context(ctx, task=task, current=current, baselines=baselines,
                               notes=notes, focus=focus,
                               trajectory_root=store.path("runs", task.id))
                assert_clean(ctx)
                key = digest_of("propose", scope, focus, current.digest(),
                                [t.answer for _, t in baselines], notes, backend.name)
                cached = store.cached(key)
                if isinstance(cached, dict):
                    prop = Proposal.from_dict(cached)
                else:
                    prop = backend.propose(scope, ctx, current)
                    store.put_cache(key, prop.to_dict())
                proposals.append((label, prop))
                counters["proposals"] += 1
                atomic_json(store.task_dir(task.id) / "proposals" / f"{label}.json", prop.to_dict())

        pool: list[dict] = []
        for label, prop in proposals:
            candidate, changed = _apply(current, prop.edits if not prop.keep_base else {})
            if prop.edits and not changed:
                counters["rejected_edits"] += 1
            out = store.path("runs", task.id, f"probe-{label}")
            traj = run_harness(candidate, task.dir, out, timeout=timeout, seed=seed)
            counters["executions"] += 1
            counters["probes"] += 1
            _write_traj(out, traj)
            diff = current.diff(candidate)
            atomic_text(out / "diff.patch", diff)
            pool.append({"label": label, "scope": prop.scope, "edits": prop.edits,
                         "tree": candidate, "traj": traj, "diff": diff})

        compose_ctx = store.context_dir(task.id, "compose", f"r{r + 1}")
        _build_context(compose_ctx, task=task, current=current, baselines=baselines,
                       notes=notes, focus={"stage": "compose", "round": r + 1},
                       pool=pool, trajectory_root=store.path("runs", task.id))
        assert_clean(compose_ctx)
        merged = backend.compose(
            [{"scope": p["scope"], "edits": p["edits"]} for p in pool], finals_per_round
        )
        for j, edits in enumerate(merged):
            label = f"r{r + 1}-final{j + 1}"
            candidate, _ = _apply(current, edits)
            out = store.path("runs", task.id, label)
            traj = run_harness(candidate, task.dir, out, timeout=timeout, seed=seed)
            counters["executions"] += 1
            _write_traj(out, traj)
            atomic_text(out / "diff.patch", current.diff(candidate))
            finals.append((label, candidate, traj))
            counters["compositions"] += 1
        if mode == "sequential" and finals:
            current = finals[-1][1]

    sel_dir = store.task_dir(task.id) / "selection"
    ctx = _clean_dir(sel_dir / "context")
    (ctx / "blind").mkdir()
    (ctx / "source").mkdir()
    atomic_text(ctx / "source" / "ledger.csv", task.source)
    atomic_text(ctx / "criteria.md", CRITERIA)
    labels = list(string.ascii_uppercase[:len(finals)])
    shuffled = labels[:]
    rng.shuffle(shuffled)
    mapping: dict[str, str] = {}
    for (label, _tree, traj), anon in zip(finals, shuffled):
        atomic_text(ctx / "blind" / f"{anon}.txt", traj.answer)
        mapping[anon] = label
    assert_clean(ctx)
    chosen_label = backend.select(ctx, labels)
    atomic_json(sel_dir / "manifest.json", {
        "mapping": mapping, "labels": labels, "chosen": chosen_label,
        "seed": seed, "finals": [f[0] for f in finals],
    })
    chosen_source = mapping.get(chosen_label, finals[0][0])
    adopted = next((t for l, t, _ in finals if l == chosen_source), finals[0][1])
    adopted.write(store.task_dir(task.id) / "adopted" / "harness")
    atomic_json(store.task_dir(task.id) / "adoption.json", {
        "chosen_label": chosen_label, "chosen_source": chosen_source,
        "harness_digest": adopted.digest(),
    })

    changed = start.changed_keys(adopted)
    refl_dir = _clean_dir(store.task_dir(task.id) / "reflection" / "context")
    (refl_dir / "source").mkdir()
    atomic_text(refl_dir / "source" / "ledger.csv", task.source)
    atomic_text(refl_dir / "base_answer.txt", baselines[0][1].answer)
    atomic_text(refl_dir / "adopted_answer.txt",
                next((t.answer for l, _, t in finals if l == chosen_source), ""))
    atomic_text(refl_dir / "diff.patch", start.diff(adopted))
    (refl_dir / "pool").mkdir()
    for item in pool:
        atomic_text(refl_dir / "pool" / (item["label"] + ".answer.txt"), item["traj"].answer)
        atomic_text(refl_dir / "pool" / (item["label"] + ".patch"), item["diff"])
    assert_clean(refl_dir)
    reflection = backend.reflect(refl_dir, changed)
    experience = Experience.from_dict(reflection.get("experience"))
    added = library.add(task.domain, experience, adopted, reflection, task.id)
    atomic_json(store.task_dir(task.id) / "reflection.json", reflection)

    base_q = artefact_quality(baselines[0][1].answer, task.source)
    final_answer = next((t.answer for l, _, t in finals if l == chosen_source), "")
    final_q = artefact_quality(final_answer, task.source)
    result = {
        "task": task.id,
        "mode": mode,
        "k": k,
        "backend": backend.name,
        "resumed": False,
        "baseline_row_coverage": base_q["coverage"],
        "adopted_row_coverage": final_q["coverage"],
        "chosen_label": chosen_label,
        "chosen_source": chosen_source,
        "adopted_digest": adopted.digest(),
        "adopted_changed_paths": changed,
        "experience_added": added,
        "counters": counters,
        "cache": {
            "hits": store.cache_stats()["hits"] - cache_before["hits"],
            "misses": store.cache_stats()["misses"] - cache_before["misses"],
        },
    }
    store.mark_stage(task.id, "adoption", inputs, result)
    return result
