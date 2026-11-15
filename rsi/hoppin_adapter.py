"""Export an adopted harness as a STANDALONE bundle.

Nothing in this module writes into the Java source tree. The output lives under
``rsi/out/harness/`` and is plain data: markdown, JSON tool schemas, SKILL.md
files and an MCP config. The Java agent can consume it through extension points
that already exist in this repository, with no source edits:

  MEMORY.md   PromptAssembler.renderMemory() reads <workspace>/MEMORY.md
  skills/     SkillLoader walks the classpath resource named "skills"
  mcp.json    McpConfigLoader reads /mcp.json from the classpath
  tools/      OpenAI-compatible function schemas for any config-driven registry

An explicit ``apply --to <dir>`` is the only operation that copies files
outside rsi/, and it never targets src/main/java.
"""

from __future__ import annotations

import hashlib
import json
import re
import shutil
import tempfile
from pathlib import Path

from rsi_harness.store import atomic_json, atomic_text, read_json
from rsi_harness.tree import EXTRA_SURFACES, SURFACES, HarnessTree

RSI_ROOT = Path(__file__).resolve().parent
OUT_ROOT = RSI_ROOT / "out"
BUNDLE = OUT_ROOT / "harness"

# Same regex/front-matter contract as hoppinzq-module-agent-05-skill
# SkillLoader.parseSkillContent().
SKILL_FRONTMATTER = re.compile(r"^---\r?\n(.*?)\r?\n---\r?\n(.*)", re.S)

CONSUMPTION: dict[str, dict[str, str]] = {
    "system_prompt": {
        "files": "system.md",
        "hook": "generated MEMORY.md -> PromptAssembler.renderMemory()",
        "code_change": "none (data file; 1500-char cap applies)",
    },
    "guardrails": {
        "files": "guardrails.md",
        "hook": "generated MEMORY.md -> PromptAssembler.renderMemory()",
        "code_change": "none (data file)",
    },
    "memory": {
        "files": "memory.md",
        "hook": "generated MEMORY.md -> PromptAssembler.renderMemory()",
        "code_change": "none",
    },
    "tool_definitions": {
        "files": "tools/definitions.json, tools/guide.md",
        "hook": "OpenAI-compatible function schemas; feed your own tool registry / MCP server",
        "code_change": "none to export; ToolDefinition in this repo is compiled Java, "
                       "so swapping the live tool list needs a config-driven registry",
    },
    "skills": {
        "files": "skills/<name>/SKILL.md",
        "hook": "SkillLoader walks classpath resource 'skills' -> add bundle dir to classpath",
        "code_change": "none (launch-time classpath only)",
    },
    "subagents": {
        "files": "subagents/*.md",
        "hook": "charter data for your own orchestrator (module 04 SubAgent is hardcoded)",
        "code_change": "none to export",
    },
    "plugins": {
        "files": "plugins/hooks.json -> generated mcp.json",
        "hook": "McpConfigLoader reads /mcp.json from the classpath",
        "code_change": "none (launch-time classpath only)",
    },
    "scripts": {
        "files": "scripts/*",
        "hook": "offline RSI executor only; never shipped to the Java agent",
        "code_change": "none",
    },
    "domain_rules": {
        "files": "domain/*.md",
        "hook": "generated MEMORY.md -> PromptAssembler.renderMemory()",
        "code_change": "none (data file)",
    },
}

MEMORY_SECTIONS = (
    ("system.md", "角色"),
    ("guardrails.md", "约束"),
    ("memory.md", "已验证经验"),
    ("tools/guide.md", "工具使用约定"),
    ("domain/rules.md", "领域规则"),
)


def _assert_under_out(dest: Path) -> None:
    resolved = dest.resolve()
    if resolved != OUT_ROOT.resolve() and OUT_ROOT.resolve() not in resolved.parents:
        raise ValueError(f"refusing to overwrite a path outside rsi/out: {resolved}")


def adopted_trees(namespace_root: Path | str) -> list[tuple[str, Path]]:
    root = Path(namespace_root)
    found = []
    for p in sorted((root / "tasks").glob("*/adopted/harness")):
        if p.is_dir():
            found.append((p.parent.parent.name, p))
    return found


def latest_adopted(namespace_root: Path | str) -> tuple[str, Path] | None:
    trees = adopted_trees(namespace_root)
    return trees[-1] if trees else None


