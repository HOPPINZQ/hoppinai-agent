# HoppinAI Agent - Web

基于 Java 的 AI Agent 框架教学平台的 Web 前端，使用 Next.js 构建。通过 14 个渐进式课程（s01-s14），从零理解 AI 编程助手的核心机制。

> 开源地址：[https://github.com/HOPPINZQ/hoppinai-agent](https://github.com/HOPPINZQ/hoppinai-agent)

## 功能概览

- **交互式学习路径** — 14 个版本逐步递进，每版只新增一个核心机制
- **Agent 循环模拟器** — 可视化模拟 Agent 的「调用模型 → 执行工具 → 回传结果」循环
- **代码浏览与对比** — 内置代码查看器，支持版本间 diff 对比
- **执行流程图** — 每个版本配有流程图，直观展示架构演进
- **架构分层** — 五大正交关注点：工具、规划、记忆、并发、协作
- **i18n** — 支持中文 / English

## 技术栈

| 类别 | 技术 |
|------|------|
| 框架 | Next.js 16 (App Router, Static Export) |
| 语言 | TypeScript, React 19 |
| 样式 | Tailwind CSS v4 |
| 动画 | Framer Motion, GSAP + ScrollTrigger |
| 构建 | Static Export (`output: "export"`) |

## 快速开始

```bash
# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 构建生产版本（静态导出）
npm run build
```

## 项目结构

```
src/
├── app/[locale]/
│   ├── page.tsx                    # 首页（Bento 布局 + 滚动动画）
│   └── (learn)/
│       ├── [version]/page.tsx      # 版本详情页
│       ├── [version]/diff/page.tsx # 版本 diff 对比
│       ├── timeline/page.tsx       # 学习路径时间线
│       ├── layers/page.tsx         # 架构分层总览
│       ├── compare/page.tsx        # 版本对比
│       └── blog/page.tsx           # 博客
├── components/
│   ├── visualizations/             # 每个版本的可视化组件
│   ├── simulator/                  # Agent 循环模拟器
│   ├── architecture/               # 架构图组件
│   ├── backgrounds/                # 背景特效（LightRays 等）
│   ├── ui/                         # 通用 UI（BentoCard, ScrollFloat 等）
│   ├── layout/                     # 布局组件
│   ├── code/                       # 代码查看器
│   ├── diff/                       # Diff 对比组件
│   ├── docs/                       # 文档渲染
│   ├── timeline/                   # 时间线组件
│   └── blog/                       # 博客组件
├── data/
│   ├── generated/                  # 由 extract 脚本生成的 JSON 数据
│   ├── scenarios/                  # 模拟器场景数据
│   └── annotations/                # 代码注解
├── hooks/                          # 自定义 Hooks
├── i18n/messages/                  # 国际化（zh.json, en.json）
├── lib/                            # 工具函数、常量、类型
└── types/                          # TypeScript 类型定义
```

## 14 个版本的内容

| 版本 | 标题 | 层次 | 核心新增 |
|------|------|------|----------|
| s01 | Agent 循环 | 工具 | 最小循环：一个工具 + while(true) |
| s02 | 工具 | 工具 | 工具分发映射 |
| s03 | todo 规划 | 规划 | TodoManager + 提醒机制 |
| s04 | 子代理 | 规划 | 独立 messages[] 的子智能体 |
| s05 | 技能加载 | 规划 | SkillLoader + 按需知识注入 |
| s06 | 上下文压缩 | 记忆 | 三层压缩策略 |
| s07 | 任务系统 | 规划 | 基于文件的 TaskManager + 依赖图 |
| s08 | 后台任务 | 并发 | BackgroundManager + 通知队列 |
| s09 | 多智能体 | 协作 | TeammateManager + 异步邮箱 |
| s10 | 团队协议 | 协作 | 请求-响应模式驱动团队协商 |
| s11 | 自主智能体 | 协作 | 任务板轮询 + 自主治理 |
| s12 | 工作树隔离 | 协作 | 按目录隔离的工作树生命周期 |
| s13 | MCP 协议 | 规划 | Model Context Protocol 标准化接口 |
| s14 | ReAct 框架 | 规划 | 思考-行动-观察循环 |

## 内容提取

项目依赖 `../agents` 和 `../docs` 目录中的 Java 源码和 Markdown 文档来生成元数据：

```bash
# 从 ../agents 和 ../docs 提取内容，生成 src/data/generated/*
npm run extract
```

## 部署

本项目使用 Next.js 静态导出，构建产物为纯 HTML/CSS/JS，可部署到任何静态托管服务（Vercel、Netlify、GitHub Pages 等）。

```bash
npm run build
# 产物在 out/ 目录
```

## License

MIT
