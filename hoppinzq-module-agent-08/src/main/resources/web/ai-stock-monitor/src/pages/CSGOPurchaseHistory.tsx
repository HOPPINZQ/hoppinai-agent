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
  Star
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
      case 'Restricted': return 'text-purple-500';
      case 'Classified': return 'text-pink-500';
      case 'Covert': return 'text-red-500';
      case 'Contraband': return 'text-orange-500';
      default: return 'text-slate-400';
    }
  };

  return (
    <motion.div 
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5 }}
      className="space-y-6"
    >
      {/* Header */}
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-[var(--text-primary)] flex items-center gap-2">
            <History className="w-6 h-6 text-[var(--accent)]" />
            AI Purchase History
          </h1>
          <p className="text-[var(--text-secondary)] text-sm mt-1">Historical log of AI-automated skin acquisitions</p>
        </div>

        <div className="flex items-center bg-[var(--surface)] border border-[var(--border)] rounded-lg px-3 py-2 w-full md:w-64 focus-within:border-[var(--accent)] transition-colors">
          <Search className="w-4 h-4 text-[var(--text-secondary)] mr-2" />
          <input 
            type="text" 
            placeholder="Search history..." 
            className="bg-transparent border-none outline-none text-sm w-full text-[var(--text-primary)] placeholder:text-[var(--text-secondary)]"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
        </div>
      </div>

      {/* Summary Stats */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="p-4 rounded-xl border border-[var(--border)] bg-[var(--surface)]">
          <div className="flex items-center gap-3 mb-2 text-[var(--text-secondary)]">
            <DollarSign className="w-4 h-4" />
            <span className="text-xs font-bold uppercase">Total Spent</span>
          </div>
          <div className="text-2xl font-mono font-bold">${history.reduce((acc, curr) => acc + curr.aiPurchasePrice, 0).toLocaleString()}</div>
        </div>
        <div className="p-4 rounded-xl border border-[var(--border)] bg-[var(--surface)]">
          <div className="flex items-center gap-3 mb-2 text-[var(--text-secondary)]">
            <TrendingUp className="w-4 h-4" />
            <span className="text-xs font-bold uppercase">Avg. Savings</span>
          </div>
          <div className="text-2xl font-mono font-bold text-[var(--up)]">
            {((1 - history.reduce((acc, curr) => acc + curr.aiPurchasePrice, 0) / history.reduce((acc, curr) => acc + curr.marketPrice, 0)) * 100).toFixed(1)}%
          </div>
        </div>
        <div className="p-4 rounded-xl border border-[var(--border)] bg-[var(--surface)]">
          <div className="flex items-center gap-3 mb-2 text-[var(--text-secondary)]">
            <Calendar className="w-4 h-4" />
            <span className="text-xs font-bold uppercase">Last 30 Days</span>
          </div>
          <div className="text-2xl font-mono font-bold">{history.length} Items</div>
        </div>
      </div>

      {/* History Table */}
      <div className="bg-[var(--surface)] border border-[var(--border)] rounded-xl overflow-hidden shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="border-b border-[var(--border)] bg-[var(--surface-hover)]/50">
                <th className="px-6 py-4 text-xs font-semibold uppercase tracking-wider text-[var(--text-secondary)] cursor-pointer hover:text-[var(--text-primary)] transition-colors" onClick={() => handleSort('name')}>
                  <div className="flex items-center gap-1">
                    Skin Name
                    {sortConfig?.key === 'name' && (sortConfig.direction === 'asc' ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />)}
                  </div>
                </th>
                <th className="px-6 py-4 text-xs font-semibold uppercase tracking-wider text-[var(--text-secondary)] cursor-pointer hover:text-[var(--text-primary)] transition-colors" onClick={() => handleSort('type')}>
                  <div className="flex items-center gap-1">
                    Type
                    {sortConfig?.key === 'type' && (sortConfig.direction === 'asc' ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />)}
                  </div>
                </th>
                <th className="px-6 py-4 text-xs font-semibold uppercase tracking-wider text-[var(--text-secondary)] cursor-pointer hover:text-[var(--text-primary)] transition-colors text-right" onClick={() => handleSort('aiPurchasePrice')}>
                  <div className="flex items-center justify-end gap-1">
                    AI Price
                    {sortConfig?.key === 'aiPurchasePrice' && (sortConfig.direction === 'asc' ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />)}
                  </div>
                </th>
                <th className="px-6 py-4 text-xs font-semibold uppercase tracking-wider text-[var(--text-secondary)] cursor-pointer hover:text-[var(--text-primary)] transition-colors text-right" onClick={() => handleSort('marketPrice')}>
                  <div className="flex items-center justify-end gap-1">
                    Market Price
                    {sortConfig?.key === 'marketPrice' && (sortConfig.direction === 'asc' ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />)}
                  </div>
                </th>
                <th className="px-6 py-4 text-xs font-semibold uppercase tracking-wider text-[var(--text-secondary)] cursor-pointer hover:text-[var(--text-primary)] transition-colors text-center" onClick={() => handleSort('aiScore')}>
                  <div className="flex items-center justify-center gap-1">
                    AI Score
                    {sortConfig?.key === 'aiScore' && (sortConfig.direction === 'asc' ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />)}
                  </div>
                </th>
                <th className="px-6 py-4 text-xs font-semibold uppercase tracking-wider text-[var(--text-secondary)] cursor-pointer hover:text-[var(--text-primary)] transition-colors text-right" onClick={() => handleSort('purchaseDate')}>
                  <div className="flex items-center justify-end gap-1">
                    Purchase Date
                    {sortConfig?.key === 'purchaseDate' && (sortConfig.direction === 'asc' ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />)}
                  </div>
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[var(--border)]">
              {filteredHistory.map((record) => (
                <tr key={record.id} className="hover:bg-[var(--surface-hover)]/30 transition-colors group">
                  <td className="px-6 py-4">
                    <div className="flex flex-col">
                      <span className="font-medium text-[var(--text-primary)] group-hover:text-[var(--accent)] transition-colors">{record.name}</span>
                      <span className={cn("text-[10px] font-bold uppercase", getRarityColor(record.rarity))}>{record.rarity}</span>
                    </div>
                  </td>
                  <td className="px-6 py-4 text-sm text-[var(--text-secondary)]">{record.type}</td>
                  <td className="px-6 py-4 text-right">
                    <div className="font-mono font-bold text-[var(--up)]">${record.aiPurchasePrice.toLocaleString()}</div>
                  </td>
                  <td className="px-6 py-4 text-right">
                    <div className="font-mono text-[var(--text-secondary)]">${record.marketPrice.toLocaleString()}</div>
                  </td>
                  <td className="px-6 py-4 text-center">
                    <div className="flex items-center justify-center gap-0.5 relative group/tooltip">
                      {/* AI Score Breakdown Tooltip */}
                      <div className="absolute bottom-full mb-2 left-1/2 -translate-x-1/2 px-3 py-2 bg-[var(--surface-hover)] border border-[var(--border)] rounded-lg shadow-xl opacity-0 group-hover/tooltip:opacity-100 transition-opacity pointer-events-none z-20 backdrop-blur-md min-w-[160px]">
                        <div className="text-[10px] uppercase font-bold text-[var(--text-secondary)] mb-2 border-b border-[var(--border)] pb-1 text-left">AI Score Details</div>
                        <div className="space-y-1.5">
                          <div className="flex justify-between items-center gap-4">
                            <span className="text-[10px] text-[var(--text-secondary)]">Volatility:</span>
                            <span className="text-[10px] font-mono font-bold text-[var(--text-primary)]">{record.aiScoreDetails.volatility}/5</span>
                          </div>
                          <div className="flex justify-between items-center gap-4">
                            <span className="text-[10px] text-[var(--text-secondary)]">Momentum:</span>
                            <span className="text-[10px] font-mono font-bold text-[var(--text-primary)]">{record.aiScoreDetails.momentum}/5</span>
                          </div>
                          <div className="flex justify-between items-center gap-4">
                            <span className="text-[10px] text-[var(--text-secondary)]">Trend:</span>
                            <span className="text-[10px] font-mono font-bold text-[var(--text-primary)]">{record.aiScoreDetails.trend}/5</span>
                          </div>
                        </div>
                        <div className="absolute bottom-[-6px] left-1/2 -translate-x-1/2 w-3 h-3 bg-[var(--surface-hover)] border-r border-b border-[var(--border)] rotate-45"></div>
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
                  <td className="px-6 py-4 text-right">
                    <div className="flex flex-col items-end">
                      <span className="text-sm text-[var(--text-primary)]">{new Date(record.purchaseDate).toLocaleDateString()}</span>
                      <span className="text-[10px] text-[var(--text-secondary)]">{new Date(record.purchaseDate).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
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
          <p>No purchase records found.</p>
        </div>
      )}
    </motion.div>
  );
}
