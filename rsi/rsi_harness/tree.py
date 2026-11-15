"""Editable harness tree and its surface specification.

Every relative path is normalised with ``Path.as_posix()``. This matters: using
``str(Path(...))`` produces backslashes on Windows, which silently breaks any
comparison against a specification written with forward slashes.
"""

from __future__ import annotations

import difflib
import hashlib
import json
from dataclasses import dataclass, field
from pathlib import Path

TEXT_SUFFIXES = {
    ".md", ".py", ".txt", ".json", ".yaml", ".yml", ".toml",
    ".sh", ".js", ".ts", ".csv", ".cfg", ".ini",
}
SKIP_PARTS = {"__pycache__", ".git", ".pytest_cache", "private"}
MAX_FILE_CHARS = 20_000


# The eight configurable surfaces of an agent, mirroring the Harness^2 paper.
# A path prefix means "every file under here is editable".
SURFACES: dict[str, str] = {
    "system_prompt": "system.md",
    "guardrails": "guardrails.md",
    "memory": "memory.md",
    "tool_definitions": "tools/",
    "skills": "skills/",
    "subagents": "subagents/",
    "plugins": "plugins/",
    "scripts": "scripts/",
}
# Domain knowledge is a ninth, optional surface (practitioner know-how).
EXTRA_SURFACES: dict[str, str] = {"domain_rules": "domain/"}


@dataclass(frozen=True)
class SurfaceSpec:
    """Which paths an improver is allowed to edit, and how large they may be."""

    editable: tuple[str, ...] = (
        "system.md",
        "guardrails.md",
        "memory.md",
        "tools/",
        "skills/",
        "subagents/",
        "plugins/",
        "domain/",
        "scripts/",
    )
    readonly: tuple[str, ...] = ("private/", "expected", "verify")
    max_file_chars: int = MAX_FILE_CHARS

    def allows(self, rel: str) -> bool:
        rel = rel.replace("\\", "/")
        if any(bad in rel.lower() for bad in self.readonly):
            return False
        return any(rel == e or rel.startswith(e) for e in self.editable)

    def validate(self, tree: "HarnessTree") -> list[str]:
        """Return human-readable violations instead of raising immediately."""
        problems: list[str] = []
        for rel, text in tree.files.items():
            if not self.allows(rel):
                problems.append(f"path outside editable surface: {rel}")
            if len(text) > self.max_file_chars:
                problems.append(f"{rel}: {len(text)} chars > {self.max_file_chars}")
        return problems


DEFAULT_SPEC = SurfaceSpec()


@dataclass
class HarnessTree:
    """A harness version: a flat mapping of POSIX relative paths to text."""

    files: dict[str, str] = field(default_factory=dict)
    spec: SurfaceSpec = field(default=DEFAULT_SPEC, repr=False)

    @classmethod
    def read(cls, root: Path | str, spec: SurfaceSpec = DEFAULT_SPEC) -> "HarnessTree":
        root = Path(root)
        files: dict[str, str] = {}
        for p in sorted(root.rglob("*")):
            if not p.is_file() or p.suffix.lower() not in TEXT_SUFFIXES:
                continue
            rel_parts = p.relative_to(root).parts
            if any(part in SKIP_PARTS for part in rel_parts):
                continue
            rel = p.relative_to(root).as_posix()
            files[rel] = p.read_text(encoding="utf-8")
        return cls(files=files, spec=spec)

    def write(self, dest: Path | str) -> Path:
        """Materialise the tree on disk, replacing any previous contents."""
        dest = Path(dest)
        dest.mkdir(parents=True, exist_ok=True)
        for rel, text in sorted(self.files.items()):
            target = dest / rel
            if ".." in Path(rel).parts or Path(rel).is_absolute():
                raise ValueError(f"unsafe tree key: {rel}")
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(text, encoding="utf-8")
        return dest

    def with_edits(self, edits: dict[str, str]) -> "HarnessTree":
        """Apply a proposal. Invalid paths are rejected rather than ignored."""
        merged = dict(self.files)
        for rel, text in edits.items():
            rel = rel.replace("\\", "/")
            if not self.spec.allows(rel):
                raise ValueError(f"edit rejected, outside editable surface: {rel}")
            if len(text) > self.spec.max_file_chars:
                raise ValueError(f"edit rejected, too large: {rel}")
            merged[rel] = text
        out = HarnessTree(files=merged, spec=self.spec)
        problems = out.spec.validate(out)
        if problems:
            raise ValueError("; ".join(problems))
        return out

    def diff(self, other: "HarnessTree") -> str:
        """Unified diff from ``self`` (base) to ``other`` (candidate)."""
        keys = sorted(set(self.files) | set(other.files))
        chunks: list[str] = []
        for key in keys:
            before = self.files.get(key, "").splitlines(keepends=True)
            after = other.files.get(key, "").splitlines(keepends=True)
            if before == after:
                continue
            chunks.extend(
                difflib.unified_diff(
                    before, after, fromfile=f"a/{key}", tofile=f"b/{key}", n=3
                )
            )
        return "".join(chunks)

    def changed_keys(self, other: "HarnessTree") -> list[str]:
        return sorted(
            k for k in set(self.files) | set(other.files)
            if self.files.get(k) != other.files.get(k)
        )

    def digest(self) -> str:
        payload = json.dumps(self.files, sort_keys=True, ensure_ascii=False)
        return hashlib.sha256(payload.encode("utf-8")).hexdigest()[:16]

    def __len__(self) -> int:
        return len(self.files)
