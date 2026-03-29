"use client";

import Link from "next/link";
import { Clock, ArrowRight } from "lucide-react";

interface BlogPost {
  id: number;
  title: string;
  excerpt: string;
  date: string;
  tags: string[];
  readTime: string;
}

interface BlogCardProps {
  post: BlogPost;
  index?: number;
}

export function BlogCard({ post, index = 0 }: BlogCardProps) {
  const getTagColor = (tag: string) => {
    const colors: Record<string, string> = {
      "AI": "bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300",
      "Agent": "bg-purple-100 dark:bg-purple-900/30 text-purple-700 dark:text-purple-300",
      "Claude": "bg-indigo-100 dark:bg-indigo-900/30 text-indigo-700 dark:text-indigo-300",
      "Design": "bg-pink-100 dark:bg-pink-900/30 text-pink-700 dark:text-pink-300",
      "Tools": "bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-300",
      "Bash": "bg-orange-100 dark:bg-orange-900/30 text-orange-700 dark:text-orange-300",
      "Memory": "bg-cyan-100 dark:bg-cyan-900/30 text-cyan-700 dark:text-cyan-300",
      "Context": "bg-teal-100 dark:bg-teal-900/30 text-teal-700 dark:text-teal-300",
      "Optimization": "bg-emerald-100 dark:bg-emerald-900/30 text-emerald-700 dark:text-emerald-300",
      "Collaboration": "bg-rose-100 dark:bg-rose-900/30 text-rose-700 dark:text-rose-300",
      "Teams": "bg-amber-100 dark:bg-amber-900/30 text-amber-700 dark:text-amber-300",
      "Protocol": "bg-violet-100 dark:bg-violet-900/30 text-violet-700 dark:text-violet-300",
      "Planning": "bg-fuchsia-100 dark:bg-fuchsia-900/30 text-fuchsia-700 dark:text-fuchsia-300",
      "Tasks": "bg-lime-100 dark:bg-lime-900/30 text-lime-700 dark:text-lime-300",
      "Workflow": "bg-sky-100 dark:bg-sky-900/30 text-sky-700 dark:text-sky-300",
      "Git": "bg-slate-100 dark:bg-slate-900/30 text-slate-700 dark:text-slate-300",
      "Isolation": "bg-zinc-100 dark:bg-zinc-900/30 text-zinc-700 dark:text-zinc-300",
      "Safety": "bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-300"
    };
    return colors[tag] || "bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300";
  };

  return (
    <Link href={`/blog/${post.id}`} className="block group cursor-pointer" style={{
      animationDelay: `${index * 100}ms`
    }}>
      <article className="relative bg-white dark:bg-zinc-900/50 rounded-xl border border-zinc-200 dark:border-zinc-800 p-6 transition-all duration-300 hover:border-zinc-300 dark:hover:border-zinc-700 hover:shadow-lg hover:shadow-zinc-200/50 dark:hover:shadow-zinc-900/50 animate-fade-in">
        <div className="flex flex-col h-full">
          <div className="flex-1">
            <div className="flex flex-wrap gap-2 mb-4">
              {post.tags.map((tag) => (
                <span key={tag} className={`text-xs font-medium px-2.5 py-1 rounded-full ${getTagColor(tag)}`}>
                  {tag}
                </span>
              ))}
            </div>
            
            <h2 className="text-xl md:text-2xl font-bold mb-3 text-zinc-900 dark:text-zinc-50 group-hover:text-blue-600 dark:group-hover:text-blue-400 transition-colors line-clamp-2">
              {post.title}
            </h2>
            
            <p className="text-zinc-600 dark:text-zinc-400 leading-relaxed mb-6 line-clamp-3">
              {post.excerpt}
            </p>
          </div>
          
          <div className="flex items-center justify-between pt-4 border-t border-zinc-100 dark:border-zinc-800">
            <div className="flex items-center gap-4 text-sm text-zinc-500 dark:text-zinc-500">
              <div className="flex items-center gap-1.5">
                <Clock size={14} className="text-zinc-400 dark:text-zinc-600" />
                <span>{post.readTime}</span>
              </div>
              <span className="text-zinc-400 dark:text-zinc-600">•</span>
              <span>{post.date}</span>
            </div>
            
            <div className="flex items-center gap-1.5 text-blue-600 dark:text-blue-400 font-medium text-sm group-hover:gap-2 transition-all">
              <span>阅读更多</span>
              <ArrowRight size={16} className="group-hover:translate-x-1 transition-transform" />
            </div>
          </div>
        </div>
      </article>
    </Link>
  );
}
