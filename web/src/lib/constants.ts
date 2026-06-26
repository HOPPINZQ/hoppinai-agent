export const VERSION_ORDER = [
  "s01", "s02", "s03", "s04", "s05", "s06", "s07", "s08",
  "s09", "s10", "s11", "s12", "s13", "s14",
  "s15", "s16", "s17", "s18", "s19", "s20", "s21", "s22", "s23",
  "cli",
] as const;

export const LEARNING_PATH = VERSION_ORDER;

export type VersionId = typeof LEARNING_PATH[number];

export const UNIMPLEMENTED_VERSIONS = new Set<string>();

export const VERSION_META: Record<string, {
  title: string;
  subtitle: string;
  coreAddition: string;
  keyInsight: string;
  layer: "tools" | "planning" | "memory" | "concurrency" | "collaboration" | "production";
  prevVersion: string | null;
}> = {
  s01: { title: "Agent 循环", subtitle: "给AI一个终端，它就能撬起整个计算机", coreAddition: "一个工具，让AI干所有工作", keyInsight: "这是一个很小的智能体，但它能干很多事", layer: "tools", prevVersion: null },
  s02: { title: "工具", subtitle: "每工具一个处理器", coreAddition: "工具分发映射", keyInsight: "循环保持不变；新工具注册到分发映射中", layer: "tools", prevVersion: "s01" },
  s03: { title: "todo规划", subtitle: "行动前先规划", coreAddition: "TodoManager + 提醒机制", keyInsight: "没有规划的智能体会偏离目标；先列出步骤，再执行。你不按规划执行，系统提示词就会追着你问。", layer: "planning", prevVersion: "s02" },
  s04: { title: "子代理", subtitle: "每个子任务独立的上下文", coreAddition: "子智能体生成并使用独立的 messages[]", keyInsight: "子智能体使用独立的 messages[]，保持主对话清晰", layer: "planning", prevVersion: "s03" },
  s05: { title: "技能加载", subtitle: "按需加载", coreAddition: "SkillLoader + 双层注入", keyInsight: "按需通过 tool_result 注入知识，而非在系统提示词中预加载", layer: "planning", prevVersion: "s04" },
  s06: { title: "上下文压缩", subtitle: "三层压缩策略", coreAddition: "微型压缩 + 自动压缩 + 归档", keyInsight: "上下文会填满；三层压缩策略支持无限会话", layer: "memory", prevVersion: "s05" },
  s07: { title: "任务系统", subtitle: "任务图 + 依赖关系", coreAddition: "基于文件状态的 TaskManager + 依赖图", keyInsight: "基于文件的任务图，支持顺序、并行和依赖——多智能体协作的协调骨干", layer: "planning", prevVersion: "s06" },
  s08: { title: "后台任务", subtitle: "后台线程 + 通知", coreAddition: "BackgroundManager + 通知队列", keyInsight: "在后台运行耗时操作；智能体持续提前思考", layer: "concurrency", prevVersion: "s07" },
  s09: { title: "权限系统", subtitle: "三道安全门", coreAddition: "denyList → permissionRules → askUser", keyInsight: "工具执行前过三道闸：黑名单直拒、规则匹配、用户确认", layer: "tools", prevVersion: "s08" },
  s10: { title: "钩子机制", subtitle: "可复用的事件扩展点", coreAddition: "HookRegistry + 4 个事件点", keyInsight: "把权限检查从硬编码抽成 PRE_TOOL_USE 钩子，可插拔扩展", layer: "tools", prevVersion: "s09" },
  s11: { title: "持久化记忆", subtitle: "跨会话记住用户偏好", coreAddition: "MemorySelector + .memory/*.md", keyInsight: "YAML 索引 + 分类型记忆文件，会话结束自动提取保存", layer: "memory", prevVersion: "s10" },
  s12: { title: "错误恢复", subtitle: "三条恢复路径", coreAddition: "ErrorClassifier + RetryWrapper", keyInsight: "max_tokens 升级 → prompt_too_long 压缩 → rate_limit 指数退避重试", layer: "memory", prevVersion: "s11" },
  s13: { title: "MCP协议", subtitle: "Model Context Protocol", coreAddition: "MCP 协议 + 标准化接口", keyInsight: "通过开放协议标准化 AI 助手与外部系统的连接，实现统一的数据和工具访问", layer: "planning", prevVersion: "s12" },
  s14: { title: "ReAct行为框架", subtitle: "思考-行动-观察", coreAddition: "ReAct模式的核心是一个持续的三阶段循环", keyInsight: "ReAct模式通过结构化的”思考-行动-观察”循环，为AI智能体提供了一种强大的问题解决框架", layer: "planning", prevVersion: "s13" },
  s15: { title: "动态系统提示", subtitle: "按上下文装配", coreAddition: "PromptAssembler + PromptSection", keyInsight: "system prompt 不再硬编码；identity/tools/workspace/memory 段按 AgentContext 动态开关与缓存", layer: "tools", prevVersion: "s14" },
  s16: { title: "定时调度", subtitle: "cron 表达式驱动", coreAddition: "CronScheduler + 双线程队列", keyInsight: "两条 daemon 线程解耦触发与执行；每轮 LLM 调用前 poll cron 队列", layer: "concurrency", prevVersion: "s15" },
  s17: { title: "Agent 团队", subtitle: "lead / teammate 协作", coreAddition: "TeammateRunner + MessageBus", keyInsight: "lead spawn teammate，每个 teammate 在守护线程跑独立 agent 循环，文件邮箱异步通信", layer: "collaboration", prevVersion: "s16" },
  s18: { title: "团队协议", subtitle: "request/response 状态机", coreAddition: "ProtocolDispatcher + ProtocolRegistry", keyInsight: "shutdown / plan_approval 两种正式协议，让 lead 能优雅关停 teammate 并审批方案", layer: "collaboration", prevVersion: "s17" },
  s19: { title: "自主 Agent", subtitle: "扫描任务板，自动认领", coreAddition: "IdlePoller + AutoClaimer", keyInsight: "teammate 跑 WORK→IDLE 两阶段循环，从 TaskBoard 自主认领任务，无需人工分配", layer: "collaboration", prevVersion: "s18" },
  s20: { title: "Worktree 隔离", subtitle: "git 工作区并发", coreAddition: "WorktreeManager + WorktreeContext", keyInsight: "每个 task 绑定 git worktree，文件/bash 操作通过 ThreadLocal 自动路由，多 agent 互不干扰", layer: "concurrency", prevVersion: "s19" },
  s21: { title: "综合集成", subtitle: "一个循环塞进所有机制", coreAddition: "27 个工具 + 全部基础设施", keyInsight: "机制可以很多，循环只有一个：压缩→组装提示→cron→重试→钩子→权限→工具→落账，全部串成一条管道", layer: "collaboration", prevVersion: "s20" },
  s22: { title: "多模态输入", subtitle: "图像 / PDF 进入消息流", coreAddition: "MessageContent 多类型 + 图片识别工具", keyInsight: "MessageParam 从纯文本升级为 text/image/file 多块结构；read_file 自动检测二进制并 base64 编码进 messages", layer: "production", prevVersion: "s21" },
  s23: { title: "安全沙箱", subtitle: "4 层防御执行 bash", coreAddition: "SandboxRunner + 4 层隔离", keyInsight: "静态分析 → 目录监禁 → OS 沙箱(bwrap) → 容器隔离，与 s09 权限 + s10 钩子形成完整安全链", layer: "production", prevVersion: "s22" },
  cli: { title: "CLI 可视化 Agent", subtitle: "ANSI 面板让 Agent 思考过程一目了然", coreAddition: "CliRenderer + ANSI 面板渲染", keyInsight: "去掉 Spring Boot/MySQL，纯命令行运行，box-drawing 面板展示思考/工具调用/结果/耗时", layer: "tools", prevVersion: null },
};

