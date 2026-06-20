"use client";

import { motion, AnimatePresence } from "framer-motion";
import { useSteppedVisualization } from "@/hooks/useSteppedVisualization";
import { StepControls } from "@/components/visualizations/shared/step-controls";

interface SkillEntry {
  name: string;
  summary: string;
  fullTokens: number;
  content: string[];
}

const SKILLS: SkillEntry[] = [
  {
    name: "/commit",
    summary: "帮我提交一下代码",
    fullTokens: 320,
    content: [
      "1. 执行 git status + git diff 查看文件和代码变动",
      "2. 分析这些信息",
      "3. 生成提交内容，并创建commit",
      "4. 执行 git status 验证",
    ],
  },
  {
    name: "/code-review",
    summary: "检查刚提交的代码的代码规范",
    fullTokens: 480,
    content: [
      "1. 查看提交记录",
      "2. 读取变更过的代码片段和所在文件",
      "3. 检查代码格式、bugs、安全漏洞等问题",
      "4. 指出问题，给出建议，提供修复方案",
    ],
  },
  {
    name: "/test",
    summary: "编写、运行、检验测试用例",
    fullTokens: 290,
    content: [
      "1. 检查pom是否引入测试依赖",
      "2. 根据项目，选择合适的测试框架",
      "3. 根据代码，生成测试用例",
      "4. 执行测试用例，观察输出",
    ],
  },
  {
    name: "/deploy",
    summary: "部署Java项目",
    fullTokens: 350,
    content: [
      "1. 验证target文件和jar包是否存在，jar包版本和pom是否一致",
      "2. 通过maven构建jar包",
      "3. 将jar包上传到服务器",
      "4. 执行构建脚本，检查服务状态",
    ],
  },
];

const TOKEN_STATES = [120, 120, 440, 440, 780, 780];
const MAX_TOKEN_DISPLAY = 1000;

const STEPS = [
  {
    title: "第一层：精简摘要",
    description:
      "所有技能在系统提示词里只有摘要：精简、常驻。",
  },
  {
    title: "触发技能调用",
    description:
      "模型识别到技能调用后，会触发 Skill 工具。",
  },
  {
    title: "第二层：完整注入",
    description:
      "完整技能说明以 tool_result 注入，而不是塞进系统提示词。",
  },
  {
    title: "上下文中生效",
    description:
      "详细说明以“工具返回”的形式进入上下文，模型据此精确执行。",
  },
  {
    title: "叠加加载技能",
    description:
      "可以加载多个技能；只有摘要常驻，完整内容按需进出。",
  },
  {
    title: "双层架构",
    description:
      "第一层：常驻且极小。第二层：按需加载且详细。优雅分层。",
  },
];

