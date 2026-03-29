import { useState } from "react";
import { motion, AnimatePresence } from "motion/react";
import { Search, Calendar, MessageSquare, ChevronDown, ChevronUp, ExternalLink, Bot, User, LineChart, Plus, FileText, Zap, Shield, CheckCircle2, ArrowRight, Crosshair } from "lucide-react";
import { cn } from "@/lib/utils";
import { generateMockChatHistory, ChatHistoryItem } from "@/mockData";

const mockHistory = generateMockChatHistory(15);

export function ChatHistory() {
  const [history, setHistory] = useState<ChatHistoryItem[]>(mockHistory);
  const [searchQuery, setSearchQuery] = useState("");
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [showToolDetail, setShowToolDetail] = useState<string | null>(null);

  const filteredHistory = history.filter(item =>
    item.topic.toLowerCase().includes(searchQuery.toLowerCase()) ||
    item.symbol.toLowerCase().includes(searchQuery.toLowerCase()) ||
    item.userSummary.toLowerCase().includes(searchQuery.toLowerCase()) ||
    item.aiSummary.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const toggleExpand = (id: string) => {
    setExpandedId(expandedId === id ? null : id);
  };

  const formatDate = (isoString: string) => {
    const date = new Date(isoString);
    return new Intl.DateTimeFormat('zh-CN', {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    }).format(date);
  };

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      className="space-y-5 max-w-4xl mx-auto"
    >
      {/* Header */}
      <div className="flex flex-col md:flex-row justify-between items-start md:items-end gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-[var(--text-primary)] font-display uppercase">AI 分析历史</h1>
          <p className="text-[var(--text-secondary)] text-[13px] mt-0.5">查看过往对话和 AI 推荐记录</p>
        </div>

        <div className="flex items-center gap-2.5 w-full md:w-auto">
          <div className="flex items-center bg-[var(--surface)] border border-[var(--border)] rounded-lg px-3 py-2 flex-1 md:w-60 focus-within:border-[var(--accent)]/40 transition-colors">
            <Search className="w-4 h-4 text-[var(--text-muted)] mr-2" />
            <input
              type="text"
              placeholder="搜索历史记录..."
              className="bg-transparent border-none outline-none text-[13px] w-full text-[var(--text-primary)] placeholder:text-[var(--text-muted)]"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
          </div>
          <button className="flex items-center justify-center bg-[var(--surface)] border border-[var(--border)] rounded-lg p-2 text-[var(--text-secondary)] hover:text-[var(--text-primary)] hover:bg-[var(--surface-hover)] transition-colors">
            <Calendar className="w-5 h-5" />
          </button>
        </div>
      </div>

      {/* History List */}
      <div className="space-y-3 relative before:absolute before:inset-0 before:ml-5 before:-translate-x-px md:before:mx-auto md:before:translate-x-0 before:h-full before:w-px before:bg-[var(--border)]">
        {filteredHistory.map((item, index) => (
          <div key={item.id} className="relative flex items-center justify-between md:justify-normal md:odd:flex-row-reverse group is-active">
            {/* Timeline dot */}
            <div className="flex items-center justify-center w-9 h-9 rounded-lg border-2 border-[var(--bg)] bg-[var(--surface)] text-[var(--text-muted)] group-hover:text-[var(--accent)] group-hover:border-[var(--accent)]/20 transition-all shrink-0 md:order-1 md:group-odd:-translate-x-1/2 md:group-even:translate-x-1/2 shadow-sm z-10">
              <MessageSquare className="w-3.5 h-3.5" />
            </div>

            {/* Card */}
            <motion.div
              layout
              className="w-[calc(100%-3.5rem)] md:w-[calc(50%-2.5rem)] p-4 rounded-xl border border-[var(--border)] bg-[var(--surface)] shadow-sm hover:border-[var(--accent)]/20 transition-all card-glow"
            >
              <div className="cursor-pointer" onClick={() => toggleExpand(item.id)}>
                <div className="flex justify-between items-start mb-2">
                  <div className="flex items-center gap-2">
                    <span className="text-[11px] font-mono text-[var(--text-muted)]">{formatDate(item.timestamp)}</span>
                    <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-[var(--bg)] border border-[var(--border)] text-[var(--text-primary)]">
                      {item.symbol}
                    </span>
                  </div>
                  {expandedId === item.id ? (
                    <ChevronUp className="w-4 h-4 text-[var(--text-muted)]" />
                  ) : (
                    <ChevronDown className="w-4 h-4 text-[var(--text-muted)]" />
                  )}
                </div>

                <h3 className="text-[14px] font-semibold text-[var(--text-primary)] mb-1.5">{item.topic}</h3>

                {!expandedId || expandedId !== item.id ? (
                  <div className="space-y-1.5">
                    <p className="text-[12px] text-[var(--text-secondary)] line-clamp-1"><span className="font-medium text-[var(--text-primary)]">问:</span> {item.userSummary}</p>
                    <p className="text-[12px] text-[var(--text-secondary)] line-clamp-2"><span className="font-medium text-[var(--accent)]">AI:</span> {item.aiSummary}</p>
                  </div>
                ) : null}
              </div>

              {/* Expanded Content */}
              <AnimatePresence>
                {expandedId === item.id && (
                  <motion.div
                    initial={{ height: 0, opacity: 0 }}
                    animate={{ height: "auto", opacity: 1 }}
                    exit={{ height: 0, opacity: 0 }}
                    className="mt-3.5 pt-3.5 border-t border-[var(--border)] space-y-3.5 overflow-hidden"
                  >
                    <div className="flex gap-2.5">
                      <div className="w-7 h-7 rounded-md bg-[var(--surface-hover)] flex items-center justify-center shrink-0">
                        <User className="w-3.5 h-3.5 text-[var(--text-muted)]" />
                      </div>
                      <div className="flex-1 bg-[var(--bg)] rounded-lg p-3 text-[12px] text-[var(--text-primary)]">
                        {item.fullContent.user}
                      </div>
                    </div>

                    <div className="flex gap-2.5">
                      <div className="w-7 h-7 rounded-md bg-[var(--accent)]/10 flex items-center justify-center shrink-0">
                        <Bot className="w-3.5 h-3.5 text-[var(--accent)]" />
                      </div>
                      <div className="flex-1 bg-[var(--accent)]/5 border border-[var(--accent)]/10 rounded-lg p-3 text-[12px] text-[var(--text-primary)] whitespace-pre-wrap leading-relaxed">
                        {item.fullContent.ai}

                        {item.toolCall && (
                          <div className="mt-3 p-2.5 rounded-lg bg-[var(--surface)] border border-[var(--border)] flex items-center justify-between group/tool cursor-pointer hover:border-[var(--accent)]/30 transition-all"
                               onClick={() => setShowToolDetail(item.id)}>
                            <div className="flex items-center gap-2.5">
                              <div className={cn(
                                "w-7 h-7 rounded-md bg-[var(--bg)] flex items-center justify-center",
                                item.toolCall.name === 'buy_csgo_item' ? "text-[var(--up)]" : "text-[var(--down)]"
                              )}>
                                {item.toolCall.name === 'buy_csgo_item' ? <Plus className="w-3.5 h-3.5" /> : <ArrowRight className="w-3.5 h-3.5" />}
                              </div>
                              <div>
                                <div className="text-[9px] font-bold uppercase tracking-widest text-[var(--text-muted)]">工具调用</div>
                                <div className="text-[11px] font-bold text-[var(--text-primary)]">
                                  {item.toolCall.name === 'buy_csgo_item' ? '买入' : '卖出'}: {item.toolCall.args.itemName}
                                </div>
                              </div>
                            </div>
                            <button className="text-[10px] font-bold text-[var(--accent)] group-hover/tool:underline flex items-center gap-1">
                              详情 <ExternalLink className="w-3 h-3" />
                            </button>
                          </div>
                        )}
                      </div>
                    </div>

                    {/* Quick Actions */}
                    <div className="flex flex-wrap gap-2 ml-9">
                      <button className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg bg-[var(--surface-hover)] border border-[var(--border)] text-[10px] font-medium text-[var(--text-primary)] hover:border-[var(--accent)]/30 transition-colors">
                        <LineChart className="w-3 h-3 text-[var(--accent)]" />
                        分析 {item.symbol}
                      </button>
                      <button className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg bg-[var(--surface-hover)] border border-[var(--border)] text-[10px] font-medium text-[var(--text-primary)] hover:border-[var(--accent)]/30 transition-colors">
                        <Plus className="w-3 h-3 text-[var(--up)]" />
                        加入关注
                      </button>
                      <button className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg bg-[var(--surface-hover)] border border-[var(--border)] text-[10px] font-medium text-[var(--text-primary)] hover:border-[var(--accent)]/30 transition-colors">
                        <FileText className="w-3 h-3 text-[var(--text-muted)]" />
                        生成报告
                      </button>
                    </div>

                    <div className="flex justify-end pt-1">
                      <button className="text-[11px] flex items-center gap-1 text-[var(--accent)] hover:underline">
                        查看 {item.symbol} 详情 <ExternalLink className="w-3 h-3" />
                      </button>
                    </div>
                  </motion.div>
                )}
              </AnimatePresence>
            </motion.div>
          </div>
        ))}

        {filteredHistory.length === 0 && (
          <div className="text-center py-12 text-[var(--text-secondary)] bg-[var(--surface)] border border-[var(--border)] rounded-xl relative z-10">
            <MessageSquare className="w-7 h-7 mx-auto mb-2 opacity-15" />
            <p className="text-[13px]">未找到匹配的聊天记录。</p>
          </div>
        )}
      </div>

      {/* Tool Detail Modal */}
      <AnimatePresence>
        {showToolDetail && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm">
            <motion.div
              initial={{ scale: 0.9, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0.9, opacity: 0 }}
              className="w-full max-w-md bg-[var(--surface)] border border-[var(--border)] rounded-xl overflow-hidden shadow-2xl"
            >
              {(() => {
                const item = history.find(h => h.id === showToolDetail);
                if (!item || !item.toolCall) return null;
                const isBuy = item.toolCall.name === "buy_csgo_item";
                const { itemName, price, rarity } = item.toolCall.args;

                return (
                  <>
                    <div className="h-[2px] bg-gradient-to-r from-transparent via-[var(--accent)] to-transparent" />
                    <div className={cn(
                      "px-5 py-3 flex justify-between items-center",
                      isBuy ? "bg-[var(--up)]/5 border-b border-[var(--up)]/15" : "bg-[var(--down)]/5 border-b border-[var(--down)]/15"
                    )}>
                      <div className="flex items-center gap-2">
                        {isBuy ? <Plus className="w-4 h-4 text-[var(--up)]" /> : <ArrowRight className="w-4 h-4 text-[var(--down)]" />}
                        <span className={cn("text-[12px] font-bold uppercase tracking-widest", isBuy ? "text-[var(--up)]" : "text-[var(--down)]")}>
                          {isBuy ? "买入详情" : "卖出详情"}
                        </span>
                      </div>
                      <button onClick={() => setShowToolDetail(null)} className="text-[var(--text-muted)] hover:text-[var(--text-primary)]">
                        <Plus className="w-4 h-4 rotate-45" />
                      </button>
                    </div>

                    <div className="p-5 space-y-5">
                      <div className="flex items-start justify-between gap-3">
                        <div>
                          <div className="text-[10px] text-[var(--text-muted)] uppercase font-bold mb-0.5">物品名称</div>
                          <div className="text-base font-bold text-[var(--text-primary)]">{itemName}</div>
                          <div className="mt-1">
                            <span className="text-[9px] font-bold px-1.5 py-0.5 rounded bg-[var(--bg)] border border-[var(--border)] text-[var(--text-secondary)] uppercase">{rarity}</span>
                          </div>
                        </div>
                        <div className="text-right">
                          <div className="text-[10px] text-[var(--text-muted)] uppercase font-bold mb-0.5">执行价格</div>
                          <div className="text-xl font-mono font-black text-[var(--text-primary)]">${price.toLocaleString()}</div>
                        </div>
                      </div>

                      <div className="grid grid-cols-2 gap-3 py-3 border-y border-[var(--border)]">
                        <div className="flex items-center gap-2.5">
                          <div className="w-9 h-9 rounded-lg bg-[var(--bg)] flex items-center justify-center">
                            <Zap className="w-4 h-4 text-[var(--accent)]" />
                          </div>
                          <div>
                            <div className="text-[9px] text-[var(--text-muted)] uppercase font-bold">状态</div>
                            <div className="text-[11px] font-bold text-[var(--up)]">已完成</div>
                          </div>
                        </div>
                        <div className="flex items-center gap-2.5">
                          <div className="w-9 h-9 rounded-lg bg-[var(--bg)] flex items-center justify-center">
                            <Shield className="w-4 h-4 text-[var(--up)]" />
                          </div>
                          <div>
                            <div className="text-[9px] text-[var(--text-muted)] uppercase font-bold">验证</div>
                            <div className="text-[11px] font-bold">安全</div>
                          </div>
                        </div>
                      </div>

                      <div className="space-y-2.5">
                        <div className="text-[9px] font-bold uppercase text-[var(--text-muted)] tracking-widest">交易日志</div>
                        <div className="space-y-1.5">
                          <div className="flex justify-between text-[10px]">
                            <span className="text-[var(--text-muted)]">时间戳:</span>
                            <span className="font-mono text-[var(--text-primary)]">{item.timestamp}</span>
                          </div>
                          <div className="flex justify-between text-[10px]">
                            <span className="text-[var(--text-muted)]">网络费用:</span>
                            <span className="font-mono text-[var(--text-primary)]">$0.00</span>
                          </div>
                          <div className="flex justify-between text-[10px]">
                            <span className="text-[var(--text-muted)]">AI 置信度:</span>
                            <span className="font-mono text-[var(--up)]">98.4%</span>
                          </div>
                        </div>
                      </div>

                      <button
                        onClick={() => setShowToolDetail(null)}
                        className="w-full bg-[var(--accent)] text-[var(--bg)] py-2.5 rounded-lg text-[12px] font-bold hover:bg-[var(--accent-hover)] transition-all uppercase tracking-wider font-display"
                      >
                        关闭详情
                      </button>
                    </div>
                  </>
                );
              })()}
            </motion.div>
          </div>
        )}
      </AnimatePresence>
    </motion.div>
  );
}
