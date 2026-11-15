"""Local executor: run a harness version against one task in a clean workspace.

Uses a single ``subprocess.run`` with a wall timeout. On Windows there is no
process group to signal, so the executor deliberately restricts harness scripts
to one process; that trade-off is documented instead of being hidden behind a
POSIX-only ``killpg`` call.
"""

from __future__ import annotations

import shutil
import subprocess
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path

from .store import atomic_json, atomic_text, scrub_env
from .tree import HarnessTree, TEXT_SUFFIXES

ARTEFACT_CAP = 60_000


@dataclass
class Trajectory:
    ok: bool = False
    exit_code: int | None = None
    timed_out: bool = False
    seconds: float = 0.0
    stdout: str = ""
    stderr: str = ""
    artefacts: dict[str, str] = field(default_factory=dict)
    workspace_files: list[str] = field(default_factory=list)
    error: str | None = None

    def to_dict(self) -> dict:
        return {
            "ok": self.ok,
            "exit_code": self.exit_code,
            "timed_out": self.timed_out,
            "seconds": round(self.seconds, 4),
            "stdout": self.stdout[-4000:],
            "stderr": self.stderr[-4000:],
            "artefacts": self.artefacts,
            "workspace_files": self.workspace_files,
            "error": self.error,
        }

    @property
    def answer(self) -> str:
        for name in ("answer.txt", "output/answer.txt"):
            if name in self.artefacts:
                return self.artefacts[name]
        return ""


def _collect(directory: Path) -> dict[str, str]:
    found: dict[str, str] = {}
    if not directory.is_dir():
        return found
    for p in sorted(directory.rglob("*")):
        if not p.is_file() or p.suffix.lower() not in TEXT_SUFFIXES:
            continue
        rel = p.relative_to(directory).as_posix()
        try:
            text = p.read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError):
            continue
        found[rel] = text[:ARTEFACT_CAP]
    return found


def run_harness(
    harness: HarnessTree,
    task_dir: Path | str,
    out_dir: Path | str,
    *,
    timeout: int = 30,
    seed: int = 0,
) -> Trajectory:
    """Execute one harness version once and persist its full trajectory."""
    task_dir = Path(task_dir)
    out_dir = Path(out_dir)
    if out_dir.exists():
        shutil.rmtree(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    workspace = out_dir / "workspace"
    workspace.mkdir()
    harness.write(workspace / "harness")

    src_input = task_dir / "input"
    if src_input.is_dir():
        shutil.copytree(src_input, workspace / "input")
    else:
        (workspace / "input").mkdir()
    (workspace / "output").mkdir()

    task_md = task_dir / "task.md"
    if task_md.is_file():
        (workspace / "task.md").write_text(task_md.read_text(encoding="utf-8"), encoding="utf-8")

    env = scrub_env({
        "RSI_INPUT": str((workspace / "input" / "ledger.csv").resolve()),
        "RSI_OUTPUT": str((workspace / "output" / "answer.txt").resolve()),
        "RSI_WORKSPACE": str(workspace.resolve()),
        "RSI_SEED": str(seed),
        "PYTHONDONTWRITEBYTECODE": "1",
    })

    started = time.monotonic()
    traj = Trajectory()
    try:
        proc = subprocess.run(
            [sys.executable, "scripts/solve.py"],
            cwd=workspace / "harness",
            env=env,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=timeout,
        )
        traj.exit_code = proc.returncode
        traj.stdout = proc.stdout or ""
        traj.stderr = proc.stderr or ""
        traj.ok = proc.returncode == 0
    except subprocess.TimeoutExpired as exc:
        traj.timed_out = True
        traj.error = f"wall timeout after {timeout}s"
        traj.stdout = (exc.stdout or "") if isinstance(exc.stdout, str) else ""
        traj.stderr = (exc.stderr or "") if isinstance(exc.stderr, str) else ""
    except OSError as exc:
        traj.error = f"executor failed to start: {exc}"
    traj.seconds = time.monotonic() - started

    traj.artefacts = _collect(workspace / "output")
    traj.workspace_files = sorted(
        p.relative_to(workspace).as_posix()
        for p in workspace.rglob("*") if p.is_file() and "__pycache__" not in p.parts
    )

    atomic_json(out_dir / "trajectory.json", traj.to_dict())
    atomic_text(out_dir / "stdout.txt", traj.stdout)
    atomic_text(out_dir / "stderr.txt", traj.stderr)
    if traj.answer:
        atomic_text(out_dir / "answer.txt", traj.answer)
    return traj
