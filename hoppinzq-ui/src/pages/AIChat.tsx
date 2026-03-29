import { useState, useRef, useEffect, useCallback } from "react";
import { motion, AnimatePresence } from "motion/react";
import {
  Send,
  Bot,
  User,
  Sparkles,
  Trash2,
  Plus,
  Crosshair,
  Shield,
  Zap,
  DollarSign,
  TrendingUp,
  Clock,
  CheckCircle2,
  AlertCircle,
  ArrowRight,
  Info,
  StopCircle,
  ChevronDown,
  ChevronUp,
  Loader2,
  MessageSquare,
  X,
} from "lucide-react";
import { cn } from "@/lib/utils";

// --- Data types matching index.html's SSE protocol ---

interface ToolCallData {
  id: string;
  tool_name: string;
  tool_input?: any;
  tool_result?: any;
  status: "running" | "success" | "error";
}

type BlockType = "text" | "tool";

interface TextBlock {
  type: "text";
  key: string;
  content: string;
}

interface ToolBlock {
  type: "tool";
  key: string;
  data: ToolCallData;
}

type MessageBlock = TextBlock | ToolBlock;

interface Message {
  id: string;
  role: "user" | "assistant";
  content: string;
  blocks?: MessageBlock[];
  timestamp: Date;
  isStreaming?: boolean;
  token?: number;
}

interface Session {
  id: string;
  title: string;
  createdAt: Date;
}

const API_BASE = "http://127.0.0.1:8099";

function generateSessionId(): string {
  return Date.now().toString(36) + Math.random().toString(36).substring(2, 8);
}

function createWelcomeMessage(): Message {
  return {
    id: Date.now().toString(),
    role: "assistant",
    content: "",
    blocks: [
      {
        type: "text",
        key: `welcome-${Date.now()}`,
        content: "你好！我是 HoppinZQ AI 助手，有什么可以帮你的？",
      },
    ],
    timestamp: new Date(),
  };
}

// --- formatMessageContent: mirrors index.html logic ---
// Strips everything after \nAction: (or Action: at start),
// wraps Thought into a collapsible block, converts \n to <br>

interface Segment {
  type: "text" | "thought";
  content: string;
}

function formatMessageContent(rawText: string): Segment[] {
  if (!rawText) return [];

  let resultText = rawText;

  let actionIndex = resultText.indexOf("\nAction:");
  if (actionIndex === -1 && resultText.startsWith("Action:")) {
    actionIndex = 0;
  }
  if (actionIndex !== -1) {
    resultText = resultText.substring(0, actionIndex);
  }

  let thoughtIndex = resultText.indexOf("\nThought:");
  if (thoughtIndex === -1 && resultText.startsWith("Thought:")) {
    thoughtIndex = 0;
  }

  if (thoughtIndex !== -1) {
    const beforeThought = resultText.substring(0, thoughtIndex).trim();
    const offset = resultText.charAt(thoughtIndex) === "\n" ? 9 : 8;
    const thoughtContent = resultText.substring(thoughtIndex + offset).trim();

    const segments: Segment[] = [];
    if (beforeThought) {
      segments.push({ type: "text", content: beforeThought });
    }
    if (thoughtContent) {
      segments.push({ type: "thought", content: thoughtContent });
    }
    return segments;
  }

  return [{ type: "text", content: resultText }];
}