export default function SkillLoading({ title }: { title?: string }) {
  const {
    currentStep,
    totalSteps,
    next,
    prev,
    reset,
    isPlaying,
    toggleAutoPlay,
  } = useSteppedVisualization({ totalSteps: STEPS.length, autoPlayInterval: 2500 });

  const tokenCount = TOKEN_STATES[currentStep];
  const highlightedSkill = currentStep >= 1 && currentStep <= 3 ? 0 : currentStep >= 4 ? 1 : -1;
  const showFirstContent = currentStep >= 2;
  const showSecondContent = currentStep >= 4;
  const firstContentFaded = currentStep >= 5;

  return (
    <section className="space-y-4">
      <h2 className="text-xl font-semibold text-[#794f27]">
        {title || "On-Demand Skill Loading"}
      </h2>

      <div
        className="rounded-lg border border-[#e8dcc8] bg-[#fbf7eb] p-6"
        style={{ minHeight: 500 }}
      >
        <div className="flex gap-6">
          {/* Main content area */}
          <div className="flex-1 space-y-4">
            {/* System Prompt Block */}
            <div>
              <div className="mb-2 flex items-center gap-2">
                <div className="h-2 w-2 rounded-full bg-[#9f927d]" />
                <span className="text-xs font-semibold text-[#9f927d]">
                  System Prompt
                </span>
                <span className="rounded bg-[#f6efe0] px-1.5 py-0.5 font-mono text-[10px] text-[#725d42]">
                  始终存在
                </span>
              </div>
              <div className="rounded-lg border border-[#d4c9b4] bg-[#fbf7eb] p-4">
                <div className="mb-2 font-mono text-[10px] text-[#8a7b66]">
                  # 可用的Skills
                </div>
                <div className="space-y-1.5">
                  {SKILLS.map((skill, i) => {
                    const isHighlighted = i === highlightedSkill;
                    return (
                      <motion.div
                        key={skill.name}
                        animate={{
                          boxShadow: isHighlighted
                            ? "0 0 12px 2px rgba(136, 157, 240, 0.5)"
                            : "0 0 0 0px rgba(136, 157, 240, 0)",
                        }}
                        transition={{ duration: 0.4 }}
                        className={`rounded px-3 py-1.5 font-mono text-xs transition-colors ${
                          isHighlighted
                            ? "bg-[#e6eafb] text-[#889df0]"
                            : "bg-[#f6efe0] text-[#725d42]"
                        }`}
                      >
                        <span className="font-semibold text-[#794f27]">
                          {skill.name}
                        </span>
                        {" - "}
                        {skill.summary}
                      </motion.div>
                    );
                  })}
                </div>
              </div>
            </div>

            {/* User invocation indicator */}
            <AnimatePresence>
              {currentStep === 1 && (
                <motion.div
                  initial={{ opacity: 0, y: -8 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0 }}
                  className="flex items-center gap-2 rounded-lg border border-[#889df0]/40 bg-[#e6eafb] px-3 py-2"
                >
                  <span className="text-xs text-[#889df0]">
                    命中技能:
                  </span>
                  <code className="rounded bg-[#e6eafb] px-2 py-0.5 text-xs font-bold text-[#889df0]">
                    /commit
                  </code>
                </motion.div>
              )}
              {currentStep === 4 && (
                <motion.div
                  initial={{ opacity: 0, y: -8 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0 }}
                  className="flex items-center gap-2 rounded-lg border border-[#889df0]/40 bg-[#e6eafb] px-3 py-2"
                >
                  <span className="text-xs text-[#889df0]">
                    命中技能:
                  </span>
                  <code className="rounded bg-[#e6eafb] px-2 py-0.5 text-xs font-bold text-[#889df0]">
                    /code-review
                  </code>
                </motion.div>
              )}
            </AnimatePresence>

            {/* Connecting arrow */}
            <AnimatePresence>
              {(showFirstContent || showSecondContent) && (
                <motion.div
                  initial={{ opacity: 0, scaleY: 0 }}
                  animate={{ opacity: 1, scaleY: 1 }}
                  exit={{ opacity: 0 }}
                  className="flex justify-center"
                >
                  <div className="flex flex-col items-center">
                    <div className="h-6 w-px bg-[#889df0]" />
                    <div className="h-0 w-0 border-l-[5px] border-r-[5px] border-t-[6px] border-l-transparent border-r-transparent border-t-[#889df0]" />
                  </div>
                </motion.div>
              )}
            </AnimatePresence>

            {/* Expanded Skill Content Blocks */}
            <div className="space-y-3">
              <AnimatePresence>
                {showFirstContent && (
                  <motion.div
                    initial={{ opacity: 0, height: 0 }}
                    animate={{
                      opacity: firstContentFaded ? 0.4 : 1,
                      height: "auto",
                    }}
                    exit={{ opacity: 0, height: 0 }}
                    transition={{ duration: 0.4 }}
                    className="overflow-hidden"
                  >
                    <div className="rounded-lg border-2 border-[#889df0]/40 bg-[#fbf7eb] p-4">
                      <div className="mb-2 flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <div className="h-2 w-2 rounded-full bg-[#889df0]" />
                          <span className="text-xs font-bold text-[#889df0]">
                            SKILL.md: /commit
                          </span>
                        </div>
                        <span className="rounded bg-[#e6eafb] px-1.5 py-0.5 font-mono text-[10px] text-[#889df0]">
                          tool_result
                        </span>
                      </div>
                      <div className="space-y-1">
                        {SKILLS[0].content.map((line, i) => (
                          <motion.div
                            key={i}
                            initial={{ opacity: 0, x: -8 }}
                            animate={{
                              opacity: firstContentFaded ? 0.5 : 1,
                              x: 0,
                            }}
                            transition={{ delay: i * 0.08 }}
                            className="font-mono text-xs text-[#9f927d]"
                          >
                            {line}
                          </motion.div>
                        ))}
                      </div>
                    </div>
                  </motion.div>
                )}
              </AnimatePresence>

              <AnimatePresence>
                {showSecondContent && (
                  <motion.div
                    initial={{ opacity: 0, height: 0 }}
                    animate={{ opacity: 1, height: "auto" }}
                    exit={{ opacity: 0, height: 0 }}
                    transition={{ duration: 0.4 }}
                    className="overflow-hidden"
                  >
                    <div className="rounded-lg border-2 border-[#b77dee]/40 bg-[#fbf7eb] p-4">
                      <div className="mb-2 flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <div className="h-2 w-2 rounded-full bg-[#b77dee]" />
                          <span className="text-xs font-bold text-[#b77dee]">
                            SKILL.md: /code-review
                          </span>
                        </div>
                        <span className="rounded bg-[#efe2fb] px-1.5 py-0.5 font-mono text-[10px] text-[#b77dee]">
                          tool_result
                        </span>
                      </div>
                      <div className="space-y-1">
                        {SKILLS[1].content.map((line, i) => (
                          <motion.div
                            key={i}
                            initial={{ opacity: 0, x: -8 }}
                            animate={{ opacity: 1, x: 0 }}
                            transition={{ delay: i * 0.08 }}
                            className="font-mono text-xs text-[#9f927d]"
                          >
                            {line}
                          </motion.div>
                        ))}
                      </div>
                    </div>
                  </motion.div>
                )}
              </AnimatePresence>
            </div>

            {/* Mechanism annotation on step 3 */}
            <AnimatePresence>
              {currentStep === 3 && (
                <motion.div
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  exit={{ opacity: 0 }}
                  className="rounded border border-[#e59266]/40 bg-[#fde6d8] px-3 py-2 text-xs text-[#e59266]"
                >
                  技能工具以工具结果消息的形式返回内容。模型可在上下文中查看该消息并执行指令，无需系统提示词冗余。
                </motion.div>
              )}
            </AnimatePresence>

            {/* Final overview label on step 5 */}
            <AnimatePresence>
              {currentStep === 5 && (
                <motion.div
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  exit={{ opacity: 0 }}
                  className="flex gap-3"
                >
                  <div className="flex-1 rounded border border-[#e8dcc8] bg-[#fbf7eb] p-2 text-center">
                    <div className="text-[10px] font-semibold text-[#8a7b66]">
                      第一层
                    </div>
                    <div className="text-xs text-[#9f927d]">
                      只携带技能的名称和描述，大约 ~120 tokens
                    </div>
                  </div>
                  <div className="flex-1 rounded border border-[#889df0]/40 bg-[#e6eafb] p-2 text-center">
                    <div className="text-[10px] font-semibold text-[#889df0]">
                      第二层
                    </div>
                    <div className="text-xs text-[#889df0]">
                      加载被命中Skills的SKILL.md，大约 ~300-500 tokens
                    </div>
                  </div>
                </motion.div>
              )}
            </AnimatePresence>
          </div>

          {/* Token Gauge (vertical bar on the right) */}
          <div className="flex w-16 flex-col items-center">
            <div className="mb-1 text-center font-mono text-[10px] text-[#725d42]">
              Tokens
            </div>
            <div
              className="relative w-8 overflow-hidden rounded-full bg-[#f6efe0]"
              style={{ height: 300 }}
            >
              <motion.div
                animate={{
                  height: `${(tokenCount / MAX_TOKEN_DISPLAY) * 100}%`,
                }}
                transition={{ duration: 0.5 }}
                className={`absolute bottom-0 w-full rounded-full ${
                  tokenCount > 600
                    ? "bg-[#e59266]"
                    : tokenCount > 300
                      ? "bg-[#889df0]"
                      : "bg-[#8ac68a]"
                }`}
              />
            </div>
            <motion.div
              key={tokenCount}
              initial={{ scale: 0.8 }}
              animate={{ scale: 1 }}
              className="mt-2 text-center font-mono text-xs font-semibold text-[#9f927d]"
            >
              {tokenCount}
            </motion.div>
          </div>
        </div>

        {/* Step Controls */}
        <div className="mt-6">
          <StepControls
            currentStep={currentStep}
            totalSteps={totalSteps}
            onPrev={prev}
            onNext={next}
            onReset={reset}
            isPlaying={isPlaying}
            onToggleAutoPlay={toggleAutoPlay}
            stepTitle={STEPS[currentStep].title}
            stepDescription={STEPS[currentStep].description}
          />
        </div>
      </div>
    </section>
  );
}
