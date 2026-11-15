"""Pluggable reasoning backends: proposer, composer, blind selector, reflector.

A backend only ever reads an evidence directory. It never receives an answer
key, a verdict, or any numeric result of grading. Two implementations ship:

* ``DeterministicBackend`` - rule-driven, free, reproducible, no credentials.
* ``OpenAICompatBackend``  - same interface over any OpenAI-compatible endpoint.

Both share the same evidence contract, so switching backends does not change
the recursion, the budget, or the leakage guarantees.
"""

from __future__ import annotations

import csv
import io
import json
import os
import re
import urllib.error
import urllib.request
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from pathlib import Path

from .tree import HarnessTree

ZERO_FILTER = re.compile(r"^(\s*)if\s+.+?(?:>\s*0|!=\s*0)\s*:\s*(?:#.*)?$")
TOTAL_LINE = re.compile(r"^total\s*=\s*(-?\d+(?:\.\d+)?)\s*$", re.M)
ROWS_LINE = re.compile(r"^rows\s*=\s*([^\n]*)$", re.M)


def parse_source(text: str) -> dict[str, float]:
    """Read the public task source file into id -> amount."""
    out: dict[str, float] = {}
    for row in csv.DictReader(io.StringIO(text)):
        try:
            out[str(row["id"]).strip()] = float(row["amount"])
        except (KeyError, TypeError, ValueError):
            continue
    return out


def artefact_quality(answer: str, source_text: str) -> dict[str, float]:
    """Judge a deliverable only against the publicly available source file.

    This is the blind-selection criterion "factual consistency with the source".
    It deliberately contains no reference to a hidden target value.
    """
    source = parse_source(source_text)
    ids = set(source)
    m_total = TOTAL_LINE.search(answer)
    m_rows = ROWS_LINE.search(answer)
    covered = {x.strip() for x in (m_rows.group(1).split(",") if m_rows else []) if x.strip()}
    covered &= ids

    delivered = 1.0 if answer.strip() else 0.0
    formatted = 1.0 if m_total and m_rows else 0.0
    coverage = len(covered) / len(ids) if ids else 0.0
    consistent = 0.0
    if m_total and covered:
        try:
            declared = float(m_total.group(1))
            recomputed = sum(source[i] for i in covered)
            consistent = 1.0 if abs(declared - recomputed) < 0.01 else 0.0
        except ValueError:
            consistent = 0.0
    complete = 0.0 if ("Traceback" in answer or "ERROR" in answer) else 1.0
    score = delivered + 4.0 * coverage + 2.0 * consistent + formatted + complete
    return {
        "delivered": delivered,
        "formatted": formatted,
        "coverage": round(coverage, 4),
        "consistent": consistent,
        "complete": complete,
        "score": round(score, 4),
    }


def _drop_zero_filter(src: str) -> str | None:
    """Lift the body of a `if value > 0:` branch out of the condition.

    Keeps every other line byte-for-byte, so the resulting edit is readable as
    a minimal diff rather than a full source rewrite.
    """
    lines = src.splitlines(keepends=True)
    out: list[str] = []
    i = 0
    changed = False
    while i < len(lines):
        m = ZERO_FILTER.match(lines[i].rstrip("\r\n"))
        if m and not changed:
            indent = len(m.group(1))
            i += 1
            while i < len(lines):
                nxt = lines[i]
                if not nxt.strip():
                    out.append(nxt)
                    i += 1
                    continue
                cur = len(nxt) - len(nxt.lstrip())
                if cur <= indent:
                    break
                out.append(nxt[4:] if nxt.startswith(" " * (indent + 4)) else nxt)
                i += 1
            changed = True
            continue
        out.append(lines[i])
        i += 1
    return "".join(out) if changed else None


def _disable_row_filter_in_tools(src: str) -> str | None:
    """Tool definitions are a first-class editable surface.

    A tool manifest can declare the policy the offline executor obeys. Flipping
    that policy changes agent behaviour exactly like rewriting a script does.
    Return complete, valid JSON so an improver never corrupts a structured file.
    """
    try:
        data = json.loads(src)
    except (ValueError, TypeError):
        return None
    policy = data.get("rsi_policy") if isinstance(data, dict) else None
    if not isinstance(policy, dict) or policy.get("skip_non_positive") is not True:
        return None
    policy["skip_non_positive"] = False
    return json.dumps(data, indent=2, sort_keys=True, ensure_ascii=False) + "\n"


