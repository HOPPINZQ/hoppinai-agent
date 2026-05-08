import { useState } from "react";
import {
  Settings as SettingsIcon,
  Sparkles,
  Cpu,
  Zap,
  Database,
  User,
  Bell,
  Globe,
  Key,
  Save,
  RotateCcw,
} from "lucide-react";
import { useTheme, type Theme } from "../lib/useTheme";

// ==================== 类型定义 ====================

interface PromptConfig {
  systemPrompt: string;
  temperature: number;
  maxTokens: number;
}

interface MCPConfig {
  name: string;
  endpoint: string;
  enabled: boolean;
}

interface SkillConfig {
  name: string;
  description: string;
  enabled: boolean;
}

interface AIConfig {
  model: string;
  provider: string;
  apiEndpoint: string;
  maxRetries: number;
  timeout: number;
}

interface DataConfig {
  autoSave: boolean;
  saveInterval: number;
  maxHistorySize: number;
}

interface UserSettings {
  nickname: string;
  email: string;
  timezone: string;
  language: string;
  theme: "dark" | "light" | "auto";
  notifications: boolean;
}

// ==================== 设置页面 ====================

export function Settings() {
  const { theme, setTheme } = useTheme();

  // 提示词配置
  const [promptConfig, setPromptConfig] = useState<PromptConfig>({
    systemPrompt: "你是一个专业的 AI 助手，帮助用户完成各种任务。",
    temperature: 0.7,
    maxTokens: 2000,
  });

  // MCP 配置
  const [mcpConfigs, setMcpConfigs] = useState<MCPConfig[]>([
    { name: "Web 搜索", endpoint: "mcp__web-reader__webReader", enabled: true },
    { name: "图像分析", endpoint: "mcp__4_5v_mcp__analyze_image", enabled: true },
    { name: "数据可视化", endpoint: "mcp__zai-mcp-server__analyze_data_visualization", enabled: false },
  ]);

  // Skills 配置
  const [skillConfigs, setSkillConfigs] = useState<SkillConfig[]>([
    { name: "代码生成", description: "自动生成代码片段", enabled: true },
    { name: "文档分析", description: "分析文档内容", enabled: true },
    { name: "数据查询", description: "查询数据库信息", enabled: false },
  ]);

  // AI 配置
  const [aiConfig, setAiConfig] = useState<AIConfig>({
    model: "claude-sonnet-4-6",
    provider: "anthropic",
    apiEndpoint: "https://api.anthropic.com",
    maxRetries: 3,
    timeout: 30000,
  });

  // 数据配置
  const [dataConfig, setDataConfig] = useState<DataConfig>({
    autoSave: true,
    saveInterval: 60,
    maxHistorySize: 1000,
  });

  // 用户设置
  const [userSettings, setUserSettings] = useState<UserSettings>({
    nickname: "用户",
    email: "user@example.com",
    timezone: "Asia/Shanghai",
    language: "zh-CN",
    theme: "dark",
    notifications: true,
  });

  const [activeTab, setActiveTab] = useState<"prompts" | "mcp" | "skills" | "ai" | "data" | "user">("prompts");
  const [saveStatus, setSaveStatus] = useState<"idle" | "saving" | "saved">("idle");

  const handleSave = () => {
    setSaveStatus("saving");
    setTimeout(() => {
      setSaveStatus("saved");
      setTimeout(() => setSaveStatus("idle"), 2000);
    }, 500);
  };

  const handleReset = () => {
    if (confirm("确定要重置所有设置吗？")) {
      // 重置为默认值
      setPromptConfig({
        systemPrompt: "你是一个专业的 AI 助手，帮助用户完成各种任务。",
        temperature: 0.7,
        maxTokens: 2000,
      });
      setAiConfig({
        model: "claude-sonnet-4-6",
        provider: "anthropic",
        apiEndpoint: "https://api.anthropic.com",
        maxRetries: 3,
        timeout: 30000,
      });
      setDataConfig({
        autoSave: true,
        saveInterval: 60,
        maxHistorySize: 1000,
      });
    }
  };

  const tabs = [
    { id: "prompts" as const, icon: Sparkles, label: "提示词" },
    { id: "mcp" as const, icon: Cpu, label: "MCP" },
    { id: "skills" as const, icon: Zap, label: "Skills" },
    { id: "ai" as const, icon: Key, label: "AI 配置" },
    { id: "data" as const, icon: Database, label: "数据设置" },
    { id: "user" as const, icon: User, label: "用户设置" },
  ];

  return (
    <div className="space-y-6">
      {/* 页面标题 */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-lg bg-[var(--accent)]/10 border border-[var(--accent)]/20 flex items-center justify-center">
            <SettingsIcon className="w-5 h-5 text-[var(--accent)]" />
          </div>
          <div>
            <h1 className="text-2xl font-bold text-[var(--text-primary)] font-display">设置</h1>
            <p className="text-sm text-[var(--text-muted)]">管理应用配置和偏好设置</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={handleReset}
            className="px-4 py-2 text-[13px] text-[var(--text-secondary)] hover:text-[var(--text-primary)] bg-[var(--surface-hover)] hover:bg-[var(--surface)] border border-[var(--border)] rounded-lg transition-all flex items-center gap-2"
          >
            <RotateCcw className="w-4 h-4" />
            重置
          </button>
          <button
            onClick={handleSave}
            disabled={saveStatus === "saving"}
            className="px-4 py-2 text-[13px] font-medium text-[var(--bg)] bg-[var(--accent)] hover:bg-[var(--accent-hover)] rounded-lg transition-all flex items-center gap-2 disabled:opacity-50"
          >
            <Save className="w-4 h-4" />
            {saveStatus === "saved" ? "已保存" : saveStatus === "saving" ? "保存中..." : "保存设置"}
          </button>
        </div>
      </div>

      <div className="flex gap-6">
        {/* 侧边栏导航 */}
        <div className="w-56 flex-shrink-0">
          <div className="bg-[var(--surface)] border border-[var(--border)] rounded-xl overflow-hidden">
            {tabs.map((tab) => {
              const Icon = tab.icon;
              return (
                <button
                  key={tab.id}
                  onClick={() => setActiveTab(tab.id)}
                  className={`w-full flex items-center gap-3 px-4 py-3 text-left text-[13px] font-medium transition-all ${
                    activeTab === tab.id
                      ? "bg-[var(--accent)]/10 text-[var(--accent)] border-r-2 border-[var(--accent)]"
                      : "text-[var(--text-secondary)] hover:bg-[var(--surface-hover)] hover:text-[var(--text-primary)]"
                  }`}
                >
                  <Icon className="w-4 h-4" />
                  {tab.label}
                </button>
              );
            })}
          </div>
        </div>

        {/* 设置内容区域 */}
        <div className="flex-1 bg-[var(--surface)] border border-[var(--border)] rounded-xl p-6">
          {/* 提示词配置 */}
          {activeTab === "prompts" && (
            <div className="space-y-6">
              <div>
                <h2 className="text-lg font-bold text-[var(--text-primary)] mb-2">系统提示词</h2>
                <p className="text-sm text-[var(--text-muted)] mb-4">配置 AI 助手的行为和响应风格</p>
                <textarea
                  value={promptConfig.systemPrompt}
                  onChange={(e) => setPromptConfig({ ...promptConfig, systemPrompt: e.target.value })}
                  className="w-full h-32 px-4 py-3 rounded-lg bg-[var(--bg)] border border-[var(--border)] text-[13px] text-[var(--text-primary)] placeholder:text-[var(--text-muted)] focus:outline-none focus:border-[var(--accent)]/50 resize-none"
                  placeholder="输入系统提示词..."
                />
              </div>

              <div className="grid grid-cols-2 gap-6">
                <div>
                  <label className="block text-sm font-medium text-[var(--text-primary)] mb-2">
                    温度: {promptConfig.temperature}
                  </label>
                  <input
                    type="range"
                    min="0"
                    max="2"
                    step="0.1"
                    value={promptConfig.temperature}
                    onChange={(e) => setPromptConfig({ ...promptConfig, temperature: parseFloat(e.target.value) })}
                    className="w-full"
                  />
                  <p className="text-xs text-[var(--text-muted)] mt-1">
                    控制响应的随机性，值越高越随机
                  </p>
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-primary)] mb-2">
                    最大 Token 数: {promptConfig.maxTokens}
                  </label>
                  <input
                    type="number"
                    min="100"
                    max="8000"
                    step="100"
                    value={promptConfig.maxTokens}
                    onChange={(e) => setPromptConfig({ ...promptConfig, maxTokens: parseInt(e.target.value) })}
                    className="w-full px-3 py-2 rounded-lg bg-[var(--bg)] border border-[var(--border)] text-[13px] text-[var(--text-primary)] focus:outline-none focus:border-[var(--accent)]/50"
                  />
                  <p className="text-xs text-[var(--text-muted)] mt-1">
                    单次响应的最大 token 数量
                  </p>
                </div>
              </div>
            </div>
          )}

          {/* MCP 配置 */}
          {activeTab === "mcp" && (
            <div className="space-y-4">
              <div>
                <h2 className="text-lg font-bold text-[var(--text-primary)] mb-2">MCP 服务配置</h2>
                <p className="text-sm text-[var(--text-muted)] mb-4">管理 Model Context Protocol 服务</p>
              </div>

              {mcpConfigs.map((mcp, index) => (
                <div
                  key={index}
                  className="p-4 rounded-lg bg-[var(--bg)] border border-[var(--border)] space-y-3"
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <Cpu className="w-5 h-5 text-[var(--accent)]" />
                      <div>
                        <div className="font-medium text-[var(--text-primary)]">{mcp.name}</div>
                        <code className="text-xs text-[var(--text-muted)]">{mcp.endpoint}</code>
                      </div>
                    </div>
                    <label className="relative inline-flex items-center cursor-pointer">
                      <input
                        type="checkbox"
                        checked={mcp.enabled}
                        onChange={(e) => {
                          const updated = [...mcpConfigs];
                          updated[index].enabled = e.target.checked;
                          setMcpConfigs(updated);
                        }}
                        className="sr-only peer"
                      />
                      <div className="w-11 h-6 bg-[var(--border)] peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-[var(--accent)]"></div>
                    </label>
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* Skills 配置 */}
          {activeTab === "skills" && (
            <div className="space-y-4">
              <div>
                <h2 className="text-lg font-bold text-[var(--text-primary)] mb-2">Skills 配置</h2>
                <p className="text-sm text-[var(--text-muted)] mb-4">管理 AI 助手的技能模块</p>
              </div>

              {skillConfigs.map((skill, index) => (
                <div
                  key={index}
                  className="p-4 rounded-lg bg-[var(--bg)] border border-[var(--border)] space-y-2"
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <Zap className="w-5 h-5 text-[var(--accent)]" />
                      <div>
                        <div className="font-medium text-[var(--text-primary)]">{skill.name}</div>
                        <div className="text-xs text-[var(--text-muted)]">{skill.description}</div>
                      </div>
                    </div>
                    <label className="relative inline-flex items-center cursor-pointer">
                      <input
                        type="checkbox"
                        checked={skill.enabled}
                        onChange={(e) => {
                          const updated = [...skillConfigs];
                          updated[index].enabled = e.target.checked;
                          setSkillConfigs(updated);
                        }}
                        className="sr-only peer"
                      />
                      <div className="w-11 h-6 bg-[var(--border)] peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-[var(--accent)]"></div>
                    </label>
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* AI 配置 */}
          {activeTab === "ai" && (
            <div className="space-y-6">
              <div>
                <h2 className="text-lg font-bold text-[var(--text-primary)] mb-2">AI 模型配置</h2>
                <p className="text-sm text-[var(--text-muted)] mb-4">配置 AI 模型和 API 设置</p>
              </div>

              <div className="grid grid-cols-2 gap-6">
                <div>
                  <label className="block text-sm font-medium text-[var(--text-primary)] mb-2">模型</label>
                  <select
                    value={aiConfig.model}
                    onChange={(e) => setAiConfig({ ...aiConfig, model: e.target.value })}
                    className="w-full px-3 py-2 rounded-lg bg-[var(--bg)] border border-[var(--border)] text-[13px] text-[var(--text-primary)] focus:outline-none focus:border-[var(--accent)]/50"
                  >
                    <option value="claude-opus-4-6">Claude Opus 4.6</option>
                    <option value="claude-sonnet-4-6">Claude Sonnet 4.6</option>
                    <option value="claude-haiku-4-5">Claude Haiku 4.5</option>
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-primary)] mb-2">提供商</label>
                  <input
                    type="text"
                    value={aiConfig.provider}
                    onChange={(e) => setAiConfig({ ...aiConfig, provider: e.target.value })}
                    className="w-full px-3 py-2 rounded-lg bg-[var(--bg)] border border-[var(--border)] text-[13px] text-[var(--text-primary)] focus:outline-none focus:border-[var(--accent)]/50"
                  />
                </div>
                <div className="col-span-2">
                  <label className="block text-sm font-medium text-[var(--text-primary)] mb-2">API 端点</label>
                  <input
                    type="text"
                    value={aiConfig.apiEndpoint}
                    onChange={(e) => setAiConfig({ ...aiConfig, apiEndpoint: e.target.value })}
                    className="w-full px-3 py-2 rounded-lg bg-[var(--bg)] border border-[var(--border)] text-[13px] text-[var(--text-primary)] focus:outline-none focus:border-[var(--accent)]/50"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-primary)] mb-2">最大重试次数</label>
                  <input
                    type="number"
                    min="0"
                    max="10"
                    value={aiConfig.maxRetries}
                    onChange={(e) => setAiConfig({ ...aiConfig, maxRetries: parseInt(e.target.value) })}
                    className="w-full px-3 py-2 rounded-lg bg-[var(--bg)] border border-[var(--border)] text-[13px] text-[var(--text-primary)] focus:outline-none focus:border-[var(--accent)]/50"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-[var(--text-primary)] mb-2">超时时间 (ms)</label>
                  <input
                    type="number"
                    min="5000"
                    max="120000"
                    step="5000"
                    value={aiConfig.timeout}
                    onChange={(e) => setAiConfig({ ...aiConfig, timeout: parseInt(e.target.value) })}
                    className="w-full px-3 py-2 rounded-lg bg-[var(--bg)] border border-[var(--border)] text-[13px] text-[var(--text-primary)] focus:outline-none focus:border-[var(--accent)]/50"
                  />
                </div>
              </div>
            </div>
          )}

          {/* 数据设置 */}
          {activeTab === "data" && (
            <div className="space-y-6">
              <div>
                <h2 className="text-lg font-bold text-[var(--text-primary)] mb-2">数据存储配置</h2>
                <p className="text-sm text-[var(--text-muted)] mb-4">配置数据自动保存和历史记录</p>
              </div>

              <div className="space-y-4">
                <div className="flex items-center justify-between p-4 rounded-lg bg-[var(--bg)] border border-[var(--border)]">
                  <div className="flex items-center gap-3">
                    <Database className="w-5 h-5 text-[var(--accent)]" />
                    <div>
                      <div className="font-medium text-[var(--text-primary)]">自动保存</div>
                      <div className="text-xs text-[var(--text-muted)]">自动保存对话历史</div>
                    </div>
                  </div>
                  <label className="relative inline-flex items-center cursor-pointer">
                    <input
                      type="checkbox"
                      checked={dataConfig.autoSave}
                      onChange={(e) => setDataConfig({ ...dataConfig, autoSave: e.target.checked })}
                      className="sr-only peer"
                    />
                    <div className="w-11 h-6 bg-[var(--border)] peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-[var(--accent)]"></div>
                  </label>
                </div>

                <div>
                  <label className="block text-sm font-medium text-[var(--text-primary)] mb-2">
                    保存间隔: {dataConfig.saveInterval} 秒
                  </label>
                  <input
                    type="range"
                    min="10"
                    max="300"
                    step="10"
                    value={dataConfig.saveInterval}
                    onChange={(e) => setDataConfig({ ...dataConfig, saveInterval: parseInt(e.target.value) })}
                    className="w-full"
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium text-[var(--text-primary)] mb-2">
                    最大历史记录数: {dataConfig.maxHistorySize}
                  </label>
                  <input
                    type="number"
                    min="100"
                    max="10000"
                    step="100"
                    value={dataConfig.maxHistorySize}
                    onChange={(e) => setDataConfig({ ...dataConfig, maxHistorySize: parseInt(e.target.value) })}
                    className="w-full px-3 py-2 rounded-lg bg-[var(--bg)] border border-[var(--border)] text-[13px] text-[var(--text-primary)] focus:outline-none focus:border-[var(--accent)]/50"
                  />
                </div>
              </div>
            </div>
          )}

          {/* 用户设置 */}
          {activeTab === "user" && (
            <div className="space-y-6">
              <div>
                <h2 className="text-lg font-bold text-[var(--text-primary)] mb-2">用户偏好设置</h2>
                <p className="text-sm text-[var(--text-muted)] mb-4">管理个人偏好和通知设置</p>
              </div>

              <div className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-[var(--text-primary)] mb-2">昵称</label>
                  <input
                    type="text"
                    value={userSettings.nickname}
                    onChange={(e) => setUserSettings({ ...userSettings, nickname: e.target.value })}
                    className="w-full px-3 py-2 rounded-lg bg-[var(--bg)] border border-[var(--border)] text-[13px] text-[var(--text-primary)] focus:outline-none focus:border-[var(--accent)]/50"
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium text-[var(--text-primary)] mb-2">邮箱</label>
                  <input
                    type="email"
                    value={userSettings.email}
                    onChange={(e) => setUserSettings({ ...userSettings, email: e.target.value })}
                    className="w-full px-3 py-2 rounded-lg bg-[var(--bg)] border border-[var(--border)] text-[13px] text-[var(--text-primary)] focus:outline-none focus:border-[var(--accent)]/50"
                  />
                </div>

                <div className="grid grid-cols-2 gap-6">
                  <div>
                    <label className="block text-sm font-medium text-[var(--text-primary)] mb-2">时区</label>
                    <select
                      value={userSettings.timezone}
                      onChange={(e) => setUserSettings({ ...userSettings, timezone: e.target.value })}
                      className="w-full px-3 py-2 rounded-lg bg-[var(--bg)] border border-[var(--border)] text-[13px] text-[var(--text-primary)] focus:outline-none focus:border-[var(--accent)]/50"
                    >
                      <option value="Asia/Shanghai">Asia/Shanghai</option>
                      <option value="America/New_York">America/New_York</option>
                      <option value="Europe/London">Europe/London</option>
                      <option value="UTC">UTC</option>
                    </select>
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-[var(--text-primary)] mb-2">语言</label>
                    <select
                      value={userSettings.language}
                      onChange={(e) => setUserSettings({ ...userSettings, language: e.target.value })}
                      className="w-full px-3 py-2 rounded-lg bg-[var(--bg)] border border-[var(--border)] text-[13px] text-[var(--text-primary)] focus:outline-none focus:border-[var(--accent)]/50"
                    >
                      <option value="zh-CN">简体中文</option>
                      <option value="zh-TW">繁體中文</option>
                      <option value="en-US">English</option>
                      <option value="ja-JP">日本語</option>
                    </select>
                  </div>
                </div>

                <div>
                  <label className="block text-sm font-medium text-[var(--text-primary)] mb-2">主题</label>
                  <div className="flex gap-3">
                    {(["dark", "light", "auto"] as const).map((t) => (
                      <button
                        key={t}
                        onClick={() => setTheme(t)}
                        className={`flex-1 py-2 px-4 rounded-lg text-[13px] font-medium transition-all ${
                          theme === t
                            ? "bg-[var(--accent)] text-[var(--bg)]"
                            : "bg-[var(--bg)] text-[var(--text-secondary)] border border-[var(--border)]"
                        }`}
                      >
                        {t === "dark" ? "深色" : t === "light" ? "浅色" : "自动"}
                      </button>
                    ))}
                  </div>
                </div>

                <div className="flex items-center justify-between p-4 rounded-lg bg-[var(--bg)] border border-[var(--border)]">
                  <div className="flex items-center gap-3">
                    <Bell className="w-5 h-5 text-[var(--accent)]" />
                    <div>
                      <div className="font-medium text-[var(--text-primary)]">启用通知</div>
                      <div className="text-xs text-[var(--text-muted)]">接收系统通知和提醒</div>
                    </div>
                  </div>
                  <label className="relative inline-flex items-center cursor-pointer">
                    <input
                      type="checkbox"
                      checked={userSettings.notifications}
                      onChange={(e) => setUserSettings({ ...userSettings, notifications: e.target.checked })}
                      className="sr-only peer"
                    />
                    <div className="w-11 h-6 bg-[var(--border)] peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-[var(--accent)]"></div>
                  </label>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
