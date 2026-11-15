"""RSI harness for HoppinZQ Agent - a pure external sidecar.

Run from anywhere:
    python D:/myProject/github/hoppinai-agent/rsi/rsi.py --help
    python D:/myProject/github/hoppinai-agent/rsi/rsi.py demo --k 2 --fresh

What it does:
    1. Builds a harness tree under <out>/assets/harness/ with all eight
       configurable surfaces (prompt, guardrails, memory, tool definitions,
       skills, subagents, plugins, scripts) plus domain rules.
    2. Runs the two-layer recursion (propose / probe / compose / blind select /
       reflect) against offline workspace-diff tasks.
    3. Grades externally, then exports the adopted tree to rsi/out/harness/
       as a standalone data bundle (MEMORY.md, skills/, mcp.json, tool schemas).

ZERO Java source changes. Nothing is written into src/main/java or
src/main/resources. The Java agent can consume the bundle through extension
points it already has: PromptAssembler reads <workspace>/MEMORY.md,
SkillLoader walks the classpath resource 'skills', McpConfigLoader reads
/mcp.json. `apply --to <dir>` is the only command that copies data files out
of rsi/, and it must be given an explicit target.

No API key is needed: the default backend is deterministic and the executor is
a local Python script standing in for the agent's tool loop. To optimise a
real model-backed agent, point --backend openai at your endpoint and replace
rsi_harness/executor.py with a call into the Java agent.
"""

from __future__ import annotations

import argparse
import shutil
import sys
from pathlib import Path

RSI_ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(RSI_ROOT))

from bootstrap_hoppin import ensure_assets  # noqa: E402
from hoppin_adapter import (  # noqa: E402
    apply_to, bundle, latest_adopted, status, surface_checks,
)
from rsi_harness.backends import build_backend  # noqa: E402
from rsi_harness.library import Library  # noqa: E402
from rsi_harness.recursion import improve_task  # noqa: E402
from rsi_harness.scoring import assert_score_isolation, grade_all  # noqa: E402
from rsi_harness.store import RunStore, atomic_json, read_json  # noqa: E402
from rsi_harness.tree import EXTRA_SURFACES, SURFACES, HarnessTree  # noqa: E402


def _proposed_surfaces(store: RunStore, task_id: str) -> list[str]:
    """Which of the eight surfaces did the proposers actually try to edit?"""
    paths: set[str] = set()
    for p in (store.task_dir(task_id) / "proposals").glob("*.json"):
        data = read_json(p, {}) or {}
        if isinstance(data, dict):
            paths.update(str(k) for k in (data.get("edits") or {}))
    labels = set()
    for name, prefix in {**SURFACES, **EXTRA_SURFACES}.items():
        if any(rel == prefix or rel.startswith(prefix) for rel in paths):
            labels.add(name)
    return sorted(labels)

EPILOG = """examples (run from the repository root or use an absolute path):
  python rsi/rsi.py demo --k 2 --fresh
  python rsi/rsi.py demo --k 3 --mode sequential --seed 11 --fresh
  python rsi/rsi.py bundle
  python rsi/rsi.py status
  python rsi/rsi.py selftest
  python rsi/rsi.py apply --to ./hoppinzq-module-agent-sysprompt
  python rsi/rsi.py demo --backend openai --model qwen2.5:14b --fresh

outputs:
  <out>/assets/harness/                 editable 8-surface tree (yours to customise)
  <out>/<namespace>/tasks/<id>/adopted/  adopted harness per task
  <out>/<namespace>/report.json        recursion + external grading
  rsi/out/harness/                     standalone bundle: MEMORY.md, skills/,
                                       mcp.json, tools/definitions.json, bundle.json

consuming the bundle WITHOUT touching Java source:
  - MEMORY.md      copy/apply into the agent's workspace; PromptAssembler's
                   memory section reads it (1500-char cap)
  - skills/        add rsi/out/harness to the classpath; SkillLoader walks it
  - mcp.json       same classpath entry; McpConfigLoader reads /mcp.json
  - tools/definitions.json  OpenAI-compatible function schemas for a
                   config-driven tool registry; ToolDefinition in this repo is
                   compiled Java and cannot be swapped at runtime
  - scripts/       offline RSI executor only, never shipped to the agent

notes:
  - Editing files under assets/ is safe: existing files are preserved unless
    --reset-assets is given (an asset-version bump regenerates built-ins).
    A task needs task.md, input/ledger.csv and private/check.json.
  - bundle/status/apply must use the same --k --mode --seed --tag as demo,
    otherwise they look in a different namespace.
  - The Java agent still needs its own API key in AIConstants.java; this RSI
    tool does not. --backend openai needs RSI_API_KEY / RSI_BASE_URL.
"""


def _common(p: argparse.ArgumentParser) -> None:
    p.add_argument("--out", default=str(RSI_ROOT / "runs"), help="output root")
    p.add_argument("--tag", default="hoppin")
    p.add_argument("--mode", choices=("parallel", "sequential"), default="parallel")
    p.add_argument("--k", type=int, default=2)
    p.add_argument("--seed", type=int, default=7)
    p.add_argument("--fresh", action="store_true", help="clear this namespace only")
    p.add_argument("--reset-assets", action="store_true", help="restore built-in assets")
    p.add_argument("--backend", choices=("deterministic", "openai"), default="deterministic")
    p.add_argument("--model", default="")
    p.add_argument("--base-url", default="")
    p.add_argument("--timeout", type=int, default=30)
    p.add_argument("--no-bundle", action="store_true", help="skip exporting rsi/out/harness")