export const LAYERS = [
  { id: "tools" as const, label: "工具与执行", color: "#889df0", versions: ["s01", "s02","s05", "s13"] },
  { id: "planning" as const, label: "规划与协调", color: "#82d5bb", versions: ["s03", "s04", "s07","s09", "s10", "s14", "s15", "cli"] },
  { id: "memory" as const, label: "记忆管理", color: "#b77dee", versions: ["s06", "s11", "s12"] },
  { id: "concurrency" as const, label: "并发", color: "#e59266", versions: ["s08", "s16", "s20"] },
  { id: "collaboration" as const, label: "协作", color: "#f8a6b2", versions: ["s17", "s18", "s19", "s21"] },
  { id: "production" as const, label: "生产化", color: "#f7cd67", versions: ["s22", "s23"] },
] as const;

/**
 * Look up a layer's NookPhone color by id. Single source of truth — pages
 * and components read from here instead of keeping their own color maps.
 */
export function getLayerColor(layerId: string): string {
  return LAYERS.find((l) => l.id === layerId)?.color ?? "#9a835a";
}

/**
 * Tailwind-friendly map of layer id -> hex color. Use as
 * `style={{ backgroundColor: LAYER_COLOR_BY_ID[layerId] }}` or for borders/dots.
 */
export const LAYER_COLOR_BY_ID: Record<string, string> = Object.fromEntries(
  LAYERS.map((l) => [l.id, l.color])
);
