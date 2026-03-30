export const VERSION_ORDER = [
  "s01", "s02", "s03", "s04", "s05", "s06", "s07", "s08", "s09", "s10", "s11", "s12", "s13", "s14"
] as const;

export const LEARNING_PATH = VERSION_ORDER;

export type VersionId = typeof LEARNING_PATH[number];

export const UNIMPLEMENTED_VERSIONS = new Set(["s09", "s10", "s11", "s12"]);

export const VERSION_META: Record<string, {
  title: string;
  subtitle: string;
  coreAddition: string;
  keyInsight: string;
  layer: "tools" | "planning" | "memory" | "concurrency" | "collaboration";
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
  s09: { title: "多智能体", subtitle: "队友 + 邮箱", coreAddition: "TeammateManager + 基于文件的邮箱", keyInsight: "当单个智能体无法完成时，通过异步邮箱委派给持久化队友", layer: "collaboration", prevVersion: "s08" },
  s10: { title: "团队协议", subtitle: "共享通信规则", coreAddition: "两种协议的 request_id 关联", keyInsight: "一种请求-响应模式驱动所有团队协商", layer: "collaboration", prevVersion: "s09" },
  s11: { title: "自主智能体", subtitle: "扫描任务板，认领任务", coreAddition: "任务板轮询 + 基于超时的自主治理", keyInsight: "队友扫描任务板并自主认领任务；无需领队逐一分配", layer: "collaboration", prevVersion: "s10" },
  s12: { title: "工作树 + 任务隔离", subtitle: "按目录隔离", coreAddition: "可组合的工作树生命周期 + 共享任务板上的事件流", keyInsight: "每个智能体在自己的目录中工作；任务管理目标，工作树管理目录，通过 ID 绑定", layer: "collaboration", prevVersion: "s11" },
  s13: { title: "MCP协议", subtitle: "Model Context Protocol", coreAddition: "MCP 协议 + 标准化接口", keyInsight: "通过开放协议标准化 AI 助手与外部系统的连接，实现统一的数据和工具访问", layer: "planning", prevVersion: "s12" },
  s14: { title: "ReAct行为框架", subtitle: "思考-行动-观察", coreAddition: "ReAct模式的核心是一个持续的三阶段循环", keyInsight: "ReAct模式通过结构化的”思考-行动-观察”循环，为AI智能体提供了一种强大的问题解决框架", layer: "planning", prevVersion: "s13" },
};

export const LAYERS = [
  { id: "tools" as const, label: "工具与执行", color: "#3B82F6", versions: ["s01", "s02", "s14"] },
  { id: "planning" as const, label: "规划与协调", color: "#10B981", versions: ["s03", "s04", "s05", "s13", "s07"] },
  { id: "memory" as const, label: "记忆管理", color: "#8B5CF6", versions: ["s06"] },
  { id: "concurrency" as const, label: "并发", color: "#F59E0B", versions: ["s08"] },
  { id: "collaboration" as const, label: "协作", color: "#EF4444", versions: ["s09", "s10", "s11", "s12"] },
] as const;
