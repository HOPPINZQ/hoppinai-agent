"use client";

import Link from "next/link";
import { useTranslations, useLocale } from "@/lib/i18n";
import { LEARNING_PATH, VERSION_META, LAYERS } from "@/lib/constants";
import { LayerBadge, NewBadge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import versionsData from "@/data/generated/versions.json";
import { useState } from "react";
import { LightRays } from "@/components/backgrounds/light-rays";
import { RotatingText } from "@/components/ui/rotating-text";
import BentoCard from "@/components/ui/bento-card";
import BentoGrid from "@/components/ui/bento-grid";
import ScrollFloat from "@/components/ui/scroll-float";
import ScrollReveal from "@/components/ui/scroll-reveal";

const LAYER_GLOW_COLORS: Record<string, string> = {
  tools: "from-blue-500/20 to-cyan-500/10",
  planning: "from-violet-500/20 to-purple-500/10",
  memory: "from-purple-500/20 to-fuchsia-500/10",
  concurrency: "from-amber-500/20 to-orange-500/10",
  collaboration: "from-rose-500/20 to-pink-500/10",
};

const LAYER_CARD_BORDER: Record<string, string> = {
  tools: "border-blue-500/20 hover:border-blue-400/40",
  planning: "border-violet-500/20 hover:border-violet-400/40",
  memory: "border-purple-500/20 hover:border-purple-400/40",
  concurrency: "border-amber-500/20 hover:border-amber-400/40",
  collaboration: "border-rose-500/20 hover:border-rose-400/40",
};

const LAYER_DOT_COLORS: Record<string, string> = {
  tools: "bg-blue-500",
  planning: "bg-violet-500",
  memory: "bg-purple-500",
  concurrency: "bg-amber-500",
  collaboration: "bg-rose-500",
};

const LAYER_TEXT_COLORS: Record<string, string> = {
  tools: "text-blue-400",
  planning: "text-violet-400",
  memory: "text-purple-400",
  concurrency: "text-amber-400",
  collaboration: "text-rose-400",
};

const LAYER_BADGE_DARK: Record<string, string> = {
  tools: "bg-blue-500/15 text-blue-300 border border-blue-500/20",
  planning: "bg-violet-500/15 text-violet-300 border border-violet-500/20",
  memory: "bg-purple-500/15 text-purple-300 border border-purple-500/20",
  concurrency: "bg-amber-500/15 text-amber-300 border border-amber-500/20",
  collaboration: "bg-rose-500/15 text-rose-300 border border-rose-500/20",
};

const LAYER_GLOW_RGB: Record<string, string> = {
  tools: "59, 130, 246",
  planning: "139, 92, 246",
  memory: "168, 85, 247",
  concurrency: "245, 158, 11",
  collaboration: "244, 63, 94",
};

const BENTO_LAYOUT: Record<string, { colSpan?: number }> = {
  s01: { colSpan: 2 },
  s05: { colSpan: 2 },
  s09: { colSpan: 2 },
  s12: { colSpan: 2 },
  s13: { colSpan: 2 },
  s14: { colSpan: 2 },
};

function getVersionData(id: string) {
  return versionsData.versions.find((v) => v.id === id);
}

export default function HomePage() {
  const t = useTranslations("home");
  const locale = useLocale();
  const [codeLanguage, setCodeLanguage] = useState<"java" | "python">("java");

  return (
    <div className="relative min-h-screen">
      {/* Light Rays Background */}
      <LightRays
        raysOrigin="top-center"
        raysColor="#8B5CF6"
        raysSpeed={0.8}
        lightSpread={1.5}
        rayLength={1.8}
        followMouse={true}
        mouseInfluence={0.15}
      />

      <div className="flex flex-col gap-20 pb-16">
            {/* Hero Section */}
            <section className="flex flex-col items-center px-2 pt-8 text-center sm:pt-20">
              <div className="inline-flex items-center gap-2 rounded-full border border-violet-500/20 bg-violet-500/10 px-4 py-1.5 text-sm font-medium text-violet-300">
                <span className="relative flex h-2 w-2">
                  <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-violet-400 opacity-75"></span>
                  <span className="relative inline-flex rounded-full h-2 w-2 bg-violet-500"></span>
                </span>
                Java AI Agent Framework
              </div>
              <h1 className="mt-6 text-3xl font-bold tracking-tight sm:text-5xl lg:text-6xl">
                <span className="bg-gradient-to-r from-white via-violet-200 to-purple-400 bg-clip-text text-transparent">
                  {t("hero_title")}
                </span>
              </h1>
              <p className="mt-4 flex items-center justify-center gap-1.5 text-base text-zinc-400 sm:text-xl">
                <span>从零构建</span>
                <RotatingText
                  texts={["AI 编程助手", "Agent 框架", "多智能体协作", "XX Claw"]}
                  rotationInterval={2500}
                  staggerDuration={0.03}
                  mainClassName="text-violet-400"
                  elementLevelClassName="font-bold"
                />
              </p>
              <div className="mt-8 flex flex-wrap items-center justify-center gap-4">
                <Link
                  href={`/${locale}/timeline`}
                  className="group relative inline-flex min-h-[44px] items-center gap-2 rounded-lg bg-gradient-to-r from-violet-600 to-purple-600 px-6 py-3 text-sm font-medium text-white shadow-lg shadow-violet-500/25 transition-all duration-300 hover:shadow-violet-500/40 hover:brightness-110"
                >
                  {t("start")}
                  <span
                    aria-hidden="true"
                    className="transition-transform group-hover:translate-x-0.5"
                  >
                    &rarr;
                  </span>
                </Link>
                <a
                  href="https://github.com/shareAI-lab/learn-claude-code"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex min-h-[44px] items-center gap-2 rounded-lg border border-white/10 bg-white/5 px-6 py-3 text-sm font-medium text-zinc-300 backdrop-blur-sm transition-all duration-200 hover:border-white/20 hover:bg-white/10 hover:text-white"
                >
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
                </a>
              </div>
            </section>

            {/* Feature Cards - Bento Layout */}
            <section className="px-2">
              <div className="mx-auto max-w-5xl">
                <ScrollReveal animation="fade-up" staggerChildren stagger={0.12}>
                <BentoGrid spotlightColor="rgba(139, 92, 246, 0.05)" spotlightRadius={400}>
                  <div className="grid gap-4 sm:grid-cols-3">
                    <BentoCard glowColor="59, 130, 246" className="p-6 sm:col-span-2">
                      <div className="mb-4 flex h-11 w-11 items-center justify-center rounded-xl bg-blue-500/10">
                        <svg className="h-5 w-5 text-blue-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                        </svg>
                      </div>
                      <h3 className="mb-2 text-sm font-semibold text-zinc-100">项目介绍</h3>
                      <p className="text-xs leading-relaxed text-zinc-500">
                        基于 <span className="text-blue-400">Java</span> 开发的 AI Agent 框架，从零构建一个功能完整的 AI
                        编程助手，深入理解 Agent 的核心机制。
                      </p>
                    </BentoCard>

                    <BentoCard glowColor="139, 92, 246" className="p-6">
                      <div className="mb-4 flex h-11 w-11 items-center justify-center rounded-xl bg-violet-500/10">
                        <svg className="h-5 w-5 text-violet-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                        </svg>
                      </div>
                      <h3 className="mb-2 text-sm font-semibold text-zinc-100">项目背景</h3>
                      <p className="text-xs leading-relaxed text-zinc-500">
                        灵感来自{" "}
                        <a href="https://github.com/shareAI-lab/learn-claude-code" target="_blank" rel="noopener noreferrer" className="text-violet-400 hover:underline">
                          shareAI-lab
                        </a>
                        ，使用 Java 重新实现，兼顾性能与企业级应用。
                      </p>
                    </BentoCard>

                    <BentoCard glowColor="16, 185, 129" className="p-6 sm:col-span-3">
                      <div className="flex flex-col sm:flex-row sm:items-start gap-4">
                        <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-emerald-500/10">
                          <svg className="h-5 w-5 text-emerald-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
                          </svg>
                        </div>
                        <div>
                          <h3 className="mb-2 text-sm font-semibold text-zinc-100">新增特性</h3>
                          <p className="text-xs leading-relaxed text-zinc-500">
                            扩展实现了 <span className="text-emerald-400">MCP 协议</span> 和{" "}
                            <span className="text-emerald-400">ReAct 框架</span>
                            ，增强 Agent 的工具调用与推理能力。增加了
                            <span className="text-emerald-400"> web </span>端的demo
                          </p>
                        </div>
                      </div>
                    </BentoCard>
                  </div>
                </BentoGrid>
                </ScrollReveal>
              </div>
            </section>

            {/* Recent Updates - Bento Layout */}
            <section className="px-2">
              <div className="mx-auto max-w-5xl">
                <div className="mb-6 flex items-center gap-3">
                  <ScrollFloat className="text-xl sm:text-2xl text-zinc-100">
                    最近更新
                  </ScrollFloat>
                  <span className="rounded-full bg-gradient-to-r from-pink-500 to-rose-500 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider text-white shadow-sm shadow-pink-500/30">
                    New
                  </span>
                </div>
                <ScrollReveal animation="fade-up" staggerChildren stagger={0.1}>
                <BentoGrid spotlightColor="rgba(139, 92, 246, 0.04)" spotlightRadius={350}>
                  <div className="grid gap-4 sm:grid-cols-3">
                    <Link href={`/${locale}/s02`} className="group block">
                      <BentoCard glowColor="59, 130, 246" className="p-5 h-full">
                        <div className="mb-3 flex items-center gap-2">
                          <span className="inline-flex items-center rounded-md bg-blue-500/15 px-2 py-0.5 text-xs font-medium text-blue-300 border border-blue-500/20">
                            s02
                          </span>
                          <span className="text-xs text-zinc-600">工具层</span>
                        </div>
                        <h3 className="mb-1.5 text-sm font-semibold text-zinc-200 group-hover:text-white">
                          新增文件搜索工具
                        </h3>
                        <p className="text-xs leading-relaxed text-zinc-500">
                          增加了{" "}
                          <code className="rounded bg-blue-500/10 px-1 py-0.5 font-mono text-[11px] text-blue-300">
                            list_files
                          </code>{" "}
                          和{" "}
                          <code className="rounded bg-blue-500/10 px-1 py-0.5 font-mono text-[11px] text-blue-300">
                            content_search
                          </code>{" "}
                          两个工具，基于 ripgrep 实现高效的文件列表与内容搜索。
                        </p>
                      </BentoCard>
                    </Link>

                    <Link href={`/${locale}/s04`} className="group block">
                      <BentoCard glowColor="16, 185, 129" className="p-5 h-full">
                        <div className="mb-3 flex items-center gap-2">
                          <span className="inline-flex items-center rounded-md bg-emerald-500/15 px-2 py-0.5 text-xs font-medium text-emerald-300 border border-emerald-500/20">
                            s04
                          </span>
                          <span className="text-xs text-zinc-600">规划层</span>
                        </div>
                        <h3 className="mb-1.5 text-sm font-semibold text-zinc-200 group-hover:text-white">
                          重写 SubAgent 逻辑
                        </h3>
                        <p className="text-xs leading-relaxed text-zinc-500">
                          完全重写了子代理的上下文隔离与任务委派机制，每个子任务使用独立的
                          messages[]，保持主对话清晰。
                        </p>
                      </BentoCard>
                    </Link>

                    <Link href={`/${locale}/s13`} className="group block">
                      <BentoCard glowColor="139, 92, 246" className="p-5 h-full">
                        <div className="mb-3 flex items-center gap-2">
                          <span className="inline-flex items-center rounded-md bg-violet-500/15 px-2 py-0.5 text-xs font-medium text-violet-300 border border-violet-500/20">
                            s13
                          </span>
                          <span className="rounded-full bg-gradient-to-r from-pink-500 to-rose-500 px-1.5 py-0 text-[10px] font-bold text-white">
                            MCP
                          </span>
                          <span className="inline-flex items-center rounded-md bg-violet-500/15 px-2 py-0.5 text-xs font-medium text-violet-300 border border-violet-500/20">
                            s14
                          </span>
                          <span className="rounded-full bg-gradient-to-r from-pink-500 to-rose-500 px-1.5 py-0 text-[10px] font-bold text-white">
                            ReAct
                          </span>
                        </div>
                        <h3 className="mb-1.5 text-sm font-semibold text-zinc-200 group-hover:text-white">
                          MCP 协议 + ReAct 框架
                        </h3>
                        <p className="text-xs leading-relaxed text-zinc-500">
                          新增 MCP 协议章节，标准化 AI 与外部系统的连接；新增 ReAct
                          行为框架，通过「思考-行动-观察」循环增强推理能力。
                        </p>
                      </BentoCard>
                    </Link>
                  </div>
                </BentoGrid>
                </ScrollReveal>
              </div>
            </section>

            {/* Core Pattern Section */}
            <section className="px-2">
              <div className="mx-auto max-w-3xl">
                <div className="mb-6 text-center">
                  <div className="text-2xl sm:text-3xl text-zinc-100">
                    {t("core_pattern")}
                  </div>
                  <p className="mt-2 text-sm text-zinc-500">
                    {t("core_pattern_desc")}
                  </p>
                </div>
                <ScrollReveal animation="blur" duration={1}>
                <div className="overflow-hidden rounded-2xl border border-white/[0.06] bg-[#0d0c1d]">
                  <div className="flex items-center gap-2 border-b border-white/[0.06] px-4 py-2.5">
                    <span className="h-3 w-3 rounded-full bg-red-500/70" />
                    <span className="h-3 w-3 rounded-full bg-yellow-500/70" />
                    <span className="h-3 w-3 rounded-full bg-green-500/70" />
                    <span className="ml-3 text-xs text-zinc-600">
                      {codeLanguage === "java"
                        ? "AgentLoop.java"
                        : "agent_loop.py"}
                    </span>
                  </div>
                  <div className="flex border-b border-white/[0.06]">
                    <button
                      onClick={() => setCodeLanguage("java")}
                      className={cn(
                        "cursor-pointer px-3 py-1.5 text-xs font-medium transition-all duration-200",
                        codeLanguage === "java"
                          ? "bg-white/[0.08] text-violet-300"
                          : "text-zinc-600 hover:text-zinc-400 hover:bg-white/[0.03]"
                      )}
                    >
                      Java
                    </button>
                    <button
                      onClick={() => setCodeLanguage("python")}
                      className={cn(
                        "cursor-pointer px-3 py-1.5 text-xs font-medium transition-all duration-200",
                        codeLanguage === "python"
                          ? "bg-white/[0.08] text-violet-300"
                          : "text-zinc-600 hover:text-zinc-400 hover:bg-white/[0.03]"
                      )}
                    >
                      Python
                    </button>
                  </div>
                  <pre className="overflow-x-auto p-4 text-sm leading-relaxed">
                    <code>
                      {codeLanguage === "java" ? (
                        <>
                          <span className="text-purple-400">while</span>
                          <span className="text-zinc-300"> </span>
                          <span className="text-orange-300">(true)</span>
                          <span className="text-zinc-600"> {"{"}</span>
                          {"\n"}
                          <span className="text-zinc-300">
                            {"    "}Message message ={" "}
                          </span>
                          <span className="text-blue-400">chatMessage</span>
                          <span className="text-zinc-600">
                            (messageParams);
                          </span>
                          {"\n"}
                          <span className="text-zinc-300">
                            {"    "}messageParams.
                          </span>
                          <span className="text-blue-400">add</span>
                          <span className="text-zinc-600">
                            (message.
                          </span>
                          <span className="text-blue-400">toParam</span>
                          <span className="text-zinc-600">());</span>
                          {"\n"}
                          <span className="text-zinc-300">
                            {"    "}List&lt;ContentBlockParam&gt; toolResults ={" "}
                          </span>
                          <span className="text-purple-400">new</span>
                          <span className="text-zinc-300">
                            {" "}
                            ArrayList&lt;&gt;();
                          </span>
                          {"\n"}
                          <span className="text-purple-400">
                            {"    "}boolean
                          </span>
                          <span className="text-zinc-300">
                            {" "}hasToolUse ={" "}
                          </span>
                          <span className="text-orange-300">false</span>
                          <span className="text-zinc-600">;</span>
                          {"\n"}
                          <span className="text-purple-400">{"    "}for</span>
                          <span className="text-zinc-300">
                            {" "}
                            (ContentBlock content : message.
                          </span>
                          <span className="text-blue-400">content</span>
                          <span className="text-zinc-600">()) {"{"}</span>
                          {"\n"}
                          <span className="text-purple-400">
                            {"        "}if
                          </span>
                          <span className="text-zinc-300">
                            {" "}(
                          </span>
                          <span className="text-zinc-300">content.</span>
                          <span className="text-blue-400">isText</span>
                          <span className="text-zinc-600">()) {"{"}</span>
                          {"\n"}
                          <span className="text-zinc-300">
                            {"            "}String result = content.
                          </span>
                          <span className="text-blue-400">text</span>
                          <span className="text-zinc-600">().</span>
                          <span className="text-blue-400">map</span>
                          <span className="text-zinc-600">
                            (TextBlock::text).
                          </span>
                          <span className="text-blue-400">orElse</span>
                          <span className="text-zinc-600">(</span>
                          <span className="text-green-400">""</span>
                          <span className="text-zinc-600">);</span>
                          {"\n"}
                          <span className="text-purple-400">
                            {"        "}
                            {"}"}
                          </span>
                          <span className="text-purple-400">
                            {" "}else if
                          </span>
                          <span className="text-zinc-300">
                            {" "}(
                          </span>
                          <span className="text-zinc-300">content.</span>
                          <span className="text-blue-400">isToolUse</span>
                          <span className="text-zinc-600">()) {"{"}</span>
                          {"\n"}
                          <span className="text-zinc-300">
                            {"            "}hasToolUse ={" "}
                          </span>
                          <span className="text-orange-300">true</span>
                          <span className="text-zinc-600">;</span>
                          {"\n"}
                          <span className="text-zinc-300">
                            {"            "}ToolUseBlock toolUse = content.
                          </span>
                          <span className="text-blue-400">asToolUse</span>
                          <span className="text-zinc-600">();</span>
                          {"\n"}
                          <span className="text-zinc-300">
                            {"            "}String toolResult ={" "}
                          </span>
                          <span className="text-blue-400">executeTool</span>
                          <span className="text-zinc-600">
                            (toolUse.
                          </span>
                          <span className="text-blue-400">name</span>
                          <span className="text-zinc-600">(), toolUse.</span>
                          <span className="text-blue-400">_input</span>
                          <span className="text-zinc-600">());</span>
                          {"\n"}
                          <span className="text-zinc-300">
                            {"            "}toolResults.
                          </span>
                          <span className="text-blue-400">add</span>
                          <span className="text-zinc-600">
                            (ContentBlockParam.
                          </span>
                          <span className="text-blue-400">ofToolResult</span>
                          <span className="text-zinc-600">(</span>
                          {"\n"}
                          <span className="text-zinc-300">
                            {"                "}ToolResultBlockParam.
                          </span>
                          <span className="text-blue-400">builder</span>
                          <span className="text-zinc-600">()</span>
                          {"\n"}
                          <span className="text-zinc-300">
                            {"                    "}.toolUseId(toolUse.
                          </span>
                          <span className="text-blue-400">id</span>
                          <span className="text-zinc-600">())</span>
                          {"\n"}
                          <span className="text-zinc-300">
                            {"                    "}.content(toolResult)
                          </span>
                          {"\n"}
                          <span className="text-zinc-300">
                            {"                    "}.
                          </span>
                          <span className="text-blue-400">build</span>
                          <span className="text-zinc-600">()));</span>
                          {"\n"}
                          <span className="text-zinc-300">
                            {"        "}
                            {"}"}
                          </span>
                          {"\n"}
                          <span className="text-zinc-300">
                            {"    "}
                            {"}"}
                          </span>
                          {"\n"}
                          <span className="text-purple-400">{"    "}if</span>
                          <span className="text-zinc-300">
                            {" "}(!hasToolUse){" "}
                          </span>
                          <span className="text-purple-400">break</span>
                          <span className="text-zinc-600">;</span>
                          {"\n"}
                          <span className="text-purple-400">
                            {"    "}MessageParam.Content
                          </span>
                          <span className="text-zinc-300">
                            {" "}content = MessageParam.Content.
                          </span>
                          <span className="text-blue-400">ofBlockParams</span>
                          <span className="text-zinc-300">(toolResults)</span>
                          <span className="text-zinc-600">;</span>
                          {"\n"}
                          <span className="text-purple-400">
                            {"    "}MessageParam
                          </span>
                          <span className="text-zinc-300">
                            {" "}toolResultMessage = MessageParam.
                          </span>
                          <span className="text-blue-400">builder</span>
                          <span className="text-zinc-600">()</span>
                          {"\n"}
                          <span className="text-zinc-300">
                            {"        "}.role(MessageParam.Role.USER)
                          </span>
                          {"\n"}
                          <span className="text-zinc-300">
                            {"        "}.content(toolResults).
                          </span>
                          <span className="text-blue-400">build</span>
                          <span className="text-zinc-600">();</span>
                          {"\n"}
                          <span className="text-zinc-300">
                            {"    "}messageParams.
                          </span>
                          <span className="text-blue-400">add</span>
                          <span className="text-zinc-600">
                            (toolResultMessage);
                          </span>
                          {"\n"}
                          <span className="text-zinc-300">{"}"}</span>
                        </>
                      ) : (
                        <>
                          <span className="text-purple-400">while</span>
                          <span className="text-zinc-300"> </span>
                          <span className="text-orange-300">True</span>
                          <span className="text-zinc-600">:</span>
                          {"\n"}
                          <span className="text-zinc-300">
                            {"    "}response = client.messages.
                          </span>
                          <span className="text-blue-400">create</span>
                          <span className="text-zinc-600">(</span>
                          <span className="text-zinc-300">messages=</span>
                          <span className="text-zinc-300">messages</span>
                          <span className="text-zinc-600">,</span>
                          <span className="text-zinc-300"> tools=</span>
                          <span className="text-zinc-300">tools</span>
                          <span className="text-zinc-600">)</span>
                          {"\n"}
                          <span className="text-purple-400">{"    "}if</span>
                          <span className="text-zinc-300">
                            {" "}response.stop_reason !={" "}
                          </span>
                          <span className="text-green-400">
                            &quot;tool_use&quot;
                          </span>
                          <span className="text-zinc-600">:</span>
                          {"\n"}
                          <span className="text-purple-400">
                            {"        "}break
                          </span>
                          {"\n"}
                          <span className="text-purple-400">{"    "}for</span>
                          <span className="text-zinc-300"> tool_call </span>
                          <span className="text-purple-400">in</span>
                          <span className="text-zinc-300"> response.content</span>
                          <span className="text-zinc-600">:</span>
                          {"\n"}
                          <span className="text-zinc-300">
                            {"        "}result ={" "}
                          </span>
                          <span className="text-blue-400">execute_tool</span>
                          <span className="text-zinc-600">(</span>
                          <span className="text-zinc-300">tool_call.name</span>
                          <span className="text-zinc-600">,</span>
                          <span className="text-zinc-300"> tool_call.input</span>
                          <span className="text-zinc-600">)</span>
                          {"\n"}
                          <span className="text-zinc-300">
                            {"        "}messages.
                          </span>
                          <span className="text-blue-400">append</span>
                          <span className="text-zinc-600">(</span>
                          <span className="text-zinc-300">result</span>
                          <span className="text-zinc-600">)</span>
                        </>
                      )}
                    </code>
                  </pre>
                </div>
                </ScrollReveal>
              </div>
            </section>

            {/* Learning Path - Bento Grid Showcase */}
            <section className="px-2">
              <div className="mx-auto max-w-6xl">
                <div className="mb-8 text-center">
                  <div className="text-2xl sm:text-3xl text-zinc-100">
                    {t("learning_path")}
                  </div>
                  <p className="mt-2 text-sm text-zinc-500">
                    {t("learning_path_desc")}
                  </p>
                </div>
                <ScrollReveal animation="fade-up" staggerChildren stagger={0.06}>
                <BentoGrid spotlightColor="rgba(139, 92, 246, 0.04)" spotlightRadius={500}>
                  <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
                    {LEARNING_PATH.map((versionId) => {
                      const meta = VERSION_META[versionId];
                      const data = getVersionData(versionId);
                      if (!meta || !data) return null;
                      const layout = BENTO_LAYOUT[versionId];
                      return (
                        <Link
                          key={versionId}
                          href={`/${locale}/${versionId}`}
                          className="group block"
                          style={layout?.colSpan ? { gridColumn: `span ${layout.colSpan}` } : undefined}
                        >
                          <BentoCard
                            glowColor={LAYER_GLOW_RGB[meta.layer]}
                            className={cn(
                              "p-5 h-full",
                              LAYER_CARD_BORDER[meta.layer]
                            )}
                            enableTilt
                            enableGlow
                            enableParticles
                          >
                            <div className="flex items-start justify-between gap-2">
                              <div className="flex items-center gap-1.5">
                                <span
                                  className={cn(
                                    "inline-flex items-center rounded-md px-2 py-0.5 text-xs font-medium",
                                    LAYER_BADGE_DARK[meta.layer]
                                  )}
                                >
                                  {versionId}
                                </span>
                                {(versionId === "s13" || versionId === "s14") && (
                                  <span className="rounded-full bg-gradient-to-r from-pink-500 to-rose-500 px-1.5 py-0 text-[10px] font-bold text-white">
                                    New
                                  </span>
                                )}
                              </div>
                              <span className="text-xs tabular-nums text-zinc-600">
                                {data.loc} {t("loc")}
                              </span>
                            </div>
                            <h3
                              className={cn(
                                "mt-3 text-sm font-semibold text-zinc-200 group-hover:text-white",
                                LAYER_TEXT_COLORS[meta.layer],
                                "group-hover:brightness-125"
                              )}
                            >
                              {meta.title}
                            </h3>
                            <p className="mt-1.5 text-xs leading-relaxed text-zinc-500">
                              {meta.keyInsight}
                            </p>
                          </BentoCard>
                        </Link>
                      );
                    })}
                  </div>
                </BentoGrid>
                </ScrollReveal>
              </div>
            </section>

            {/* Layer Overview */}
            <section className="px-2">
              <div className="mx-auto max-w-4xl">
                <div className="mb-6 text-center">
                  <div className="text-2xl sm:text-3xl text-zinc-100">
                    {t("layers_title")}
                  </div>
                  <p className="mt-2 text-sm text-zinc-500">
                    {t("layers_desc")}
                  </p>
                </div>
                <ScrollReveal animation="fade-up" staggerChildren stagger={0.12}>
                <div className="flex flex-col gap-3">
                  {LAYERS.map((layer) => (
                    <div
                      key={layer.id}
                      className="flex items-center gap-4 rounded-2xl border border-white/[0.06] bg-white/[0.02] p-4 backdrop-blur-sm"
                    >
                      <div
                        className={cn(
                          "h-full w-1.5 self-stretch rounded-full",
                          LAYER_DOT_COLORS[layer.id]
                        )}
                      />
                      <div className="flex-1">
                        <div className="flex items-center gap-2">
                          <h3 className="text-sm font-semibold text-zinc-200">
                            {layer.label}
                          </h3>
                          <span className="text-xs text-zinc-600">
                            {layer.versions.length} {t("versions_in_layer")}
                          </span>
                        </div>
                        <div className="mt-2 flex flex-wrap gap-1.5">
                          {layer.versions.map((vid) => {
                            const meta = VERSION_META[vid];
                            return (
                              <Link key={vid} href={`/${locale}/${vid}`}>
                                <span
                                  className={cn(
                                    "inline-flex cursor-pointer items-center rounded-md px-2 py-0.5 text-xs font-medium transition-opacity hover:opacity-80",
                                    LAYER_BADGE_DARK[layer.id]
                                  )}
                                >
                                  {vid}: {meta?.title}
                                </span>
                              </Link>
                            );
                          })}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
                </ScrollReveal>
              </div>
            </section>
        </div>
      </div>
  );
}