def _memory_text(tree: HarnessTree) -> str:
    blocks: list[str] = []
    for rel, heading in MEMORY_SECTIONS:
        text = tree.files.get(rel, "").strip()
        if not text:
            continue
        # The file may already carry its own markdown heading.
        blocks.append(text if text.startswith("#") else f"## {heading}\n\n{text}")
    tools = json.loads(tree.files.get("tools/definitions.json", "{}") or "{}")
    names = [t.get("function", {}).get("name", "") for t in tools.get("tools", [])]
    names = [n for n in names if n]
    if names:
        blocks.append("## 工具清单\n\n" + ", ".join(names)
                      + "\n\n完整参数契约见 tools/definitions.json。")
    skills = []
    for rel, text in sorted(tree.files.items()):
        if not (rel.startswith("skills/") and rel.endswith("SKILL.md")):
            continue
        m = SKILL_FRONTMATTER.match(text)
        meta = m.group(1) if m else ""
        desc = re.search(r"^description:\s*(.+)$", meta, re.M)
        name = re.search(r"^name:\s*(.+)$", meta, re.M)
        skills.append(f"- {name.group(1).strip() if name else Path(rel).parent.name}: "
                      f"{desc.group(1).strip() if desc else rel}")
    if skills:
        blocks.append("## 可用技能\n\n" + "\n".join(skills))
    header = ("# RSI harness bundle\n\n"
              "本文件由 rsi/rsi.py bundle 生成，供 PromptAssembler 的 memory section 读取。\n"
              "Java 源码无需改动；该文件会被截断到前 1500 字符，重要规则请放在前几节。\n")
    return header + "\n\n".join(blocks) + "\n"


def bundle(adopted_dir: Path | str, *, dest: Path | str = BUNDLE) -> dict:
    """Materialise the full adopted tree plus drop-in data files under rsi/out."""
    src = Path(adopted_dir)
    if not src.is_dir():
        raise FileNotFoundError(f"no adopted harness at {src}")
    dst = Path(dest).resolve()
    _assert_under_out(dst)
    if dst.is_dir():
        shutil.rmtree(dst)
    dst.mkdir(parents=True, exist_ok=True)

    tree = HarnessTree.read(src)
    for rel, text in tree.files.items():
        target = dst / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        atomic_text(target, text)

    atomic_text(dst / "MEMORY.md", _memory_text(tree))
    hooks = read_json(dst / "plugins" / "hooks.json", {}) or {}
    servers = hooks.get("mcpServers", {}) if isinstance(hooks, dict) else {}
    atomic_json(dst / "mcp.json", {"mcpServers": servers})

    surfaces = {}
    for name, prefix in {**SURFACES, **EXTRA_SURFACES}.items():
        matched = sorted(rel for rel in tree.files
                         if rel == prefix or rel.startswith(prefix))
        surfaces[name] = {
            "editable": True,
            "files": matched,
            "consumption": CONSUMPTION.get(name, {}),
        }
    payload = json.dumps(tree.files, sort_keys=True, ensure_ascii=False)
    info = {
        "source": src.as_posix(),
        "destination": dst.as_posix(),
        "digest": hashlib.sha256(payload.encode("utf-8")).hexdigest()[:16],
        "surfaces": surfaces,
        "drop_in_files": ["MEMORY.md", "mcp.json", "skills/", "tools/definitions.json"],
        "writes_outside_rsi": False,
    }
    atomic_json(dst / "bundle.json", info)
    return info


def apply_to(bundle_dir: Path | str, target: Path | str) -> dict:
    """Opt-in copy of the data-only hooks into a user-specified runtime directory."""
    src = Path(bundle_dir).resolve()
    dst = Path(target).resolve()
    if "src" in dst.parts and "java" in dst.parts:
        raise ValueError("refusing to write into a Java source directory")
    if not (src / "bundle.json").is_file():
        raise FileNotFoundError("run `rsi.py bundle` first")
    dst.mkdir(parents=True, exist_ok=True)
    copied: list[str] = []
    for rel in ("MEMORY.md", "mcp.json"):
        if (src / rel).is_file():
            shutil.copy2(src / rel, dst / rel)
            copied.append(rel)
    skills = src / "skills"
    if skills.is_dir():
        target_skills = dst / "skills"
        if target_skills.is_dir():
            shutil.rmtree(target_skills)
        shutil.copytree(skills, target_skills)
        copied.append("skills/")
    return {"target": dst.as_posix(), "files": copied}