@dataclass
class Proposal:
    scope: str
    edits: dict[str, str] = field(default_factory=dict)
    rationale: str = ""
    evidence: list[str] = field(default_factory=list)
    keep_base: bool = False

    def to_dict(self) -> dict:
        return {
            "scope": self.scope,
            "edits": self.edits,
            "rationale": self.rationale,
            "evidence": self.evidence,
            "keep_base": self.keep_base,
        }

    @classmethod
    def from_dict(cls, data: dict) -> "Proposal":
        return cls(
            scope=str(data.get("scope", "INV")),
            edits={str(k): str(v) for k, v in (data.get("edits") or {}).items()},
            rationale=str(data.get("rationale", "")),
            evidence=[str(x) for x in (data.get("evidence") or [])],
            keep_base=bool(data.get("keep_base", False)),
        )

    @staticmethod
    def keep(scope: str, why: str) -> "Proposal":
        return Proposal(scope=scope, keep_base=True, rationale=why)


class Backend(ABC):
    name = "abstract"

    @abstractmethod
    def propose(self, scope: str, context_dir: Path, base: HarnessTree) -> Proposal: ...

    @abstractmethod
    def select(self, selection_dir: Path, labels: list[str]) -> str: ...

    @abstractmethod
    def reflect(self, reflection_dir: Path, changed: list[str]) -> dict: ...

    def compose(self, candidates: list[dict], k: int) -> list[dict[str, str]]:
        """Merge INV and DOM edits into at most k compositions.

        Deterministic and identical for every backend: composition is a set
        operation on edits, not an act of judgement.
        """
        inv = [c for c in candidates if c["scope"] == "INV" and c["edits"]]
        dom = [c for c in candidates if c["scope"] == "DOM" and c["edits"]]
        if not inv:
            inv = [{"edits": {}}]
        if not dom:
            dom = [{"edits": {}}]
        merged: list[dict[str, str]] = []
        for i in range(max(k, 1)):
            a = inv[i % len(inv)]["edits"]
            b = dom[(i // max(len(inv), 1)) % len(dom)]["edits"]
            edits = dict(b)
            edits.update(a)
            merged.append(edits)
        return merged


class DeterministicBackend(Backend):
    """Evidence-driven rules. Free, offline, and fully reproducible."""

    name = "deterministic"

    def propose(self, scope: str, context_dir: Path, base: HarnessTree) -> Proposal:
        from .store import read_json

        focus = read_json(context_dir / "focus.json", {})
        variant = int(focus.get("variant", 0)) if isinstance(focus, dict) else 0
        source = _safe_read(context_dir / "source" / "ledger.csv")
        baseline_answers = sorted((context_dir / "base").glob("*/answer.txt"))
        if not source or not baseline_answers:
            return Proposal.keep(scope, "no baseline trajectory to reason about")

        ids = set(parse_source(source))
        all_answers = "\n".join(_safe_read(p) for p in baseline_answers)
        uncovered = sorted(ids - _covered_ids(all_answers))
        worst_coverage = min(
            artefact_quality(_safe_read(p), source)["coverage"] for p in baseline_answers
        )
        if worst_coverage >= 1.0:
            return Proposal.keep(
                scope,
                "every source row is already accounted for in the delivered artefact",
            )
        evidence = [p.relative_to(context_dir).as_posix() for p in baseline_answers[:2]]
        rationale = (
            f"Baseline deliverables omit source rows {uncovered}; "
            f"row coverage is only {worst_coverage:.0%}."
        )

        if scope == "DOM" or variant > 0:
            def first(prefix: str, fallback: str) -> str:
                hits = sorted(p for p in base.files
                              if p.startswith(prefix) and p.endswith(".md"))
                return hits[0] if hits else fallback

            # Spread the proposals across surfaces so the evidence shows which
            # part of the configuration can carry the lesson.
            if variant == 0:
                target = "domain/rules.md"
            elif variant == 1:
                target = ("tools/guide.md" if scope == "INV"
                          else first("skills/", "tools/guide.md"))
            else:
                target = (first("subagents/", "guardrails.md") if scope == "DOM"
                          else "guardrails.md")
            # Prose may only be appended to markdown; never corrupt a JSON file.
            if not target.endswith(".md"):
                target = "system.md"
            existing = base.files.get(target, f"# {Path(target).stem}\n")
            addition = (
                "\n- Every row in the source file must be represented in the deliverable; "
                "an unexplained omission is a defect.\n"
            )
            if "Every row" in existing:
                return Proposal.keep(scope, "guidance already recorded")
            return Proposal(
                scope=scope,
                edits={target: existing.rstrip() + "\n" + addition},
                rationale=rationale + " Record that contract in the configuration.",
                evidence=evidence,
            )

        if scope == "INV" and variant == 0:
            manifest = base.files.get("tools/definitions.json")
            if manifest:
                flipped = _disable_row_filter_in_tools(manifest)
                if flipped:
                    return Proposal(
                        scope=scope,
                        edits={"tools/definitions.json": flipped},
                        rationale=rationale + " The declared tool policy skips "
                        "those rows; disable the filter in the tool definition "
                        "so the contract covers every source row.",
                        evidence=evidence,
                    )

        for path, text in sorted(base.files.items()):
            if not path.endswith(".py"):
                continue
            transformed = _drop_zero_filter(text)
            if transformed and transformed != text:
                return Proposal(
                    scope=scope,
                    edits={path: transformed},
                    rationale=rationale + f" A conditional branch in {path} skips those "
                    "rows; lift its body so every row is processed.",
                    evidence=evidence,
                )
        return Proposal.keep(scope, "no behaviour-changing edit is supported by the evidence")

    def select(self, selection_dir: Path, labels: list[str]) -> str:
        source = _safe_read(selection_dir / "source" / "ledger.csv")
        best_label, best = "", (-1.0, "")
        for label in labels:
            answer = _safe_read(selection_dir / "blind" / f"{label}.txt")
            quality = artefact_quality(answer, source)
            ranked = (quality["score"], label)
            if ranked[0] > best[0] or (ranked[0] == best[0] and label < best[1]):
                best_label, best = label, ranked
        return best_label or labels[0]

    def reflect(self, reflection_dir: Path, changed: list[str]) -> dict:
        before = _safe_read(reflection_dir / "base_answer.txt")
        after = _safe_read(reflection_dir / "adopted_answer.txt")
        source = _safe_read(reflection_dir / "source" / "ledger.csv")
        q0 = artefact_quality(before, source)["coverage"]
        q1 = artefact_quality(after, source)["coverage"]
        verdicts = []
        for path in changed:
            # Scripts and tool manifests are the surfaces the offline executor
            # actually reads; prose-only edits cannot claim to have helped.
            causal = path.endswith(".py") or path == "tools/definitions.json"
            if q1 > q0:
                verdict = "helped" if causal else "no observed effect"
            elif q1 == q0:
                verdict = "confounded" if len(changed) > 1 else "no observed effect"
            else:
                verdict = "hurt"
            verdicts.append({"path": path, "verdict": verdict})
        entry = None
        if q1 > q0 and changed:
            entry = {
                "cue": "source rows are present in the input but absent from the deliverable",
                "lesson": "do not filter input rows by sign; process every row and list its id",
                "evidence": f"row coverage moved from {q0:.2f} to {q1:.2f} across the probed runs",
                "confidence": "high" if len(changed) == 1 else "medium",
            }
        return {"edit_verdicts": verdicts, "experience": entry}


def _covered_ids(answer: str) -> set[str]:
    out: set[str] = set()
    for m in ROWS_LINE.finditer(answer):
        out.update(x.strip() for x in m.group(1).split(",") if x.strip())
    return out


def _safe_read(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError):
        return ""


SYSTEM_INSTRUCTION = """You improve the files that configure an agent. You are given a
directory of evidence: the task, the public source file, recorded trajectories,
the current harness, and diffs of every candidate harness. Read them on disk.

You do not have any hidden target value, checking rules, or verdicts. Do not try
to infer how the work will be checked. Reason only from observed behaviour: what
the agent did, what it delivered, and how a candidate harness changed that.

Every edit must point to a concrete moment in a trajectory. If the evidence is
insufficient, return keep_base=true. Record workspace facts as concrete text and
record procedures in a transferable form.

Editable surfaces: system prompt, guardrails, memory, tool definitions (JSON),
tool guidance, skills (SKILL.md with front matter), subagents, plugins, scripts
and domain rules. Always return the COMPLETE new file text. For .json files that
means a valid JSON document, never a prose fragment or a diff. A skill's
front matter must keep its --- delimiters and name/description fields.

Reply with a single JSON object, no markdown fences:
{"keep_base": false, "edits": {"path/inside/harness.py": "full new file text"},
 "rationale": "why this follows from the evidence", "evidence": ["relative/path"]}
"""


class OpenAICompatBackend(Backend):
    """Use any OpenAI-compatible chat endpoint for proposals, selection, reflection."""

    name = "openai"

    def __init__(self, model: str, base_url: str | None = None, api_key: str | None = None,
                 timeout: int = 120):
        self.model = model
        self.base_url = (base_url or os.environ.get("RSI_BASE_URL")
                         or "https://api.openai.com/v1").rstrip("/")
        self.api_key = api_key or os.environ.get("RSI_API_KEY", "")
        self.timeout = timeout
        if not self.api_key:
            raise ValueError("RSI_API_KEY is required for the openai backend")

    def _chat(self, instruction: str, user: str) -> dict:
        payload = json.dumps({
            "model": self.model,
            "temperature": 0,
            "messages": [
                {"role": "system", "content": instruction},
                {"role": "user", "content": user},
            ],
        }).encode("utf-8")
        req = urllib.request.Request(
            self.base_url + "/chat/completions",
            data=payload,
            headers={
                "Content-Type": "application/json",
                "Authorization": f"Bearer {self.api_key}",
            },
            method="POST",
        )
        try:
            with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                body = json.loads(resp.read().decode("utf-8"))
        except (urllib.error.URLError, OSError, ValueError) as exc:
            raise RuntimeError(f"backend call failed: {exc}") from exc
        content = body["choices"][0]["message"]["content"]
        start, end = content.find("{"), content.rfind("}")
        if start < 0 or end < start:
            raise RuntimeError("backend did not return a JSON object")
        return json.loads(content[start:end + 1])

    def _render(self, context_dir: Path) -> str:
        """Flatten an evidence directory into a bounded prompt."""
        parts: list[str] = []
        for p in sorted(context_dir.rglob("*")):
            if not p.is_file() or p.stat().st_size > 40_000:
                continue
            rel = p.relative_to(context_dir).as_posix()
            parts.append(f"--- {rel} ---\n{_safe_read(p)[:12_000]}")
        return "\n\n".join(parts)[:90_000]

    def propose(self, scope: str, context_dir: Path, base: HarnessTree) -> Proposal:
        question = (
            f"Scope: {scope}.\n"
            "INV concerns how the agent works (tool errors, repeated actions, scripts, "
            "triggered workflows). DOM concerns what a practitioner knows (deliverable "
            "contracts, analysis steps, decision rules).\n"
            "Propose one minimal edit set. Allowed paths: " + ", ".join(base.spec.editable) + "\n\n"
            + self._render(context_dir)
        )
        data = self._chat(SYSTEM_INSTRUCTION, question)
        result = Proposal.from_dict(data)
        result.scope = scope
        valid = {}
        for key, value in result.edits.items():
            key = key.replace("\\", "/")
            if base.spec.allows(key) and key in base.files:
                valid[key] = value
        result.edits = valid
        if not valid:
            result.keep_base = True
        return result

    def select(self, selection_dir: Path, labels: list[str]) -> str:
        instruction = (
            "Pick the best deliverable. Criteria, in order: delivery completeness, "
            "factual consistency with the public source file, internal coherence, "
            "usability, completeness. You do not know which harness produced which "
            "file and there is no hidden target. Reply with JSON: {\"label\": \"A\"}"
        )
        data = self._chat(instruction, self._render(selection_dir))
        label = str(data.get("label", "")).strip().upper()
        return label if label in labels else labels[0]

    def reflect(self, reflection_dir: Path, changed: list[str]) -> dict:
        instruction = (
            "An adopted deliverable is not proof that its edits helped: the choice was "
            "blind. Assess each changed path as helped / hurt / no observed effect / "
            "confounded. Then optionally write one transferable lesson. "
            "A lesson needs cue, lesson and evidence; otherwise set experience to null. "
            "Reply with JSON: {\"edit_verdicts\": [{\"path\":..., \"verdict\":...}], "
            "\"experience\": null or {\"cue\":..., \"lesson\":..., "
            "\"evidence\":..., \"confidence\":...}}"
        )
        data = self._chat(instruction, self._render(reflection_dir))
        return {
            "edit_verdicts": data.get("edit_verdicts") or [],
            "experience": data.get("experience"),
        }


def build_backend(name: str, *, model: str = "", base_url: str | None = None,
                  api_key: str | None = None) -> Backend:
    if name in ("deterministic", "local", "rule"):
        return DeterministicBackend()
    if name in ("openai", "llm", "http"):
        return OpenAICompatBackend(model=model or os.environ.get("RSI_MODEL", "gpt-4o-mini"),
                                   base_url=base_url, api_key=api_key)
    raise ValueError(f"unknown backend: {name}")
