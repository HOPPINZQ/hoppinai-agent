#!/usr/bin/env node
/**
 * Sync markdown -> docs.json content fields.
 * Manual one-shot: run with `node scripts/sync-docs.mjs`.
 * Reads src/data/generated/s{N}.md (no leading zero), updates the matching
 * entry's `content` field in src/data/generated/docs.json, and adds new
 * entries for any version present on disk but missing in docs.json.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const GEN_DIR = path.join(__dirname, "..", "src", "data", "generated");
const DOCS_PATH = path.join(GEN_DIR, "docs.json");

const REPO_URL = "https://github.com/HOPPINZQ/hoppinai-agent";

const TITLE_BY_VERSION = {
  s01: "s01: The Agent Loop (智能体循环)",
  s02: "s02: Tool Use (工具使用)",
  s03: "s03: Todo Planning (任务规划)",
  s04: "s04: Subagent (子智能体)",
  s05: "s05: Skill Loading (技能加载)",
  s06: "s06: Context Compaction (上下文压缩)",
  s07: "s07: Task System (任务系统)",
  s08: "s08: Background Tasks (后台任务)",
  s09: "s09: Permission System (权限系统)",
  s10: "s10: Hooks (钩子机制)",
  s11: "s11: Persistent Memory (持久化记忆)",
  s12: "s12: Error Recovery (错误恢复)",
  s13: "s13: MCP Protocol (MCP 协议)",
  s14: "s14: ReAct Framework (ReAct 行为模式)",
  s15: "s15: Dynamic System Prompt (动态系统提示)",
  s16: "s16: Cron Scheduler (定时调度)",
  s17: "s17: Agent Teams (Agent 团队)",
  s18: "s18: Team Protocols (团队协议)",
  s19: "s19: Autonomous Agents (自主 Agent)",
  s20: "s20: Worktree Isolation (Worktree 隔离)",
};

// Read all sN.md files (sorted by numeric suffix)
const mdFiles = fs.readdirSync(GEN_DIR)
  .filter((f) => /^s\d+\.md$/.test(f))
  .sort((a, b) => {
    const na = parseInt(a.match(/\d+/)[0], 10);
    const nb = parseInt(b.match(/\d+/)[0], 10);
    return na - nb;
  });

const docs = JSON.parse(fs.readFileSync(DOCS_PATH, "utf8"));
const byVersion = new Map(docs.map((d) => [d.version, d]));

// Drop bogus entries (version not matching /^s\d{2}$/) from previous buggy runs
for (let i = docs.length - 1; i >= 0; i--) {
  if (!/^s\d{2}$/.test(docs[i].version)) {
    console.log(`[sync-docs] removing bogus entry '${docs[i].version}'`);
    docs.splice(i, 1);
  }
}
byVersion.clear();
for (const d of docs) byVersion.set(d.version, d);

for (const file of mdFiles) {
  const num = file.match(/\d+/)[0];
  // Zero-pad to 2 digits: "1" -> "s01", "10" -> "s10"
  const padded = "s" + num.padStart(2, "0");
  const md = fs.readFileSync(path.join(GEN_DIR, file), "utf8");
  const title = TITLE_BY_VERSION[padded] || `${padded}`;
  const entry = byVersion.get(padded);
  if (entry) {
    entry.title = title;
    entry.locale = "zh";
    entry.content = md;
    entry.githubUrl = REPO_URL;
    entry.downloadUrl = REPO_URL;
  } else {
    const newEntry = {
      version: padded,
      locale: "zh",
      title,
      content: md,
      githubUrl: REPO_URL,
      downloadUrl: REPO_URL,
    };
    docs.push(newEntry);
    byVersion.set(padded, newEntry);
    console.log(`[sync-docs] added new entry ${padded}`);
  }
}

// Sort by version
docs.sort((a, b) => {
  const na = parseInt(a.version.slice(1), 10);
  const nb = parseInt(b.version.slice(1), 10);
  return na - nb;
});

fs.writeFileSync(DOCS_PATH, JSON.stringify(docs, null, 2) + "\n", "utf8");
console.log(`[sync-docs] wrote ${docs.length} entries to docs.json`);