def status(*, bundle_dir: Path | str = BUNDLE) -> dict:
    dst = Path(bundle_dir)
    info = read_json(dst / "bundle.json")
    present = sorted(p.relative_to(dst).as_posix() for p in dst.rglob("*")
                     if p.is_file()) if dst.is_dir() else []
    return {"bundle_dir": dst.as_posix(), "files": present, "manifest": info}


# --------------------------------------------------------------------------
# Hoppin-specific contract checks, run by ``rsi.py selftest``.
# --------------------------------------------------------------------------

def _check_bundle_contract() -> str:
    from bootstrap_hoppin import ensure_assets

    with tempfile.TemporaryDirectory() as td:
        root = Path(td)
        base, _tasks = ensure_assets(root / "assets")
        adopted = base.write(root / "adopted")
        dst = root / "out" / "harness"
        # bundle() normally refuses paths outside rsi/out; for the test we
        # reproduce the same generation under a temp root by temporarily
        # pointing OUT_ROOT at it.
        global OUT_ROOT
        original = OUT_ROOT
        try:
            OUT_ROOT = root / "out"
            info = bundle(adopted, dest=dst)
        finally:
            OUT_ROOT = original

        assert set(info["surfaces"]) >= set(SURFACES) | set(EXTRA_SURFACES)
        assert info["writes_outside_rsi"] is False
        assert (dst / "MEMORY.md").is_file()
        assert "rsi" in (dst / "MEMORY.md").read_text(encoding="utf-8").lower()

        skill = (dst / "skills" / "change-report" / "SKILL.md").read_text(encoding="utf-8")
        m = SKILL_FRONTMATTER.match(skill)
        assert m and "name:" in m.group(1) and "description:" in m.group(1), \
            "SKILL.md front matter is not compatible with SkillLoader"

        tools = json.loads((dst / "tools" / "definitions.json").read_text(encoding="utf-8"))
        assert tools["tools"] and all(
            t.get("function", {}).get("name") and
            t.get("function", {}).get("description") and
            "parameters" in t.get("function", {})
            for t in tools["tools"]
        ), "tool definitions are not OpenAI-compatible function schemas"
        assert "mcpServers" in json.loads((dst / "mcp.json").read_text(encoding="utf-8"))
        assert (dst / "plugins" / "hooks.json").is_file()
        assert (dst / "subagents" / "reviewer.md").is_file()
        return (f"{len(info['surfaces'])} surfaces, "
                f"{len(tools['tools'])} tool schemas, SKILL.md/mcp.json/MEMORY.md valid")


def _check_tool_and_skill_are_causal() -> str:
    """Editing a tool definition or a skill must change the executor's output."""
    from bootstrap_hoppin import ensure_assets
    from rsi_harness.backends import artefact_quality, _disable_row_filter_in_tools
    from rsi_harness.executor import run_harness

    with tempfile.TemporaryDirectory() as td:
        root = Path(td)
        base, tasks = ensure_assets(root / "assets")
        task = tasks[0]
        source = (task.dir / "input" / "ledger.csv").read_text(encoding="utf-8")

        plain = run_harness(base, task.dir, root / "plain", timeout=20)
        assert plain.ok, plain.stderr or plain.error
        q0 = artefact_quality(plain.answer, source)["coverage"]

        flipped = _disable_row_filter_in_tools(base.files["tools/definitions.json"])
        assert flipped, "tool policy flip produced no edit"
        fixed = base.with_edits({"tools/definitions.json": flipped})
        better = run_harness(fixed, task.dir, root / "fixed", timeout=20)
        q1 = artefact_quality(better.answer, source)["coverage"]
        assert q0 < 1.0 <= q1, (q0, q1)

        skill = base.files["skills/change-report/SKILL.md"].replace(
            "output_fields: total,rows", "output_fields: rows")
        changed_skill = base.with_edits({"skills/change-report/SKILL.md": skill})
        reshaped = run_harness(changed_skill, task.dir, root / "reshaped", timeout=20)
        assert "total=" not in reshaped.answer, reshaped.answer
        return f"tool policy {q0:.2f} -> {q1:.2f} row coverage; skill edits reshape the deliverable"


def surface_checks() -> list[tuple[str, bool, str]]:
    out = []
    for name, fn in (
        ("standalone bundle matches agent contracts", _check_bundle_contract),
        ("tool definitions and skills are causal surfaces", _check_tool_and_skill_are_causal),
    ):
        try:
            out.append((name, True, fn()))
        except Exception as exc:
            out.append((name, False, f"{type(exc).__name__}: {exc}"))
    return out
