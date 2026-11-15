"""Command line interface for the mini RSI harness framework."""

from __future__ import annotations

import argparse
import json
import shutil
import sys
from pathlib import Path

from . import __version__
from .backends import build_backend
from .bootstrap import ensure_assets
from .library import Library
from .recursion import improve_task
from .scoring import assert_score_isolation, grade_all
from .store import RunStore, atomic_json, read_json
from .tree import HarnessTree

EPILOG = """examples:
  python -m rsi_harness demo --out runs/rsi-demo --k 2
  python -m rsi_harness demo --out runs/rsi-seq --k 3 --mode sequential --seed 11
  python -m rsi_harness demo --out runs/rsi-llm --backend openai --model gpt-4o-mini
  python -m rsi_harness judge --out runs/rsi-demo --k 2
  python -m rsi_harness report --out runs/rsi-demo --k 2
  python -m rsi_harness inspect --out runs/rsi-demo --k 2 --task ledger-1
  python -m rsi_harness selftest
  python -m rsi_harness demo --out runs/rsi-demo --k 2 --fresh --reset-assets
  RSI_API_KEY=x RSI_BASE_URL=http://localhost:11434/v1 python -m rsi_harness \\
      demo --out runs/rsi-ollama --backend openai --model qwen2.5:14b

Edit files under <out>/assets/ to use your own tasks and starting harness.
They are preserved on subsequent runs; --reset-assets restores the built-ins.
A task needs task.md, input/ledger.csv and private/check.json with
{"total": <net>, "rows": [<every id>]}.

backend=openai needs RSI_API_KEY, optionally RSI_BASE_URL and RSI_MODEL.
No credentials are needed for the default deterministic backend.
To use your own model, subclass rsi_harness.backends.Backend and pass the
instance directly to rsi_harness.recursion.improve_task().

invariants:
  - proposals read recorded trajectories, never a hidden check file
  - blind selection sees renamed deliverables, not harness provenance
  - grading runs afterwards in `judge` and is never read by the editing loop
  - one seeded namespace per run; repeating a command resumes from cache
  - standard library only; no fcntl, killpg, bash scripts or Docker
"""