def _store(args) -> tuple[RunStore, HarnessTree, list]:
    out = Path(args.out).resolve()
    base, tasks = ensure_assets(out / "assets", reset=args.reset_assets)
    store = RunStore(out, tag=args.tag, mode=args.mode, k=args.k, seed=args.seed)
    if args.fresh and store.root.is_dir():
        shutil.rmtree(store.root)
        store = RunStore(out, tag=args.tag, mode=args.mode, k=args.k, seed=args.seed)
    return store, base, tasks


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        prog="rsi", description="RSI harness for HoppinZQ Agent (no Java edits)",
        epilog=EPILOG, formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    sub = parser.add_subparsers(dest="cmd", required=True)
    for name, text in (
        ("demo", "run recursion, grade externally, export standalone bundle"),
        ("bundle", "export the latest adopted harness to rsi/out/harness"),
        ("apply", "opt-in copy of MEMORY.md / skills / mcp.json to a target dir"),
        ("status", "show the current standalone bundle"),
        ("selftest", "run framework + integration invariant checks"),
    ):
        _common(sub.add_parser(name, help=text))
    sub.choices["apply"].add_argument("--to", required=True,
                                      help="explicit target directory (never src/main/java)")
    args = parser.parse_args(argv)

    if args.cmd == "selftest":
        from rsi_harness.selftest import run_checks
        checks = run_checks() + surface_checks()
        for name, ok, detail in checks:
            print(f"  [{'PASS' if ok else 'FAIL'}] {name}: {detail}")
        passed = sum(1 for _, ok, _ in checks if ok)
        print(f"  {passed}/{len(checks)} checks passed")
        return 0 if passed == len(checks) else 1

    try:
        store, base, tasks = _store(args)
    except (OSError, ValueError) as exc:
        print(f"setup error: {exc}", file=sys.stderr)
        return 2

    if args.cmd == "status":
        import json
        print(json.dumps(status(), indent=2, ensure_ascii=False))
        return 0

    if args.cmd == "bundle":
        latest = latest_adopted(store.root)
        if not latest:
            print("no adopted harness yet; run `rsi.py demo` first")
            return 1
        info = bundle(latest[1])
        print(f"bundled {latest[0]} -> {info['destination']}")
        print(f"  surfaces: {', '.join(info['surfaces'])}  digest {info['digest']}")
        return 0

    if args.cmd == "apply":
        from hoppin_adapter import BUNDLE
        try:
            info = apply_to(BUNDLE, args.to)
        except (OSError, ValueError) as exc:
            print(f"apply failed: {exc}", file=sys.stderr)
            return 2
        print(f"applied data files -> {info['target']}: {', '.join(info['files'])}")
        return 0

    backend = build_backend(args.backend, model=args.model, base_url=args.base_url or None)
    library = Library(store.path("library"))
    print(f"namespace {store.ns} | backend {backend.name} | {args.mode} k={args.k}")
    print("-" * 76)
    results = []
    for task in tasks:
        start = library.starting_harness(task.domain, base)
        res = improve_task(store=store, task=task, start=start, backend=backend,
                           library=library, mode=args.mode, k=args.k, seed=args.seed,
                           timeout=args.timeout, base_harness=base)
        res["proposed_surfaces"] = _proposed_surfaces(store, task.id)
        results.append(res)
        print(f"  {task.id:<10} rows {res['baseline_row_coverage']:.2f} -> "
              f"{res['adopted_row_coverage']:.2f}  chose {res['chosen_label']} "
              f"edits {len(res['adopted_changed_paths'])}  "
              f"experience {'yes' if res['experience_added'] else 'no'}  "
              f"[{'resume' if res.get('resumed') else 'new'}]")
        if res["proposed_surfaces"]:
            print(f"    proposed surfaces: {', '.join(res['proposed_surfaces'])}")

    summary = grade_all(store, tasks, base=base, timeout=args.timeout)
    leaks = assert_score_isolation(store, [t.id for t in tasks])
    print("-" * 76)
    print(f"  external grading: cold {summary['cold_pass']}/{summary['tasks']} -> "
          f"adopted {summary['adopted_pass']}/{summary['tasks']} | "
          f"score isolation {'ok' if not leaks else 'FAILED'}")
    print(f"  library: {Library(store.path('library')).summary()}")

    bundle_info = None
    if not args.no_bundle:
        latest = latest_adopted(store.root)
        if latest:
            bundle_info = bundle(latest[1])
            print(f"  bundle: {bundle_info['destination']} "
                  f"({len(bundle_info['surfaces'])} surfaces, digest {bundle_info['digest']})")
            print("  Java source untouched: True")
    atomic_json(store.path("report.json"), {
        "namespace": store.ns, "recursion": results, "grading": summary,
        "isolation_violations": leaks, "bundle": bundle_info,
    })
    print(f"  report: {store.path('report.json')}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
