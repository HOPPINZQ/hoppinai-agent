import { useState, useMemo } from "react";
import { motion, AnimatePresence } from "motion/react";
import {
  ArrowUpRight,
  ArrowDownRight,
  TrendingUp,
  Activity,
  Star,
  Filter,
  ChevronDown,
  ChevronUp,
  X,
  Clock,
  DollarSign,
  BarChart3,
  Crosshair
} from "lucide-react";
import {
  LineChart,
  Line,
  ResponsiveContainer,
  Tooltip,
  YAxis,
  XAxis,
  AreaChart,
  Area
} from "recharts";
import { cn } from "@/lib/utils";
import { generateMockStocks, mockPortfolioTrend, StockData, generateMockStockHistory, generateMockCSGOItems, CSGOItem } from "@/mockData";

const mockStocks = generateMockStocks(30);
const mockCSGO = generateMockCSGOItems(20);

export function Dashboard() {
  const [stocks, setStocks] = useState<StockData[]>(mockStocks);
  const [csgoItems, setCsgoItems] = useState<CSGOItem[]>(mockCSGO);
  const [activeTab, setActiveTab] = useState<'stocks' | 'csgo'>('stocks');
  const [sortConfig, setSortConfig] = useState<{ key: string; direction: 'asc' | 'desc' } | null>(null);
  const [filterAction, setFilterAction] = useState<string | null>(null);
  const [selectedStock, setSelectedStock] = useState<StockData | null>(null);
  const [selectedCSGO, setSelectedCSGO] = useState<CSGOItem | null>(null);

  const stockHistory = useMemo(() => {
    if (!selectedStock) return [];
    return generateMockStockHistory(selectedStock.price, 20);
  }, [selectedStock]);

  const csgoHistory = useMemo(() => {
    if (!selectedCSGO) return [];
    return generateMockStockHistory(selectedCSGO.marketPrice, 20);
  }, [selectedCSGO]);

  const handleSort = (key: string) => {
    let direction: 'asc' | 'desc' = 'asc';
    if (sortConfig && sortConfig.key === key && sortConfig.direction === 'asc') {
      direction = 'desc';
    }
    setSortConfig({ key, direction });

    if (activeTab === 'stocks') {
      const sorted = [...stocks].sort((a, b) => {
        const valA = a[key as keyof StockData];
        const valB = b[key as keyof StockData];
        if (valA < valB) return direction === 'asc' ? -1 : 1;
        if (valA > valB) return direction === 'asc' ? 1 : -1;
        return 0;
      });
      setStocks(sorted);
    } else {
      const sorted = [...csgoItems].sort((a, b) => {
        const valA = a[key as keyof CSGOItem];
        const valB = b[key as keyof CSGOItem];
        if (valA < valB) return direction === 'asc' ? -1 : 1;
        if (valA > valB) return direction === 'asc' ? 1 : -1;
        return 0;
      });
      setCsgoItems(sorted);
    }
  };

  const filteredStocks = filterAction
    ? stocks.filter(s => s.aiAction === filterAction)
    : stocks;

  const SortIcon = ({ columnKey }: { columnKey: string }) => {
    if (sortConfig?.key !== columnKey) return <ChevronDown className="w-3 h-3 opacity-30 ml-1" />;
    return sortConfig.direction === 'asc'
      ? <ChevronUp className="w-3 h-3 text-[var(--accent)] ml-1" />
      : <ChevronDown className="w-3 h-3 text-[var(--accent)] ml-1" />;
  };

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
      {/* Header Section */}
      <div className="flex justify-between items-end">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-[var(--text-primary)] font-display uppercase">
            市场概览
          </h1>
          <p className="text-[var(--text-secondary)] text-sm mt-0.5">实时 AI 分析与市场数据</p>
        </div>
        <div className="flex gap-2">
          <button className="px-4 py-2 bg-[var(--surface)] border border-[var(--border)] rounded-lg text-sm font-medium hover:bg-[var(--surface-hover)] transition-colors flex items-center gap-2 text-[var(--text-secondary)]">
            <Activity className="w-4 h-4" />
            实时更新: <span className="text-[var(--up)]">ON</span>
          </button>
        </div>
      </div>

      {/* Top Cards */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
        {/* Portfolio Trend Card */}
        <div className="bg-[var(--surface)] border border-[var(--border)] rounded-xl p-5 col-span-1 lg:col-span-2 flex flex-col justify-between min-h-[220px] hover:border-[var(--accent)]/20 transition-all duration-300 relative overflow-hidden card-glow">
          <div className="flex justify-between items-start mb-4">
            <div>
              <h3 className="text-[var(--text-secondary)] text-[11px] font-semibold uppercase tracking-widest">总资产价值</h3>
              <div className="text-3xl font-mono font-bold mt-1 flex items-baseline gap-3">
                $124,500.00
                <span className="text-[var(--up)] text-sm font-sans flex items-center bg-[var(--up)]/10 px-2 py-0.5 rounded">
                  <ArrowUpRight className="w-3 h-3 mr-1" />
                  +2.4%
                </span>
              </div>
            </div>
            <div className="p-2 bg-[var(--accent)]/10 rounded-lg">
              <TrendingUp className="w-5 h-5 text-[var(--accent)]" />
            </div>
          </div>
          <div className="h-28 w-full mt-auto">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={mockPortfolioTrend}>
                <Tooltip
                  contentStyle={{ backgroundColor: 'var(--surface)', borderColor: 'var(--border)', borderRadius: '8px', fontSize: '12px' }}
                  itemStyle={{ color: 'var(--text-primary)', fontFamily: 'var(--font-mono)' }}
                  labelStyle={{ color: 'var(--text-secondary)' }}
                />
                <Line
                  type="monotone"
                  dataKey="value"
                  stroke="var(--accent)"
                  strokeWidth={2}
                  dot={false}
                  activeDot={{ r: 4, fill: 'var(--accent)' }}
                />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* AI Summary Card */}
        <div className="bg-[var(--surface)] border border-[var(--border)] rounded-xl p-5 flex flex-col min-h-[220px] hover:border-[var(--accent)]/20 transition-all duration-300 relative overflow-hidden">
          {/* Top accent */}
          <div className="absolute top-0 left-0 right-0 h-[2px] bg-gradient-to-r from-transparent via-[var(--accent)]/40 to-transparent" />
          <h3 className="text-[var(--text-secondary)] text-[11px] font-semibold uppercase tracking-widest mb-4">AI 市场情绪</h3>
          <div className="flex-1 flex flex-col justify-center items-center text-center">
            <div className="w-20 h-20 rounded-full border-2 border-[var(--up)]/20 flex items-center justify-center mb-3 relative">
              <div className="absolute inset-0 rounded-full border-2 border-[var(--up)] border-t-transparent animate-[spin_3s_linear_infinite]"></div>
              <span className="text-lg font-bold text-[var(--up)] font-display uppercase">看涨</span>
            </div>
            <p className="text-[12px] text-[var(--text-secondary)] leading-relaxed">
              AI 预测未来 48 小时内科技板块有 <span className="text-[var(--text-primary)] font-medium">78%</span> 的概率延续上涨势头。
            </p>
          </div>
        </div>
      </div>

      {/* Data Table Section */}
      <div className="bg-[var(--surface)] border border-[var(--border)] rounded-xl overflow-hidden flex flex-col">
        <div className="px-4 py-3 border-b border-[var(--border)] flex flex-col md:flex-row justify-between items-start md:items-center gap-3">
          <div className="flex bg-[var(--bg)] p-0.5 rounded-lg border border-[var(--border)]">
            <button
              onClick={() => { setActiveTab('stocks'); setSortConfig(null); }}
              className={cn(
                "px-4 py-1.5 rounded-md text-[12px] font-bold uppercase tracking-wider transition-all font-display",
                activeTab === 'stocks' ? "bg-[var(--accent)] text-[var(--bg)]" : "text-[var(--text-secondary)] hover:text-[var(--text-primary)]"
              )}
            >
              AI 股票筛选
            </button>
            <button
              onClick={() => { setActiveTab('csgo'); setSortConfig(null); }}
              className={cn(
                "px-4 py-1.5 rounded-md text-[12px] font-bold uppercase tracking-wider transition-all font-display",
                activeTab === 'csgo' ? "bg-[var(--accent)] text-[var(--bg)]" : "text-[var(--text-secondary)] hover:text-[var(--text-primary)]"
              )}
            >
              CSGO 热门商品
            </button>
          </div>

          <div className="flex items-center gap-2">
            <Filter className="w-4 h-4 text-[var(--text-secondary)]" />
            <select
              className="bg-transparent border border-[var(--border)] rounded-lg px-2.5 py-1 text-[12px] text-[var(--text-primary)] outline-none focus:border-[var(--accent)]/40"
              value={filterAction || ''}
              onChange={(e) => setFilterAction(e.target.value || null)}
            >
              <option value="">全部操作</option>
              <option value="Buy">推荐买入</option>
              <option value="Hold">持有</option>
              <option value="Sell">推荐卖出</option>
            </select>
          </div>
        </div>

        <div className="overflow-x-auto">
          {activeTab === 'stocks' ? (
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="border-b border-[var(--border)] bg-[var(--bg-subtle)] text-[10px] uppercase tracking-wider text-[var(--text-muted)]">
                  <th className="p-4 font-semibold cursor-pointer hover:text-[var(--text-primary)] transition-colors" onClick={() => handleSort('symbol')}>
                    <div className="flex items-center">代码 <SortIcon columnKey="symbol" /></div>
                  </th>
                  <th className="p-4 font-semibold cursor-pointer hover:text-[var(--text-primary)] transition-colors" onClick={() => handleSort('name')}>
                    <div className="flex items-center">名称 <SortIcon columnKey="name" /></div>
                  </th>
                  <th className="p-4 font-semibold cursor-pointer hover:text-[var(--text-primary)] transition-colors text-right" onClick={() => handleSort('price')}>
                    <div className="flex items-center justify-end">价格 <SortIcon columnKey="price" /></div>
                  </th>
                  <th className="p-4 font-semibold cursor-pointer hover:text-[var(--text-primary)] transition-colors text-right" onClick={() => handleSort('changePercent')}>
                    <div className="flex items-center justify-end">24h 涨跌 <SortIcon columnKey="changePercent" /></div>
                  </th>
                  <th className="p-4 font-semibold cursor-pointer hover:text-[var(--text-primary)] transition-colors text-center" onClick={() => handleSort('aiScore')}>
                    <div className="flex items-center justify-center">AI 评分 <SortIcon columnKey="aiScore" /></div>
                  </th>
                  <th className="p-4 font-semibold cursor-pointer hover:text-[var(--text-primary)] transition-colors text-right" onClick={() => handleSort('aiProfitLoss')}>
                    <div className="flex items-center justify-end">AI 预测 <SortIcon columnKey="aiProfitLoss" /></div>
                  </th>
                  <th className="p-4 font-semibold cursor-pointer hover:text-[var(--text-primary)] transition-colors text-center" onClick={() => handleSort('aiAction')}>
                    <div className="flex items-center justify-center">操作 <SortIcon columnKey="aiAction" /></div>
                  </th>
                  <th className="p-4 font-semibold cursor-pointer hover:text-[var(--text-primary)] transition-colors text-right" onClick={() => handleSort('updatedAt')}>
                    <div className="flex items-center justify-end">更新 <SortIcon columnKey="updatedAt" /></div>
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--border)]">
                {filteredStocks.map((stock) => (
                  <tr
                    key={stock.id}
                    className="hover:bg-[var(--surface-hover)]/50 transition-colors cursor-pointer group"
                    onClick={() => setSelectedStock(stock)}
                  >
                    <td className="p-4">
                      <span className="font-mono font-bold text-[var(--text-primary)] bg-[var(--bg)] px-2 py-0.5 rounded text-[12px] border border-[var(--border)]">
                        {stock.symbol}
                      </span>
                    </td>
                    <td className="p-4 text-[13px] text-[var(--text-secondary)] group-hover:text-[var(--text-primary)] transition-colors">
                      {stock.name}
                    </td>
                    <td className="p-4 text-right font-mono text-[13px]">
                      ${stock.price.toFixed(2)}
                    </td>
                    <td className={cn(
                      "p-4 text-right font-mono text-[13px] flex items-center justify-end gap-1",
                      stock.change >= 0 ? "text-[var(--up)]" : "text-[var(--down)]"
                    )}>
                      {stock.change >= 0 ? <ArrowUpRight className="w-3 h-3" /> : <ArrowDownRight className="w-3 h-3" />}
                      {Math.abs(stock.changePercent).toFixed(2)}%
                    </td>
                    <td className="p-4 text-center">
                      <div className="flex items-center justify-center gap-0.5 relative group/tooltip">
                        {/* AI Score Breakdown Tooltip */}
                        <div className="absolute bottom-full mb-2 left-1/2 -translate-x-1/2 px-3 py-2 bg-[var(--surface-elevated)] border border-[var(--border)] rounded-lg shadow-xl opacity-0 group-hover/tooltip:opacity-100 transition-opacity pointer-events-none z-20 min-w-[160px]">
                          <div className="text-[9px] uppercase font-bold text-[var(--text-muted)] mb-2 border-b border-[var(--border)] pb-1">AI 评分详情</div>
                          <div className="space-y-1.5">
                            <div className="flex justify-between items-center gap-4">
                              <span className="text-[10px] text-[var(--text-secondary)]">波动性:</span>
                              <span className="text-[10px] font-mono font-bold text-[var(--text-primary)]">{stock.aiScoreDetails.volatility}/5</span>
                            </div>
                            <div className="flex justify-between items-center gap-4">
                              <span className="text-[10px] text-[var(--text-secondary)]">动量:</span>
                              <span className="text-[10px] font-mono font-bold text-[var(--text-primary)]">{stock.aiScoreDetails.momentum}/5</span>
                            </div>
                            <div className="flex justify-between items-center gap-4">
                              <span className="text-[10px] text-[var(--text-secondary)]">趋势:</span>
                              <span className="text-[10px] font-mono font-bold text-[var(--text-primary)]">{stock.aiScoreDetails.trend}/5</span>
                            </div>
                          </div>
                        </div>

                        {Array.from({ length: 5 }).map((_, i) => (
                          <Star
                            key={i}
                            className={cn(
                              "w-3 h-3",
                              i < stock.aiScore ? "fill-[var(--accent)] text-[var(--accent)]" : "text-[var(--border)]"
                            )}
                          />
                        ))}
                      </div>
                    </td>
                    <td className={cn(
                      "p-4 text-right font-mono text-[13px]",
                      stock.aiProfitLoss >= 0 ? "text-[var(--up)]" : "text-[var(--down)]"
                    )}>
                      {stock.aiProfitLoss >= 0 ? '+' : '-'}${Math.abs(stock.aiProfitLoss).toFixed(2)}
                      <span className="text-[11px] ml-1 opacity-60">
                        ({stock.aiProfitLossPercent > 0 ? '+' : ''}{stock.aiProfitLossPercent.toFixed(2)}%)
                      </span>
                    </td>
                    <td className="p-4 text-center">
                      <span className={cn(
                        "px-2.5 py-1 rounded text-[10px] font-bold uppercase tracking-wider inline-block min-w-[50px]",
                        stock.aiAction === 'Buy' && "bg-[var(--up)]/10 text-[var(--up)] border border-[var(--up)]/20",
                        stock.aiAction === 'Sell' && "bg-[var(--down)]/10 text-[var(--down)] border border-[var(--down)]/20",
                        stock.aiAction === 'Hold' && "bg-[var(--text-secondary)]/10 text-[var(--text-secondary)] border border-[var(--text-secondary)]/20"
                      )}>
                        {stock.aiAction === 'Buy' ? '买入' : stock.aiAction === 'Sell' ? '卖出' : '持有'}
                      </span>
                    </td>
                    <td className="p-4 text-right text-[11px] text-[var(--text-muted)] font-mono">
                      {new Date(stock.updatedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="border-b border-[var(--border)] bg-[var(--bg-subtle)] text-[10px] uppercase tracking-wider text-[var(--text-muted)]">
                  <th className="p-4 font-semibold cursor-pointer hover:text-[var(--text-primary)] transition-colors" onClick={() => handleSort('name')}>
                    <div className="flex items-center">皮肤名称 <SortIcon columnKey="name" /></div>
                  </th>
                  <th className="p-4 font-semibold cursor-pointer hover:text-[var(--text-primary)] transition-colors" onClick={() => handleSort('type')}>
                    <div className="flex items-center">类型 <SortIcon columnKey="type" /></div>
                  </th>
                  <th className="p-4 font-semibold cursor-pointer hover:text-[var(--text-primary)] transition-colors text-right" onClick={() => handleSort('marketPrice')}>
                    <div className="flex items-center justify-end">市场价格 <SortIcon columnKey="marketPrice" /></div>
                  </th>
                  <th className="p-4 font-semibold cursor-pointer hover:text-[var(--text-primary)] transition-colors text-right" onClick={() => handleSort('aiPurchasePrice')}>
                    <div className="flex items-center justify-end">AI 买入价 <SortIcon columnKey="aiPurchasePrice" /></div>
                  </th>
                  <th className="p-4 font-semibold cursor-pointer hover:text-[var(--text-primary)] transition-colors text-center" onClick={() => handleSort('aiScore')}>
                    <div className="flex items-center justify-center">AI 评分 <SortIcon columnKey="aiScore" /></div>
                  </th>
                  <th className="p-4 font-semibold cursor-pointer hover:text-[var(--text-primary)] transition-colors text-right">
                    <div className="flex items-center justify-end">潜力</div>
                  </th>
                  <th className="p-4 font-semibold cursor-pointer hover:text-[var(--text-primary)] transition-colors text-right" onClick={() => handleSort('updatedAt')}>
                    <div className="flex items-center justify-end">更新 <SortIcon columnKey="updatedAt" /></div>
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--border)]">
                {csgoItems.map((item) => (
                  <tr
                    key={item.id}
                    className="hover:bg-[var(--surface-hover)]/50 transition-colors cursor-pointer group"
                    onClick={() => setSelectedCSGO(item)}
                  >
                    <td className="p-4">
                      <div className="flex flex-col">
                        <span className="font-bold text-[var(--text-primary)] group-hover:text-[var(--accent)] transition-colors text-[13px]">{item.name}</span>
                        <span className={cn("text-[10px] font-bold uppercase", getRarityColor(item.rarity))}>{item.rarity}</span>
                      </div>
                    </td>
                    <td className="p-4 text-[13px] text-[var(--text-secondary)]">
                      {item.type}
                    </td>
                    <td className="p-4 text-right font-mono text-[13px]">
                      ${item.marketPrice.toLocaleString()}
                    </td>
                    <td className="p-4 text-right font-mono text-[13px] text-[var(--up)] font-bold">
                      ${item.aiPurchasePrice.toLocaleString()}
                    </td>
                    <td className="p-4 text-center">
                      <div className="flex items-center justify-center gap-0.5">
                        {Array.from({ length: 5 }).map((_, i) => (
                          <Star
                            key={i}
                            className={cn(
                              "w-3 h-3",
                              i < item.aiScore ? "fill-[var(--accent)] text-[var(--accent)]" : "text-[var(--border)]"
                            )}
                          />
                        ))}
                      </div>
                    </td>
                    <td className="p-4 text-right font-mono text-[13px] text-[var(--accent)] font-bold">
                      {((1 - item.aiPurchasePrice / item.marketPrice) * 100).toFixed(1)}%
                    </td>
                    <td className="p-4 text-right text-[11px] text-[var(--text-muted)] font-mono">
                      {new Date(item.updatedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          {((activeTab === 'stocks' && filteredStocks.length === 0) || (activeTab === 'csgo' && csgoItems.length === 0)) && (
            <div className="p-8 text-center text-[var(--text-secondary)]">
              没有匹配当前条件的物品。
            </div>
          )}
        </div>
      </div>

      {/* Stock Detail Modal */}
      <AnimatePresence>
        {selectedStock && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 md:p-8">
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={() => setSelectedStock(null)}
              className="absolute inset-0 bg-black/70 backdrop-blur-sm"
            />
            <motion.div
              initial={{ scale: 0.9, opacity: 0, y: 20 }}
              animate={{ scale: 1, opacity: 1, y: 0 }}
              exit={{ scale: 0.9, opacity: 0, y: 20 }}
              className="relative w-full max-w-2xl bg-[var(--bg)] rounded-xl shadow-2xl border border-[var(--border)] overflow-hidden flex flex-col"
            >
              {/* Top accent line */}
              <div className="h-[2px] bg-gradient-to-r from-transparent via-[var(--accent)] to-transparent" />

              {/* Modal Header */}
              <div className="p-5 border-b border-[var(--border)] flex justify-between items-center bg-[var(--surface)]">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-lg bg-[var(--accent)]/10 border border-[var(--accent)]/20 flex items-center justify-center text-[var(--accent)] font-bold font-mono">
                    {selectedStock.symbol[0]}
                  </div>
                  <div>
                    <h2 className="text-lg font-bold text-[var(--text-primary)] flex items-center gap-2 font-display">
                      {selectedStock.name}
                      <span className="text-[11px] font-mono text-[var(--text-secondary)] bg-[var(--bg)] px-2 py-0.5 rounded border border-[var(--border)]">
                        {selectedStock.symbol}
                      </span>
                    </h2>
                    <p className="text-[11px] text-[var(--text-muted)] flex items-center gap-1.5">
                      <Clock className="w-3 h-3" />
                      {new Date(selectedStock.updatedAt).toLocaleTimeString()}
                    </p>
                  </div>
                </div>
                <button
                  onClick={() => setSelectedStock(null)}
                  className="p-2 hover:bg-[var(--surface-hover)] rounded-lg transition-colors"
                >
                  <X className="w-5 h-5 text-[var(--text-secondary)]" />
                </button>
              </div>

              {/* Modal Content */}
              <div className="p-5 space-y-5 overflow-y-auto max-h-[70vh]">
                <div className="grid grid-cols-2 gap-3">
                  <div className="p-4 rounded-xl bg-[var(--surface)] border border-[var(--border)]">
                    <div className="text-[10px] text-[var(--text-muted)] uppercase font-bold mb-1 flex items-center gap-1.5">
                      <DollarSign className="w-3 h-3" />
                      当前价格
                    </div>
                    <div className="text-2xl font-mono font-bold text-[var(--text-primary)]">
                      ${selectedStock.price.toFixed(2)}
                    </div>
                  </div>
                  <div className="p-4 rounded-xl bg-[var(--surface)] border border-[var(--border)]">
                    <div className="text-[10px] text-[var(--text-muted)] uppercase font-bold mb-1 flex items-center gap-1.5">
                      <Activity className="w-3 h-3" />
                      24h 涨跌
                    </div>
                    <div className={cn(
                      "text-2xl font-mono font-bold flex items-center gap-2",
                      selectedStock.change >= 0 ? "text-[var(--up)]" : "text-[var(--down)]"
                    )}>
                      {selectedStock.change >= 0 ? <ArrowUpRight className="w-5 h-5" /> : <ArrowDownRight className="w-5 h-5" />}
                      {Math.abs(selectedStock.changePercent).toFixed(2)}%
                    </div>
                  </div>
                </div>

                <div className="grid grid-cols-3 gap-3">
                  <div className="p-3 rounded-xl bg-[var(--surface)] border border-[var(--border)]">
                    <div className="text-[9px] text-[var(--text-muted)] uppercase font-bold mb-2">AI 评分</div>
                    <div className="flex items-center gap-0.5">
                      {Array.from({ length: 5 }).map((_, i) => (
                        <Star
                          key={i}
                          className={cn(
                            "w-4 h-4",
                            i < selectedStock.aiScore ? "fill-[var(--accent)] text-[var(--accent)]" : "text-[var(--border)]"
                          )}
                        />
                      ))}
                    </div>
                  </div>
                  <div className="p-3 rounded-xl bg-[var(--surface)] border border-[var(--border)]">
                    <div className="text-[9px] text-[var(--text-muted)] uppercase font-bold mb-1">AI 预测盈亏</div>
                    <div className={cn(
                      "text-lg font-mono font-bold",
                      selectedStock.aiProfitLoss >= 0 ? "text-[var(--up)]" : "text-[var(--down)]"
                    )}>
                      {selectedStock.aiProfitLoss >= 0 ? '+' : '-'}${Math.abs(selectedStock.aiProfitLoss).toFixed(2)}
                    </div>
                  </div>
                  <div className="p-3 rounded-xl bg-[var(--surface)] border border-[var(--border)]">
                    <div className="text-[9px] text-[var(--text-muted)] uppercase font-bold mb-1">AI 建议</div>
                    <span className={cn(
                      "px-2 py-0.5 rounded text-[10px] font-bold uppercase tracking-wider inline-block",
                      selectedStock.aiAction === 'Buy' && "bg-[var(--up)]/10 text-[var(--up)] border border-[var(--up)]/20",
                      selectedStock.aiAction === 'Sell' && "bg-[var(--down)]/10 text-[var(--down)] border border-[var(--down)]/20",
                      selectedStock.aiAction === 'Hold' && "bg-[var(--text-secondary)]/10 text-[var(--text-secondary)] border border-[var(--text-secondary)]/20"
                    )}>
                      {selectedStock.aiAction === 'Buy' ? '买入' : selectedStock.aiAction === 'Sell' ? '卖出' : '持有'}
                    </span>
                  </div>
                </div>

                <div className="p-4 rounded-xl bg-[var(--surface)] border border-[var(--border)]">
                  <div className="flex justify-between items-center mb-3">
                    <div className="text-[10px] text-[var(--text-muted)] uppercase font-bold flex items-center gap-1.5">
                      <BarChart3 className="w-3 h-3" />
                      20 日走势
                    </div>
                  </div>
                  <div className="h-44 w-full">
                    <ResponsiveContainer width="100%" height="100%">
                      <AreaChart data={stockHistory}>
                        <defs>
                          <linearGradient id="colorPrice" x1="0" y1="0" x2="0" y2="1">
                            <stop offset="5%" stopColor="var(--accent)" stopOpacity={0.2}/>
                            <stop offset="95%" stopColor="var(--accent)" stopOpacity={0}/>
                          </linearGradient>
                        </defs>
                        <XAxis dataKey="date" hide />
                        <YAxis domain={['auto', 'auto']} hide />
                        <Tooltip
                          contentStyle={{ backgroundColor: 'var(--surface)', borderColor: 'var(--border)', borderRadius: '8px', fontSize: '12px' }}
                          itemStyle={{ color: 'var(--text-primary)', fontFamily: 'var(--font-mono)' }}
                          labelStyle={{ color: 'var(--text-secondary)' }}
                        />
                        <Area
                          type="monotone"
                          dataKey="price"
                          stroke="var(--accent)"
                          fillOpacity={1}
                          fill="url(#colorPrice)"
                          strokeWidth={2}
                        />
                      </AreaChart>
                    </ResponsiveContainer>
                  </div>
                </div>
              </div>

              <div className="p-5 border-t border-[var(--border)] bg-[var(--surface)] flex justify-end">
                <button
                  onClick={() => setSelectedStock(null)}
                  className="px-6 py-2 bg-[var(--accent)] text-[var(--bg)] rounded-lg text-[12px] font-bold hover:bg-[var(--accent-hover)] transition-colors uppercase tracking-wider font-display"
                >
                  关闭详情
                </button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      {/* CSGO Detail Modal */}
      <AnimatePresence>
        {selectedCSGO && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 md:p-8">
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={() => setSelectedCSGO(null)}
              className="absolute inset-0 bg-black/70 backdrop-blur-sm"
            />
            <motion.div
              initial={{ scale: 0.9, opacity: 0, y: 20 }}
              animate={{ scale: 1, opacity: 1, y: 0 }}
              exit={{ scale: 0.9, opacity: 0, y: 20 }}
              className="relative w-full max-w-2xl bg-[var(--bg)] rounded-xl shadow-2xl border border-[var(--border)] overflow-hidden flex flex-col"
            >
              <div className="h-[2px] bg-gradient-to-r from-transparent via-[var(--accent)] to-transparent" />

              <div className="p-5 border-b border-[var(--border)] flex justify-between items-center bg-[var(--surface)]">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-lg bg-[var(--accent)]/10 border border-[var(--accent)]/20 flex items-center justify-center text-[var(--accent)]">
                    <Crosshair className="w-5 h-5" />
                  </div>
                  <div>
                    <h2 className="text-lg font-bold text-[var(--text-primary)] flex items-center gap-2 font-display">
                      {selectedCSGO.name}
                      <span className={cn("text-[10px] font-bold uppercase px-2 py-0.5 rounded bg-[var(--bg)] border border-[var(--border)]", getRarityColor(selectedCSGO.rarity))}>
                        {selectedCSGO.rarity}
                      </span>
                    </h2>
                    <p className="text-[11px] text-[var(--text-muted)] flex items-center gap-1.5">
                      <Clock className="w-3 h-3" />
                      {new Date(selectedCSGO.updatedAt).toLocaleTimeString()}
                    </p>
                  </div>
                </div>
                <button
                  onClick={() => setSelectedCSGO(null)}
                  className="p-2 hover:bg-[var(--surface-hover)] rounded-lg transition-colors"
                >
                  <X className="w-5 h-5 text-[var(--text-secondary)]" />
                </button>
              </div>

              <div className="p-5 space-y-5 overflow-y-auto max-h-[70vh]">
                <div className="grid grid-cols-2 gap-3">
                  <div className="p-4 rounded-xl bg-[var(--surface)] border border-[var(--border)]">
                    <div className="text-[10px] text-[var(--text-muted)] uppercase font-bold mb-1 flex items-center gap-1.5">
                      <DollarSign className="w-3 h-3" />
                      市场价格
                    </div>
                    <div className="text-2xl font-mono font-bold text-[var(--text-primary)]">
                      ${selectedCSGO.marketPrice.toLocaleString()}
                    </div>
                  </div>
                  <div className="p-4 rounded-xl bg-[var(--surface)] border border-[var(--border)]">
                    <div className="text-[10px] text-[var(--text-muted)] uppercase font-bold mb-1 flex items-center gap-1.5">
                      <TrendingUp className="w-3 h-3" />
                      AI 目标价格
                    </div>
                    <div className="text-2xl font-mono font-bold text-[var(--up)]">
                      ${selectedCSGO.aiPurchasePrice.toLocaleString()}
                    </div>
                  </div>
                </div>

                <div className="grid grid-cols-3 gap-3">
                  <div className="p-3 rounded-xl bg-[var(--surface)] border border-[var(--border)]">
                    <div className="text-[9px] text-[var(--text-muted)] uppercase font-bold mb-2">AI 评分</div>
                    <div className="flex items-center gap-0.5">
                      {Array.from({ length: 5 }).map((_, i) => (
                        <Star
                          key={i}
                          className={cn(
                            "w-4 h-4",
                            i < selectedCSGO.aiScore ? "fill-[var(--accent)] text-[var(--accent)]" : "text-[var(--border)]"
                          )}
                        />
                      ))}
                    </div>
                  </div>
                  <div className="p-3 rounded-xl bg-[var(--surface)] border border-[var(--border)]">
                    <div className="text-[9px] text-[var(--text-muted)] uppercase font-bold mb-1">AI 建议</div>
                    <span className={cn(
                      "px-2 py-0.5 rounded text-[10px] font-bold uppercase tracking-wider inline-block",
                      selectedCSGO.aiAction === 'Buy' && "bg-[var(--up)]/10 text-[var(--up)] border border-[var(--up)]/20",
                      selectedCSGO.aiAction === 'Sell' && "bg-[var(--down)]/10 text-[var(--down)] border border-[var(--down)]/20",
                      selectedCSGO.aiAction === 'Hold' && "bg-[var(--text-secondary)]/10 text-[var(--text-secondary)] border border-[var(--text-secondary)]/20"
                    )}>
                      {selectedCSGO.aiAction === 'Buy' ? '买入' : selectedCSGO.aiAction === 'Sell' ? '卖出' : '持有'}
                    </span>
                  </div>
                  <div className="p-3 rounded-xl bg-[var(--surface)] border border-[var(--border)]">
                    <div className="text-[9px] text-[var(--text-muted)] uppercase font-bold mb-1">潜力</div>
                    <div className="text-lg font-mono font-bold text-[var(--accent)]">
                      {((1 - selectedCSGO.aiPurchasePrice / selectedCSGO.marketPrice) * 100).toFixed(1)}%
                    </div>
                  </div>
                </div>

                <div className="p-4 rounded-xl bg-[var(--surface)] border border-[var(--border)]">
                  <div className="flex justify-between items-center mb-3">
                    <div className="text-[10px] text-[var(--text-muted)] uppercase font-bold flex items-center gap-1.5">
                      <BarChart3 className="w-3 h-3" />
                      价格走势 (20天)
                    </div>
                  </div>
                  <div className="h-44 w-full">
                    <ResponsiveContainer width="100%" height="100%">
                      <AreaChart data={csgoHistory}>
                        <defs>
                          <linearGradient id="colorCSGO" x1="0" y1="0" x2="0" y2="1">
                            <stop offset="5%" stopColor="var(--accent)" stopOpacity={0.2}/>
                            <stop offset="95%" stopColor="var(--accent)" stopOpacity={0}/>
                          </linearGradient>
                        </defs>
                        <XAxis dataKey="date" hide />
                        <YAxis domain={['auto', 'auto']} hide />
                        <Tooltip
                          contentStyle={{ backgroundColor: 'var(--surface)', borderColor: 'var(--border)', borderRadius: '8px', fontSize: '12px' }}
                          itemStyle={{ color: 'var(--text-primary)', fontFamily: 'var(--font-mono)' }}
                          labelStyle={{ color: 'var(--text-secondary)' }}
                        />
                        <Area
                          type="monotone"
                          dataKey="price"
                          stroke="var(--accent)"
                          fillOpacity={1}
                          fill="url(#colorCSGO)"
                          strokeWidth={2}
                        />
                      </AreaChart>
                    </ResponsiveContainer>
                  </div>
                </div>
              </div>

              <div className="p-5 border-t border-[var(--border)] bg-[var(--surface)] flex justify-end">
                <button
                  onClick={() => setSelectedCSGO(null)}
                  className="px-6 py-2 bg-[var(--accent)] text-[var(--bg)] rounded-lg text-[12px] font-bold hover:bg-[var(--accent-hover)] transition-colors uppercase tracking-wider font-display"
                >
                  关闭详情
                </button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>
    </motion.div>
  );
}
