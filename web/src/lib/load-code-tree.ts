import * as fs from "fs";
import * as path from "path";
import { FileNode } from "@/components/code/source-viewer";

const CODE_BASE_DIR = path.resolve(
  process.cwd(),
  "src",
  "app",
  "[locale]",
  "(learn)",
  "code"
);

export function loadCodeTree(version: string): FileNode[] {
  const versionDir = path.join(CODE_BASE_DIR, version);

  if (!fs.existsSync(versionDir)) {
    return [];
  }

  return buildTree(versionDir, "");
}

function buildTree(dirPath: string, relativePath: string): FileNode[] {
  const entries = fs.readdirSync(dirPath, { withFileTypes: true });
  const nodes: FileNode[] = [];

  for (const entry of entries) {
    const fullPath = path.join(dirPath, entry.name);
    const relPath = path.join(relativePath, entry.name);

    if (entry.isDirectory()) {
      const children = buildTree(fullPath, relPath);
      if (children.length > 0) {
        nodes.push({
          name: entry.name,
          path: relPath,
          type: "directory",
          children,
        });
      }
    } else if (entry.isFile()) {
      const isJavaFile = entry.name.endsWith(".java");
      const isMarkdownFile = entry.name.endsWith(".md");

      if (isJavaFile || isMarkdownFile) {
        const content = fs.readFileSync(fullPath, "utf-8");
        nodes.push({
          name: entry.name,
          path: relPath,
          type: "file",
          source: content,
        });
      }
    }
  }

  return nodes.sort((a, b) => {
    if (a.type !== b.type) {
      return a.type === "directory" ? -1 : 1;
    }
    return a.name.localeCompare(b.name);
  });
}