def _common(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--out", default="runs/rsi-demo", help="output root")
    parser.add_argument("--tag", default="rsi", help="namespace prefix")
    parser.add_argument("--mode", choices=("parallel", "sequential"), default="parallel")
    parser.add_argument("--k", type=int, default=2, help="recursion budget")
    parser.add_argument("--seed", type=int, default=7)
    parser.add_argument("--backend", default="deterministic",
                        choices=("deterministic", "openai"))
    parser.add_argument("--model", default="", help="model id for backend=openai")
    parser.add_argument("--base-url", default="", help="OpenAI-compatible base URL")
    parser.add_argument("--timeout", type=int, default=30, help="per-execution wall seconds")
    parser.add_argument("--fresh", action="store_true",
                        help="clear only this run's namespace before starting")
    parser.add_argument("--reset-assets", action="store_true",
                        help="overwrite assets/ with built-in tasks and harness (discards edits)")


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        prog="rsi_harness",
        description=f"mini RSI harness framework v{__version__} - edit the harness, not the model",
        epilog=EPILOG,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    sub = p.add_subparsers(dest="cmd", required=True)
    for name, text in (
        ("demo", "generate assets, run recursion, grade, and report"),
        ("run", "run recursion only (no grading)"),
        ("judge", "grade an existing run externally"),
        ("report", "summarise an existing run"),
        ("inspect", "show the evidence laid out for one task"),
        ("selftest", "run invariant checks on the framework itself"),
    ):
        sp = sub.add_parser(name, help=text)
        _common(sp)
        if name == "inspect":
            sp.add_argument("--task", default="ledger-1")
    return p


def _setup(args) -> tuple[RunStore, HarnessTree, list, Library, object]:
    out = Path(args.out).resolve()
    base, tasks = ensure_assets(out / "assets", reset=args.reset_assets)
    store = RunStore(out, tag=args.tag, mode=args.mode, k=args.k, seed=args.seed)
    if args.fresh and store.root.is_dir():
        shutil.rmtree(store.root)
        store = RunStore(out, tag=args.tag, mode=args.mode, k=args.k, seed=args.seed)
    library = Library(store.path("library"))
    backend = build_backend(args.backend, model=args.model, base_url=args.base_url or None)
    return store, base, tasks, library, backend


def _run(args, store, base, tasks, library, backend) -> list[dict]:
    results = []
    print(f"namespace {store.ns} | backend {backend.name} | {args.mode} k={args.k} seed={args.seed}")
    print("-" * 78)
    for task in tasks:
        start = library.starting_harness(task.domain, base)
        res = improve_task(
            store=store, task=task, start=start, backend=backend, library=library,
            mode=args.mode, k=args.k, seed=args.seed, timeout=args.timeout,
            base_harness=base,
        )
        results.append(res)
        flag = "resume" if res.get("resumed") else "new"
        print(
            f"  {task.id:<10} rows {res['baseline_row_coverage']:.2f} -> "
            f"{res['adopted_row_coverage']:.2f}   chose {res['chosen_label']} "
            f"({res['chosen_source']})   edits {len(res['adopted_changed_paths'])}   "
            f"experience {'yes' if res['experience_added'] else 'no'}   [{flag}]"
        )
    return results


def _print_summary(store, tasks, grade_summary, results) -> None:
    print("-" * 78)
    total_exec = sum(r["counters"]["executions"] for r in results)
    total_prop = sum(r["counters"]["proposals"] for r in results)
    hits = sum(r["cache"]["hits"] for r in results)
    misses = sum(r["cache"]["misses"] for r in results)
    print(f"  executions {total_exec} | proposals {total_prop} | "
          f"cache {hits}/{misses} (hit/miss)")
    if grade_summary:
        cold = (f"cold {grade_summary['cold_pass']}/{grade_summary['tasks']} -> "
                if grade_summary["cold_available"] else "")
        print(f"  external grading: {cold}in-run baseline "
              f"{grade_summary['baseline_pass']}/{grade_summary['tasks']} -> "
              f"adopted {grade_summary['adopted_pass']}/{grade_summary['tasks']} | "
              f"improved vs cold {grade_summary['improved_vs_cold']}")
    leaks = assert_score_isolation(store, [t.id for t in tasks])
    print(f"  score isolation: {'ok - no verdict in evidence' if not leaks else 'FAILED ' + str(leaks)}")
    lib = Library(store.path("library"))
    print(f"  library: {lib.summary() or '(empty)'}")
    print(f"  output: {store.root}")


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    if args.cmd == "selftest":
        from .selftest import run_checks
        checks = run_checks()
        for name, ok, detail in checks:
            print(f"  [{'PASS' if ok else 'FAIL'}] {name}: {detail}")
        passed = sum(1 for _, ok, _ in checks if ok)
        print(f"  {passed}/{len(checks)} checks passed")
        return 0 if passed == len(checks) else 1
    if args.k < 1:
        print("error: --k must be >= 1", file=sys.stderr)
        return 2
    try:
        store, base, tasks, library, backend = _setup(args)
    except (ValueError, OSError) as exc:
        print(f"setup error: {exc}", file=sys.stderr)
        return 2

    if args.cmd in ("demo", "run"):
        try:
            results = _run(args, store, base, tasks, library, backend)
        except Exception as exc:  # surface the stage that failed
            print(f"recursion failed: {type(exc).__name__}: {exc}", file=sys.stderr)
            raise
        grade_summary = None
        if args.cmd == "demo":
            grade_summary = grade_all(store, tasks, base=base, timeout=args.timeout)
            atomic_json(store.path("report.json"), {
                "namespace": store.ns, "recursion": results, "grading": grade_summary,
                "isolation_violations": assert_score_isolation(store, [t.id for t in tasks]),
            })
        _print_summary(store, tasks, grade_summary, results)
        return 0

    if args.cmd == "judge":
        summary = grade_all(store, tasks, base=base, timeout=args.timeout)
        print(json.dumps(summary, indent=2, ensure_ascii=False))
        return 0

    if args.cmd == "report":
        summary = read_json(store.path("scores", "summary.json"))
        if summary is None:
            print("no grading yet; run `demo` or `judge` first")
            return 1
        print(f"namespace {store.ns}")
        for r in summary["results"]:
            print(f"  {r['task']:<10} baseline pass={r['baseline']['pass']} "
                  f"adopted pass={r['adopted']['pass']} improved={r['improved']}")
        print(f"  total: {summary['baseline_pass']} -> {summary['adopted_pass']} "
              f"of {summary['tasks']}")
        return 0

    if args.cmd == "inspect":
        tdir = store.task_dir(args.task)
        if not tdir.is_dir():
            print(f"no evidence for {args.task}")
            return 1
        print(f"evidence for {args.task} under {tdir}")
        for rel in ("context/propose", "context/compose", "selection/context",
                    "selection/manifest.json", "adoption.json", "reflection.json"):
            p = tdir / rel
            if p.is_dir():
                n = sum(1 for x in p.rglob("*") if x.is_file())
                print(f"  {rel:<28} {n} files")
            elif p.is_file():
                print(f"  {rel:<28} {json.dumps(read_json(p), ensure_ascii=False)[:160]}")
        adopted = tdir / "adopted" / "harness" / "scripts" / "solve.py"
        if adopted.is_file():
            print("\nadopted scripts/solve.py:")
            for line in adopted.read_text(encoding="utf-8").splitlines():
                print("  | " + line)
        return 0

    return 2


if __name__ == "__main__":
    raise SystemExit(main())
