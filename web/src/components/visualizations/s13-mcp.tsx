"use client";

import { motion, AnimatePresence } from "framer-motion";
import { useSteppedVisualization } from "@/hooks/useSteppedVisualization";
import { StepControls } from "@/components/visualizations/shared/step-controls";

interface MCPTool {
  name: string;
  description: string;
  inputSchema: Record<string, unknown>;
  server: string;
  color: string;
}

const MCP_TOOLS: MCPTool[] = [
  {
    name: "get_weather",
    description: "获取指定城市的实时天气信息",
    inputSchema: {
      type: "object",
      properties: {
        city: { type: "string", description: "城市名称" },
        unit: { type: "string", enum: ["celsius", "fahrenheit"], description: "温度单位" }
      },
      required: ["city"]
    },
    server: "Weather Server",
    color: "blue"
  },
  {
    name: "search_files",
    description: "在文件系统中搜索匹配的文件",
    inputSchema: {
      type: "object",
      properties: {
        pattern: { type: "string", description: "文件名模式" },
        path: { type: "string", description: "搜索路径" }
      },
      required: ["pattern"]
    },
    server: "Filesystem MCP",
    color: "purple"
  },
  {
    name: "execute_query",
    description: "执行数据库查询操作",
    inputSchema: {
      type: "object",
      properties: {
        sql: { type: "string", description: "SQL查询语句" },
        params: { type: "array", items: { type: "string" }, description: "查询参数" }
      },
      required: ["sql"]
    },
    server: "Database MCP",
    color: "green"
  },
  {
    name: "git_commit",
    description: "创建Git提交记录",
    inputSchema: {
      type: "object",
      properties: {
        message: { type: "string", description: "提交信息" },
        files: { type: "array", items: { type: "string" }, description: "要提交的文件" }
      },
      required: ["message"]
    },
    server: "Git MCP",
    color: "orange"
  }
];

const SCHEMA_PREVIEW = `{
  "name": "get_weather",
  "description": "获取指定城市的实时天气信息",
  "inputSchema": {
    "type": "object",
    "properties": {
      "city": { "type": "string" },
      "unit": { "type": "string", "enum": ["celsius", "fahrenheit"] }
    },
    "required": ["city"]
  }
}`;

const AGENT_RESPONSE = `{
  "tool": "get_weather",
  "arguments": {
    "city": "北京",
    "unit": "celsius"
  }
}`;

const TOOL_RESULT = `{
  "status": "success",
  "temperature": 22,
  "condition": "晴朗",
  "humidity": 45,
  "wind": "东北风 3级"
}`;

const TOKEN_STATES = [0, 0, 1500, 1500, 1500, 1700];
const MAX_TOKEN_DISPLAY = 2000;

const STEPS = [
  {
    title: "第一步：建立连接",
    description: "客户端通过标准协议（SSE、WebSocket或stdio）与MCP Server建立通信通道，完成协议握手和版本协商。"
  },
  {
    title: "第二步：工具发现",
    description: "客户端发送 tools/list 请求，Server返回可用工具清单，包含名称、描述和JSON Schema格式的参数定义。"
  },
  {
    title: "第三步：Schema注入",
    description: "将工具的JSON Schema格式化为Agent能理解的描述文本，嵌入到系统提示词中，告知Agent工具的功能和调用格式。"
  },
  {
    title: "第四步：结构化调用",
    description: "Agent根据用户问题判断需要调用工具，生成符合Schema的结构化响应，包含工具名称和参数对象。"
  },
  {
    title: "第五步：执行与处理",
    description: "客户端通过MCP协议发送 tools/call 请求，Server执行工具并返回结果，完成工具调用闭环。"
  },
  {
    title: "完整闭环",
    description: "MCP协议让Agent能够动态扩展能力，与外部系统无缝交互。从连接、发现、注入、调用到执行，形成完整工具调用流程。"
  }
];

