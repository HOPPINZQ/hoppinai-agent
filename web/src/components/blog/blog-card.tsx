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
      "AI": "bg-[#e6eafb] text-[#889df0]",
      "Agent": "bg-[#efe2fb] text-[#b77dee]",
      "Claude": "bg-[#efe2fb] text-[#b77dee]",
      "Design": "bg-[#fde2e0] text-[#f8a6b2]",
      "Tools": "bg-[#e0f0e0] text-[#82d5bb]",
      "Bash": "bg-[#fde6d8] text-[#e59266]",
      "Memory": "bg-[#e6f9f6] text-[#19c8b9]",
      "Context": "bg-[#e6f9f6] text-[#19c8b9]",
      "Optimization": "bg-[#e0f0e0] text-[#82d5bb]",
      "Collaboration": "bg-[#fde2e0] text-[#f8a6b2]",
      "Teams": "bg-[#fde6d8] text-[#e59266]",
      "Protocol": "bg-[#efe2fb] text-[#b77dee]",
      "Planning": "bg-[#efe2fb] text-[#b77dee]",
      "Tasks": "bg-[#e0f0e0] text-[#82d5bb]",
      "Workflow": "bg-[#e6eafb] text-[#889df0]",
      "Git": "bg-[#f6efe0] text-[#9f927d]",
      "Isolation": "bg-[#f6efe0] text-[#9f927d]",
      "Safety": "bg-[#fde2e0] text-[#fc736d]"
    };
    return colors[tag] || "bg-[#f6efe0] text-[#9f927d]";
  };

  return (
    <Link href={`/blog/${post.id}`} className="block group cursor-pointer" style={{
      animationDelay: `${index * 100}ms`
    }}>
      <article className="relative bg-[#fbf7eb] rounded-xl border border-[#e8dcc8] p-6 transition-all duration-300 hover:border-[#b77dee]/40 hover:shadow-lg hover:shadow-[#e8dcc8] animate-fade-in">
        <div className="flex flex-col h-full">
          <div className="flex-1">
            <div className="flex flex-wrap gap-2 mb-4">
              {post.tags.map((tag) => (
                <span key={tag} className={`text-xs font-medium px-2.5 py-1 rounded-full ${getTagColor(tag)}`}>
                  {tag}
                </span>
              ))}
            </div>
            
            <h2 className="text-xl md:text-2xl font-bold mb-3 text-[#5a4a30] group-hover:text-[#889df0] transition-colors line-clamp-2">
              {post.title}
            </h2>

            <p className="text-[#9f927d] leading-relaxed mb-6 line-clamp-3">
              {post.excerpt}
            </p>
          </div>
          
          <div className="flex items-center justify-between pt-4 border-t border-[#e8dcc8]">
            <div className="flex items-center gap-4 text-sm text-[#8a7b66]">
              <div className="flex items-center gap-1.5">
                <Clock size={14} className="text-[#725d42]" />
                <span>{post.readTime}</span>
              </div>
              <span className="text-[#725d42]">•</span>
              <span>{post.date}</span>
            </div>

            <div className="flex items-center gap-1.5 text-[#889df0] font-medium text-sm group-hover:gap-2 transition-all">
              <span>阅读更多</span>
              <ArrowRight size={16} className="group-hover:translate-x-1 transition-transform" />
            </div>
          </div>
        </div>
      </article>
    </Link>
  );
}
