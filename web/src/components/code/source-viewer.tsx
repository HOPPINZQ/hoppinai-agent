"use client";

import { useMemo, useState, useEffect } from "react";
import { ChevronRight, ChevronDown, File, Folder, FolderOpen } from "lucide-react";

export interface FileNode {
  name: string;
  path: string;
  type: "file" | "directory";
  children?: FileNode[];
  source?: string;
}

interface SourceViewerProps {
  source: string;
  filename: string;
  tree?: FileNode[];
  githubUrl?: string;
  downloadUrl?: string;
}

function highlightLine(line: string): React.ReactNode[] {
  const trimmed = line.trimStart();
  if (trimmed.startsWith("#")) {
    return [
      <span key={0} className="text-zinc-400 italic">
        {line}
      </span>,
    ];
  }
  if (trimmed.startsWith("@")) {
    return [
      <span key={0} className="text-amber-400">
        {line}
      </span>,
    ];
  }
  if (trimmed.startsWith('"""') || trimmed.startsWith("'''")) {
    return [
      <span key={0} className="text-emerald-500">
        {line}
      </span>,
    ];
  }

  const keywordSet = new Set([
    "def", "class", "import", "from", "return", "if", "elif", "else",
    "while", "for", "in", "not", "and", "or", "is", "None", "True",
    "False", "try", "except", "raise", "with", "as", "yield", "break",
    "continue", "pass", "global", "lambda", "async", "await",
  ]);

  const parts = line.split(
    /(\b(?:def|class|import|from|return|if|elif|else|while|for|in|not|and|or|is|None|True|False|try|except|raise|with|as|yield|break|continue|pass|global|lambda|async|await|self)\b|"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'|f"(?:[^"\\]|\\.)*"|f'(?:[^'\\]|\\.)*'|#.*$|\b\d+(?:\.\d+)?\b)/
  );

  return parts.map((part, idx) => {
    if (!part) return null;
    if (keywordSet.has(part)) {
      return <span key={idx} className="text-blue-400 font-medium">{part}</span>;
    }
    if (part === "self") {
      return <span key={idx} className="text-purple-400">{part}</span>;
    }
    if (part.startsWith("#")) {
      return <span key={idx} className="text-zinc-400 italic">{part}</span>;
    }
    if (
      (part.startsWith('"') && part.endsWith('"')) ||
      (part.startsWith("'") && part.endsWith("'")) ||
      (part.startsWith('f"') && part.endsWith('"')) ||
      (part.startsWith("f'") && part.endsWith("'"))
    ) {
      return <span key={idx} className="text-emerald-500">{part}</span>;
    }
    if (/^\d+(?:\.\d+)?$/.test(part)) {
      return <span key={idx} className="text-orange-400">{part}</span>;
    }
    return <span key={idx}>{part}</span>;
  });
}

function TreeNode({ node, level, selectedFile, onFileSelect, isOpen, onToggle, openNodes }: {
  node: FileNode;
  level: number;
  selectedFile: string | null;
  onFileSelect: (path: string, source: string) => void;
  isOpen: boolean;
  onToggle: (path: string) => void;
  openNodes: Set<string>;
}) {
  const isFile = node.type === "file";
  const isSelected = selectedFile === node.path;

  return (
    <div>
      <div
        className={`flex items-center gap-1.5 px-2 py-1.5 cursor-pointer hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors ${
          isSelected ? "bg-blue-50 dark:bg-blue-900/20 text-blue-600 dark:text-blue-400" : ""
        }`}
        style={{ paddingLeft: `${level * 12 + 8}px` }}
        onClick={() => {
          if (isFile && node.source) {
            onFileSelect(node.path, node.source);
          } else {
            onToggle(node.path);
          }
        }}
      >
        {isFile ? (
          <File className="h-3.5 w-3.5 text-zinc-500" />
        ) : isOpen ? (
          <FolderOpen className="h-3.5 w-3.5 text-zinc-500" />
        ) : (
          <Folder className="h-3.5 w-3.5 text-zinc-500" />
        )}
        {!isFile && (
          isOpen ? (
            <ChevronDown className="h-3 w-3 text-zinc-400" />
          ) : (
            <ChevronRight className="h-3 w-3 text-zinc-400" />
          )
        )}
        <span className="text-xs font-mono truncate">{node.name}</span>
      </div>
      {!isFile && isOpen && node.children && (
        <div>
          {node.children.map((child, idx) => (
            <TreeNode
              key={`${child.path}-${idx}`}
              node={child}
              level={level + 1}
              selectedFile={selectedFile}
              onFileSelect={onFileSelect}
              isOpen={openNodes.has(child.path)}
              onToggle={onToggle}
              openNodes={openNodes}
            />
          ))}
        </div>
      )}
    </div>
  );
}

function CodeDisplay({ source, filename, githubUrl, downloadUrl }: { 
  source: string; 
  filename: string;
  githubUrl?: string;
  downloadUrl?: string;
}) {
  const lines = useMemo(() => source.split("\n"), [source]);

  return (
    <div className="rounded-lg border border-zinc-200 dark:border-zinc-700">
      <div className="flex items-center justify-between border-b border-zinc-200 px-4 py-2 dark:border-zinc-700">
        <div className="flex items-center gap-2">
          <div className="flex gap-1.5">
            <span className="h-3 w-3 rounded-full bg-red-400" />
            <span className="h-3 w-3 rounded-full bg-yellow-400" />
            <span className="h-3 w-3 rounded-full bg-green-400" />
          </div>
          <span className="font-mono text-xs text-zinc-400">{filename}</span>
        </div>
        <div className="flex items-center gap-2">
          {githubUrl && (
            <a
              href={githubUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-1.5 px-2.5 py-1 text-xs font-medium text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100 transition-colors bg-zinc-100 dark:bg-zinc-800 rounded-md"
            >
              <svg className="h-3.5 w-3.5" fill="currentColor" viewBox="0 0 24 24">
                <path fillRule="evenodd" d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.531 1.032 1.531 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z" clipRule="evenodd" />
              </svg>
              GitHub
            </a>
          )}
          {downloadUrl && (
            <a
              href={downloadUrl}
              download
              className="flex items-center gap-1.5 px-2.5 py-1 text-xs font-medium text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100 transition-colors bg-zinc-100 dark:bg-zinc-800 rounded-md"
            >
              <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
              </svg>
              Download
            </a>
          )}
        </div>
      </div>
      <div className="overflow-x-auto bg-zinc-950">
        <pre className="p-2 text-[10px] leading-4 sm:p-4 sm:text-xs sm:leading-5">
          <code>
            {lines.map((line, i) => (
              <div key={i} className="flex">
                <span className="mr-2 inline-block w-6 shrink-0 select-none text-right text-zinc-600 sm:mr-4 sm:w-8">
                  {i + 1}
                </span>
                <span className="text-zinc-200">
                  {highlightLine(line)}
                </span>
              </div>
            ))}
          </code>
        </pre>
      </div>
    </div>
  );
}

function findFileByPath(tree: FileNode[], path: string): FileNode | null {
  for (const node of tree) {
    if (node.path === path) {
      return node;
    }
    if (node.children) {
      const found = findFileByPath(node.children, path);
      if (found) return found;
    }
  }
  return null;
}

export function SourceViewer({ source, filename, tree, githubUrl, downloadUrl }: SourceViewerProps) {
  const [selectedFile, setSelectedFile] = useState<string | null>(null);
  const [currentSource, setCurrentSource] = useState(source);
  const [currentFilename, setCurrentFilename] = useState(filename);
  const [openNodes, setOpenNodes] = useState<Set<string>>(new Set());

  useEffect(() => {
    if (!tree || tree.length === 0) {
      return;
    }

    const readmePath = "README.md";
    const readmeFile = findFileByPath(tree, readmePath);
    
    if (readmeFile && readmeFile.source) {
      setSelectedFile(readmePath);
      setCurrentSource(readmeFile.source);
      setCurrentFilename(readmeFile.name);
    }
  }, [tree]);

  const handleFileSelect = (path: string, newSource: string) => {
    setSelectedFile(path);
    setCurrentSource(newSource);
    setCurrentFilename(path.split("/").pop() || filename);
  };

  const toggleNode = (path: string) => {
    setOpenNodes((prev) => {
      const next = new Set(prev);
      if (next.has(path)) {
        next.delete(path);
      } else {
        next.add(path);
      }
      return next;
    });
  };

  if (!tree || tree.length === 0) {
    return <CodeDisplay source={source} filename={filename} githubUrl={githubUrl} downloadUrl={downloadUrl} />;
  }

  return (
    <div className="flex border border-zinc-200 dark:border-zinc-700 rounded-lg overflow-hidden">
      <div className="border-r border-zinc-200 dark:border-zinc-700 bg-zinc-50 dark:bg-zinc-900/50 min-w-[200px] max-w-[280px]">
        <div className="px-3 py-2 text-xs font-semibold text-zinc-500 uppercase tracking-wider border-b border-zinc-200 dark:border-zinc-700">
          Files
        </div>
        <div className="py-2">
          {tree.map((node, idx) => (
            <TreeNode
              key={`${node.path}-${idx}`}
              node={node}
              level={0}
              selectedFile={selectedFile}
              onFileSelect={handleFileSelect}
              isOpen={openNodes.has(node.path)}
              onToggle={toggleNode}
              openNodes={openNodes}
            />
          ))}
        </div>
      </div>
      <div className="flex-1 overflow-auto">
        <CodeDisplay source={currentSource} filename={currentFilename} githubUrl={githubUrl} downloadUrl={downloadUrl} />
      </div>
    </div>
  );
}
