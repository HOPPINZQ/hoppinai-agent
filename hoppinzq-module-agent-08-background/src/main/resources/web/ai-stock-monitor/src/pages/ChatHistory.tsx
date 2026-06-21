import { useState } from "react";
import { motion, AnimatePresence } from "motion/react";
import { Search, Calendar, MessageSquare, ChevronDown, ChevronUp, ExternalLink, Bot, User, LineChart, Plus, FileText, Zap, Shield, CheckCircle2, ArrowRight } from "lucide-react";
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
    return new Intl.DateTimeFormat('en-US', { 
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
      className="space-y-6 max-w-4xl mx-auto"
    >
      {/* Header Section */}
      <div className="flex flex-col md:flex-row justify-between items-start md:items-end gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-[var(--text-primary)]">AI Analysis History</h1>
          <p className="text-[var(--text-secondary)] text-sm mt-1">Review past conversations and AI recommendations</p>
        </div>
        
        <div className="flex items-center gap-3 w-full md:w-auto">
          <div className="flex items-center bg-[var(--surface)] border border-[var(--border)] rounded-lg px-3 py-2 flex-1 md:w-64 focus-within:border-[var(--accent)] transition-colors">
            <Search className="w-4 h-4 text-[var(--text-secondary)] mr-2" />
            <input 
              type="text" 
              placeholder="Search history..." 
              className="bg-transparent border-none outline-none text-sm w-full text-[var(--text-primary)] placeholder:text-[var(--text-secondary)]"
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
      <div className="space-y-4 relative before:absolute before:inset-0 before:ml-5 before:-translate-x-px md:before:mx-auto md:before:translate-x-0 before:h-full before:w-0.5 before:bg-gradient-to-b before:from-transparent before:via-[var(--border)] before:to-transparent">
        {filteredHistory.map((item, index) => (
          <div key={item.id} className="relative flex items-center justify-between md:justify-normal md:odd:flex-row-reverse group is-active">
            {/* Timeline dot */}
            <div className="flex items-center justify-center w-10 h-10 rounded-full border-4 border-[var(--bg)] bg-[var(--surface)] text-[var(--text-secondary)] group-hover:text-[var(--accent)] group-hover:border-[var(--accent)]/20 transition-colors shrink-0 md:order-1 md:group-odd:-translate-x-1/2 md:group-even:translate-x-1/2 shadow-sm z-10">
              <MessageSquare className="w-4 h-4" />
            </div>
            
            {/* Card */}
            <motion.div 
              layout
              className="w-[calc(100%-4rem)] md:w-[calc(50%-2.5rem)] p-4 rounded-xl border border-[var(--border)] bg-[var(--surface)] shadow-sm hover:shadow-md transition-all"
            >
              <div 
                className="cursor-pointer"
                onClick={() => toggleExpand(item.id)}
              >
                <div className="flex justify-between items-start mb-2">
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-mono text-[var(--text-secondary)]">{formatDate(item.timestamp)}</span>
                    <span className="px-2 py-0.5 rounded text-xs font-bold bg-[var(--border)]/50 text-[var(--text-primary)]">
                      {item.symbol}
                    </span>
                  </div>
                  {expandedId === item.id ? (
                    <ChevronUp className="w-4 h-4 text-[var(--text-secondary)]" />
                  ) : (
                    <ChevronDown className="w-4 h-4 text-[var(--text-secondary)]" />
                  )}
                </div>
                
                <h3 className="text-base font-semibold text-[var(--text-primary)] mb-2">{item.topic}</h3>
                
                {!expandedId || expandedId !== item.id ? (
                  <div className="space-y-2">
                    <p className="text-sm text-[var(--text-secondary)] line-clamp-1"><span className="font-medium text-[var(--text-primary)]">Q:</span> {item.userSummary}</p>
                    <p className="text-sm text-[var(--text-secondary)] line-clamp-2"><span className="font-medium text-[var(--accent)]">AI:</span> {item.aiSummary}</p>
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
                    className="mt-4 pt-4 border-t border-[var(--border)] space-y-4 overflow-hidden"
                  >
                    <div className="flex gap-3">
                      <div className="w-8 h-8 rounded-full bg-[var(--surface-hover)] flex items-center justify-center shrink-0">
                        <User className="w-4 h-4 text-[var(--text-secondary)]" />
                      </div>
                      <div className="flex-1 bg-[var(--bg)] rounded-lg p-3 text-sm text-[var(--text-primary)]">
                        {item.fullContent.user}
                      </div>
                    </div>
                    
                    <div className="flex gap-3">
                      <div className="w-8 h-8 rounded-full bg-[var(--accent)]/10 flex items-center justify-center shrink-0">
                        <Bot className="w-4 h-4 text-[var(--accent)]" />
                      </div>
                      <div className="flex-1 bg-[var(--accent)]/5 border border-[var(--accent)]/10 rounded-lg p-3 text-sm text-[var(--text-primary)] whitespace-pre-wrap leading-relaxed">
                        {item.fullContent.ai}
                        
                        {item.toolCall && (
                          <div className="mt-4 p-3 rounded-lg bg-[var(--surface)] border border-[var(--border)] flex items-center justify-between group/tool cursor-pointer hover:border-[var(--accent)] transition-all"
                               onClick={() => setShowToolDetail(item.id)}>
                            <div className="flex items-center gap-3">
                              <div className={cn(
                                "w-8 h-8 rounded bg-[var(--bg)] flex items-center justify-center",
                                item.toolCall.name === 'buy_csgo_item' ? "text-[var(--up)]" : "text-[var(--down)]"
                              )}>
                                {item.toolCall.name === 'buy_csgo_item' ? <Plus className="w-4 h-4" /> : <ArrowRight className="w-4 h-4" />}
                              </div>
                              <div>
                                <div className="text-[10px] font-black uppercase tracking-widest text-[var(--text-secondary)]">Tool Call</div>
                                <div className="text-xs font-bold text-[var(--text-primary)]">
                                  {item.toolCall.name === 'buy_csgo_item' ? 'Purchase' : 'Sale'}: {item.toolCall.args.itemName}
                                </div>
                              </div>
                            </div>
                            <button className="text-[10px] font-bold text-[var(--accent)] group-hover/tool:underline flex items-center gap-1">
                              View Details <ExternalLink className="w-3 h-3" />
                            </button>
                          </div>
                        )}
                      </div>
                    </div>

                    {/* Quick Actions */}
                    <div className="flex flex-wrap gap-2 ml-11">
                      <button className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-[var(--surface-hover)] border border-[var(--border)] text-xs font-medium text-[var(--text-primary)] hover:border-[var(--accent)] transition-colors">
                        <LineChart className="w-3.5 h-3.5 text-[var(--accent)]" />
                        Analyze {item.symbol} further
                      </button>
                      <button className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-[var(--surface-hover)] border border-[var(--border)] text-xs font-medium text-[var(--text-primary)] hover:border-[var(--accent)] transition-colors">
                        <Plus className="w-3.5 h-3.5 text-[var(--up)]" />
                        Add to watchlist
                      </button>
                      <button className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-[var(--surface-hover)] border border-[var(--border)] text-xs font-medium text-[var(--text-primary)] hover:border-[var(--accent)] transition-colors">
                        <FileText className="w-3.5 h-3.5 text-[var(--text-secondary)]" />
                        Generate report
                      </button>
                    </div>

                    <div className="flex justify-end pt-2">
                      <button className="text-xs flex items-center gap-1 text-[var(--accent)] hover:underline">
                        View {item.symbol} Details <ExternalLink className="w-3 h-3" />
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
            <MessageSquare className="w-8 h-8 mx-auto mb-3 opacity-20" />
            <p>No chat history found matching your criteria.</p>
          </div>
        )}
      </div>

      {/* Tool Detail Modal */}
      <AnimatePresence>
        {showToolDetail && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
            <motion.div 
              initial={{ scale: 0.9, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0.9, opacity: 0 }}
              className="w-full max-w-md bg-[var(--surface)] border border-[var(--border)] rounded-2xl overflow-hidden shadow-2xl"
            >
              {(() => {
                const item = history.find(h => h.id === showToolDetail);
                if (!item || !item.toolCall) return null;
                const isBuy = item.toolCall.name === "buy_csgo_item";
                const { itemName, price, rarity } = item.toolCall.args;

                return (
                  <>
                    <div className={cn(
                      "px-6 py-4 flex justify-between items-center",
                      isBuy ? "bg-[var(--up)]/10" : "bg-[var(--down)]/10"
                    )}>
                      <div className="flex items-center gap-2">
                        {isBuy ? <Plus className="w-5 h-5 text-[var(--up)]" /> : <ArrowRight className="w-5 h-5 text-[var(--down)]" />}
                        <span className={cn("text-sm font-black uppercase tracking-widest", isBuy ? "text-[var(--up)]" : "text-[var(--down)]")}>
                          {isBuy ? "Purchase Details" : "Sale Details"}
                        </span>
                      </div>
                      <button 
                        onClick={() => setShowToolDetail(null)}
                        className="text-[var(--text-secondary)] hover:text-[var(--text-primary)]"
                      >
                        <Plus className="w-5 h-5 rotate-45" />
                      </button>
                    </div>
                    
                    <div className="p-6 space-y-6">
                      <div className="flex items-start justify-between gap-4">
                        <div>
                          <div className="text-xs text-[var(--text-secondary)] uppercase font-bold mb-1">Item Name</div>
                          <div className="text-lg font-bold text-[var(--text-primary)]">{itemName}</div>
                          <div className="mt-2">
                            <span className="text-[10px] font-black px-2 py-1 rounded bg-[var(--border)]/50 text-[var(--text-secondary)] uppercase">
                              {rarity}
                            </span>
                          </div>
                        </div>
                        <div className="text-right">
                          <div className="text-xs text-[var(--text-secondary)] uppercase font-bold mb-1">Execution Price</div>
                          <div className="text-2xl font-mono font-black text-[var(--text-primary)]">${price.toLocaleString()}</div>
                        </div>
                      </div>

                      <div className="grid grid-cols-2 gap-4 py-4 border-y border-[var(--border)]">
                        <div className="flex items-center gap-3">
                          <div className="w-10 h-10 rounded-xl bg-[var(--bg)] flex items-center justify-center">
                            <Zap className="w-5 h-5 text-[var(--accent)]" />
                          </div>
                          <div>
                            <div className="text-[10px] text-[var(--text-secondary)] uppercase font-bold">Status</div>
                            <div className="text-xs font-bold text-emerald-500">Completed</div>
                          </div>
                        </div>
                        <div className="flex items-center gap-3">
                          <div className="w-10 h-10 rounded-xl bg-[var(--bg)] flex items-center justify-center">
                            <Shield className="w-5 h-5 text-emerald-500" />
                          </div>
                          <div>
                            <div className="text-[10px] text-[var(--text-secondary)] uppercase font-bold">Verification</div>
                            <div className="text-xs font-bold">Secure</div>
                          </div>
                        </div>
                      </div>

                      <div className="space-y-3">
                        <div className="text-[10px] font-black uppercase text-[var(--text-secondary)] tracking-widest">Transaction Log</div>
                        <div className="space-y-2">
                          <div className="flex justify-between text-[10px]">
                            <span className="text-[var(--text-secondary)]">Timestamp:</span>
                            <span className="font-mono text-[var(--text-primary)]">{item.timestamp}</span>
                          </div>
                          <div className="flex justify-between text-[10px]">
                            <span className="text-[var(--text-secondary)]">Network Fee:</span>
                            <span className="font-mono text-[var(--text-primary)]">$0.00</span>
                          </div>
                          <div className="flex justify-between text-[10px]">
                            <span className="text-[var(--text-secondary)]">AI Confidence:</span>
                            <span className="font-mono text-emerald-500">98.4%</span>
                          </div>
                        </div>
                      </div>

                      <button 
                        onClick={() => setShowToolDetail(null)}
                        className="w-full bg-[var(--accent)] text-white py-3 rounded-xl text-sm font-bold hover:bg-[var(--accent)]/90 transition-all"
                      >
                        Close Details
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
