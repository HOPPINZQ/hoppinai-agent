"""Second recursion layer: turn an adopted harness into a reusable prior.

Experience is stored as the *complete harness tree*, not a prose summary. The
next task in the same domain starts from that tree and receives the lesson in
``memory.md``, so learning is carried by executable configuration.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from .store import atomic_json, read_json
from .tree import HarnessTree


@dataclass
class Experience:
    cue: str
    lesson: str
    evidence: str
    confidence: str

    def to_dict(self) -> dict:
        return {
            "cue": self.cue, "lesson": self.lesson,
            "evidence": self.evidence, "confidence": self.confidence,
        }

    @classmethod
    def from_dict(cls, data: dict | None) -> "Experience | None":
        if not isinstance(data, dict):
            return None
        fields = {k: str(data.get(k, "")).strip() for k in ("cue", "lesson", "evidence")}
        if not all(fields.values()):
            return None
        return cls(**fields, confidence=str(data.get("confidence", "medium")))


class Library:
    def __init__(self, root: Path | str):
        self.root = Path(root)
        self.root.mkdir(parents=True, exist_ok=True)

    def _domain_dir(self, domain: str) -> Path:
        return self.root / domain

    def entries(self, domain: str) -> list[Path]:
        d = self._domain_dir(domain)
        if not d.is_dir():
            return []
        return sorted(p for p in d.iterdir() if p.is_dir() and p.name.startswith("e"))

    def add(self, domain: str, experience: Experience | None, harness: HarnessTree,
            reflection: dict, task_id: str) -> bool:
        d = self._domain_dir(domain)
        seq = len(self.entries(domain)) + 1
        entry = d / f"e{seq:03d}"
        entry.mkdir(parents=True, exist_ok=True)
        harness.write(entry / "harness")
        atomic_json(entry / "entry.json", {
            "task": task_id,
            "harness_digest": harness.digest(),
            "experience": experience.to_dict() if experience else None,
            "reflection": reflection,
        })
        return experience is not None

    def note_text(self, domain: str) -> str:
        lines: list[str] = []
        for entry in self.entries(domain):
            data = read_json(entry / "entry.json", {})
            exp = Experience.from_dict(data.get("experience")) if isinstance(data, dict) else None
            if exp:
                lines.append(f"- When {exp.cue}: {exp.lesson} [{exp.confidence} confidence]")
        return "\n".join(lines)

    def starting_harness(self, domain: str, base: HarnessTree) -> HarnessTree:
        """Prior for a task: last adopted tree, with lessons appended to memory."""
        entries = self.entries(domain)
        if not entries:
            return base
        last = entries[-1]
        tree = HarnessTree.read(last / "harness", spec=base.spec)
        lessons = self.note_text(domain)
        if lessons:
            memory = tree.files.get("memory.md", "# Memory\n")
            marker = "## Carried lessons"
            if marker not in memory:
                tree.files["memory.md"] = memory.rstrip() + f"\n\n{marker}\n{lessons}\n"
        return tree

    def summary(self) -> dict:
        out: dict[str, int] = {}
        for d in sorted(self.root.iterdir()):
            if d.is_dir():
                out[d.name] = len(self.entries(d.name))
        return out
