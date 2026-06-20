"use client";

import Link from "next/link";
import { useTranslations, useLocale } from "@/lib/i18n";
import {
  LEARNING_PATH,
  VERSION_META,
  LAYERS,
  LAYER_COLOR_BY_ID,
} from "@/lib/constants";
import { LayerBadge, NewBadge } from "@/components/ui/badge";
import { useState, useMemo } from "react";
import { createPortal } from "react-dom";
import { RotatingText } from "@/components/ui/rotating-text";
import BentoCard from "@/components/ui/bento-card";
import ScrollReveal from "@/components/ui/scroll-reveal";
import { Card, Title, Button } from "animal-island-ui";
import hljs from "highlight.js/lib/common";
import { cn } from "@/lib/utils";
import versionsData from "@/data/generated/versions.json";

const BENTO_LAYOUT: Record<string, { colSpan?: boolean }> = {
  s01: { colSpan: true },
  s05: { colSpan: true },
  s09: { colSpan: true },
  s12: { colSpan: true },
  s13: { colSpan: true },
  s14: { colSpan: true },
};

const JAVA_LOOP_SOURCE = `while (true) {
    Message message = chatMessage(messageParams);
    messageParams.add(message.toParam());
    List<ContentBlockParam> toolResults = new ArrayList<>();
    boolean hasToolUse = false;
    for (ContentBlock content : message.content()) {
        if (content.isText()) {
            String result = content.text().map(TextBlock::text).orElse("");
        } else if (content.isToolUse()) {
            hasToolUse = true;
            ToolUseBlock toolUse = content.asToolUse();
            String toolResult = executeTool(toolUse.name(), toolUse._input());
            toolResults.add(ContentBlockParam.ofToolResult(
                ToolResultBlockParam.builder()
                    .toolUseId(toolUse.id())
                    .content(toolResult)
                    .build()));
        }
    }
    if (!hasToolUse) break;
    MessageParam.Content content = MessageParam.Content.ofBlockParams(toolResults);
    MessageParam toolResultMessage = MessageParam.builder()
        .role(MessageParam.Role.USER)
        .content(toolResults).build();
    messageParams.add(toolResultMessage);
}`;

const PYTHON_LOOP_SOURCE = `while True:
    response = client.messages.create(
        messages=messages, tools=tools
    )
    if response.stop_reason != "tool_use":
        break
    for tool_call in response.content:
        result = execute_tool(tool_call.name, tool_call.input)
        messages.append(result)`;

function getVersionData(id: string) {
  return versionsData.versions.find((v) => v.id === id);
}

