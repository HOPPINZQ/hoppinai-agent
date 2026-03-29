"use client";

import { BlogCard } from "@/components/blog/blog-card";
import { Github, Twitter, Mail } from "lucide-react";

const BLOG_POSTS = [
  {
    id: 1,
    title: "AI 编程智能体：从零开始的探索之旅",
    excerpt: "深入探讨如何从基础循环构建出一个完整的 AI 编程智能体，了解每个阶段的设计决策和技术挑战。",
    date: "2024-03-14",
    tags: ["AI", "Agent", "Claude"],
    readTime: "8 分钟"
  },
  {
    id: 2,
    title: "工具系统设计：为什么 Bash 就足够了",
    excerpt: "探讨为什么在构建 AI Agent 时，简单的 Bash 工具比复杂的工具集更有效，以及最小化设计的价值。",
    date: "2024-03-13",
    tags: ["Design", "Tools", "Bash"],
    readTime: "5 分钟"
  },
  {
    id: 3,
    title: "上下文压缩：让 Agent 长期工作的关键",
    excerpt: "介绍如何通过智能的上下文压缩策略，让 AI Agent 能够在不失去连贯性的情况下处理长期任务。",
    date: "2024-03-12",
    tags: ["Memory", "Context", "Optimization"],
    readTime: "10 分钟"
  },
  {
    id: 4,
    title: "多 Agent 协作：从单兵作战到团队协作",
    excerpt: "讲解如何设计多 Agent 系统，实现团队成员之间的有效通信和任务协调。",
    date: "2024-03-11",
    tags: ["Collaboration", "Teams", "Protocol"],
    readTime: "12 分钟"
  },
  {
    id: 5,
    title: "任务系统：复杂任务的分解与执行",
    excerpt: "深入分析任务系统的设计，如何将复杂任务分解为可管理的小任务，并处理任务间的依赖关系。",
    date: "2024-03-10",
    tags: ["Planning", "Tasks", "Workflow"],
    readTime: "9 分钟"
  },
  {
    id: 6,
    title: "Worktree 隔离：安全的多任务执行",
    excerpt: "介绍如何使用 Git worktree 实现任务隔离，确保多个任务同时执行时不会相互干扰。",
    date: "2024-03-09",
    tags: ["Git", "Isolation", "Safety"],
    readTime: "7 分钟"
  }
];

export default function BlogPage() {
  return (
    <div className="min-h-screen">
      <div className="relative overflow-hidden py-16 md:py-24">
        <div className="absolute top-0 left-1/2 -translate-x-1/2 w-[800px] h-[400px] bg-gradient-to-br from-violet-500/5 via-purple-500/5 to-pink-500/5 rounded-full blur-3xl -translate-y-1/2"></div>

        <div className="relative max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 text-center">
          <h1 className="text-4xl md:text-6xl font-bold mb-6 text-zinc-50 tracking-tight">
            我的博客
          </h1>
          <p className="text-lg md:text-xl text-zinc-400 max-w-2xl mx-auto leading-relaxed">
            分享 AI、编程和技术探索的思考与见解
          </p>
        </div>
      </div>

      <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-12">

      <div className="mb-16 animate-fade-in">
        <div className="relative rounded-2xl p-8 md:p-10 border border-white/[0.06] bg-white/[0.03] overflow-hidden">
          <div className="absolute top-0 right-0 w-64 h-64 bg-gradient-to-br from-violet-500/10 to-purple-500/10 rounded-full blur-3xl -translate-y-1/2 translate-x-1/2"></div>

          <div className="relative flex flex-col md:flex-row items-center md:items-start gap-8">
            <div className="flex-shrink-0">
              <div className="w-24 h-24 md:w-28 md:h-28 bg-gradient-to-br from-zinc-700 to-zinc-600 rounded-full flex items-center justify-center text-4xl md:text-5xl shadow-lg">
                👨‍💻
              </div>
            </div>

            <div className="flex-1 text-center md:text-left">
              <h2 className="text-2xl md:text-3xl font-bold mb-4 text-zinc-50">关于我</h2>
              <div className="space-y-3 mb-6">
                <p className="text-zinc-400 leading-relaxed">
                  我是一名热爱 AI 和编程的技术爱好者。热衷于探索大语言模型的应用，特别是 AI 编程智能体的构建与实践。
                </p>
                <p className="text-zinc-400 leading-relaxed">
                  在这个博客中，我会分享我在构建 AI Agent 过程中的学习心得、技术探索和项目经验。希望这些内容能够帮助你更好地理解 AI 编程的世界。
                </p>
              </div>

              <div className="flex items-center justify-center md:justify-start gap-4">
                <a href="https://github.com" target="_blank" rel="noopener noreferrer"
                   className="p-2.5 bg-white/[0.05] rounded-lg border border-white/[0.08] text-zinc-400 hover:text-zinc-100 hover:border-white/[0.15] transition-all duration-200 hover:shadow-md cursor-pointer">
                  <Github size={20} />
                </a>
                <a href="https://twitter.com" target="_blank" rel="noopener noreferrer"
                   className="p-2.5 bg-white/[0.05] rounded-lg border border-white/[0.08] text-zinc-400 hover:text-zinc-100 hover:border-white/[0.15] transition-all duration-200 hover:shadow-md cursor-pointer">
                  <Twitter size={20} />
                </a>
                <a href="mailto:contact@example.com"
                   className="p-2.5 bg-white/[0.05] rounded-lg border border-white/[0.08] text-zinc-400 hover:text-zinc-100 hover:border-white/[0.15] transition-all duration-200 hover:shadow-md cursor-pointer">
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
