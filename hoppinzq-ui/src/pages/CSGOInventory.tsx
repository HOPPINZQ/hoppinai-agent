import { useState, useMemo, useEffect } from "react";
import { motion, AnimatePresence } from "motion/react";
import axios from "axios";
import {
  Search,
  Filter,
  ChevronDown,
  ChevronUp,
  ChevronLeft,
  ChevronRight,
  ChevronsLeft,
  ChevronsRight,
  Crosshair,
  Shield,
  Zap,
  Tag,
  LayoutGrid,
  List as ListIcon,
  TrendingUp,
  Clock,
  CheckCircle2,
  X,
  Scale,
  DollarSign,
  Image as ImageIcon
} from "lucide-react";
import { cn } from "@/lib/utils";
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';

export interface BuffGoods {
  id: number;
  name: string;
  marketHashName: string;
  shortName: string;
  game: string;
  appid: number;
  iconUrl: string;
  sellMinPrice: number | null;
  sellReferencePrice: number | null;
  steamPrice: number | null;
  steamPriceCny: number | null;
  updateTime: string;
  info?: string;
}

export function CSGOInventory() {
  const [items, setItems] = useState<BuffGoods[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(24);
  const [searchQuery, setSearchQuery] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [sortConfig, setSortConfig] = useState<{ key: keyof BuffGoods; direction: 'asc' | 'desc' } | null>(null);
  const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [isCompareModalOpen, setIsCompareModalOpen] = useState(false);
  const [selectedItem, setSelectedItem] = useState<BuffGoods | null>(null);
  const [priceHistory, setPriceHistory] = useState<any[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);

  useEffect(() => {
    if (selectedItem) {
      setHistoryLoading(true);
      axios.get(`/api/buff/price/goods/${selectedItem.id}/history`)
        .then(res => {
          setPriceHistory(res.data || []);
        })
        .catch(err => console.error("Failed to fetch price history:", err))
        .finally(() => setHistoryLoading(false));
    } else {
      setPriceHistory([]);
    }
  }, [selectedItem]);

  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(searchQuery);
      setCurrent(1);
    }, 500);
    return () => clearTimeout(timer);
  }, [searchQuery]);

  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      try {
        let url = `/api/buff/goods/page`;
        const params: any = { current, size: pageSize };
        if (debouncedSearch) {
          url = `/api/buff/goods/search`;
          params.name = debouncedSearch;
        }
        const response = await axios.get(url, { params });
        const data = response.data;
        if (data.records) {
          setItems(data.records);
          setTotal(data.total);
        } else {
          setItems([]);
          setTotal(0);
        }
      } catch (error) {
        console.error("Failed to fetch goods:", error);
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, [current, pageSize, debouncedSearch]);

  const toggleSelection = (id: number) => {
    setSelectedIds(prev =>
      prev.includes(id) ? prev.filter(i => i !== id) : [...prev, id]
    );
  };

  const selectedItems = items.filter(item => selectedIds.includes(item.id));

  const handleSort = (key: keyof BuffGoods) => {
    let direction: 'asc' | 'desc' = 'asc';
    if (sortConfig && sortConfig.key === key && sortConfig.direction === 'asc') {
      direction = 'desc';
    }
    setSortConfig({ key, direction });
  };

  const sortedItems = [...items].sort((a, b) => {
    if (!sortConfig) return 0;
    const { key, direction } = sortConfig;
    const valA = a[key] ?? 0;
    const valB = b[key] ?? 0;
    if (valA < valB) return direction === 'asc' ? -1 : 1;
    if (valA > valB) return direction === 'asc' ? 1 : -1;
    return 0;
  });

  const getRarityBg = (infoStr?: string) => {
    if (!infoStr) return 'bg-[var(--bg-subtle)]';
    try {
      const parsed = JSON.parse(infoStr);
      const rarity = parsed?.info?.tags?.rarity?.internal_name;
      switch (rarity) {
        case 'ancient_weapon':
          return 'bg-gradient-to-br from-red-500/15 to-red-900/30 border-b-2 border-b-[var(--rarity-covert)]';
        case 'legendary_weapon':
          return 'bg-gradient-to-br from-pink-500/15 to-pink-900/30 border-b-2 border-b-[var(--rarity-classified)]';
        case 'mythical_weapon':
          return 'bg-gradient-to-br from-purple-500/15 to-purple-900/30 border-b-2 border-b-[var(--rarity-restricted)]';
        case 'rare_weapon':
          return 'bg-gradient-to-br from-blue-600/15 to-blue-900/30 border-b-2 border-b-[var(--rarity-milspec)]';
        case 'uncommon_weapon':
          return 'bg-gradient-to-br from-blue-400/15 to-blue-800/30 border-b-2 border-b-[var(--rarity-industrial)]';
        default:
          return 'bg-[var(--bg-subtle)]';
      }
    } catch (e) {
      return 'bg-[var(--bg-subtle)]';
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
      <div className="flex flex-col lg:flex-row justify-between items-start lg:items-end gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-[var(--text-primary)] flex items-center gap-2 font-display uppercase">
            <Crosshair className="w-6 h-6 text-[var(--accent)]" />
            CSGO 市场分析
          </h1>
          <p className="text-[var(--text-secondary)] text-[13px] mt-0.5">实时 Buff 市场数据与 Steam 价格对比</p>
        </div>

        <div className="flex flex-wrap items-center gap-2.5 w-full lg:w-auto">
          {selectedIds.length > 0 && (
            <motion.button
              initial={{ scale: 0.9, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              onClick={() => setIsCompareModalOpen(true)}
              className="flex items-center gap-2 px-3.5 py-2 bg-[var(--accent)] text-[var(--bg)] rounded-lg text-[12px] font-bold hover:bg-[var(--accent-hover)] transition-colors uppercase tracking-wider font-display"
            >
              <Scale className="w-3.5 h-3.5" />
              对比 ({selectedIds.length})
            </motion.button>
          )}

          <div className="flex bg-[var(--surface)] border border-[var(--border)] rounded-lg p-0.5">
            <button
              onClick={() => setViewMode('grid')}
              className={cn(
                "p-1.5 rounded-md transition-all",
                viewMode === 'grid' ? "bg-[var(--accent)] text-[var(--bg)]" : "text-[var(--text-secondary)] hover:text-[var(--text-primary)]"
              )}
            >
              <LayoutGrid className="w-4 h-4" />
            </button>
            <button
              onClick={() => setViewMode('list')}
              className={cn(
                "p-1.5 rounded-md transition-all",
                viewMode === 'list' ? "bg-[var(--accent)] text-[var(--bg)]" : "text-[var(--text-secondary)] hover:text-[var(--text-primary)]"
              )}
            >
              <ListIcon className="w-4 h-4" />
            </button>
          </div>

          <div className="flex items-center bg-[var(--surface)] border border-[var(--border)] rounded-lg px-3 py-2 flex-1 lg:w-60 focus-within:border-[var(--accent)]/40 transition-colors">
            <Search className="w-4 h-4 text-[var(--text-muted)] mr-2" />
            <input
              type="text"
              placeholder="搜索物品..."
              className="bg-transparent border-none outline-none text-[13px] w-full text-[var(--text-primary)] placeholder:text-[var(--text-muted)]"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
          </div>
        </div>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <motion.div whileHover={{ y: -2 }} className="p-4 rounded-xl border border-[var(--border)] bg-[var(--surface)] border-l-2 border-l-[var(--rarity-milspec)]">
          <div className="flex items-center gap-2.5 mb-2">
            <div className="p-1.5 rounded-md bg-[var(--rarity-milspec)]/10">
              <Shield className="w-4 h-4 text-[var(--rarity-milspec)]" />
            </div>
            <span className="text-[11px] font-medium text-[var(--text-secondary)] uppercase tracking-wider">已加载物品</span>
          </div>
          <div className="text-2xl font-bold font-mono">{total}</div>
        </motion.div>
        <motion.div whileHover={{ y: -2 }} className="p-4 rounded-xl border border-[var(--border)] bg-[var(--surface)] border-l-2 border-l-[var(--up)]">
          <div className="flex items-center gap-2.5 mb-2">
            <div className="p-1.5 rounded-md bg-[var(--up)]/10">
              <DollarSign className="w-4 h-4 text-[var(--up)]" />
            </div>
            <span className="text-[11px] font-medium text-[var(--text-secondary)] uppercase tracking-wider">本页市值</span>
          </div>
          <div className="text-2xl font-bold font-mono text-[var(--up)]">
            ¥{items.reduce((acc, curr) => acc + (curr.sellMinPrice || 0), 0).toLocaleString(undefined, {minimumFractionDigits: 2, maximumFractionDigits: 2})}
          </div>
        </motion.div>
        <motion.div whileHover={{ y: -2 }} className="p-4 rounded-xl border border-[var(--border)] bg-[var(--surface)] border-l-2 border-l-[var(--rarity-restricted)]">
          <div className="flex items-center gap-2.5 mb-2">
            <div className="p-1.5 rounded-md bg-[var(--rarity-restricted)]/10">
              <Tag className="w-4 h-4 text-[var(--rarity-restricted)]" />
            </div>
            <span className="text-[11px] font-medium text-[var(--text-secondary)] uppercase tracking-wider">高价值 (¥100+)</span>
          </div>
          <div className="text-2xl font-bold font-mono">{items.filter(i => (i.sellMinPrice || 0) > 100).length}</div>
        </motion.div>
      </div>

      {/* Content */}
      {loading ? (
        <div className="flex items-center justify-center py-20">
          <div className="animate-spin rounded-full h-10 w-10 border-2 border-[var(--accent)] border-t-transparent"></div>
        </div>
      ) : (
        <>
          <AnimatePresence mode="wait">
            {viewMode === 'grid' ? (
              <motion.div
                key="grid"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-3"
              >
                {sortedItems.map((item) => (
                  <motion.div
                    key={item.id}
                    layout
                    whileHover={{ scale: 1.02, y: -2 }}
                    className={cn(
                      "bg-[var(--surface)] border rounded-lg overflow-hidden flex flex-col group cursor-pointer transition-all relative card-glow",
                      selectedIds.includes(item.id) ? "border-[var(--accent)] ring-1 ring-[var(--accent)]/20" : "border-[var(--border)] hover:border-[var(--accent)]/20"
                    )}
                  >
                    <div
                      onClick={(e) => { e.stopPropagation(); toggleSelection(item.id); }}
                      className="absolute top-2 left-2 z-20 p-1 rounded-md bg-black/30 backdrop-blur-sm border border-white/5 hover:bg-black/50 transition-colors"
                    >
                      {selectedIds.includes(item.id) ? (
                        <CheckCircle2 className="w-4 h-4 text-[var(--accent)]" />
                      ) : (
                        <div className="w-4 h-4 rounded border border-white/20" />
                      )}
                    </div>

                    <div onClick={() => setSelectedItem(item)} className="flex-1 flex flex-col">
                      <div className={cn("h-28 flex items-center justify-center relative overflow-hidden group-hover:h-32 transition-all duration-500", getRarityBg(item.info))}>
                        {item.iconUrl ? (
                          <img src={item.iconUrl} alt={item.name} className="h-20 object-contain transition-transform duration-500 group-hover:scale-110" />
                        ) : (
                          <ImageIcon className="w-12 h-12 opacity-15" />
                        )}
                        <div className="absolute top-2 right-2">
                          <span className="text-[8px] font-bold px-1.5 py-0.5 rounded uppercase tracking-tight bg-black/40 backdrop-blur-sm border border-white/5 text-[var(--text-secondary)]">
                            {item.shortName || '物品'}
                          </span>
                        </div>
                      </div>

                      <div className="p-3 flex-1 flex flex-col gap-2.5">
                        <div>
                          <div className="text-[10px] text-[var(--text-muted)] mb-0.5 uppercase tracking-wider">{item.game || 'CS:GO'}</div>
                          <h3 className="font-bold text-[12px] text-[var(--text-primary)] group-hover:text-[var(--accent)] transition-colors line-clamp-1" title={item.name}>{item.name}</h3>
                        </div>

                        <div className="grid grid-cols-2 gap-2">
                          <div className="bg-[var(--bg)]/60 p-2 rounded-md">
                            <div className="text-[8px] text-[var(--text-muted)] uppercase font-bold mb-0.5 tracking-wider">Buff</div>
                            <div className="font-mono text-[12px] font-bold">¥{item.sellMinPrice?.toLocaleString(undefined, {minimumFractionDigits: 2}) || '--'}</div>
                          </div>
                          <div className="bg-[var(--up)]/5 p-2 rounded-md border border-[var(--up)]/10">
                            <div className="text-[8px] text-[var(--up)] uppercase font-bold mb-0.5 tracking-wider">Steam</div>
                            <div className="font-mono text-[12px] font-bold text-[var(--up)]">¥{item.steamPriceCny?.toLocaleString(undefined, {minimumFractionDigits: 2}) || '--'}</div>
                          </div>
                        </div>

                        <div className="flex items-center justify-between pt-2 border-t border-[var(--border)]">
                          <div className="flex items-center gap-1 text-[9px] text-[var(--text-muted)]">
                            <Clock className="w-3 h-3" />
                            {item.updateTime ? new Date(item.updateTime).toLocaleDateString() : '--'}
                          </div>
                          {item.sellMinPrice && item.steamPriceCny && (
                            <div className="flex items-center gap-1 text-[9px] text-[var(--accent)] font-bold">
                              <TrendingUp className="w-3 h-3" />
                              {((item.steamPriceCny / item.sellMinPrice - 1) * 100).toFixed(1)}%
                            </div>
                          )}
                        </div>
                      </div>
                    </div>
                  </motion.div>
                ))}
              </motion.div>
            ) : (
              <motion.div
                key="list"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                className="bg-[var(--surface)] border border-[var(--border)] rounded-xl overflow-hidden"
              >
                <div className="overflow-x-auto">
                  <table className="w-full text-left border-collapse">
                    <thead>
                      <tr className="border-b border-[var(--border)] bg-[var(--bg-subtle)]">
                        <th className="px-5 py-3 text-[10px] font-semibold uppercase tracking-wider text-[var(--text-muted)] cursor-pointer hover:text-[var(--text-primary)] transition-colors" onClick={() => handleSort('name')}>
                          <div className="flex items-center gap-1">
                            物品名称
                            {sortConfig?.key === 'name' && (sortConfig.direction === 'asc' ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />)}
                          </div>
                        </th>
                        <th className="px-5 py-3 text-[10px] font-semibold uppercase tracking-wider text-[var(--text-muted)] cursor-pointer hover:text-[var(--text-primary)] transition-colors" onClick={() => handleSort('shortName')}>
                          <div className="flex items-center gap-1">
                            类型
                            {sortConfig?.key === 'shortName' && (sortConfig.direction === 'asc' ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />)}
                          </div>
                        </th>
                        <th className="px-5 py-3 text-[10px] font-semibold uppercase tracking-wider text-[var(--text-muted)] cursor-pointer hover:text-[var(--text-primary)] transition-colors text-right" onClick={() => handleSort('sellMinPrice')}>
                          <div className="flex items-center justify-end gap-1">
                            Buff 最低价
                            {sortConfig?.key === 'sellMinPrice' && (sortConfig.direction === 'asc' ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />)}
                          </div>
                        </th>
                        <th className="px-5 py-3 text-[10px] font-semibold uppercase tracking-wider text-[var(--text-muted)] cursor-pointer hover:text-[var(--text-primary)] transition-colors text-right" onClick={() => handleSort('steamPriceCny')}>
                          <div className="flex items-center justify-end gap-1">
                            Steam (CNY)
                            {sortConfig?.key === 'steamPriceCny' && (sortConfig.direction === 'asc' ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />)}
                          </div>
                        </th>
                        <th className="px-5 py-3 text-[10px] font-semibold uppercase tracking-wider text-[var(--text-muted)]">
                          最后同步
                        </th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-[var(--border)]">
                      {sortedItems.map((item) => (
                        <tr
                          key={item.id}
                          onClick={() => setSelectedItem(item)}
                          className={cn(
                            "hover:bg-[var(--surface-hover)]/50 transition-colors group cursor-pointer",
                            selectedIds.includes(item.id) && "bg-[var(--accent)]/5"
                          )}
                        >
                          <td className="px-5 py-3">
                            <div className="flex items-center gap-2.5">
                              <div
                                onClick={(e) => { e.stopPropagation(); toggleSelection(item.id); }}
                                className="p-0.5 rounded bg-[var(--bg)] border border-[var(--border)] hover:bg-[var(--surface-hover)] transition-colors cursor-pointer"
                              >
                                {selectedIds.includes(item.id) ? (
                                  <CheckCircle2 className="w-3.5 h-3.5 text-[var(--accent)]" />
                                ) : (
                                  <div className="w-3.5 h-3.5" />
                                )}
                              </div>
                              <div className="flex items-center gap-2">
                                {item.iconUrl && <img src={item.iconUrl} alt="" className="w-7 h-7 object-contain" />}
                                <span className="font-medium text-[13px] text-[var(--text-primary)] group-hover:text-[var(--accent)] transition-colors">{item.name}</span>
                              </div>
                            </div>
                          </td>
                          <td className="px-5 py-3 text-[13px] text-[var(--text-secondary)]">{item.shortName || '--'}</td>
                          <td className="px-5 py-3 text-[13px] font-mono text-[var(--text-primary)]">
                            ¥{item.sellMinPrice?.toLocaleString(undefined, {minimumFractionDigits: 2}) || '--'}
                          </td>
                          <td className="px-5 py-3 text-[13px] font-mono text-[var(--up)]">
                            ¥{item.steamPriceCny?.toLocaleString(undefined, {minimumFractionDigits: 2}) || '--'}
                          </td>
                          <td className="px-5 py-3 text-[13px] text-[var(--text-secondary)]">
                            {item.updateTime ? new Date(item.updateTime).toLocaleDateString() : '--'}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </motion.div>
            )}
          </AnimatePresence>

          {/* Pagination */}
          <Pagination
            current={current}
            pageSize={pageSize}
            total={total}
            onChange={setCurrent}
          />
        </>
      )}

      {/* Compare Modal */}
      <AnimatePresence>
        {isCompareModalOpen && selectedItems.length > 0 && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={() => setIsCompareModalOpen(false)}
              className="absolute inset-0 bg-black/70 backdrop-blur-sm"
            />
            <motion.div
              initial={{ scale: 0.95, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0.95, opacity: 0 }}
              className="relative w-full max-w-6xl max-h-[90vh] bg-[var(--bg)] rounded-xl shadow-2xl border border-[var(--border)] overflow-hidden flex flex-col"
            >
              <div className="h-[2px] bg-gradient-to-r from-transparent via-[var(--accent)] to-transparent" />
              <div className="p-5 border-b border-[var(--border)] flex justify-between items-center bg-[var(--surface)]">
                <div>
                  <h2 className="text-lg font-bold text-[var(--text-primary)] flex items-center gap-2 font-display uppercase">
                    <Scale className="w-4 h-4 text-[var(--accent)]" />
                    市场对比
                  </h2>
                  <p className="text-[12px] text-[var(--text-secondary)]">正在对比 {selectedItems.length} 件物品</p>
                </div>
                <button onClick={() => setIsCompareModalOpen(false)} className="p-2 hover:bg-[var(--surface-hover)] rounded-lg transition-colors">
                  <X className="w-5 h-5 text-[var(--text-secondary)]" />
                </button>
              </div>

              <div className="flex-1 overflow-x-auto p-5">
                <div className="flex gap-5 min-w-max">
                  {selectedItems.map(item => (
                    <div key={item.id} className="w-64 flex-shrink-0 space-y-3">
                      <div className={cn("h-36 rounded-lg flex items-center justify-center relative overflow-hidden", getRarityBg(item.info))}>
                        {item.iconUrl ? (
                          <img src={item.iconUrl} alt={item.name} className="h-24 object-contain" />
                        ) : (
                          <ImageIcon className="w-12 h-12 opacity-15" />
                        )}
                      </div>

                      <div className="space-y-3">
                        <div>
                          <div className="text-[10px] text-[var(--text-muted)] uppercase font-bold mb-0.5">名称</div>
                          <div className="text-[15px] font-bold text-[var(--text-primary)] leading-tight">{item.name}</div>
                          <div className="text-[12px] text-[var(--text-secondary)]">{item.shortName || '--'}</div>
                        </div>

                        <div className="grid grid-cols-2 gap-3">
                          <div className="p-2.5 rounded-lg bg-[var(--surface)] border border-[var(--border)]">
                            <div className="text-[8px] text-[var(--text-muted)] uppercase font-bold mb-0.5">Buff</div>
                            <div className="text-[14px] font-mono font-bold text-[var(--text-primary)]">¥{item.sellMinPrice?.toLocaleString() || '--'}</div>
                          </div>
                          <div className="p-2.5 rounded-lg bg-[var(--up)]/5 border border-[var(--up)]/15">
                            <div className="text-[8px] text-[var(--up)] uppercase font-bold mb-0.5">Steam</div>
                            <div className="text-[14px] font-mono font-bold text-[var(--up)]">¥{item.steamPriceCny?.toLocaleString() || '--'}</div>
                          </div>
                        </div>

                        <div className="p-2.5 rounded-lg bg-[var(--surface)] border border-[var(--border)]">
                          <div className="flex justify-between items-center">
                            <span className="text-[10px] text-[var(--text-muted)]">比率</span>
                            <span className="text-[10px] font-mono font-bold text-[var(--accent)]">
                              {item.sellMinPrice && item.steamPriceCny ? ((item.steamPriceCny / item.sellMinPrice)).toFixed(2) : '--'}
                            </span>
                          </div>
                        </div>

                        <button
                          onClick={() => toggleSelection(item.id)}
                          className="w-full py-1.5 text-[10px] font-bold text-[var(--down)] hover:bg-[var(--down)]/5 rounded-lg transition-colors border border-[var(--down)]/20 uppercase tracking-wider"
                        >
                          移除
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      {/* Detail Modal */}
      <AnimatePresence>
        {selectedItem && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 md:p-8">
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={() => setSelectedItem(null)}
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
                  <div className={cn("w-14 h-14 rounded-lg flex items-center justify-center p-2", getRarityBg(selectedItem.info))}>
                    {selectedItem.iconUrl ? (
                      <img src={selectedItem.iconUrl} alt="" className="w-full h-full object-contain" />
                    ) : (
                      <ImageIcon className="w-7 h-7 text-[var(--text-muted)]" />
                    )}
                  </div>
                  <div>
                    <h2 className="text-lg font-bold text-[var(--text-primary)] font-display">{selectedItem.name}</h2>
                    <p className="text-[11px] text-[var(--text-muted)] flex items-center gap-1.5">
                      <Clock className="w-3 h-3" />
                      {selectedItem.updateTime ? new Date(selectedItem.updateTime).toLocaleString() : '--'}
                    </p>
                  </div>
                </div>
                <button onClick={() => setSelectedItem(null)} className="p-2 hover:bg-[var(--surface-hover)] rounded-lg transition-colors self-start">
                  <X className="w-5 h-5 text-[var(--text-secondary)]" />
                </button>
              </div>

              <div className="p-5 space-y-4 overflow-y-auto max-h-[70vh]">
                <div className="grid grid-cols-2 gap-3">
                  <div className="p-3.5 rounded-xl bg-[var(--surface)] border border-[var(--border)]">
                    <div className="text-[10px] text-[var(--text-muted)] uppercase font-bold mb-1 flex items-center gap-1.5">
                      <DollarSign className="w-3 h-3" />
                      Buff 市场价
                    </div>
                    <div className="text-2xl font-mono font-bold text-[var(--text-primary)]">
                      ¥{(priceHistory.length > 0 ? priceHistory[priceHistory.length - 1].sellMinPrice : selectedItem.sellMinPrice)?.toLocaleString(undefined, {minimumFractionDigits: 2}) || '--'}
                    </div>
                    {(priceHistory.length > 0 ? priceHistory[priceHistory.length - 1].sellReferencePrice : selectedItem.sellReferencePrice) && (
                      <div className="text-[10px] text-[var(--text-muted)] mt-1.5 flex justify-between">
                        <span>参考价: ¥{(priceHistory.length > 0 ? priceHistory[priceHistory.length - 1].sellReferencePrice : selectedItem.sellReferencePrice).toLocaleString()}</span>
                        {priceHistory.length > 0 && priceHistory[priceHistory.length - 1].buyMaxPrice && (
                          <span className="text-[var(--text-primary)]">最高买价: ¥{priceHistory[priceHistory.length - 1].buyMaxPrice.toLocaleString()}</span>
                        )}
                      </div>
                    )}
                  </div>
                  <div className="p-3.5 rounded-xl bg-[var(--surface)] border border-[var(--border)]">
                    <div className="text-[10px] text-[var(--text-muted)] uppercase font-bold mb-1 flex items-center gap-1.5">
                      <TrendingUp className="w-3 h-3" />
                      Steam 市场价
                    </div>
                    <div className="text-2xl font-mono font-bold text-[var(--up)]">
                      ¥{(priceHistory.length > 0 ? priceHistory[priceHistory.length - 1].steamPriceCny : selectedItem.steamPriceCny)?.toLocaleString(undefined, {minimumFractionDigits: 2}) || '--'}
                    </div>
                    {selectedItem.steamPrice && (
                      <div className="text-[10px] text-[var(--text-muted)] mt-1.5">
                        USD: ${selectedItem.steamPrice.toLocaleString()}
                      </div>
                    )}
                  </div>
                </div>

                <div className="p-3.5 rounded-xl bg-[var(--surface)] border border-[var(--border)]">
                  <div className="text-[10px] text-[var(--text-muted)] uppercase font-bold mb-3 flex items-center gap-1.5">
                    <TrendingUp className="w-3 h-3" />
                    价格走势 (Buff)
                  </div>
                  {historyLoading ? (
                    <div className="h-[180px] flex items-center justify-center text-[var(--text-muted)] text-[13px]">加载中...</div>
                  ) : priceHistory.length > 0 ? (
                    <div className="h-[180px] w-full">
                      <ResponsiveContainer width="100%" height="100%">
                        <LineChart data={priceHistory}>
                          <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" vertical={false} />
                          <XAxis dataKey="createTime" stroke="var(--text-muted)" fontSize={10} tickFormatter={(val) => new Date(val).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })} />
                          <YAxis domain={['auto', 'auto']} stroke="var(--text-muted)" fontSize={10} tickFormatter={(val) => `¥${val}`} />
                          <Tooltip
                            contentStyle={{ backgroundColor: 'var(--surface)', borderColor: 'var(--border)', borderRadius: '8px', fontSize: '11px' }}
                            itemStyle={{ color: 'var(--text-primary)' }}
                            labelStyle={{ color: 'var(--text-secondary)', marginBottom: '4px' }}
                            labelFormatter={(val) => new Date(val).toLocaleString()}
                          />
                          <Line type="monotone" dataKey="sellMinPrice" stroke="var(--accent)" strokeWidth={2} dot={false} name="最低售价" />
                        </LineChart>
                      </ResponsiveContainer>
                    </div>
                  ) : (
                    <div className="h-[180px] flex items-center justify-center text-[var(--text-muted)] text-[13px]">暂无价格历史</div>
                  )}
                </div>

                <div className="grid grid-cols-3 gap-3">
                  <div className="p-3 rounded-xl bg-[var(--surface)] border border-[var(--border)]">
                    <div className="text-[9px] text-[var(--text-muted)] uppercase font-bold mb-0.5">游戏</div>
                    <div className="text-[12px] font-bold text-[var(--text-primary)]">{selectedItem.game || 'CS:GO'}</div>
                  </div>
                  <div className="p-3 rounded-xl bg-[var(--surface)] border border-[var(--border)]">
                    <div className="text-[9px] text-[var(--text-muted)] uppercase font-bold mb-0.5">类型</div>
                    <div className="text-[12px] font-bold text-[var(--text-primary)] truncate" title={selectedItem.shortName}>{selectedItem.shortName || '--'}</div>
                  </div>
                  <div className="p-3 rounded-xl bg-[var(--surface)] border border-[var(--border)]">
                    <div className="text-[9px] text-[var(--text-muted)] uppercase font-bold mb-0.5">价格比率</div>
                    <div className="text-base font-mono font-bold text-[var(--accent)]">
                      {selectedItem.sellMinPrice && selectedItem.steamPriceCny ? (selectedItem.steamPriceCny / selectedItem.sellMinPrice).toFixed(2) : '--'}
                    </div>
                  </div>
                </div>
              </div>

              <div className="p-5 border-t border-[var(--border)] bg-[var(--surface)] flex justify-end">
                <button
                  onClick={() => setSelectedItem(null)}
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

function Pagination({ current, pageSize, total, onChange }: {
  current: number;
  pageSize: number;
  total: number;
  onChange: (page: number) => void;
}) {
  const totalPages = Math.ceil(total / pageSize);
  if (totalPages <= 1) return null;

  const pages: (number | 'ellipsis')[] = [];

  // Build page list: first 3, current neighborhood, last 2, with ellipsis
  const shown = new Set<number>();
  const add = (n: number) => { if (n >= 1 && n <= totalPages) shown.add(n); };

  // First 3 pages
  for (let i = 1; i <= Math.min(3, totalPages); i++) add(i);
  // Last 2 pages
  for (let i = Math.max(totalPages - 1, 1); i <= totalPages; i++) add(i);
  // Current page ± 1
  add(current);
  if (current > 1) add(current - 1);
  if (current < totalPages) add(current + 1);

  const sorted = [...shown].sort((a, b) => a - b);
  let prev = 0;
  for (const p of sorted) {
    if (p - prev > 1) pages.push('ellipsis');
    pages.push(p);
    prev = p;
  }

  return (
    <div className="flex items-center justify-between mt-5 bg-[var(--surface)] p-3.5 rounded-xl border border-[var(--border)]">
      <div className="text-[12px] text-[var(--text-secondary)]">
        <span className="text-[var(--text-muted)]">显示</span>{' '}
        {(current - 1) * pageSize + 1}-{Math.min(current * pageSize, total)}{' '}
        <span className="text-[var(--text-muted)]">/</span> {total}
      </div>

      <div className="flex items-center gap-1">
        {/* First page */}
        <button
          onClick={() => onChange(1)}
          disabled={current === 1}
          className="p-1.5 rounded-lg text-[var(--text-secondary)] hover:bg-[var(--surface-hover)] hover:text-[var(--text-primary)] disabled:opacity-25 disabled:cursor-not-allowed transition-colors"
        >
          <ChevronsLeft className="w-4 h-4" />
        </button>
        {/* Previous */}
        <button
          onClick={() => onChange(current - 1)}
          disabled={current === 1}
          className="p-1.5 rounded-lg text-[var(--text-secondary)] hover:bg-[var(--surface-hover)] hover:text-[var(--text-primary)] disabled:opacity-25 disabled:cursor-not-allowed transition-colors"
        >
          <ChevronLeft className="w-4 h-4" />
        </button>

        {/* Page numbers */}
        {pages.map((p, i) =>
          p === 'ellipsis' ? (
            <span key={`e${i}`} className="w-8 h-8 flex items-center justify-center text-[var(--text-muted)] text-[12px] select-none">
              ···
            </span>
          ) : (
            <button
              key={p}
              onClick={() => onChange(p)}
              className={cn(
                "w-8 h-8 rounded-lg text-[12px] font-bold transition-all",
                p === current
                  ? "bg-[var(--accent)] text-[var(--bg)]"
                  : "text-[var(--text-secondary)] hover:bg-[var(--surface-hover)] hover:text-[var(--text-primary)]"
              )}
            >
              {p}
            </button>
          )
        )}

        {/* Next */}
        <button
          onClick={() => onChange(current + 1)}
          disabled={current >= totalPages}
          className="p-1.5 rounded-lg text-[var(--text-secondary)] hover:bg-[var(--surface-hover)] hover:text-[var(--text-primary)] disabled:opacity-25 disabled:cursor-not-allowed transition-colors"
        >
          <ChevronRight className="w-4 h-4" />
        </button>
        {/* Last page */}
        <button
          onClick={() => onChange(totalPages)}
          disabled={current >= totalPages}
          className="p-1.5 rounded-lg text-[var(--text-secondary)] hover:bg-[var(--surface-hover)] hover:text-[var(--text-primary)] disabled:opacity-25 disabled:cursor-not-allowed transition-colors"
        >
          <ChevronsRight className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
}