export default function HomePage() {
  const t = useTranslations("home");
  const locale = useLocale();
  const [codeLanguage, setCodeLanguage] = useState<"java" | "python">("java");
  const [adImageOpen, setAdImageOpen] = useState(false);

  const source =
    codeLanguage === "java" ? JAVA_LOOP_SOURCE : PYTHON_LOOP_SOURCE;
  const highlighted = useMemo(() => {
    const lang = codeLanguage === "java" ? "java" : "python";
    try {
      return hljs.highlight(source, { language: lang }).value;
    } catch {
      return source;
    }
  }, [source, codeLanguage]);

  return (
    <div className="relative min-h-screen">
      <div className="flex flex-col gap-20 pb-16">
        {/* Hero Section */}
        <section className="flex flex-col items-center px-2 pt-8 text-center sm:pt-16">
          <div className="inline-flex items-center gap-2 rounded-full border border-[#82d5bb] bg-[#e6f9f6] px-4 py-1.5 text-sm font-semibold text-[#11a89b]">
            <span className="relative flex h-2 w-2">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-[#82d5bb] opacity-75"></span>
              <span className="relative inline-flex rounded-full h-2 w-2 bg-[#19c8b9]"></span>
            </span>
            Java AI Agent Framework
          </div>

          <div className="mt-6">
            <Title size="large" color="app-teal">
              {t("hero_title")}
            </Title>
          </div>

          <p className="mt-6 flex items-center justify-center gap-1.5 text-base text-[#725d42] sm:text-xl">
            <span>从零构建</span>
            <RotatingText
              texts={["AI 编程助手", "Agent 框架", "多智能体协作", "XX Claw"]}
              rotationInterval={2500}
              staggerDuration={0.03}
              mainClassName="text-[#11a89b]"
              elementLevelClassName="font-extrabold"
            />
          </p>

          <div className="mt-8 flex flex-wrap items-center justify-center gap-4">
            <Link href={`/${locale}/timeline`}>
              <Button type="primary" size="large">
                {t("start")} <span aria-hidden="true" className="ml-1">&rarr;</span>
              </Button>
            </Link>
            <a
              href="https://github.com/HOPPINZQ/hoppinai-agent"
              target="_blank"
              rel="noopener noreferrer"
            >
              <Button type="default" size="large">
                <span className="inline-flex items-center gap-2">
                  <svg
                    className="h-4 w-4"
                    fill="currentColor"
                    viewBox="0 0 24 24"
                    aria-hidden="true"
                  >
                    <path
                      fillRule="evenodd"
                      d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.531 1.032 1.531 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z"
                      clipRule="evenodd"
                    />
                  </svg>
                  GitHub
                </span>
              </Button>
            </a>
          </div>
        </section>

        {/* Ad Banner */}
        <section className="px-2">
          <div className="mx-auto max-w-4xl">
            <ScrollReveal animation="fade-up" duration={0.6}>
              <Card className="transition-colors hover:border-[#f8a6b2]">
                <div className="flex flex-col items-center gap-4 p-1 sm:flex-row sm:p-1">
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img
                    src="https://hoppinzq.com/ai/BigmodelPoster.png"
                    alt="智谱 Coding Plan"
                    className="h-20 w-20 shrink-0 cursor-zoom-in rounded-2xl object-cover transition-transform hover:scale-105 sm:h-24 sm:w-24"
                    onClick={() => setAdImageOpen(true)}
                  />
                  <div className="text-center sm:text-left">
                    <p className="text-sm font-semibold text-[#794f27]">
                      🙋 蹲队友拼智谱 Coding Plan！
                    </p>
                    <p className="mt-1 text-xs leading-relaxed text-[#8a7b66]">
                      🧩 国内顶流编程大模型，20+ 主流工具全适配，性价比拉满
                    </p>
                    <a
                      href="https://www.bigmodel.cn/glm-coding?ic=75JGQG0W9G"
                      target="_blank"
                      rel="noopener noreferrer"
                      className="mt-2 inline-block text-xs font-bold text-[#fc736d] underline decoration-[#f8a6b2] underline-offset-2 transition-colors hover:text-[#c44a4a]"
                    >
                      👉 立即参与「拼好模」→
                    </a>
                  </div>
                </div>
              </Card>
            </ScrollReveal>
            {/* Ad Image Lightbox - rendered via portal to escape stacking context */}
            {adImageOpen &&
              typeof window !== "undefined" &&
              createPortal(
                <div
                  className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/40 backdrop-blur-sm"
                  onClick={() => setAdImageOpen(false)}
                >
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img
                    src="https://hoppinzq.com/ai/BigmodelPoster.png"
                    alt="智谱 Coding Plan"
                    className="max-h-[90vh] max-w-[90vw] rounded-2xl object-contain shadow-2xl"
                    onClick={(e) => e.stopPropagation()}
                  />
                  <button
                    onClick={() => setAdImageOpen(false)}
                    className="fixed top-4 right-4 flex h-9 w-9 items-center justify-center rounded-full bg-white/30 text-white backdrop-blur-sm transition-colors hover:bg-white/50"
                  >
                    ✕
                  </button>
                </div>,
                document.body
              )}
          </div>
        </section>

        {/* Feature Cards */}
        <section className="px-2">
          <div className="mx-auto max-w-5xl">
            <ScrollReveal
              animation="fade-up"
              staggerChildren
              stagger={0.12}
            >
              <div className="grid gap-4 sm:grid-cols-3">
                <BentoCard className="p-6 sm:col-span-2" color="app-blue">
                  <div className="mb-4 flex h-11 w-11 items-center justify-center rounded-2xl bg-white/30">
                    <svg
                      className="h-5 w-5 text-white"
                      fill="none"
                      stroke="currentColor"
                      viewBox="0 0 24 24"
                    >
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeWidth={2.5}
                        d="M13 10V3L4 14h7v7l9-11h-7z"
                      />
                    </svg>
                  </div>
                  <h3 className="mb-2 text-sm font-bold text-white">
                    项目介绍
                  </h3>
                  <p className="text-xs leading-relaxed text-white/90">
                    基于 <span className="font-bold">Java</span> 开发的 AI
                    Agent 框架，从零构建一个功能完整的 AI 编程助手，深入理解
                    Agent 的核心机制。提供{" "}
                    <span className="font-bold">Anthropic API</span> 的完整兼容。
                  </p>
                </BentoCard>

                <BentoCard className="p-6" color="purple">
                  <div className="mb-4 flex h-11 w-11 items-center justify-center rounded-2xl bg-white/30">
                    <svg
                      className="h-5 w-5 text-white"
                      fill="none"
                      stroke="currentColor"
                      viewBox="0 0 24 24"
                    >
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeWidth={2}
                        d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
                      />
                    </svg>
                  </div>
                  <h3 className="mb-2 text-sm font-bold text-white">项目背景</h3>
                  <p className="text-xs leading-relaxed text-white/90">
                    完全参考开源项目{" "}
                    <a
                      href="https://github.com/shareAI-lab/learn-claude-code"
                      target="_blank"
                      rel="noopener noreferrer"
                      className="font-bold underline decoration-white/50 underline-offset-2"
                    >
                      learn-claude-code
                    </a>
                    ，使用 Java 重新实现，兼顾性能与企业级应用。
                  </p>
                </BentoCard>

                <BentoCard className="p-6 sm:col-span-3" color="app-teal">
                  <div className="flex flex-col gap-4 sm:flex-row sm:items-start">
                    <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-white/30">
                      <svg
                        className="h-5 w-5 text-white"
                        fill="none"
                        stroke="currentColor"
                        viewBox="0 0 24 24"
                      >
                        <path
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          strokeWidth={2}
                          d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z"
                        />
                      </svg>
                    </div>
                    <div>
                      <h3 className="mb-2 text-sm font-bold text-white">
                        项目特点
                      </h3>
                      <ul className="grid gap-1.5 text-xs leading-relaxed text-white/95 sm:grid-cols-2">
                        {[
                          ["递进式架构", "从单一工具调用到多工具协同，逐步引入新能力"],
                          ["统一基类", "ZQAgent 提供标准化的 Agent 循环"],
                          ["工具系统", "ToolDefinition + Schema，支持动态注册"],
                          ["上下文管理", "三层压缩策略（微压缩、自动压缩、手动压缩）"],
                          ["技能系统", "两层注入的 Skill 技能加载机制"],
                          ["任务管理", "基于 DAG 的任务图，支持依赖解析"],
                          ["后台执行", "守护线程后台任务 + 通知队列注入"],
                          ["MCP 协议", "支持 STDIO / SSE / Streamable HTTP"],
                          ["ReAct 模式", "Thought → Action → Observation 推理循环"],
                          ["Web 服务", "Spring Boot 集成，会话管理 + REST API"],
                        ].map(([term, desc]) => (
                          <li
                            key={term}
                            className="flex items-start gap-1.5"
                          >
                            <span className="mt-1 h-1.5 w-1.5 shrink-0 rounded-full bg-white" />
                            <span>
                              <span className="font-bold whitespace-nowrap">
                                {term}
                              </span>
                              ：{desc}
                            </span>
                          </li>
                        ))}
                      </ul>
                    </div>
                  </div>
                </BentoCard>
              </div>
            </ScrollReveal>
          </div>
        </section>

        {/* Recent Updates */}
        <section className="px-2">
          <div className="mx-auto max-w-5xl">
            <div className="mb-6 flex items-center gap-3">
              <Title size="small" color="app-yellow">
                最近更新
              </Title>
              <NewBadge>New</NewBadge>
            </div>
            <ScrollReveal animation="fade-up" staggerChildren stagger={0.1}>
              <div className="grid gap-4 sm:grid-cols-3">
                <Link href={`/${locale}/s02`} className="group block">
                  <BentoCard className="h-full p-5" pattern="app-blue">
                    <div className="mb-3 flex items-center gap-2">
                      <span className="inline-flex items-center rounded-full bg-white/40 px-2 py-0.5 text-xs font-bold text-[#3a3a5a]">
                        s02
                      </span>
                      <span className="text-xs font-semibold text-[#5a4a30]">
                        工具层
                      </span>
                    </div>
                    <h3 className="mb-1.5 text-sm font-bold text-[#5a4a30] group-hover:text-[#3a3a5a]">
                      新增文件搜索工具
                    </h3>
                    <p className="text-xs leading-relaxed text-[#6a5a40]">
                      增加了 <code className="font-mono text-[11px] font-bold">list_files</code>{" "}
                      和 <code className="font-mono text-[11px] font-bold">content_search</code>{" "}
                      两个工具，基于 ripgrep 实现高效的文件列表与内容搜索。
                    </p>
                  </BentoCard>
                </Link>

                <Link href={`/${locale}/s04`} className="group block">
                  <BentoCard className="h-full p-5" pattern="app-teal">
                    <div className="mb-3 flex items-center gap-2">
                      <span className="inline-flex items-center rounded-full bg-white/40 px-2 py-0.5 text-xs font-bold text-[#2a5a4a]">
                        s04
                      </span>
                      <span className="text-xs font-semibold text-[#2a5a4a]">
                        规划层
                      </span>
                    </div>
                    <h3 className="mb-1.5 text-sm font-bold text-[#2a5a4a] group-hover:text-[#1a4a3a]">
                      重写核心逻辑
                    </h3>
                    <p className="text-xs leading-relaxed text-[#3a6a5a]">
                      完全重写了子智能体{" "}
                      <code className="font-mono text-[11px] font-bold">SubAgent</code>、
                      <code className="font-mono text-[11px] font-bold">后台任务</code>、
                      <code className="font-mono text-[11px] font-bold">Skills</code>{" "}
                      的逻辑，提升任务委派与执行能力。
                    </p>
                  </BentoCard>
                </Link>

                <Link href={`/${locale}/s13`} className="group block">
                  <BentoCard className="h-full p-5" pattern="purple">
                    <div className="mb-3 flex flex-wrap items-center gap-2">
                      <span className="inline-flex items-center rounded-full bg-white/40 px-2 py-0.5 text-xs font-bold text-[#5a2a8a]">
                        s13
                      </span>
                      <NewBadge>MCP</NewBadge>
                      <span className="inline-flex items-center rounded-full bg-white/40 px-2 py-0.5 text-xs font-bold text-[#5a2a8a]">
                        s14
                      </span>
                      <NewBadge>ReAct</NewBadge>
                    </div>
                    <h3 className="mb-1.5 text-sm font-bold text-[#5a2a8a] group-hover:text-[#3a1a6a]">
                      MCP 协议 + ReAct 框架
                    </h3>
                    <p className="text-xs leading-relaxed text-[#6a4a9a]">
                      新增 MCP 协议章节，标准化 AI 与外部系统的连接；新增
                      ReAct 行为框架，通过「思考-行动-观察」循环增强推理能力。
                    </p>
                  </BentoCard>
                </Link>
              </div>
            </ScrollReveal>
          </div>
        </section>

        {/* Core Pattern Section */}
        <section className="px-2">
          <div className="mx-auto max-w-3xl">
            <div className="mb-6 text-center">
              <Title size="middle" color="app-orange">
                {t("core_pattern")}
              </Title>
              <p className="mt-4 text-sm text-[#8a7b66]">
                {t("core_pattern_desc")}
              </p>
            </div>
            <ScrollReveal animation="blur" duration={0.8}>
              <div className="overflow-hidden rounded-2xl border border-[#e8dcc8] bg-[#fbf7eb] shadow-sm">
                <div className="flex items-center gap-2 border-b border-[#e8dcc8] bg-[#f6efe0] px-4 py-2.5">
                  <span className="h-3 w-3 rounded-full bg-[#fc736d]" />
                  <span className="h-3 w-3 rounded-full bg-[#f7cd67]" />
                  <span className="h-3 w-3 rounded-full bg-[#8ac68a]" />
                  <span className="ml-3 text-xs font-semibold text-[#8a7b66]">
                    {codeLanguage === "java"
                      ? "AgentLoop.java"
                      : "agent_loop.py"}
                  </span>
                </div>
                <div className="flex border-b border-[#e8dcc8]">
                  <button
                    onClick={() => setCodeLanguage("java")}
                    className={cn(
                      "cursor-pointer px-4 py-2 text-xs font-bold transition-colors",
                      codeLanguage === "java"
                        ? "bg-[#19c8b9] text-white"
                        : "text-[#8a7b66] hover:bg-[#f0e8d8] hover:text-[#794f27]"
                    )}
                  >
                    Java
                  </button>
                  <button
                    onClick={() => setCodeLanguage("python")}
                    className={cn(
                      "cursor-pointer px-4 py-2 text-xs font-bold transition-colors",
                      codeLanguage === "python"
                        ? "bg-[#19c8b9] text-white"
                        : "text-[#8a7b66] hover:bg-[#f0e8d8] hover:text-[#794f27]"
                    )}
                  >
                    Python
                  </button>
                </div>
                <pre className="overflow-x-auto p-4 text-sm leading-relaxed">
                  <code
                    className={`hljs language-${codeLanguage}`}
                    dangerouslySetInnerHTML={{ __html: highlighted }}
                  />
                </pre>
              </div>
            </ScrollReveal>
          </div>
        </section>

        {/* Learning Path */}
        <section className="px-2">
          <div className="mx-auto max-w-6xl">
            <div className="mb-8 text-center">
              <Title size="middle" color="app-green">
                {t("learning_path")}
              </Title>
              <p className="mt-4 text-sm text-[#8a7b66]">
                {t("learning_path_desc")}
              </p>
            </div>
            <ScrollReveal animation="fade-up" staggerChildren stagger={0.06}>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
                {LEARNING_PATH.map((versionId) => {
                  const meta = VERSION_META[versionId];
                  const data = getVersionData(versionId);
                  if (!meta || !data) return null;
                  const layout = BENTO_LAYOUT[versionId];
                  const layerColor = LAYER_COLOR_BY_ID[meta.layer];
                  return (
                    <Link
                      key={versionId}
                      href={`/${locale}/${versionId}`}
                      className={cn(
                        "group block",
                        layout?.colSpan ? "sm:col-span-2" : undefined
                      )}
                    >
                      <BentoCard
                        className={cn("h-full p-5 hover:-translate-y-1")}
                      >
                        <div
                          className="mb-3 h-1 w-full rounded-full"
                          style={{ backgroundColor: layerColor }}
                        />
                        <div className="flex items-start justify-between gap-2">
                          <div className="flex items-center gap-1.5">
                            <LayerBadge layer={meta.layer}>
                              {versionId}
                            </LayerBadge>
                            {(versionId === "s13" || versionId === "s14") && (
                              <NewBadge>New</NewBadge>
                            )}
                          </div>
                          <span className="text-xs font-semibold tabular-nums text-[#9f927d]">
                            {data.loc} {t("loc")}
                          </span>
                        </div>
                        <h3
                          className="mt-3 text-sm font-bold transition-transform group-hover:scale-[1.02]"
                          style={{ color: "#3a3a5a" }}
                        >
                          {meta.title}
                        </h3>
                        <p className="mt-1.5 text-xs leading-relaxed text-[#725d42]">
                          {meta.keyInsight}
                        </p>
                      </BentoCard>
                    </Link>
                  );
                })}
              </div>
            </ScrollReveal>
          </div>
        </section>

        {/* Layer Overview */}
        <section className="px-2">
          <div className="mx-auto max-w-4xl">
            <div className="mb-6 text-center">
              <Title size="middle" color="purple">
                {t("layers_title")}
              </Title>
              <p className="mt-4 text-sm text-[#8a7b66]">
                {t("layers_desc")}
              </p>
            </div>
            <ScrollReveal animation="fade-up" staggerChildren stagger={0.12}>
              <div className="flex flex-col gap-3">
                {LAYERS.map((layer) => (
                  <Card key={layer.id} className="transition-transform hover:-translate-y-1">
                    <div className="flex items-center gap-4 p-1">
                      <div
                        className="h-12 w-1.5 self-stretch rounded-full"
                        style={{
                          backgroundColor: LAYER_COLOR_BY_ID[layer.id],
                        }}
                      />
                      <div className="flex-1">
                        <div className="flex items-center gap-2">
                          <h3 className="text-sm font-bold text-[#794f27]">
                            {layer.label}
                          </h3>
                          <span className="text-xs text-[#9f927d]">
                            {layer.versions.length} {t("versions_in_layer")}
                          </span>
                        </div>
                        <div className="mt-2 flex flex-wrap gap-1.5">
                          {layer.versions.map((vid) => {
                            const meta = VERSION_META[vid];
                            return (
                              <Link key={vid} href={`/${locale}/${vid}`}>
                                <span
                                  className="inline-flex cursor-pointer items-center rounded-full px-2.5 py-0.5 text-xs font-semibold text-white transition-transform hover:scale-105"
                                  style={{
                                    backgroundColor:
                                      LAYER_COLOR_BY_ID[layer.id],
                                  }}
                                >
                                  {vid}: {meta?.title}
                                </span>
                              </Link>
                            );
                          })}
                        </div>
                      </div>
                    </div>
                  </Card>
                ))}
              </div>
            </ScrollReveal>
          </div>
        </section>
      </div>
    </div>
  );
}
