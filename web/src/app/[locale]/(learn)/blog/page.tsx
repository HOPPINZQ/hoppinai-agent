"use client";

import { BlogCard } from "@/components/blog/blog-card";
import { Github, Twitter, Mail } from "lucide-react";

const BLOG_POSTS = [
  {
    id: 1,
    title: "AI 编程智能体：从零开始的探索之旅",
    excerpt:
      "深入探讨如何从基础循环构建出一个完整的 AI 编程智能体，了解每个阶段的设计决策和技术挑战。",
    date: "2024-03-14",
    tags: ["AI", "Agent", "Claude"],
    readTime: "8 分钟",
  },
  {
    id: 2,
    title: "工具系统设计：为什么 Bash 就足够了",
    excerpt:
      "探讨为什么在构建 AI Agent 时，简单的 Bash 工具比复杂的工具集更有效，以及最小化设计的价值。",
    date: "2024-03-13",
    tags: ["Design", "Tools", "Bash"],
    readTime: "5 分钟",
  },
  {
    id: 3,
    title: "上下文压缩：让 Agent 长期工作的关键",
    excerpt:
      "介绍如何通过智能的上下文压缩策略，让 AI Agent 能够在不失去连贯性的情况下处理长期任务。",
    date: "2024-03-12",
    tags: ["Memory", "Context", "Optimization"],
    readTime: "10 分钟",
  },
  {
    id: 4,
    title: "多 Agent 协作：从单兵作战到团队协作",
    excerpt:
      "讲解如何设计多 Agent 系统，实现团队成员之间的有效通信和任务协调。",
    date: "2024-03-11",
    tags: ["Collaboration", "Teams", "Protocol"],
    readTime: "12 分钟",
  },
  {
    id: 5,
    title: "任务系统：复杂任务的分解与执行",
    excerpt:
      "深入分析任务系统的设计，如何将复杂任务分解为可管理的小任务，并处理任务间的依赖关系。",
    date: "2024-03-10",
    tags: ["Planning", "Tasks", "Workflow"],
    readTime: "9 分钟",
  },
  {
    id: 6,
    title: "Worktree 隔离：安全的多任务执行",
    excerpt:
      "介绍如何使用 Git worktree 实现任务隔离，确保多个任务同时执行时不会相互干扰。",
    date: "2024-03-09",
    tags: ["Git", "Isolation", "Safety"],
    readTime: "7 分钟",
  },
];

export default function BlogPage() {
  return (
    <div className="min-h-screen">
      <div className="py-16 md:py-24">
        <div className="relative mx-auto max-w-4xl px-4 text-center sm:px-6 lg:px-8">
          <h1 className="mb-6 text-4xl font-extrabold tracking-tight text-[#794f27] md:text-6xl">
            我的博客
          </h1>
          <p className="mx-auto max-w-2xl text-lg leading-relaxed text-[#8a7b66] md:text-xl">
            分享 AI、编程和技术探索的思考与见解
          </p>
        </div>
      </div>

      <div className="mx-auto max-w-5xl px-4 py-12 sm:px-6 lg:px-8">
        {/* About card */}
        <div className="mb-16 animate-fade-in">
          <div className="relative overflow-hidden rounded-3xl border-2 border-[#e8dcc8] bg-[#fbf7eb] p-8 md:p-10">
            <div className="relative flex flex-col items-center gap-8 md:flex-row md:items-start">
              <div className="shrink-0">
                <div className="flex h-24 w-24 items-center justify-center rounded-full bg-gradient-to-br from-[#19c8b9] to-[#82d5bb] text-4xl shadow-lg md:h-28 md:w-28 md:text-5xl">
                  👨‍💻
                </div>
              </div>

              <div className="flex-1 text-center md:text-left">
                <h2 className="mb-4 text-2xl font-extrabold text-[#794f27] md:text-3xl">
                  关于我
                </h2>
                <div className="mb-6 space-y-3">
                  <p className="leading-relaxed text-[#725d42]">
                    我是一名热爱 AI 和编程的技术爱好者。热衷于探索大语言模型的应用，特别是
                    AI 编程智能体的构建与实践。
                  </p>
                  <p className="leading-relaxed text-[#725d42]">
                    在这个博客中，我会分享我在构建 AI Agent
                    过程中的学习心得、技术探索和项目经验。希望这些内容能够帮助你更好地理解
                    AI 编程的世界。
                  </p>
                </div>

                <div className="flex items-center justify-center gap-4 md:justify-start">
                  <a
                    href="https://github.com"
                    target="_blank"
                    rel="noopener noreferrer"
                    className="rounded-2xl border-2 border-[#e8dcc8] bg-[#f8f8f0] p-2.5 text-[#8a7b66] transition-all duration-200 hover:-translate-y-0.5 hover:border-[#19c8b9] hover:text-[#19c8b9] cursor-pointer"
                  >
                    <Github size={20} />
                  </a>
                  <a
                    href="https://twitter.com"
                    target="_blank"
                    rel="noopener noreferrer"
                    className="rounded-2xl border-2 border-[#e8dcc8] bg-[#f8f8f0] p-2.5 text-[#8a7b66] transition-all duration-200 hover:-translate-y-0.5 hover:border-[#19c8b9] hover:text-[#19c8b9] cursor-pointer"
                  >
                    <Twitter size={20} />
                  </a>
                  <a
                    href="mailto:contact@example.com"
                    className="rounded-2xl border-2 border-[#e8dcc8] bg-[#f8f8f0] p-2.5 text-[#8a7b66] transition-all duration-200 hover:-translate-y-0.5 hover:border-[#19c8b9] hover:text-[#19c8b9] cursor-pointer"
                  >
                    <Mail size={20} />
                  </a>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className="space-y-6">
          {BLOG_POSTS.map((post, index) => (
            <BlogCard key={post.id} post={post} index={index} />
          ))}
        </div>
      </div>
    </div>
  );
}
