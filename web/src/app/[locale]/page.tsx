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
import hljs from "highlight.js";
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
                      <h3 className="mb-3 text-sm font-bold text-white">
                        项目特点
                      </h3>
                      {[
                        {
                          label: "工具与执行",
                          color: "#889df0",
                          items: [
                            ["工具系统", "ToolDefinition + Schema，支持动态注册"],
                            ["权限系统", "denyList → permissionRules → askUser 三道闸"],
                            ["钩子机制", "PreToolUse / PostToolUse / Stop 可插拔扩展点"],
                            ["ReAct 模式", "Thought → Action → Observation 推理循环"],
                            ["MCP 协议", "支持 STDIO / SSE / Streamable HTTP"],
                            ["CLI 可视化 Agent", "纯命令行 ANSI 面板渲染，双模式支持"],
                          ]
                        },
                        {
                          label: "规划与协调",
                          color: "#f0a8b8",
                          items: [
                            ["递进式架构", "从单一工具调用到多工具协同，逐步引入新能力"],
                            ["统一基类", "ZQAgent 提供标准化的 Agent 循环"],
                            ["技能系统", "两层注入的 Skill 技能加载机制"],
                            ["任务管理", "基于 DAG 的任务图，支持依赖解析"],
                            ["动态系统提示", "PromptAssembler 按 AgentContext 装配并缓存"],
                            ["Web 服务", "Spring Boot 集成，会话管理 + REST API"],
                            ["Agent 团队", "lead / teammate 文件邮箱异步协作"],
                            ["团队协议", "shutdown / plan_approval 状态机协商"],
                            ["自主 Agent", "IdlePoller 扫任务板，自动认领"],
                          ]
                        },
                        {
                          label: "记忆管理",
                          color: "#b77dee",
                          items: [
                            ["上下文管理", "三层压缩策略（微压缩、自动压缩、手动压缩）"],
                            ["持久化记忆", "YAML 索引 + 分类型 .memory/*.md，跨会话保留偏好"],
                            ["错误恢复", "ErrorClassifier 分类 + RetryWrapper 指数退避"],
                          ]
                        },
                        {
                          label: "并发",
                          color: "#e59266",
                          items: [
                            ["后台执行", "守护线程后台任务 + 通知队列注入"],
                            ["定时调度", "Cron 双线程解耦触发与执行"],
                            ["Worktree 隔离", "ThreadLocal 路由，多 agent 互不干扰"],
                          ]
                        },
                        {
                          label: "生产化",
                          color: "#f7cd67",
                          items: [
                            ["多模态输入", "MessageContent 多类型消息管道，read_file 自动 base64"],
                            ["安全沙箱", "4 层沙箱防护（L1 静态分析 → L2 目录监禁 → L3 OS → L4 容器）"],
                            ["综合集成", "27 工具 + 全部基础设施塞进同一个循环"],
                          ]
                        },
                      ].map((group) => (
                        <div key={group.label} className="mb-3 last:mb-0">
                          <div className="mb-1.5 flex items-center gap-1.5">
                            <span
                              className="inline-block h-2 w-2 rounded-full"
                              style={{ backgroundColor: group.color }}
                            />
                            <span
                              className="text-[11px] font-bold tracking-wider uppercase"
                              style={{ color: group.color }}
                            >
                              {group.label}
                            </span>
                          </div>
                          <div className="grid gap-1.5 sm:grid-cols-2 lg:grid-cols-3">
                            {group.items.map(([term, desc]) => (
                              <div
                                key={term}
                                className="flex items-start gap-2 rounded-xl bg-white/15 px-3 py-2 backdrop-blur-sm transition-colors hover:bg-white/25"
                              >
                                <span
                                  className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full"
                                  style={{ backgroundColor: group.color }}
                                />
                                <span className="text-xs leading-relaxed text-black/75">
                                  <span className="font-semibold whitespace-nowrap text-black/85">
                                    {term}
                                  </span>
                                  ：{desc}
                                </span>
                              </div>
                            ))}
                          </div>
                        </div>
                      ))}
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
              <div className="grid gap-4 mb-4 sm:grid-cols-3">
                <Link href={`/${locale}/cli`} className="group block">
                  <BentoCard className="h-full p-5" pattern="app-yellow">
                    <div className="mb-3 flex items-center gap-2">
                      <span className="inline-flex items-center rounded-full bg-white/40 px-2 py-0.5 text-xs font-bold text-[#7a5a1a]">
                        cli
                      </span>
                      <NewBadge>CLI</NewBadge>
                      <span className="text-xs font-semibold text-[#7a5a1a]">
                        工具层
                      </span>
                    </div>
                    <h3 className="mb-1.5 text-sm font-bold text-[#7a5a1a] group-hover:text-[#5a3a0a]">
                      CLI 可视化 Agent
                    </h3>
                    <p className="text-xs leading-relaxed text-[#8a6a2a]">
                      新增纯命令行模块，ANSI box-drawing 面板渲染 Agent 思考/工具调用/结果/耗时，
                      去掉 Spring Boot/MySQL 依赖，ReAct 与原生 tool_use 双模式支持。
                    </p>
                  </BentoCard>
                </Link>

                <Link href={`/${locale}/s22`} className="group block">
                  <BentoCard className="h-full p-5" pattern="app-yellow">
                    <div className="mb-3 flex items-center gap-2">
                      <span className="inline-flex items-center rounded-full bg-white/40 px-2 py-0.5 text-xs font-bold text-[#7a5a1a]">
                        s22
                      </span>
                      <NewBadge>NEW</NewBadge>
                      <span className="text-xs font-semibold text-[#7a5a1a]">
                        生产化
                      </span>
                    </div>
                    <h3 className="mb-1.5 text-sm font-bold text-[#7a5a1a] group-hover:text-[#5a3a0a]">
                      多模态输入
                    </h3>
                    <p className="text-xs leading-relaxed text-[#8a6a2a]">
                      新增 MessageContent 多类型消息管道，read_file 自动检测图片并 base64 编码，
                      screenshot 截屏工具，视觉 API 适配与降级策略。
                    </p>
                  </BentoCard>
                </Link>

                <Link href={`/${locale}/s23`} className="group block">
                  <BentoCard className="h-full p-5" pattern="app-yellow">
                    <div className="mb-3 flex items-center gap-2">
                      <span className="inline-flex items-center rounded-full bg-white/40 px-2 py-0.5 text-xs font-bold text-[#7a5a1a]">
                        s23
                      </span>
                      <NewBadge>NEW</NewBadge>
                      <span className="text-xs font-semibold text-[#7a5a1a]">
                        生产化
                      </span>
                    </div>
                    <h3 className="mb-1.5 text-sm font-bold text-[#7a5a1a] group-hover:text-[#5a3a0a]">
                      安全沙箱
                    </h3>
                    <p className="text-xs leading-relaxed text-[#8a6a2a]">
                      新增四层 bash 沙箱防护：L1 静态分析黑名单 → L2 目录监禁 → L3 bwrap OS 沙箱 → L4 docker 容器隔离，
                      与 s09 权限 + s10 钩子形成完整安全链。
                    </p>
                  </BentoCard>
                </Link>

                <Link href={`/${locale}/s01`} className="group block">
                  <BentoCard className="h-full p-5" pattern="app-yellow">
                    <div className="mb-3 flex items-center gap-2">
                      <span className="inline-flex items-center rounded-full bg-white/40 px-2 py-0.5 text-xs font-bold text-[#7a5a1a]">
                        s01
                      </span>
                      <NewBadge>NEW</NewBadge>
                      <span className="text-xs font-semibold text-[#7a5a1a]">
                        会话机制
                      </span>
                    </div>
                    <h3 className="mb-1.5 text-sm font-bold text-[#7a5a1a] group-hover:text-[#5a3a0a]">
                      sessionId 会话持久化
                    </h3>
                    <p className="text-xs leading-relaxed text-[#8a6a2a]">
                      新增 SessionManager 编排器：每次对话生成 sessionId，消息自动落盘到 .sessions/&lt;id&gt;.json；
                      启动时按 sessionId 恢复历史回放到上下文，原子写入 + 路径白名单安全防护。
                    </p>
                  </BentoCard>
                </Link>

                <Link href={`/${locale}/s01`} className="group block">
                  <BentoCard className="h-full p-5" pattern="app-yellow">
                    <div className="mb-3 flex items-center gap-2">
                      <span className="inline-flex items-center rounded-full bg-white/40 px-2 py-0.5 text-xs font-bold text-[#7a5a1a]">
                        s01
                      </span>
                      <NewBadge>NEW</NewBadge>
                      <span className="text-xs font-semibold text-[#7a5a1a]">
                        工具循环
                      </span>
                    </div>
                    <h3 className="mb-1.5 text-sm font-bold text-[#7a5a1a] group-hover:text-[#5a3a0a]">
                      工具调用 stop_reason 驱动
                    </h3>
                    <p className="text-xs leading-relaxed text-[#8a6a2a]">
                      对齐官方 canonical agentic loop：循环退出条件改用 stop_reason == TOOL_USE，
                      修复 JsonValue 序列化 bug（JsonNode 中转），新增 MAX_TOKENS 截断警告，
                      抽出 printText/executeToolCalls 让纯文本回复不再被吞。
                    </p>
                  </BentoCard>
                </Link>
              </div>
            </ScrollReveal>
          </div>
        </section>

        {/* V2 Major Update */}
        <section className="px-2">
          <div className="mx-auto max-w-5xl">
            <div className="mb-6 flex items-center gap-3">
              <Title size="small" color="app-orange">
                v2 大版本更新
              </Title>
              <NewBadge>v2</NewBadge>
            </div>

            {/* Stats banner */}
            <ScrollReveal animation="fade-up">
              <div className="mb-4 grid grid-cols-2 gap-3 rounded-2xl border border-[#e8dcc8] bg-[#fbf7eb] p-4 sm:grid-cols-4">
                {[
                  ["13", "port 可视化"],
                  ["3", "新增章节 (s21-s23)"],
                  ["200+", "UI 文案翻译"],
                  ["8", "扩展项目特点"],
                ].map(([num, label]) => (
                  <div key={label} className="text-center">
                    <div className="font-mono text-2xl font-black text-[#794f27]">
                      {num}
                    </div>
                    <div className="mt-0.5 text-[11px] font-medium text-[#8a7b66]">
                      {label}
                    </div>
                  </div>
                ))}
              </div>
            </ScrollReveal>

            {/* Former updates */}
            <div className="mb-3 flex items-center gap-2">
              <span className="text-xs font-bold tracking-wider text-[#8a7b66] uppercase">
                以往更新
              </span>
              <div className="h-px flex-1 bg-[#e8dcc8]" />
            </div>
            <ScrollReveal animation="fade-up" staggerChildren stagger={0.1}>
              <div className="mb-6 grid gap-4 sm:grid-cols-3">
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
                      增加了 <code className="font-mono text-[11px] font-bold">glob</code>{" "}
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
                      ReAct 行为模式，通过「思考-行动-观察」循环增强推理能力。
                    </p>
                  </BentoCard>
                </Link>
              </div>
            </ScrollReveal>

            <ScrollReveal animation="fade-up" staggerChildren stagger={0.08}>
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                <Link href={`/${locale}/s09`} className="group block">
                  <BentoCard className="h-full p-5" pattern="app-blue">
                    <div className="mb-3 flex flex-wrap items-center gap-2">
                      <span className="inline-flex items-center rounded-full bg-white/40 px-2 py-0.5 text-xs font-bold text-[#3a3a5a]">
                        s07 — s20
                      </span>
                      <NewBadge>14 viz</NewBadge>
                    </div>
                    <h3 className="mb-1.5 text-sm font-bold text-[#5a4a30] group-hover:text-[#3a3a5a]">
                      可视化大对齐
                    </h3>
                    <p className="text-xs leading-relaxed text-[#6a5a40]">
                      重写 <code className="font-mono text-[11px] font-bold">s07-s20</code>{" "}
                      共 14 个版本的概念可视化：每个版本号终于对上自己的主题（权限、钩子、记忆、错误恢复、cron、团队协议、自主
                      Agent、worktree 隔离等）。
                    </p>
                  </BentoCard>
                </Link>

                <Link href={`/${locale}/s21`} className="group block">
                  <BentoCard className="h-full p-5" pattern="app-teal">
                    <div className="mb-3 flex flex-wrap items-center gap-2">
                      <span className="inline-flex items-center rounded-full bg-white/40 px-2 py-0.5 text-xs font-bold text-[#2a5a4a]">
                        s21
                      </span>
                      <NewBadge>NEW</NewBadge>
                      <span className="text-xs font-semibold text-[#2a5a4a]">协作层</span>
                    </div>
                    <h3 className="mb-1.5 text-sm font-bold text-[#2a5a4a] group-hover:text-[#1a4a3a]">
                      综合集成
                    </h3>
                    <p className="text-xs leading-relaxed text-[#3a6a5a]">
                      新增 <code className="font-mono text-[11px] font-bold">s21</code>：把前面所有机制塞进同一个 agent
                      loop。7 阶段流水线动画演示"机制可以很多，循环只有一个"。
                    </p>
                  </BentoCard>
                </Link>

                <BentoCard className="h-full p-5" pattern="app-yellow">
                  <div className="mb-3 flex flex-wrap items-center gap-2">
                    <span className="inline-flex items-center rounded-full bg-white/40 px-2 py-0.5 text-xs font-bold text-[#7a5a1a]">
                      s09 / s10 / s11 / s12
                    </span>
                    <NewBadge>FIX</NewBadge>
                  </div>
                  <h3 className="mb-1.5 text-sm font-bold text-[#7a5a1a]">
                    主题错配修正
                  </h3>
                  <p className="text-xs leading-relaxed text-[#8a6a2a]">
                    历史遗留的 4 个错配可视化被替换：s09 团队 → 权限系统、s10 协议 →
                    钩子机制、s11 自主 → 持久化记忆、s12 worktree → 错误恢复。每个版本号现在讲述自己的故事。
                  </p>
                </BentoCard>

                <BentoCard className="h-full p-5" pattern="purple">
                  <div className="mb-3 flex flex-wrap items-center gap-2">
                    <span className="inline-flex items-center rounded-full bg-white/40 px-2 py-0.5 text-xs font-bold text-[#5a2a8a]">
                      全部组件
                    </span>
                    <NewBadge>i18n</NewBadge>
                  </div>
                  <h3 className="mb-1.5 text-sm font-bold text-[#5a2a8a]">
                    全部中文化
                  </h3>
                  <p className="text-xs leading-relaxed text-[#6a4a9a]">
                    13 个新可视化合计 200+ 条 UI
                    文案翻译：步骤标题、状态标签（待处理/进行中/完成）、SVG 内嵌文字、图例、状态机字段名、提示信息全部本地化。
                  </p>
                </BentoCard>

                <Link href={`/${locale}/layers`} className="group block">
                  <BentoCard className="h-full p-5" pattern="app-pink">
                    <div className="mb-3 flex flex-wrap items-center gap-2">
                      <span className="inline-flex items-center rounded-full bg-white/40 px-2 py-0.5 text-xs font-bold text-[#8a3a4a]">
                        collaboration
                      </span>
                      <NewBadge>+1</NewBadge>
                    </div>
                    <h3 className="mb-1.5 text-sm font-bold text-[#8a3a4a] group-hover:text-[#6a1a2a]">
                      协作层扩容
                    </h3>
                    <p className="text-xs leading-relaxed text-[#9a4a5a]">
                      s21 综合集成加入协作层，让"协作"分组从
                      s17/s18/s19 三节扩展到 4 节。layers 页、侧边栏分组、首页 Bento 全部同步纳入。
                    </p>
                  </BentoCard>
                </Link>

                <Link href={`/${locale}/timeline`} className="group block">
                  <BentoCard className="h-full p-5" pattern="app-green">
                    <div className="mb-3 flex flex-wrap items-center gap-2">
                      <span className="inline-flex items-center rounded-full bg-white/40 px-2 py-0.5 text-xs font-bold text-[#2a5a3a]">
                        全站
                      </span>
                      <NewBadge>UX</NewBadge>
                    </div>
                    <h3 className="mb-1.5 text-sm font-bold text-[#2a5a3a] group-hover:text-[#1a4a2a]">
                      学习体验优化
                    </h3>
                    <p className="text-xs leading-relaxed text-[#3a6a4a]">
                      侧边栏支持滚动（21 个版本不再溢出视口）；首页"项目特点"从 10 项扩展到 21 项，3 列布局更紧凑；
                      s15-s20 此前 Hero 区空白，全部补齐。
                    </p>
                  </BentoCard>
                </Link>
              </div>
            </ScrollReveal>

            {/* Detailed changelog */}
            <ScrollReveal animation="fade-up">
              <div className="mt-4 rounded-2xl border border-[#e8dcc8] bg-[#fbf7eb] p-5">
                <div className="mb-3 flex items-center gap-2">
                  <span className="text-sm font-bold text-[#794f27]">详细变更日志</span>
                  <span className="rounded-full bg-[#fc736d] px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider text-white whitespace-nowrap">
                    changelog
                  </span>
                </div>
                <ul className="grid gap-2 text-xs leading-relaxed text-[#725d42] sm:grid-cols-2">
                  {[
                    ["port", "s07-task-system / s08-background-tasks 覆盖重写"],
                    ["new", "s09-permission 三道闸可视化（denyList → rules → askUser）"],
                    ["new", "s10-hooks 钩子工作台（PreToolUse/PostToolUse/Stop）"],
                    ["new", "s11-memory 记忆图书馆（YAML 索引 + .memory/*.md）"],
                    ["new", "s12-error-recovery 错误恢复路径（4 类 + 重试退避）"],
                    ["port", "s13-mcp 工具桥（内置工具箱 / 外部 MCP / 调用本）"],
                    ["new", "s15-system-prompt 运行时提示组装 + 缓存键"],
                    ["new", "s16-cron-scheduler 每周时钟 + 双线程队列"],
                    ["new", "s17-agent-teams 主管/编码员/审查员 .jsonl 邮箱"],
                    ["new", "s18-team-protocols 关停协议 / 方案审批切换"],
                    ["new", "s19-autonomous-agents 自主工作看板（计时器+认领）"],
                    ["new", "s20-worktree-task-isolation 任务板 + worktree 索引 + 车道"],
                    ["new", "s21-comprehensive 综合智能体一轮（7 阶段流水线）"],
                    ["new", "s22-multimodal 多模态输入（MessageContent + screenshot）"],
                    ["new", "s23-sandbox 四层沙箱防御（静态分析→目录监禁→OS→容器）"],
                    ["new", "cli CLI 可视化 Agent（ANSI 面板渲染，无 Spring 依赖）"],
                    ["fix", "registry 13 处 lazy import 修正，build 通过"],
                    ["ux", "侧边栏 max-h + overflow-y-auto，21 版本不再溢出"],
                    ["ux", "首页项目特点 10 → 21 项，sm:2 列 → lg:3 列"],
                  ].map(([tag, desc]) => (
                    <li key={desc} className="flex items-start gap-2">
                      <span
                        className={cn(
                          "mt-0.5 shrink-0 rounded px-1.5 py-0.5 font-mono text-[10px] font-bold uppercase",
                          tag === "new" && "bg-[#19c8b9]/20 text-[#0e8a7f]",
                          tag === "port" && "bg-[#889df0]/30 text-[#3a3a5a]",
                          tag === "fix" && "bg-[#fc736d]/20 text-[#a04030]",
                          tag === "ux" && "bg-[#f7cd67]/40 text-[#7a5a1a]"
                        )}
                      >
                        {tag}
                      </span>
                      <span className="min-w-0 break-words">{desc}</span>
                    </li>
                  ))}
                </ul>
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