export default function MCProtocol({ title }: { title?: string }) {
  const {
    currentStep,
    totalSteps,
    next,
    prev,
    reset,
    isPlaying,
    toggleAutoPlay,
  } = useSteppedVisualization({ totalSteps: STEPS.length, autoPlayInterval: 2500 });

  const showConnection = currentStep >= 1;
  const showToolsList = currentStep >= 2;
  const showSchemaInjection = currentStep >= 3;
  const showCompleteFlow = currentStep === 5;

  const highlightedTool = currentStep === 2 ? 0 : currentStep >= 3 ? 0 : -1;
  const showFullSchema = currentStep >= 3;
  const showAgentResponse = currentStep >= 4;
  const showToolResult = currentStep === 5;

  const tokenCount = TOKEN_STATES[currentStep];

  return (
    <section className="space-y-4">
      <h2 className="text-xl font-semibold text-[#794f27]">
        {title || "MCP 完整运行流程"}
      </h2>

      <div
        className="rounded-lg border border-[#e8dcc8] bg-[#fbf7eb] p-6"
        style={{ minHeight: 520 }}
      >
        <div className="flex gap-6">
          {/* Main content area */}
          <div className="flex-1 space-y-4">
            {/* MCP Client Block */}
            <div>
              <div className="mb-2 flex items-center gap-2">
                <div className="h-2 w-2 rounded-full bg-[#8ac68a]" />
                <span className="text-xs font-semibold text-[#9f927d]">
                  MCP Client (AI Agent)
                </span>
                <span className="rounded bg-[#e0f0e0] px-1.5 py-0.5 font-mono text-[10px] text-[#8ac68a]">
                  Host
                </span>
              </div>
              <div className="rounded-lg border border-[#8ac68a]/40 bg-[#fbf7eb] p-4">
                <div className="mb-2 font-mono text-[10px] text-[#8a7b66]">
                  # Agent 系统提示词
                </div>
                <div className="space-y-1.5">
                  {showSchemaInjection ? (
                    <motion.div
                      initial={{ opacity: 0 }}
                      animate={{ opacity: 1 }}
                      className="rounded border-2 border-[#889df0]/50 bg-[#e6eafb] p-3"
                    >
                      <div className="mb-2 text-xs font-semibold text-[#889df0]">
                        可用工具 (Tools)
                      </div>
                      <div className="space-y-1">
                        {MCP_TOOLS.slice(0, 2).map((tool, i) => {
                          const isHighlighted = i === highlightedTool;
                          return (
                            <motion.div
                              key={tool.name}
                              animate={{
                                boxShadow: isHighlighted
                                  ? "0 0 12px 2px rgba(136, 157, 240, 0.5)"
                                  : "0 0 0 0px rgba(136, 157, 240, 0)",
                              }}
                              transition={{ duration: 0.4 }}
                              className={`rounded px-3 py-1.5 font-mono text-xs transition-colors ${
                                isHighlighted
                                  ? "bg-[#e6eafb] text-[#889df0]"
                                  : "bg-[#f6efe0] text-[#725d42]"
                              }`}
                            >
                              <span className="font-semibold text-[#794f27]">
                                {tool.name}
                              </span>
                              {" - "}
                              {tool.description}
                            </motion.div>
                          );
                        })}
                      </div>
                    </motion.div>
                  ) : (
                    <div className="rounded bg-[#f6efe0] px-3 py-2 text-xs text-[#725d42]">
                      等待工具注册...
                    </div>
                  )}
                </div>
              </div>
            </div>

            {/* Connection indicator */}
            <AnimatePresence>
              {showConnection && currentStep <= 2 && (
                <motion.div
                  initial={{ opacity: 0, y: -8 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0 }}
                  className="flex items-center gap-2 rounded-lg border border-[#8ac68a]/40 bg-[#e0f0e0] px-3 py-2"
                >
                  <div className="h-2 w-2 rounded-full bg-[#8ac68a] animate-pulse" />
                  <span className="text-xs text-[#8ac68a]">
                    已连接到 {MCP_TOOLS.length} 个 MCP 服务器
                  </span>
                </motion.div>
              )}
            </AnimatePresence>

            {/* Connecting arrow */}
            <AnimatePresence>
              {(showToolsList || showSchemaInjection) && (
                <motion.div
                  initial={{ opacity: 0, scaleY: 0 }}
                  animate={{ opacity: 1, scaleY: 1 }}
                  exit={{ opacity: 0 }}
                  className="flex justify-center"
                >
                  <div className="flex flex-col items-center">
                    <div className="h-6 w-px bg-[#889df0]" />
                    <div className="h-0 w-0 border-l-[5px] border-r-[5px] border-t-[6px] border-l-transparent border-r-transparent border-t-[#889df0]" />
                  </div>
                </motion.div>
              )}
            </AnimatePresence>

            {/* MCP Server Response Blocks */}
            <div className="space-y-3">
              {/* Tools List Response */}
              <AnimatePresence>
                {showToolsList && currentStep <= 3 && (
                  <motion.div
                    initial={{ opacity: 0, height: 0 }}
                    animate={{ opacity: currentStep === 3 ? 0.5 : 1, height: "auto" }}
                    exit={{ opacity: 0, height: 0 }}
                    transition={{ duration: 0.4 }}
                    className="overflow-hidden"
                  >
                    <div className="rounded-lg border-2 border-[#b77dee]/40 bg-[#fbf7eb] p-4">
                      <div className="mb-2 flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <div className="h-2 w-2 rounded-full bg-[#b77dee]" />
                          <span className="text-xs font-bold text-[#b77dee]">
                            tools/list 响应
                          </span>
                        </div>
                        <span className="rounded bg-[#efe2fb] px-1.5 py-0.5 font-mono text-[10px] text-[#b77dee]">
                          MCP Server
                        </span>
                      </div>
                      <div className="space-y-1">
                        {MCP_TOOLS.slice(0, 3).map((tool, i) => (
                          <motion.div
                            key={i}
                            initial={{ opacity: 0, x: -8 }}
                            animate={{ opacity: 1, x: 0 }}
                            transition={{ delay: i * 0.08 }}
                            className="rounded bg-[#f6efe0] px-3 py-1.5"
                          >
                            <div className="font-mono text-xs font-semibold text-[#794f27]">
                              {tool.name}
                            </div>
                            <div className="text-[10px] text-[#725d42]">
                              {tool.description}
                            </div>
                          </motion.div>
                        ))}
                      </div>
                    </div>
                  </motion.div>
                )}
              </AnimatePresence>

              {/* JSON Schema Injection */}
              <AnimatePresence>
                {showFullSchema && (
                  <motion.div
                    initial={{ opacity: 0, height: 0 }}
                    animate={{ opacity: 1, height: "auto" }}
                    exit={{ opacity: 0, height: 0 }}
                    transition={{ duration: 0.4 }}
                    className="overflow-hidden"
                  >
                    <div className="rounded-lg border-2 border-[#889df0]/40 bg-[#fbf7eb] p-4">
                      <div className="mb-2 flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <div className="h-2 w-2 rounded-full bg-[#889df0]" />
                          <span className="text-xs font-bold text-[#889df0]">
                            工具 Schema 注入
                          </span>
                        </div>
                        <span className="rounded bg-[#e6eafb] px-1.5 py-0.5 font-mono text-[10px] text-[#889df0]">
                          System Prompt
                        </span>
                      </div>
                      <pre className="font-mono text-[10px] text-[#9f927d]">
                        {SCHEMA_PREVIEW}
                      </pre>
                    </div>
                  </motion.div>
                )}
              </AnimatePresence>

              {/* Agent Response */}
              <AnimatePresence>
                {showAgentResponse && currentStep <= 4 && (
                  <motion.div
                    initial={{ opacity: 0, height: 0 }}
                    animate={{ opacity: 1, height: "auto" }}
                    exit={{ opacity: 0, height: 0 }}
                    transition={{ duration: 0.4 }}
                    className="overflow-hidden"
                  >
                    <div className="rounded-lg border-2 border-[#e59266]/40 bg-[#fbf7eb] p-4">
                      <div className="mb-2 flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <div className="h-2 w-2 rounded-full bg-[#e59266]" />
                          <span className="text-xs font-bold text-[#e59266]">
                            Agent 结构化响应
                          </span>
                        </div>
                        <span className="rounded bg-[#fde6d8] px-1.5 py-0.5 font-mono text-[10px] text-[#e59266]">
                          Agent → Client
                        </span>
                      </div>
                      <pre className="font-mono text-[10px] text-[#9f927d]">
                        {AGENT_RESPONSE}
                      </pre>
                    </div>
                  </motion.div>
                )}
              </AnimatePresence>

              {/* Tool Execution Result */}
              <AnimatePresence>
                {showToolResult && (
                  <motion.div
                    initial={{ opacity: 0, height: 0 }}
                    animate={{ opacity: 1, height: "auto" }}
                    exit={{ opacity: 0, height: 0 }}
                    transition={{ duration: 0.4 }}
                    className="overflow-hidden"
                  >
                    <div className="rounded-lg border-2 border-[#8ac68a]/40 bg-[#fbf7eb] p-4">
                      <div className="mb-2 flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <div className="h-2 w-2 rounded-full bg-[#8ac68a]" />
                          <span className="text-xs font-bold text-[#8ac68a]">
                            工具执行结果
                          </span>
                        </div>
                        <span className="rounded bg-[#e0f0e0] px-1.5 py-0.5 font-mono text-[10px] text-[#8ac68a]">
                          MCP Server → Client
                        </span>
                      </div>
                      <pre className="font-mono text-[10px] text-[#9f927d]">
                        {TOOL_RESULT}
                      </pre>
                    </div>
                  </motion.div>
                )}
              </AnimatePresence>
            </div>

            {/* Complete Flow Overview */}
            <AnimatePresence>
              {showCompleteFlow && (
                <motion.div
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  exit={{ opacity: 0 }}
                  className="rounded-lg border border-[#e8dcc8] bg-[#fbf7eb] p-3"
                >
                  <div className="mb-2 text-xs font-bold text-[#9f927d]">
                    🔄 MCP 工具调用闭环
                  </div>
                  <div className="flex flex-wrap gap-2 text-[10px]">
                    <span className="rounded bg-[#e0f0e0] px-2 py-1 font-semibold text-[#8ac68a]">
                      1. 连接建立
                    </span>
                    <span className="text-[#725d42]">→</span>
                    <span className="rounded bg-[#efe2fb] px-2 py-1 font-semibold text-[#b77dee]">
                      2. 工具发现
                    </span>
                    <span className="text-[#725d42]">→</span>
                    <span className="rounded bg-[#e6eafb] px-2 py-1 font-semibold text-[#889df0]">
                      3. Schema注入
                    </span>
                    <span className="text-[#725d42]">→</span>
                    <span className="rounded bg-[#fde6d8] px-2 py-1 font-semibold text-[#e59266]">
                      4. 结构化调用
                    </span>
                    <span className="text-[#725d42]">→</span>
                    <span className="rounded bg-[#e0f0e0] px-2 py-1 font-semibold text-[#8ac68a]">
                      5. 执行处理
                    </span>
                  </div>
                </motion.div>
              )}
            </AnimatePresence>
          </div>

          {/* Info Panel */}

          {/* Token Gauge */}
          <div className="flex w-16 flex-col items-center">
            <div className="mb-1 text-center font-mono text-[10px] text-[#725d42]">
              Tokens
            </div>
            <div
              className="relative w-8 overflow-hidden rounded-full bg-[#f6efe0]"
              style={{ height: 300 }}
            >
              <motion.div
                animate={{
                  height: `${(tokenCount / MAX_TOKEN_DISPLAY) * 100}%`,
                }}
                transition={{ duration: 0.5 }}
                className={`absolute bottom-0 w-full rounded-full ${
                  tokenCount > 1000
                    ? "bg-[#e59266]"
                    : tokenCount > 0
                      ? "bg-[#889df0]"
                      : "bg-[#8ac68a]"
                }`}
              />
            </div>
            <motion.div
              key={tokenCount}
              initial={{ scale: 0.8 }}
              animate={{ scale: 1 }}
              className="mt-2 text-center font-mono text-xs font-semibold text-[#9f927d]"
            >
              {tokenCount}
            </motion.div>
          </div>
        </div>

        {/* Step Controls */}
        <div className="mt-6">
          <StepControls
            currentStep={currentStep}
            totalSteps={totalSteps}
            onPrev={prev}
            onNext={next}
            onReset={reset}
            isPlaying={isPlaying}
            onToggleAutoPlay={toggleAutoPlay}
            stepTitle={STEPS[currentStep].title}
            stepDescription={STEPS[currentStep].description}
          />
        </div>
      </div>
    </section>
  );
}