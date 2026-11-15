"""Deterministic run storage: atomic writes, content-addressed cache, resume.

There is deliberately no ``fcntl`` here. Cross-process safety comes from writing
to a unique temporary file and calling ``os.replace``, which is atomic on both
POSIX and Windows. Concurrent writers may redo work, but never corrupt a record.
"""

from __future__ import annotations

import hashlib
import json
import os
import tempfile
from pathlib import Path

BANNED_FILENAMES = (
    "verify.json", "answer_key", "answerkey", "rubric", "grader",
    "ground_truth", "grading", "scores.json", "expected",
)
BANNED_PHRASES = (
    "ground truth", "answer key", "expected_total", "rubric", "grader verdict",
    "do not have the score", "benchmark score", "evaluation criteria",
)
SECRET_MARKERS = (
    "TOKEN", "SECRET", "API_KEY", "APIKEY", "PASSWORD", "CREDENTIAL",
    "GOOGLE_APPLICATION", "GOOGLE_CLOUD", "ANTHROPIC", "OPENAI", "VERTEX",
)


def atomic_text(path: Path | str, text: str) -> Path:
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(prefix=path.name + ".", suffix=".tmp", dir=path.parent)
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as fh:
            fh.write(text)
        os.replace(tmp, path)
    finally:
        if os.path.exists(tmp):
            os.unlink(tmp)
    return path


def atomic_json(path: Path | str, obj: object) -> Path:
    return atomic_text(path, json.dumps(obj, indent=2, sort_keys=True, ensure_ascii=False) + "\n")


def read_json(path: Path | str, default: object = None) -> object:
    p = Path(path)
    if not p.is_file():
        return default
    try:
        return json.loads(p.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return default


def namespace(tag: str, mode: str, k: int, seed: int) -> str:
    return f"{tag}-{mode}{k}-s{seed}"


def digest_of(*parts: object) -> str:
    payload = json.dumps(parts, sort_keys=True, default=str, ensure_ascii=False)
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()[:20]


def assert_clean(root: Path | str, *, skip_names: tuple[str, ...] = ("manifest.json",)) -> None:
    """Guard rail: no grading signal may be present in an evidence directory.

    Raises ``LeakError`` with the offending paths. Called before every session
    that hands evidence to an improver or selector.
    """
    root = Path(root)
    offenders: list[str] = []
    if not root.is_dir():
        return
    for p in sorted(root.rglob("*")):
        if not p.is_file():
            continue
        name = p.name.lower()
        if any(bad in name for bad in BANNED_FILENAMES) and name not in skip_names:
            offenders.append(f"{p.relative_to(root).as_posix()} (filename)")
            continue
        try:
            body = p.read_text(encoding="utf-8").lower()
        except (OSError, UnicodeDecodeError):
            continue
        for phrase in BANNED_PHRASES:
            if phrase in body:
                offenders.append(f"{p.relative_to(root).as_posix()} (contains {phrase!r})")
                break
    if offenders:
        raise LeakError("grading signal leaked into evidence:\n  - " + "\n  - ".join(offenders))


class LeakError(RuntimeError):
    pass


def scrub_env(extra: dict[str, str] | None = None) -> dict[str, str]:
    """Environment for executor subprocesses: strip host credentials."""
    env = {
        k: v for k, v in os.environ.items()
        if not any(marker in k.upper() for marker in SECRET_MARKERS)
    }
    for key in ("PYTHONPATH", "PYTHONHOME", "VIRTUAL_ENV"):
        env.pop(key, None)
    if extra:
        env.update(extra)
    return env


class RunStore:
    """Owns the on-disk layout of one seeded experiment."""

    def __init__(self, out: Path | str, *, tag: str, mode: str, k: int, seed: int):
        self.ns = namespace(tag, mode, k, seed)
        self.root = Path(out).resolve() / self.ns
        self.mode, self.k, self.seed = mode, k, seed
        self.root.mkdir(parents=True, exist_ok=True)
        self._cache_hits = 0
        self._cache_misses = 0

    def path(self, *parts: str) -> Path:
        return self.root.joinpath(*parts)

    def task_dir(self, task_id: str) -> Path:
        return self.path("tasks", task_id)

    def context_dir(self, task_id: str, stage: str, label: str) -> Path:
        return self.task_dir(task_id) / "context" / stage / label

    def cached(self, key: str) -> object | None:
        p = self.path("cache", key + ".json")
        if p.is_file():
            self._cache_hits += 1
            return read_json(p)
        self._cache_misses += 1
        return None

    def put_cache(self, key: str, value: object) -> None:
        atomic_json(self.path("cache", key + ".json"), value)

    def stage_done(self, task_id: str, stage: str, inputs_digest: str) -> object | None:
        rec = read_json(self.task_dir(task_id) / "stages" / (stage + ".json"))
        if isinstance(rec, dict) and rec.get("inputs") == inputs_digest:
            return rec.get("result")
        return None

    def mark_stage(self, task_id: str, stage: str, inputs_digest: str, result: object) -> None:
        atomic_json(
            self.task_dir(task_id) / "stages" / (stage + ".json"),
            {"inputs": inputs_digest, "result": result},
        )

    def cache_stats(self) -> dict[str, int]:
        return {"hits": self._cache_hits, "misses": self._cache_misses}