// --- ThoughtBlock: collapsible thought container (mirrors index.html) ---
function ThoughtBlock({ content, isStreaming }: { content: string; isStreaming?: boolean }) {
  const [open, setOpen] = useState(true);
  const prevIsStreamingRef = useRef(isStreaming ?? false);

  useEffect(() => {
    const prev = prevIsStreamingRef.current;
    prevIsStreamingRef.current = isStreaming ?? false;
    if (prev && !(isStreaming ?? false)) {
      setOpen(false);
    }
  }, [isStreaming]);

  return (
    <div className="border border-[var(--border)]/60 rounded-md overflow-hidden bg-[var(--surface)]/30">
      <div
        className="px-2.5 py-1.5 flex items-center gap-2 cursor-pointer hover:bg-[var(--surface-hover)]/30 transition-colors select-none"
        onClick={() => setOpen(!open)}
      >
        <svg className="w-3.5 h-3.5 text-white" viewBox="0 0 24 24">
          <path fill="currentColor" d="M9 21c0 .55.45 1 1 1h4c.55 0 1-.45 1-1v-1H9v1zm3-19C8.14 2 5 5.14 5 9c0 2.38 1.19 4.47 3 5.74V17c0 .55.45 1 1 1h6c.55 0 1-.45 1-1v-2.26c1.81-1.27 3-3.36 3-5.74 0-3.86-3.14-7-7-7zm2.85 11.1l-.85.6V16h-4v-1.3l-.85-.6C7.8 13.16 7 11.63 7 9c0-2.76 2.24-5 5-5s5 2.24 5 5c0 2.63-.8 4.16-2.15 5.1z" />
        </svg>
        <span className="text-[11px] font-bold uppercase tracking-wider text-[var(--text-muted)]">AI思考中🤔</span>
        {open ? <ChevronUp className="w-3 h-3 text-[var(--text-muted)] ml-auto" /> : <ChevronDown className="w-3 h-3 text-[var(--text-muted)] ml-auto" />}
      </div>
      <AnimatePresence>
        {open && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: "auto", opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            className="overflow-hidden"
          >
            <div className="px-2.5 py-2 text-[11px] text-[var(--text-secondary)] leading-relaxed border-t border-[var(--border)]/40 whitespace-pre-wrap">
              {content}
              {isStreaming && <span className="inline-block w-[2px] h-[14px] bg-[var(--accent)] ml-0.5 animate-pulse align-middle" />}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

// --- ToolCallCard: mirrors index.html's tool-message ---
function ToolCallCard({ tool }: { tool: ToolCallData }) {
  const isRunning = tool.status === "running";
  const [open, setOpen] = useState(isRunning);
  const prevStatusRef = useRef(tool.status);
  useEffect(() => {
    if (prevStatusRef.current === "running" && tool.status !== "running") {
      setOpen(false);
    }
    prevStatusRef.current = tool.status;
  }, [tool.status]);

  return (
    <div className="border border-[var(--border)]/60 rounded-md overflow-hidden bg-[var(--surface)]/30">
      <div
        className="px-2.5 py-1.5 flex items-center justify-between cursor-pointer hover:bg-[var(--surface-hover)]/30 transition-colors"
        onClick={() => setOpen(!open)}
      >
        <div className="flex items-center gap-2">
          {isRunning ? (
            <svg className="w-3.5 h-3.5 animate-spin text-[var(--accent)]" viewBox="0 0 24 24" fill="none">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
            </svg>
          ) : (
            <span className="text-sm">{tool.status === "success" ? "✅" : "❌"}</span>
          )}
          <span className="text-[11px] font-mono text-[var(--text-secondary)]">
            Calling Tool: {tool.tool_name}
          </span>
        </div>
        <span className="text-[10px] text-[var(--text-muted)]">
          {open ? "▲" : "▼"}
        </span>
      </div>

      <AnimatePresence>
        {open && (
          <motion.div
            initial={{ height: 0 }}
            animate={{ height: "auto" }}
            exit={{ height: 0 }}
            className="border-t border-[var(--border)]/40 overflow-hidden"
          >
            {tool.tool_input !== undefined && (
              <div className="px-2.5 py-2 border-b border-[var(--border)]/40">
                <div className="text-[9px] text-[var(--text-muted)] uppercase font-bold mb-1">Input Parameters</div>
                <pre className="text-[11px] text-[var(--text-primary)] bg-[var(--bg)] p-2 rounded font-mono whitespace-pre-wrap break-words max-h-[200px] overflow-y-auto">
                  {typeof tool.tool_input === "object" ? JSON.stringify(tool.tool_input, null, 2) : String(tool.tool_input)}
                </pre>
              </div>
            )}
            {tool.tool_result !== undefined && (
              <div className="px-2.5 py-2">
                <div className="text-[9px] text-[var(--text-muted)] uppercase font-bold mb-1">Result</div>
                <pre className="text-[11px] text-[var(--text-primary)] bg-[var(--bg)] p-2 rounded font-mono whitespace-pre-wrap break-words max-h-[200px] overflow-y-auto">
                  {typeof tool.tool_result === "object" ? JSON.stringify(tool.tool_result, null, 2) : String(tool.tool_result)}
                </pre>
              </div>
            )}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

// --- Render a single text block using formatMessageContent logic ---
function TextBlockContent({ content, isStreaming }: { content: string; isStreaming?: boolean }) {
  const segments = formatMessageContent(content);

  if (segments.length === 0) {
    return isStreaming ? (
      <span className="flex gap-1.5">
        <span className="w-1.5 h-1.5 bg-[var(--text-muted)] rounded-full animate-bounce" />
        <span className="w-1.5 h-1.5 bg-[var(--text-muted)] rounded-full animate-bounce [animation-delay:0.15s]" />
        <span className="w-1.5 h-1.5 bg-[var(--text-muted)] rounded-full animate-bounce [animation-delay:0.3s]" />
      </span>
    ) : null;
  }

  return (
    <div>
      {segments.map((seg, i) => {
        const isLast = i === segments.length - 1;
        if (seg.type === "thought") {
          return <ThoughtBlock key={i} content={seg.content} isStreaming={isLast && isStreaming} />;
        }
        return (
          <div key={i}>
            {seg.content.split("\n").map((line, j, arr) => (
              <span key={j}>
                {line}
                {j < arr.length - 1 && <br />}
              </span>
            ))}
            {isLast && isStreaming && <span className="inline-block w-[2px] h-[14px] bg-[var(--accent)] ml-0.5 animate-pulse align-middle" />}
          </div>
        );
      })}
    </div>
  );
}

// --- Assistant message: render blocks list ---
function AssistantContent({ msg }: { msg: Message }) {
  const blocks = msg.blocks || [];
  const isStreaming = msg.isStreaming;

  if (blocks.length === 0 && !msg.content) {
    return isStreaming ? (
      <span className="flex gap-1.5">
        <span className="w-1.5 h-1.5 bg-[var(--text-muted)] rounded-full animate-bounce" />
        <span className="w-1.5 h-1.5 bg-[var(--text-muted)] rounded-full animate-bounce [animation-delay:0.15s]" />
        <span className="w-1.5 h-1.5 bg-[var(--text-muted)] rounded-full animate-bounce [animation-delay:0.3s]" />
      </span>
    ) : null;
  }

  // Legacy: if no blocks but has content, render as single text block
  if (blocks.length === 0 && msg.content) {
    return <TextBlockContent content={msg.content} isStreaming={isStreaming} />;
  }

  return (
    <div className="space-y-2.5">
      {blocks.map((block, i) => {
        const isLast = i === blocks.length - 1;
        if (block.type === "tool") {
          return <ToolCallCard key={block.key} tool={block.data} />;
        }
        return <TextBlockContent key={block.key} content={block.content} isStreaming={isLast && isStreaming} />;
      })}
    </div>
  );
}

// --- Main Chat Component ---
export function AIChat() {
  const [sessions, setSessions] = useState<Session[]>([]);
  const [activeSessionId, setActiveSessionId] = useState<string>("");
  const [sessionMessages, setSessionMessages] = useState<Record<string, Message[]>>({});
  const [input, setInput] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [showMarketSidebar, setShowMarketSidebar] = useState(true);
  const [showSessionSidebar, setShowSessionSidebar] = useState(false);
  const [historyLoaded, setHistoryLoaded] = useState(false);
  const loadedSessionsRef = useRef<Set<string>>(new Set());
  const [editingSessionId, setEditingSessionId] = useState<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  const blockCounterRef = useRef(0);

  const messages = sessionMessages[activeSessionId] || [];

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages]);

  const loadSessions = useCallback(async () => {
    try {
      const res = await fetch(`${API_BASE}/api/chat/sessions`);
      if (!res.ok) return;
      const data: any[] = await res.json();
      const loaded: Session[] = data.map((s: any) => ({
        id: s.sessionId,
        title: s.title || "新对话",
        createdAt: new Date(s.createdAt),
      }));
      setSessions(loaded);
      if (loaded.length > 0) {
        setActiveSessionId(loaded[0].id);
      }
    } catch (e) {
      console.error("Failed to load sessions", e);
    } finally {
      setHistoryLoaded(true);
    }
  }, []);

  const loadMessages = useCallback(async (sessionId: string) => {
    if (!sessionId || loadedSessionsRef.current.has(sessionId)) return;
    loadedSessionsRef.current.add(sessionId);
    try {
      const res = await fetch(`${API_BASE}/api/chat/sessions/${sessionId}/messages`);
      if (!res.ok) return;
      const data: any[] = await res.json();
      const msgs: Message[] = data.map((m: any) => {
        if (m.role !== "assistant") {
          return {
            id: `db-${m.id}`,
            role: m.role as "user" | "assistant",
            content: m.content || "",
            blocks: undefined,
            timestamp: new Date(m.createdAt),
            token: m.token,
          };
        }
        const raw = m.content || "";
        const toolMarkerRegex = /<!--TOOL:(.*?)-->/g;
        const blocks: MessageBlock[] = [];
        const toolIndexMap = new Map<string, number>();
        let lastTextIdx = 0;
        let match: RegExpExecArray | null;
        while ((match = toolMarkerRegex.exec(raw)) !== null) {
          const textBefore = raw.slice(lastTextIdx, match.index);
          if (textBefore.trim()) {
            blocks.push({ type: "text", key: `db-${m.id}-t-${blocks.length}`, content: textBefore });
          }
          lastTextIdx = match.index + match[0].length;
          try {
            const parsed = JSON.parse(match[1]);
            if (parsed.type === "tool_call") {
              const statusMap: Record<string, "running" | "success" | "error"> = {
                calling: "running",
                success: "success",
                unknown: "error",
                error: "error",
              };
              const status = statusMap[parsed.status] || "error";
              const existingIdx = toolIndexMap.get(parsed.tool_id);
              if (existingIdx !== undefined) {
                const existing = (blocks[existingIdx] as ToolBlock).data;
                const merged: ToolCallData = {
                  id: existing.id,
                  tool_name: existing.tool_name,
                  status,
                  tool_input: parsed.tool_input !== undefined ? parsed.tool_input : existing.tool_input,
                  tool_result: parsed.tool_result !== undefined ? parsed.tool_result : existing.tool_result,
                };
                blocks[existingIdx] = { type: "tool", key: `tool-${parsed.tool_id}`, data: merged };
              } else {
                const toolData: ToolCallData = {
                  id: parsed.tool_id,
                  tool_name: parsed.tool_name || "unknown",
                  status,
                  tool_input: parsed.tool_input,
                  tool_result: parsed.tool_result,
                };
                toolIndexMap.set(parsed.tool_id, blocks.length);
                blocks.push({ type: "tool", key: `tool-${parsed.tool_id}`, data: toolData });
              }
            }
          } catch (_) {}
        }
        const remainingText = raw.slice(lastTextIdx);
        if (remainingText.trim()) {
          blocks.push({ type: "text", key: `db-${m.id}-t-${blocks.length}`, content: remainingText });
        }
        if (blocks.length === 0) {
          blocks.push({ type: "text", key: `db-${m.id}`, content: raw });
        }
        return {
          id: `db-${m.id}`,
          role: m.role as "assistant",
          content: raw,
          blocks,
          timestamp: new Date(m.createdAt),
          token: m.token,
        };
      });
      if (msgs.length === 0) {
        msgs.push(createWelcomeMessage());
      }
      setSessionMessages(prev => ({ ...prev, [sessionId]: msgs }));
    } catch (e) {
      console.error("Failed to load messages", e);
      setSessionMessages(prev => ({ ...prev, [sessionId]: [createWelcomeMessage()] }));
    }
  }, []);

  useEffect(() => {
    loadSessions();
  }, [loadSessions]);

  useEffect(() => {
    if (activeSessionId && historyLoaded) {
      loadMessages(activeSessionId);
    }
  }, [activeSessionId, historyLoaded, loadMessages]);

  const stopGeneration = useCallback(() => {
    if (abortRef.current) {
      abortRef.current.abort();
      abortRef.current = null;
    }
  }, []);

  const updateMessages = (updater: (prev: Message[]) => Message[]) => {
    setSessionMessages(prev => ({
      ...prev,
      [activeSessionId]: updater(prev[activeSessionId] || []),
    }));
  };

  const handleSend = async () => {
    if (!input.trim() || isLoading) return;

    let sessionId = activeSessionId;
    if (!sessionId) {
      const newId = generateSessionId();
      const newSession: Session = { id: newId, title: "新对话", createdAt: new Date() };
      setSessions(prev => [newSession, ...prev]);
      setSessionMessages(prev => ({ ...prev, [newId]: [createWelcomeMessage()] }));
      setActiveSessionId(newId);
      sessionId = newId;
    }

    const userMessage: Message = {
      id: Date.now().toString(),
      role: "user",
      content: input,
      timestamp: new Date(),
    };

    const botId = (Date.now() + 1).toString();
    const botMessage: Message = {
      id: botId,
      role: "assistant",
      content: "",
      blocks: [],
      timestamp: new Date(),
      isStreaming: true,
    };

    const localUpdateMessages = (updater: (prev: Message[]) => Message[]) => {
      setSessionMessages(prev => ({
        ...prev,
        [sessionId]: updater(prev[sessionId] || []),
      }));
    };

    localUpdateMessages(prev => [...prev, userMessage, botMessage]);

    const sentContent = input;
    setInput("");
    setIsLoading(true);

    const newTitle = sentContent.length > 20 ? sentContent.substring(0, 20) + "..." : sentContent;
    setSessions(prev => {
      const needSync = prev.find(s => s.id === sessionId)?.title === "新对话";
      if (needSync) {
        fetch(`${API_BASE}/api/chat/sessions/${sessionId}/title`, {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ title: newTitle }),
        }).catch(() => {});
      }
      return prev.map(s =>
        s.id === sessionId ? { ...s, title: newTitle } : s
      );
    });

    const controller = new AbortController();
    abortRef.current = controller;

    let currentTextSegment = "";

    const appendTextBlock = (botMsg: Message, text: string): Message => {
      const blocks = [...(botMsg.blocks || [])];
      let lastBlock = blocks[blocks.length - 1];
      if (!lastBlock || lastBlock.type !== "text") {
        lastBlock = { type: "text" as const, key: `tb-${++blockCounterRef.current}`, content: "" };
        blocks.push(lastBlock);
      }
      return { ...botMsg, blocks: blocks.map((b, i) => i === blocks.length - 1 ? { ...b, content: text } : b) };
    };

    const updateToolBlock = (botMsg: Message, toolId: string, data: any): Message => {
      const blocks = [...(botMsg.blocks || [])];
      const idx = blocks.findIndex(b => b.type === "tool" && b.key === `tool-${toolId}`);
      const toolData: ToolCallData = {
        id: toolId,
        tool_name: data.tool_name || (idx >= 0 && blocks[idx].type === "tool" ? blocks[idx].data.tool_name : "unknown"),
        status: data.status === "success" ? "success" : data.status === "error" ? "error" : "running",
        tool_input: data.tool_input !== undefined ? data.tool_input : (idx >= 0 && blocks[idx].type === "tool" ? (blocks[idx] as ToolBlock).data.tool_input : undefined),
        tool_result: data.tool_result !== undefined ? data.tool_result : (idx >= 0 && blocks[idx].type === "tool" ? (blocks[idx] as ToolBlock).data.tool_result : undefined),
      };
      const toolBlock: ToolBlock = { type: "tool", key: `tool-${toolId}`, data: toolData };
      if (idx >= 0) {
        blocks[idx] = toolBlock;
      } else {
        blocks.push(toolBlock);
      }
      return { ...botMsg, blocks };
    };

    try {
      const response = await fetch("http://127.0.0.1:8099/agent/streamChat", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-Session-Id": activeSessionId,
        },
        body: JSON.stringify({ message: sentContent }),
        signal: controller.signal,
      });

      const reader = response.body!.getReader();
      const decoder = new TextDecoder("utf-8");
      let buffer = "";

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split("\n");
        buffer = lines.pop()!;

        for (const line of lines) {
          if (!line.trim()) continue;

          let jsonStr = line;
          if (line.startsWith("data:")) {
            jsonStr = line.substring(5).trim();
          }
          if (!jsonStr) continue;

          try {
            const data = JSON.parse(jsonStr);

            if (data.type === "content_block_delta" && data.delta && data.delta.type === "text_delta") {
              currentTextSegment += data.delta.text;
              localUpdateMessages(prev => prev.map(m => {
                if (m.id !== botId) return m;
                return appendTextBlock(m, currentTextSegment);
              }));
            } else if (data.type === "tool_status") {
              currentTextSegment = "";
              if (data.tool_id) {
                localUpdateMessages(prev => prev.map(m => {
                  if (m.id !== botId) return m;
                  return updateToolBlock(m, data.tool_id, data);
                }));
              }
            } else if (data.type === "end") {
              localUpdateMessages(prev => prev.map(m =>
                m.id === botId ? { ...m, isStreaming: false, token: data.token } : m
              ));
            }
          } catch {
            // skip unparseable lines
          }
        }

        if (scrollRef.current) {
          scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
        }
      }
    } catch (error: any) {
      if (error.name === "AbortError") {
        localUpdateMessages(prev => prev.map(m => {
          if (m.id !== botId) return m;
          return appendTextBlock(m, (m.blocks || []).reduce((acc, b) => b.type === "text" ? b.content : acc, "") + "\n\n[对话已终止]");
        }));
      } else {
        console.error("Chat Error:", error);
        localUpdateMessages(prev => prev.map(m => {
          if (m.id !== botId) return m;
          return appendTextBlock(m, "Error communicating with the server.");
        }));
      }
    } finally {
      localUpdateMessages(prev => prev.map(m =>
        m.id === botId ? { ...m, isStreaming: false } : m
      ));
      setIsLoading(false);
      abortRef.current = null;
    }
  };

  const createNewSession = () => {
    if (isLoading) stopGeneration();
    const newId = generateSessionId();
    const newSession: Session = { id: newId, title: "新对话", createdAt: new Date() };
    setSessions(prev => [newSession, ...prev]);
    setSessionMessages(prev => ({ ...prev, [newId]: [createWelcomeMessage()] }));
    setActiveSessionId(newId);
  };

  const switchSession = (sessionId: string) => {
    if (sessionId === activeSessionId || isLoading) return;
    setActiveSessionId(sessionId);
  };

  const deleteSession = (sessionId: string) => {
    if (sessionId === activeSessionId) return;
    if (isLoading) return;
    fetch(`${API_BASE}/api/chat/sessions/${sessionId}`, { method: "DELETE" }).catch(() => {});
    setSessions(prev => prev.filter(s => s.id !== sessionId));
    setSessionMessages(prev => {
      const next = { ...prev };
      delete next[sessionId];
      return next;
    });
  };

  const saveTitle = async (sessionId: string, newTitle: string) => {
    const trimmed = newTitle.trim();
    if (!trimmed) {
      setEditingSessionId(null);
      return;
    }
    setSessions(prev => prev.map(s => s.id === sessionId ? { ...s, title: trimmed } : s));
    setEditingSessionId(null);
    try {
      await fetch(`${API_BASE}/api/chat/sessions/${sessionId}/title`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ title: trimmed }),
      });
    } catch (e) {
      console.error("Failed to save title", e);
    }
  };

  const clearChat = createNewSession;

  return (
    <div className="flex h-[calc(100vh-12rem)] max-w-7xl mx-auto bg-[var(--surface)] border border-[var(--border)] rounded-xl overflow-hidden shadow-xl relative">
      {/* Session Sidebar */}
      <AnimatePresence>
        {showSessionSidebar && (
          <motion.div
            initial={{ width: 0, opacity: 0 }}
            animate={{ width: 240, opacity: 1 }}
            exit={{ width: 0, opacity: 0 }}
            className="border-r border-[var(--border)] bg-[var(--surface)] overflow-hidden flex-shrink-0"
          >
            <div className="w-[240px] h-full flex flex-col">
              <div className="px-3 py-2.5 border-b border-[var(--border)] flex items-center justify-between">
                <span className="text-[10px] font-bold uppercase text-[var(--text-muted)] tracking-widest">会话列表</span>
                <button
                  onClick={() => setShowSessionSidebar(false)}
                  className="p-1 text-[var(--text-muted)] hover:text-[var(--text-primary)] rounded transition-colors"
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              </div>
              <div className="flex-1 overflow-y-auto p-2 space-y-1">
                {sessions.map(session => (
                  <div
                    key={session.id}
                    onClick={() => {
                      if (editingSessionId !== session.id) switchSession(session.id);
                    }}
                    className={cn(
                      "group flex items-center gap-2 px-3 py-2 rounded-lg cursor-pointer transition-all text-[12px]",
                      session.id === activeSessionId
                        ? "bg-[var(--accent)]/10 text-[var(--accent)] border border-[var(--accent)]/20"
                        : "text-[var(--text-secondary)] hover:bg-[var(--surface-hover)] border border-transparent"
                    )}
                  >
                    <MessageSquare className="w-3.5 h-3.5 shrink-0 opacity-60" />
                    {editingSessionId === session.id ? (
                      <input
                        autoFocus
                        defaultValue={session.title}
                        onBlur={(e) => saveTitle(session.id, e.target.value)}
                        onKeyDown={(e) => {
                          if (e.key === "Enter") {
                            (e.target as HTMLInputElement).blur();
                          } else if (e.key === "Escape") {
                            setEditingSessionId(null);
                          }
                        }}
                        onClick={(e) => e.stopPropagation()}
                        className="flex-1 min-w-0 bg-[var(--bg)] border border-[var(--accent)]/30 rounded px-1.5 py-0.5 text-[12px] text-[var(--text-primary)] outline-none"
                      />
                    ) : (
                      <span
                        className="flex-1 truncate"
                        onDoubleClick={(e) => {
                          e.stopPropagation();
                          setEditingSessionId(session.id);
                        }}
                      >{session.title}</span>
                    )}
                    {sessions.length > 1 && session.id !== activeSessionId && (
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          deleteSession(session.id);
                        }}
                        className="p-0.5 text-[var(--text-muted)] hover:text-[var(--down)] opacity-0 group-hover:opacity-100 transition-all rounded"
                      >
                        <Trash2 className="w-3 h-3" />
                      </button>
                    )}
                  </div>
                ))}
              </div>
              <div className="px-3 py-2 border-t border-[var(--border)]">
                <button
                  onClick={createNewSession}
                  className="w-full flex items-center justify-center gap-1.5 px-3 py-1.5 rounded-lg text-[11px] font-medium text-[var(--accent)] bg-[var(--accent)]/5 border border-[var(--accent)]/15 hover:bg-[var(--accent)]/10 transition-colors"
                >
                  <Plus className="w-3.5 h-3.5" />
                  新对话
                </button>
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Main Chat Area */}
      <div className="flex-1 flex flex-col min-w-0">
        {/* Chat Header */}
        <div className="px-5 py-3 border-b border-[var(--border)] bg-[var(--surface)] flex justify-between items-center relative">
          <div className="h-[2px] bg-gradient-to-r from-transparent via-[var(--accent)]/50 to-transparent absolute top-0 left-0 right-0" />
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-lg bg-[var(--accent)]/10 border border-[var(--accent)]/20 flex items-center justify-center text-[var(--accent)]">
              <Bot className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-[15px] font-bold text-[var(--text-primary)] font-display uppercase tracking-wide">AI Chat</h2>
              <div className="flex items-center gap-1.5">
                <span className="w-1.5 h-1.5 rounded-full bg-[var(--up)]"></span>
                <span className="text-[10px] text-[var(--text-muted)] uppercase tracking-wider">
                  {isLoading ? "生成中..." : "在线"}
                </span>
              </div>
            </div>
          </div>
          <div className="flex items-center gap-1.5">
            <button
              onClick={createNewSession}
              className="p-2 text-[var(--text-secondary)] hover:text-[var(--accent)] hover:bg-[var(--accent)]/5 rounded-lg transition-all"
              title="新对话"
            >
              <Plus className="w-4 h-4" />
            </button>
            <button
              onClick={() => setShowSessionSidebar(!showSessionSidebar)}
              className={cn(
                "p-2 rounded-lg transition-all",
                showSessionSidebar ? "bg-[var(--accent)]/10 text-[var(--accent)]" : "text-[var(--text-secondary)] hover:bg-[var(--surface-hover)]"
              )}
              title="会话列表"
            >
              <MessageSquare className="w-4 h-4" />
            </button>
            <button
              onClick={() => setShowMarketSidebar(!showMarketSidebar)}
              className={cn(
                "p-2 rounded-lg transition-all",
                showMarketSidebar ? "bg-[var(--accent)]/10 text-[var(--accent)]" : "text-[var(--text-secondary)] hover:bg-[var(--surface-hover)]"
              )}
              title="切换市场侧栏"
            >
              <TrendingUp className="w-4 h-4" />
            </button>
          </div>
        </div>

        {/* Messages Area */}
        <div
          ref={scrollRef}
          className="flex-1 overflow-y-auto p-5 space-y-5 scroll-smooth"
        >
          <AnimatePresence initial={false}>
            {messages.map((msg) => (
              <motion.div
                key={msg.id}
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                className={cn(
                  "flex gap-3",
                  msg.role === "user" ? "flex-row-reverse" : "flex-row"
                )}
              >
                <div className={cn(
                  "w-8 h-8 rounded-lg flex items-center justify-center shrink-0 mt-0.5",
                  msg.role === "user" ? "bg-[var(--surface-hover)] border border-[var(--border)]" : "bg-[var(--accent)]/10 border border-[var(--accent)]/20"
                )}>
                  {msg.role === "user" ? <User className="w-4 h-4 text-[var(--text-secondary)]" /> : <Bot className="w-4 h-4 text-[var(--accent)]" />}
                </div>

                <div className={cn(
                  "flex flex-col gap-1.5 max-w-[85%]",
                  msg.role === "user" ? "items-end" : "items-start"
                )}>
                  <div className={cn(
                    "px-4 py-3 rounded-xl text-[13px] leading-relaxed",
                    msg.role === "user"
                      ? "bg-[var(--accent)] text-[var(--bg)] rounded-tr-none font-medium"
                      : "bg-[var(--bg)] border border-[var(--border)] text-[var(--text-primary)] rounded-tl-none"
                  )}>
                    {msg.role === "assistant" ? (
                      <AssistantContent msg={msg} />
                    ) : (
                      msg.content
                    )}
                  </div>

                  <span className="text-[10px] text-[var(--text-muted)] font-mono px-1">
                    {msg.timestamp.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                    {msg.token != null && msg.token > 0 && (
                      <span className="ml-2 opacity-60">· {msg.token} tokens</span>
                    )}
                  </span>
                </div>
              </motion.div>
            ))}
          </AnimatePresence>
        </div>

        {/* Input Area */}
        <div className="p-4 bg-[var(--surface)] border-t border-[var(--border)]">
          <div className="max-w-4xl mx-auto relative">
            <div className="flex gap-2">
              <textarea
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter" && !e.shiftKey) {
                    e.preventDefault();
                    handleSend();
                  }
                }}
                placeholder="输入消息..."
                className="flex-1 resize-none rounded-xl bg-[var(--bg)] border border-[var(--border)] px-4 py-3 text-[13px] text-[var(--text-primary)] placeholder:text-[var(--text-muted)] focus:outline-none focus:border-[var(--accent)]/50 transition-colors min-h-[44px] max-h-[120px]"
                rows={1}
                disabled={isLoading}
              />
              {isLoading ? (
                <button
                  onClick={stopGeneration}
                  className="px-4 rounded-xl bg-[var(--down)]/10 border border-[var(--down)]/30 text-[var(--down)] hover:bg-[var(--down)]/20 transition-colors flex items-center justify-center"
                  title="停止生成"
                >
                  <StopCircle className="w-5 h-5" />
                </button>
              ) : (
                <button
                  onClick={handleSend}
                  disabled={!input.trim()}
                  className={cn(
                    "px-4 rounded-xl transition-colors flex items-center justify-center",
                    input.trim()
                      ? "bg-[var(--accent)] text-[var(--bg)] hover:bg-[var(--accent-hover)]"
                      : "bg-[var(--surface-hover)] text-[var(--text-muted)] cursor-not-allowed"
                  )}
                  title="发送"
                >
                  <Send className="w-5 h-5" />
                </button>
              )}
            </div>
            <div className="text-center mt-2 text-[10px] text-[var(--text-muted)]">
              内容由AI生成，请仔细甄别
            </div>
          </div>
        </div>
      </div>

      {/* Market Sidebar */}
      <AnimatePresence>
        {showMarketSidebar && (
          <motion.div
            initial={{ width: 0, opacity: 0 }}
            animate={{ width: 280, opacity: 1 }}
            exit={{ width: 0, opacity: 0 }}
            className="border-l border-[var(--border)] bg-[var(--surface)] overflow-hidden flex-shrink-0"
          >
            <div className="w-[280px] p-4 space-y-4 h-full overflow-y-auto">
              <div className="space-y-3">
                <div className="text-[9px] font-bold uppercase text-[var(--text-muted)] tracking-widest">市场概览</div>
                {[
                  { label: "总库存价值", value: "¥12,450", change: "+3.2%", up: true },
                  { label: "今日交易", value: "8", change: "", up: true },
                  { label: "待处理订单", value: "2", change: "", up: false },
                ].map((item, i) => (
                  <div key={i} className="flex items-center justify-between">
                    <span className="text-[11px] text-[var(--text-secondary)]">{item.label}</span>
                    <div className="flex items-center gap-2">
                      <span className="text-[13px] font-bold text-[var(--text-primary)] font-mono">{item.value}</span>
                      {item.change && (
                        <span className={cn("text-[10px] font-mono", item.up ? "text-[var(--up)]" : "text-[var(--down)]")}>
                          {item.change}
                        </span>
                      )}
                    </div>
                  </div>
                ))}
              </div>

              <div className="h-px bg-[var(--border)]" />

              <div className="p-3 rounded-lg bg-[var(--accent)]/5 border border-[var(--accent)]/15">
                <div className="text-[9px] font-bold uppercase text-[var(--accent)] mb-1.5 tracking-widest">AI 情绪分析</div>
                <div className="flex items-center gap-2 mb-1.5">
                  <div className="text-lg font-black text-[var(--accent)] font-display uppercase">看涨</div>
                  <TrendingUp className="w-4 h-4 text-[var(--accent)]" />
                </div>
                <p className="text-[10px] text-[var(--text-secondary)] leading-relaxed">
                  过去 24h 交易量上涨 12%。隐秘级物品需求旺盛。
                </p>
              </div>

              <div>
                <div className="text-[9px] font-bold uppercase text-[var(--text-muted)] mb-3 tracking-widest">最近动态</div>
                <div className="space-y-2">
                  {[
                    { user: "用户_42", action: "买入了", item: "格洛克 18 | 渐变", time: "2分钟前" },
                    { user: "交易者X", action: "卖出了", item: "沙漠之鹰 | 烈焰", time: "5分钟前" }
                  ].map((act, i) => (
                    <div key={i} className="flex items-center gap-2 text-[10px]">
                      <div className="w-5 h-5 rounded bg-[var(--surface-hover)] flex items-center justify-center shrink-0">
                        <User className="w-2.5 h-2.5 text-[var(--text-muted)]" />
                      </div>
                      <div className="flex-1 min-w-0">
                        <span className="font-bold text-[var(--text-primary)]">{act.user}</span>
                        <span className="text-[var(--text-muted)] mx-0.5">{act.action}</span>
                        <span className="font-medium text-[var(--text-primary)]">{act.item}</span>
                      </div>
                      <div className="text-[var(--text-muted)] font-mono shrink-0">{act.time}</div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
