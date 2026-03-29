import { useState } from "react";
import { motion } from "motion/react";
import {
  History,
  Search,
  ArrowUpRight,
  ArrowDownRight,
  Calendar,
  DollarSign,
  TrendingUp,
  ChevronDown,
  ChevronUp,
  Star,
  Crosshair
} from "lucide-react";
import { cn } from "@/lib/utils";
import { generateMockCSGOPurchaseHistory, CSGOPurchaseRecord } from "@/mockData";

const mockHistory = generateMockCSGOPurchaseHistory(25);

export function CSGOPurchaseHistory() {
  const [history] = useState<CSGOPurchaseRecord[]>(mockHistory);
  const [searchQuery, setSearchQuery] = useState("");
  const [sortConfig, setSortConfig] = useState<{ key: keyof CSGOPurchaseRecord; direction: 'asc' | 'desc' } | null>(null);

  const handleSort = (key: keyof CSGOPurchaseRecord) => {
    let direction: 'asc' | 'desc' = 'asc';
    if (sortConfig && sortConfig.key === key && sortConfig.direction === 'asc') {
      direction = 'desc';
    }
    setSortConfig({ key, direction });
  };

  const sortedHistory = [...history].sort((a, b) => {
    if (!sortConfig) return 0;
    const { key, direction } = sortConfig;
    if (a[key] < b[key]) return direction === 'asc' ? -1 : 1;
    if (a[key] > b[key]) return direction === 'asc' ? 1 : -1;
    return 0;
  });

  const filteredHistory = sortedHistory.filter(item =>
    item.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
    item.type.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const getRarityColor = (rarity: string) => {
    switch (rarity) {
      case 'Restricted': return 'text-[var(--rarity-restricted)]';
      case 'Classified': return 'text-[var(--rarity-classified)]';
      case 'Covert': return 'text-[var(--rarity-covert)]';
      case 'Contraband': return 'text-[var(--rarity-contraband)]';
      default: return 'text-[var(--text-secondary)]';
    }
  };

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5 }}
      className="space-y-5"
    >
      {/* Header */}
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-[var(--text-primary)] flex items-center gap-2 font-display uppercase">
            <History className="w-6 h-6 text-[var(--accent)]" />
            AI 购买历史
          </h1>
          <p className="text-[var(--text-secondary)] text-[13px] mt-0.5">AI 自动化皮肤采购的历史记录</p>
        </div>

        <div className="flex items-center bg-[var(--surface)] border border-[var(--border)] rounded-lg px-3 py-2 w-full md:w-60 focus-within:border-[var(--accent)]/40 transition-colors">
          <Search className="w-4 h-4 text-[var(--text-muted)] mr-2" />
          <input
            type="text"
            placeholder="搜索历史记录..."
            className="bg-transparent border-none outline-none text-[13px] w-full text-[var(--text-primary)] placeholder:text-[var(--text-muted)]"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
        </div>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="p-4 rounded-xl border border-[var(--border)] bg-[var(--surface)] border-l-2 border-l-[var(--accent)]">
          <div className="flex items-center gap-2.5 mb-2 text-[var(--text-muted)]">
            <DollarSign className="w-4 h-4" />
            <span className="text-[10px] font-bold uppercase tracking-widest">总花费</span>
          </div>
          <div className="text-2xl font-mono font-bold">${history.reduce((acc, curr) => acc + curr.aiPurchasePrice, 0).toLocaleString()}</div>
        </div>
        <div className="p-4 rounded-xl border border-[var(--border)] bg-[var(--surface)] border-l-2 border-l-[var(--up)]">
          <div className="flex items-center gap-2.5 mb-2 text-[var(--text-muted)]">
            <TrendingUp className="w-4 h-4" />
            <span className="text-[10px] font-bold uppercase tracking-widest">平均节省</span>
          </div>
          <div className="text-2xl font-mono font-bold text-[var(--up)]">
            {((1 - history.reduce((acc, curr) => acc + curr.aiPurchasePrice, 0) / history.reduce((acc, curr) => acc + curr.marketPrice, 0)) * 100).toFixed(1)}%
          </div>
        </div>
        <div className="p-4 rounded-xl border border-[var(--border)] bg-[var(--surface)] border-l-2 border-l-[var(--rarity-restricted)]">
          <div className="flex items-center gap-2.5 mb-2 text-[var(--text-muted)]">
            <Calendar className="w-4 h-4" />
            <span className="text-[10px] font-bold uppercase tracking-widest">最近 30 天</span>
          </div>
          <div className="text-2xl font-mono font-bold">{history.length} 件物品</div>
        </div>
      </div>

      {/* Table */}
      <div className="bg-[var(--surface)] border border-[var(--border)] rounded-xl overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="border-b border-[var(--border)] bg-[var(--bg-subtle)]">
                <th className="px-5 py-3 text-[10px] font-semibold uppercase tracking-wider text-[var(--text-muted)] cursor-pointer hover:text-[var(--text-primary)] transition-colors" onClick={() => handleSort('name')}>
                  <div className="flex items-center gap-1">
                    皮肤名称
                    {sortConfig?.key === 'name' && (sortConfig.direction === 'asc' ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />)}
                  </div>
                </th>
                <th className="px-5 py-3 text-[10px] font-semibold uppercase tracking-wider text-[var(--text-muted)] cursor-pointer hover:text-[var(--text-primary)] transition-colors" onClick={() => handleSort('type')}>
                  <div className="flex items-center gap-1">
                    类型
                    {sortConfig?.key === 'type' && (sortConfig.direction === 'asc' ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />)}
                  </div>
                </th>
                <th className="px-5 py-3 text-[10px] font-semibold uppercase tracking-wider text-[var(--text-muted)] cursor-pointer hover:text-[var(--text-primary)] transition-colors text-right" onClick={() => handleSort('aiPurchasePrice')}>
                  <div className="flex items-center justify-end gap-1">
                    AI 价格
                    {sortConfig?.key === 'aiPurchasePrice' && (sortConfig.direction === 'asc' ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />)}
                  </div>
                </th>
                <th className="px-5 py-3 text-[10px] font-semibold uppercase tracking-wider text-[var(--text-muted)] cursor-pointer hover:text-[var(--text-primary)] transition-colors text-right" onClick={() => handleSort('marketPrice')}>
                  <div className="flex items-center justify-end gap-1">
                    市场价格
                    {sortConfig?.key === 'marketPrice' && (sortConfig.direction === 'asc' ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />)}
                  </div>
                </th>
                <th className="px-5 py-3 text-[10px] font-semibold uppercase tracking-wider text-[var(--text-muted)] cursor-pointer hover:text-[var(--text-primary)] transition-colors text-center" onClick={() => handleSort('aiScore')}>
                  <div className="flex items-center justify-center gap-1">
                    AI 评分
                    {sortConfig?.key === 'aiScore' && (sortConfig.direction === 'asc' ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />)}
                  </div>
                </th>
                <th className="px-5 py-3 text-[10px] font-semibold uppercase tracking-wider text-[var(--text-muted)] cursor-pointer hover:text-[var(--text-primary)] transition-colors text-right" onClick={() => handleSort('purchaseDate')}>
                  <div className="flex items-center justify-end gap-1">
                    购买日期
                    {sortConfig?.key === 'purchaseDate' && (sortConfig.direction === 'asc' ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />)}
                  </div>
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[var(--border)]">
              {filteredHistory.map((record) => (
                <tr key={record.id} className="hover:bg-[var(--surface-hover)]/50 transition-colors group">
                  <td className="px-5 py-3.5">
                    <div className="flex flex-col">
                      <span className="font-medium text-[13px] text-[var(--text-primary)] group-hover:text-[var(--accent)] transition-colors">{record.name}</span>
                      <span className={cn("text-[10px] font-bold uppercase", getRarityColor(record.rarity))}>{record.rarity}</span>
                    </div>
                  </td>
                  <td className="px-5 py-3.5 text-[13px] text-[var(--text-secondary)]">{record.type}</td>
                  <td className="px-5 py-3.5 text-right">
                    <div className="font-mono font-bold text-[var(--up)] text-[13px]">${record.aiPurchasePrice.toLocaleString()}</div>
                  </td>
                  <td className="px-5 py-3.5 text-right">
                    <div className="font-mono text-[var(--text-secondary)] text-[13px]">${record.marketPrice.toLocaleString()}</div>
                  </td>
                  <td className="px-5 py-3.5 text-center">
                    <div className="flex items-center justify-center gap-0.5 relative group/tooltip">
                      <div className="absolute bottom-full mb-2 left-1/2 -translate-x-1/2 px-3 py-2 bg-[var(--surface-elevated)] border border-[var(--border)] rounded-lg shadow-xl opacity-0 group-hover/tooltip:opacity-100 transition-opacity pointer-events-none z-20 min-w-[160px]">
                        <div className="text-[9px] uppercase font-bold text-[var(--text-muted)] mb-2 border-b border-[var(--border)] pb-1 text-left">AI 评分详情</div>
                        <div className="space-y-1.5">
                          <div className="flex justify-between items-center gap-4">
                            <span className="text-[10px] text-[var(--text-secondary)]">波动性:</span>
                            <span className="text-[10px] font-mono font-bold text-[var(--text-primary)]">{record.aiScoreDetails.volatility}/5</span>
                          </div>
                          <div className="flex justify-between items-center gap-4">
                            <span className="text-[10px] text-[var(--text-secondary)]">动量:</span>
                            <span className="text-[10px] font-mono font-bold text-[var(--text-primary)]">{record.aiScoreDetails.momentum}/5</span>
                          </div>
                          <div className="flex justify-between items-center gap-4">
                            <span className="text-[10px] text-[var(--text-secondary)]">趋势:</span>
                            <span className="text-[10px] font-mono font-bold text-[var(--text-primary)]">{record.aiScoreDetails.trend}/5</span>
                          </div>
                        </div>
                      </div>

                      {Array.from({ length: 5 }).map((_, i) => (
                        <Star
                          key={i}
                          className={cn(
                            "w-3 h-3",
                            i < record.aiScore ? "fill-[var(--accent)] text-[var(--accent)]" : "text-[var(--border)]"
                          )}
                        />
                      ))}
                    </div>
                  </td>
                  <td className="px-5 py-3.5 text-right">
                    <div className="flex flex-col items-end">
                      <span className="text-[13px] text-[var(--text-primary)]">{new Date(record.purchaseDate).toLocaleDateString()}</span>
                      <span className="text-[10px] text-[var(--text-muted)]">{new Date(record.purchaseDate).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {filteredHistory.length === 0 && (
        <div className="text-center py-12 text-[var(--text-secondary)]">
          <p>未找到购买记录。</p>
        </div>
      )}
    </motion.div>
  );
}
