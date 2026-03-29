import { useState, useMemo } from "react";
import { motion, AnimatePresence } from "motion/react";
import { 
  Search, 
  Filter, 
  ChevronDown, 
  ChevronUp, 
  Sword, 
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
  BarChart3,
  DollarSign,
  Star
} from "lucide-react";
import { 
  AreaChart, 
  Area, 
  ResponsiveContainer, 
  Tooltip, 
  YAxis, 
  XAxis 
} from "recharts";
import { cn } from "@/lib/utils";
import { generateMockCSGOItems, CSGOItem, generateMockStockHistory } from "@/mockData";

const mockCSGOItems = generateMockCSGOItems(30);

export function CSGOInventory() {
  const [items, setItems] = useState<CSGOItem[]>(mockCSGOItems);
  const [searchQuery, setSearchQuery] = useState("");
  const [sortConfig, setSortConfig] = useState<{ key: keyof CSGOItem; direction: 'asc' | 'desc' } | null>(null);
  const [rarityFilter, setRarityFilter] = useState<string>("All");
  const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [isCompareModalOpen, setIsCompareModalOpen] = useState(false);
  const [selectedItem, setSelectedItem] = useState<CSGOItem | null>(null);

  const itemHistory = useMemo(() => {
    if (!selectedItem) return [];
    return generateMockStockHistory(selectedItem.marketPrice, 20);
  }, [selectedItem]);

  const rarities = ["All", "Consumer Grade", "Industrial Grade", "Mil-Spec Grade", "Restricted", "Classified", "Covert", "Contraband"];

  const toggleSelection = (id: string) => {
    setSelectedIds(prev => 
      prev.includes(id) ? prev.filter(i => i !== id) : [...prev, id]
    );
  };

  const selectedItems = items.filter(item => selectedIds.includes(item.id));

  const handleSort = (key: keyof CSGOItem) => {
    let direction: 'asc' | 'desc' = 'asc';
    if (sortConfig && sortConfig.key === key && sortConfig.direction === 'asc') {
      direction = 'desc';
    }
    setSortConfig({ key, direction });
  };

  const sortedItems = [...items].sort((a, b) => {
    if (!sortConfig) return 0;
    const { key, direction } = sortConfig;
    if (a[key] < b[key]) return direction === 'asc' ? -1 : 1;
    if (a[key] > b[key]) return direction === 'asc' ? 1 : -1;
    return 0;
  });

  const filteredItems = sortedItems.filter(item => {
    const matchesSearch = item.name.toLowerCase().includes(searchQuery.toLowerCase()) || 
                         item.type.toLowerCase().includes(searchQuery.toLowerCase());
    const matchesRarity = rarityFilter === "All" || item.rarity === rarityFilter;
    return matchesSearch && matchesRarity;
  });

  const getRarityColor = (rarity: string) => {
    switch (rarity) {
      case 'Consumer Grade': return 'text-slate-400';
      case 'Industrial Grade': return 'text-blue-400';
      case 'Mil-Spec Grade': return 'text-blue-600';
      case 'Restricted': return 'text-purple-500';
      case 'Classified': return 'text-pink-500';
      case 'Covert': return 'text-red-500';
      case 'Contraband': return 'text-orange-500';
      default: return 'text-slate-400';
    }
  };

  const getRarityBg = (rarity: string) => {
    switch (rarity) {
      case 'Consumer Grade': return 'bg-gradient-to-br from-slate-400/20 to-slate-500/5';
      case 'Industrial Grade': return 'bg-gradient-to-br from-blue-400/20 to-blue-500/5';
      case 'Mil-Spec Grade': return 'bg-gradient-to-br from-blue-600/20 to-blue-700/5';
      case 'Restricted': return 'bg-gradient-to-br from-purple-500/20 to-purple-600/5';
      case 'Classified': return 'bg-gradient-to-br from-pink-500/20 to-pink-600/5';
      case 'Covert': return 'bg-gradient-to-br from-red-500/20 to-red-600/5';
      case 'Contraband': return 'bg-gradient-to-br from-orange-500/20 to-orange-600/5';
      default: return 'bg-gradient-to-br from-slate-400/20 to-slate-500/5';
    }
  };

  const getRarityIcon = (rarity: string) => {
    switch (rarity) {
      case 'Consumer Grade': return Shield;
      case 'Industrial Grade': return Zap;
      case 'Mil-Spec Grade': return Sword;
      case 'Restricted': return Tag;
      case 'Classified': return Zap;
      case 'Covert': return Sword;
      case 'Contraband': return Zap;
      default: return Shield;
    }
  };

  const RarityIcon = (props: { rarity: string; className?: string }) => {
    const Icon = getRarityIcon(props.rarity);
    return <Icon className={props.className} />;
  };

  return (
    <motion.div 
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5 }}
      className="space-y-6"
    >
      {/* Header Section */}
      <div className="flex flex-col lg:flex-row justify-between items-start lg:items-end gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-[var(--text-primary)] flex items-center gap-2">
            <Sword className="w-6 h-6 text-[var(--accent)]" />
            CSGO Inventory Analysis
          </h1>
          <p className="text-[var(--text-secondary)] text-sm mt-1">AI-driven skin valuation and market insights</p>
        </div>
        
        <div className="flex flex-wrap items-center gap-3 w-full lg:w-auto">
          {/* Compare Button */}
          {selectedIds.length > 0 && (
            <motion.button
              initial={{ scale: 0.9, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              onClick={() => setIsCompareModalOpen(true)}
              className="flex items-center gap-2 px-4 py-2 bg-[var(--accent)] text-white rounded-lg text-sm font-bold shadow-lg hover:bg-[var(--accent)]/90 transition-colors"
            >
              <Scale className="w-4 h-4" />
              Compare ({selectedIds.length})
            </motion.button>
          )}

          {/* View Toggle */}
          <div className="flex bg-[var(--surface)] border border-[var(--border)] rounded-lg p-1">
            <button 
              onClick={() => setViewMode('grid')}
              className={cn(
                "p-1.5 rounded-md transition-all",
                viewMode === 'grid' ? "bg-[var(--accent)] text-white" : "text-[var(--text-secondary)] hover:text-[var(--text-primary)]"
              )}
            >
              <LayoutGrid className="w-4 h-4" />
            </button>
            <button 
              onClick={() => setViewMode('list')}
              className={cn(
                "p-1.5 rounded-md transition-all",
                viewMode === 'list' ? "bg-[var(--accent)] text-white" : "text-[var(--text-secondary)] hover:text-[var(--text-primary)]"
              )}
            >
              <ListIcon className="w-4 h-4" />
            </button>
          </div>

          <div className="flex items-center bg-[var(--surface)] border border-[var(--border)] rounded-lg px-3 py-2 flex-1 lg:w-64 focus-within:border-[var(--accent)] transition-colors">
            <Search className="w-4 h-4 text-[var(--text-secondary)] mr-2" />
            <input 
              type="text" 
              placeholder="Search skins or types..." 
              className="bg-transparent border-none outline-none text-sm w-full text-[var(--text-primary)] placeholder:text-[var(--text-secondary)]"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
          </div>
          
          <div className="flex items-center gap-2 bg-[var(--surface)] border border-[var(--border)] rounded-lg px-3 py-2">
            <Filter className="w-4 h-4 text-[var(--text-secondary)]" />
            <select 
              className="bg-transparent border-none outline-none text-sm text-[var(--text-primary)] cursor-pointer"
              value={rarityFilter}
              onChange={(e) => setRarityFilter(e.target.value)}
            >
              {rarities.map(r => <option key={r} value={r} className="bg-[var(--surface)]">{r}</option>)}
            </select>
          </div>
        </div>
      </div>

      {/* Stats Overview */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <motion.div 
          whileHover={{ y: -4 }}
          className="p-4 rounded-xl border border-[var(--border)] bg-[var(--surface)] shadow-sm"
        >
          <div className="flex items-center gap-3 mb-2">
            <div className="p-2 rounded-lg bg-blue-500/10">
              <Shield className="w-5 h-5 text-blue-500" />
            </div>
            <span className="text-sm font-medium text-[var(--text-secondary)]">Total Inventory Value</span>
          </div>
          <div className="text-2xl font-bold font-mono">${items.reduce((acc, curr) => acc + curr.marketPrice, 0).toLocaleString()}</div>
        </motion.div>
        <motion.div 
          whileHover={{ y: -4 }}
          className="p-4 rounded-xl border border-[var(--border)] bg-[var(--surface)] shadow-sm"
        >
          <div className="flex items-center gap-3 mb-2">
            <div className="p-2 rounded-lg bg-emerald-500/10">
              <Zap className="w-5 h-5 text-emerald-500" />
            </div>
            <span className="text-sm font-medium text-[var(--text-secondary)]">AI Purchase Potential</span>
          </div>
          <div className="text-2xl font-bold font-mono text-[var(--up)]">${items.reduce((acc, curr) => acc + curr.aiPurchasePrice, 0).toLocaleString()}</div>
        </motion.div>
        <motion.div 
          whileHover={{ y: -4 }}
          className="p-4 rounded-xl border border-[var(--border)] bg-[var(--surface)] shadow-sm"
        >
          <div className="flex items-center gap-3 mb-2">
            <div className="p-2 rounded-lg bg-purple-500/10">
              <Tag className="w-5 h-5 text-purple-500" />
            </div>
            <span className="text-sm font-medium text-[var(--text-secondary)]">High Rarity Items</span>
          </div>
          <div className="text-2xl font-bold font-mono">{items.filter(i => ['Covert', 'Contraband', 'Classified'].includes(i.rarity)).length}</div>
        </motion.div>
      </div>

      {/* Content Area */}
      <AnimatePresence mode="wait">
        {viewMode === 'grid' ? (
          <motion.div 
            key="grid"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4"
          >
            {filteredItems.map((item) => (
              <motion.div
                key={item.id}
                layout
                whileHover={{ scale: 1.02 }}
                className={cn(
                  "bg-[var(--surface)] border rounded-xl overflow-hidden flex flex-col group cursor-pointer transition-all relative",
                  selectedIds.includes(item.id) ? "border-[var(--accent)] ring-2 ring-[var(--accent)]/20" : "border-[var(--border)]"
                )}
              >
                {/* Selection Checkbox */}
                <div 
                  onClick={(e) => {
                    e.stopPropagation();
                    toggleSelection(item.id);
                  }}
                  className="absolute top-2 left-2 z-20 p-1 rounded-md bg-black/20 backdrop-blur-sm border border-white/10 hover:bg-black/40 transition-colors"
                >
                  {selectedIds.includes(item.id) ? (
                    <CheckCircle2 className="w-5 h-5 text-[var(--accent)] fill-white" />
                  ) : (
                    <div className="w-5 h-5 rounded border-2 border-white/30" />
                  )}
                </div>

                {/* Main Content Clickable for Details */}
                <div 
                  onClick={() => setSelectedItem(item)}
                  className="flex-1 flex flex-col"
                >
                  <div className={cn("h-32 flex items-center justify-center relative overflow-hidden group-hover:h-36 transition-all duration-500", getRarityBg(item.rarity))}>
                    <div className="absolute inset-0 opacity-20 bg-[radial-gradient(circle_at_center,_var(--tw-gradient-from)_0%,_transparent_70%)]"></div>
                    <RarityIcon rarity={item.rarity} className={cn("w-20 h-20 opacity-10 transform -rotate-12 transition-transform duration-700 group-hover:scale-125 group-hover:rotate-0", getRarityColor(item.rarity))} />
                    
                    {/* Glowing Effect for High Rarity */}
                    {['Covert', 'Contraband', 'Classified'].includes(item.rarity) && (
                      <div className={cn("absolute inset-0 opacity-30 animate-pulse", getRarityBg(item.rarity))}></div>
                    )}

                    <div className="absolute top-2 right-2">
                      <span className={cn("text-[9px] font-black px-2 py-0.5 rounded-full uppercase tracking-tighter bg-black/40 backdrop-blur-md border border-white/10", getRarityColor(item.rarity))}>
                        {item.rarity}
                      </span>
                    </div>
                  </div>
                  
                  <div className="p-4 flex-1 flex flex-col gap-3 relative">
                    {/* Tooltip */}
                    <div className="absolute inset-x-0 bottom-full mb-2 px-4 py-2 bg-[var(--surface-hover)] border border-[var(--border)] rounded-lg shadow-xl opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none z-20 backdrop-blur-md">
                      <div className="text-[10px] uppercase font-bold text-[var(--text-secondary)] mb-1 border-b border-[var(--border)] pb-1">Technical Specs</div>
                      <div className="flex justify-between items-center gap-4 mb-1">
                        <span className="text-[10px] text-[var(--text-secondary)]">Float Value:</span>
                        <span className="text-[10px] font-mono text-[var(--text-primary)]">{item.floatValue}</span>
                      </div>
                      <div className="flex justify-between items-center gap-4">
                        <span className="text-[10px] text-[var(--text-secondary)]">Last Sync:</span>
                        <span className="text-[10px] font-mono text-[var(--text-primary)]">{new Date(item.updatedAt).toLocaleTimeString()}</span>
                      </div>
                      <div className="absolute bottom-[-6px] left-1/2 -translate-x-1/2 w-3 h-3 bg-[var(--surface-hover)] border-r border-b border-[var(--border)] rotate-45"></div>
                    </div>

                    <div>
                      <div className="text-xs text-[var(--text-secondary)] mb-1">{item.type}</div>
                      <h3 className="font-bold text-[var(--text-primary)] group-hover:text-[var(--accent)] transition-colors line-clamp-1">{item.name}</h3>
                    </div>

                    <div className="grid grid-cols-2 gap-2">
                      <div className="bg-[var(--bg)]/50 p-2 rounded-lg">
                        <div className="text-[10px] text-[var(--text-secondary)] uppercase font-semibold mb-1">Market</div>
                        <div className="font-mono text-sm font-bold">${item.marketPrice.toLocaleString()}</div>
                      </div>
                      <div className="bg-[var(--up)]/5 p-2 rounded-lg border border-[var(--up)]/10">
                        <div className="text-[10px] text-[var(--up)] uppercase font-semibold mb-1">AI Buy</div>
                        <div className="font-mono text-sm font-bold text-[var(--up)]">${item.aiPurchasePrice.toLocaleString()}</div>
                      </div>
                    </div>

                    <div className="flex items-center justify-between pt-2 border-t border-[var(--border)]">
                      <div className="flex items-center gap-1 text-[10px] text-[var(--text-secondary)]">
                        <Clock className="w-3 h-3" />
                        {new Date(item.updatedAt).toLocaleDateString()}
                      </div>
                      <div className="flex items-center gap-1 text-[10px] text-[var(--accent)] font-bold">
                        <TrendingUp className="w-3 h-3" />
                        {( (item.aiPurchasePrice / item.marketPrice - 1) * 100).toFixed(1)}% Pot.
                      </div>
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
            className="bg-[var(--surface)] border border-[var(--border)] rounded-xl overflow-hidden shadow-sm"
          >
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="border-b border-[var(--border)] bg-[var(--surface-hover)]/50">
                    <th 
                      className="px-6 py-4 text-xs font-semibold uppercase tracking-wider text-[var(--text-secondary)] cursor-pointer hover:text-[var(--text-primary)] transition-colors"
                      onClick={() => handleSort('name')}
                    >
                      <div className="flex items-center gap-1">
                        Skin Name
                        {sortConfig?.key === 'name' && (sortConfig.direction === 'asc' ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />)}
                      </div>
                    </th>
                    <th 
                      className="px-6 py-4 text-xs font-semibold uppercase tracking-wider text-[var(--text-secondary)] cursor-pointer hover:text-[var(--text-primary)] transition-colors"
                      onClick={() => handleSort('type')}
                    >
                      <div className="flex items-center gap-1">
                        Type
                        {sortConfig?.key === 'type' && (sortConfig.direction === 'asc' ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />)}
                      </div>
                    </th>
                    <th 
                      className="px-6 py-4 text-xs font-semibold uppercase tracking-wider text-[var(--text-secondary)] cursor-pointer hover:text-[var(--text-primary)] transition-colors"
                      onClick={() => handleSort('rarity')}
                    >
                      <div className="flex items-center gap-1">
                        Rarity
                        {sortConfig?.key === 'rarity' && (sortConfig.direction === 'asc' ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />)}
                      </div>
                    </th>
                    <th 
                      className="px-6 py-4 text-xs font-semibold uppercase tracking-wider text-[var(--text-secondary)] cursor-pointer hover:text-[var(--text-primary)] transition-colors"
                      onClick={() => handleSort('aiPurchasePrice')}
                    >
                      <div className="flex items-center gap-1">
                        AI Purchase Price
                        {sortConfig?.key === 'aiPurchasePrice' && (sortConfig.direction === 'asc' ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />)}
                      </div>
                    </th>
                    <th 
                      className="px-6 py-4 text-xs font-semibold uppercase tracking-wider text-[var(--text-secondary)] cursor-pointer hover:text-[var(--text-primary)] transition-colors"
                      onClick={() => handleSort('marketPrice')}
                    >
                      <div className="flex items-center gap-1">
                        Market Price
                        {sortConfig?.key === 'marketPrice' && (sortConfig.direction === 'asc' ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />)}
                      </div>
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[var(--border)]">
                  {filteredItems.map((item) => (
                    <tr 
                      key={item.id} 
                      className={cn(
                        "hover:bg-[var(--surface-hover)]/30 transition-colors group cursor-pointer",
                        selectedIds.includes(item.id) && "bg-[var(--accent)]/5"
                      )}
                    >
                      <td 
                        className="px-6 py-4"
                        onClick={(e) => {
                          e.stopPropagation();
                          toggleSelection(item.id);
                        }}
                      >
                        <div className="flex items-center gap-3">
                          <div className={cn(
                            "w-4 h-4 rounded border flex items-center justify-center transition-colors",
                            selectedIds.includes(item.id) ? "bg-[var(--accent)] border-[var(--accent)]" : "border-[var(--border)]"
                          )}>
                            {selectedIds.includes(item.id) && <CheckCircle2 className="w-3 h-3 text-white" />}
                          </div>
                          <div className="font-medium text-[var(--text-primary)] group-hover:text-[var(--accent)] transition-colors">{item.name}</div>
                        </div>
                      </td>
                      <td className="px-6 py-4" onClick={() => setSelectedItem(item)}>
                        <span className="text-sm text-[var(--text-secondary)]">{item.type}</span>
                      </td>
                      <td className="px-6 py-4" onClick={() => setSelectedItem(item)}>
                        <span className={cn("text-xs font-bold px-2 py-1 rounded bg-[var(--bg)]/50", getRarityColor(item.rarity))}>
                          {item.rarity}
                        </span>
                      </td>
                      <td className="px-6 py-4" onClick={() => setSelectedItem(item)}>
                        <div className="font-mono font-medium text-[var(--up)]">${item.aiPurchasePrice.toLocaleString()}</div>
                      </td>
                      <td className="px-6 py-4" onClick={() => setSelectedItem(item)}>
                        <div className="font-mono font-medium text-[var(--text-primary)]">${item.marketPrice.toLocaleString()}</div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {filteredItems.length === 0 && (
        <div className="px-6 py-12 text-center text-[var(--text-secondary)]">
          <Search className="w-8 h-8 mx-auto mb-3 opacity-20" />
          <p>No skins found matching your criteria.</p>
        </div>
      )}

      {/* Comparison Modal */}
      <AnimatePresence>
        {isCompareModalOpen && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 md:p-8">
            <motion.div 
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={() => setIsCompareModalOpen(false)}
              className="absolute inset-0 bg-black/60 backdrop-blur-sm"
            />
            <motion.div 
              initial={{ scale: 0.9, opacity: 0, y: 20 }}
              animate={{ scale: 1, opacity: 1, y: 0 }}
              exit={{ scale: 0.9, opacity: 0, y: 20 }}
              className="relative w-full max-w-6xl bg-[var(--bg)] rounded-2xl shadow-2xl border border-[var(--border)] overflow-hidden flex flex-col max-h-[90vh]"
            >
              {/* Modal Header */}
              <div className="p-6 border-b border-[var(--border)] flex justify-between items-center bg-[var(--surface)]">
                <div>
                  <h2 className="text-xl font-bold text-[var(--text-primary)] flex items-center gap-2">
                    <Scale className="w-6 h-6 text-[var(--accent)]" />
                    Skin Comparison
                  </h2>
                  <p className="text-sm text-[var(--text-secondary)]">Comparing {selectedItems.length} items side-by-side</p>
                </div>
                <button 
                  onClick={() => setIsCompareModalOpen(false)}
                  className="p-2 hover:bg-[var(--surface-hover)] rounded-full transition-colors"
                >
                  <X className="w-6 h-6 text-[var(--text-secondary)]" />
                </button>
              </div>

              {/* Modal Content */}
              <div className="flex-1 overflow-x-auto p-6">
                <div className="flex gap-6 min-w-max pb-4">
                  {selectedItems.map((item) => (
                    <div key={item.id} className="w-72 flex flex-col gap-6">
                      {/* Item Preview */}
                      <div className={cn("h-40 rounded-xl flex items-center justify-center relative overflow-hidden", getRarityBg(item.rarity))}>
                        <Sword className={cn("w-20 h-20 opacity-20 transform -rotate-45", getRarityColor(item.rarity))} />
                        <div className="absolute top-3 right-3">
                          <span className={cn("text-[10px] font-bold px-2 py-1 rounded uppercase tracking-wider bg-[var(--bg)]/80 backdrop-blur-sm", getRarityColor(item.rarity))}>
                            {item.rarity}
                          </span>
                        </div>
                      </div>

                      {/* Details List */}
                      <div className="space-y-4">
                        <div>
                          <div className="text-xs text-[var(--text-secondary)] uppercase font-bold mb-1">Name</div>
                          <div className="text-lg font-bold text-[var(--text-primary)]">{item.name}</div>
                          <div className="text-sm text-[var(--text-secondary)]">{item.type}</div>
                        </div>

                        <div className="grid grid-cols-2 gap-4">
                          <div className="p-3 rounded-xl bg-[var(--surface)] border border-[var(--border)]">
                            <div className="text-[10px] text-[var(--text-secondary)] uppercase font-bold mb-1">Market</div>
                            <div className="text-lg font-mono font-bold">${item.marketPrice.toLocaleString()}</div>
                          </div>
                          <div className="p-3 rounded-xl bg-[var(--up)]/5 border border-[var(--up)]/20">
                            <div className="text-[10px] text-[var(--up)] uppercase font-bold mb-1">AI Buy</div>
                            <div className="text-lg font-mono font-bold text-[var(--up)]">${item.aiPurchasePrice.toLocaleString()}</div>
                          </div>
                        </div>

                        <div className="space-y-3 p-4 rounded-xl bg-[var(--surface)] border border-[var(--border)]">
                          <div className="flex justify-between items-center">
                            <span className="text-xs text-[var(--text-secondary)]">Float Value</span>
                            <span className="text-xs font-mono font-bold text-[var(--text-primary)]">{item.floatValue}</span>
                          </div>
                          <div className="w-full h-1.5 bg-[var(--bg)] rounded-full overflow-hidden">
                            <div 
                              className="h-full bg-[var(--accent)]" 
                              style={{ width: `${(1 - item.floatValue) * 100}%` }}
                            />
                          </div>
                          <div className="flex justify-between items-center pt-2 border-t border-[var(--border)]">
                            <span className="text-xs text-[var(--text-secondary)]">Potential</span>
                            <span className="text-xs font-bold text-[var(--accent)]">
                              {((item.aiPurchasePrice / item.marketPrice - 1) * 100).toFixed(1)}%
                            </span>
                          </div>
                        </div>

                        <button 
                          onClick={() => toggleSelection(item.id)}
                          className="w-full py-2 text-xs font-bold text-red-500 hover:bg-red-500/10 rounded-lg transition-colors border border-red-500/20"
                        >
                          Remove from Comparison
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Modal Footer */}
              <div className="p-6 border-t border-[var(--border)] bg-[var(--surface)] flex justify-end gap-3">
                <button 
                  onClick={() => setSelectedIds([])}
                  className="px-6 py-2 text-sm font-bold text-[var(--text-secondary)] hover:text-[var(--text-primary)] transition-colors"
                >
                  Clear Selection
                </button>
                <button 
                  onClick={() => setIsCompareModalOpen(false)}
                  className="px-8 py-2 bg-[var(--accent)] text-white rounded-lg text-sm font-bold shadow-lg hover:bg-[var(--accent)]/90 transition-colors"
                >
                  Close Comparison
                </button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      {/* CSGO Detail Modal */}
      <AnimatePresence>
        {selectedItem && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 md:p-8">
            <motion.div 
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={() => setSelectedItem(null)}
              className="absolute inset-0 bg-black/60 backdrop-blur-sm"
            />
            <motion.div 
              initial={{ scale: 0.9, opacity: 0, y: 20 }}
              animate={{ scale: 1, opacity: 1, y: 0 }}
              exit={{ scale: 0.9, opacity: 0, y: 20 }}
              className="relative w-full max-w-2xl bg-[var(--bg)] rounded-2xl shadow-2xl border border-[var(--border)] overflow-hidden flex flex-col"
            >
              {/* Modal Header */}
              <div className="p-6 border-b border-[var(--border)] flex justify-between items-center bg-[var(--surface)]">
                <div className="flex items-center gap-4">
                  <div className="w-12 h-12 rounded-xl bg-[var(--accent)]/10 flex items-center justify-center text-[var(--accent)]">
                    <Star className="w-6 h-6" />
                  </div>
                  <div>
                    <h2 className="text-xl font-bold text-[var(--text-primary)] flex items-center gap-2">
                      {selectedItem.name}
                      <span className={cn("text-[10px] font-bold uppercase px-2 py-0.5 rounded bg-[var(--border)]/50", getRarityColor(selectedItem.rarity))}>
                        {selectedItem.rarity}
                      </span>
                    </h2>
                    <p className="text-sm text-[var(--text-secondary)] flex items-center gap-2">
                      <Clock className="w-3 h-3" />
                      Last updated: {new Date(selectedItem.updatedAt).toLocaleTimeString()}
                    </p>
                  </div>
                </div>
                <button 
                  onClick={() => setSelectedItem(null)}
                  className="p-2 hover:bg-[var(--surface-hover)] rounded-full transition-colors"
                >
                  <X className="w-6 h-6 text-[var(--text-secondary)]" />
                </button>
              </div>

              {/* Modal Content */}
              <div className="p-6 space-y-6 overflow-y-auto max-h-[70vh]">
                {/* Price and Potential */}
                <div className="grid grid-cols-2 gap-4">
                  <div className="p-4 rounded-xl bg-[var(--surface)] border border-[var(--border)]">
                    <div className="text-xs text-[var(--text-secondary)] uppercase font-bold mb-1 flex items-center gap-2">
                      <DollarSign className="w-3 h-3" />
                      Market Price
                    </div>
                    <div className="text-3xl font-mono font-bold text-[var(--text-primary)]">
                      ${selectedItem.marketPrice.toLocaleString()}
                    </div>
                  </div>
                  <div className="p-4 rounded-xl bg-[var(--surface)] border border-[var(--border)]">
                    <div className="text-xs text-[var(--text-secondary)] uppercase font-bold mb-1 flex items-center gap-2">
                      <TrendingUp className="w-3 h-3" />
                      AI Target Price
                    </div>
                    <div className="text-3xl font-mono font-bold text-[var(--up)]">
                      ${selectedItem.aiPurchasePrice.toLocaleString()}
                    </div>
                  </div>
                </div>

                {/* Technical Details */}
                <div className="grid grid-cols-3 gap-4">
                  <div className="p-4 rounded-xl bg-[var(--surface)] border border-[var(--border)]">
                    <div className="text-[10px] text-[var(--text-secondary)] uppercase font-bold mb-2">AI Score</div>
                    <div className="flex items-center gap-0.5">
                      {Array.from({ length: 5 }).map((_, i) => (
                        <Star 
                          key={i} 
                          className={cn(
                            "w-4 h-4", 
                            i < selectedItem.aiScore ? "fill-[var(--accent)] text-[var(--accent)]" : "text-[var(--border)]"
                          )} 
                        />
                      ))}
                    </div>
                  </div>
                  <div className="p-4 rounded-xl bg-[var(--surface)] border border-[var(--border)]">
                    <div className="text-[10px] text-[var(--text-secondary)] uppercase font-bold mb-1">AI Action</div>
                    <span className={cn(
                      "px-2 py-0.5 rounded text-[10px] font-bold uppercase tracking-wider inline-block",
                      selectedItem.aiAction === 'Buy' && "bg-[var(--up)]/10 text-[var(--up)] border border-[var(--up)]/20",
                      selectedItem.aiAction === 'Sell' && "bg-[var(--down)]/10 text-[var(--down)] border border-[var(--down)]/20",
                      selectedItem.aiAction === 'Hold' && "bg-[var(--text-secondary)]/10 text-[var(--text-secondary)] border border-[var(--text-secondary)]/20"
                    )}>
                      {selectedItem.aiAction}
                    </span>
                  </div>
                  <div className="p-4 rounded-xl bg-[var(--surface)] border border-[var(--border)]">
                    <div className="text-[10px] text-[var(--text-secondary)] uppercase font-bold mb-1">Potential</div>
                    <div className="text-lg font-mono font-bold text-[var(--accent)]">
                      {((1 - selectedItem.aiPurchasePrice / selectedItem.marketPrice) * 100).toFixed(1)}%
                    </div>
                  </div>
                </div>

                {/* Historical Chart */}
                <div className="p-4 rounded-xl bg-[var(--surface)] border border-[var(--border)]">
                  <div className="flex justify-between items-center mb-4">
                    <div className="text-xs text-[var(--text-secondary)] uppercase font-bold flex items-center gap-2">
                      <BarChart3 className="w-3 h-3" />
                      Price Trend (20D)
                    </div>
                    <div className="text-[10px] text-[var(--text-secondary)]">Historical Mock Data</div>
                  </div>
                  <div className="h-48 w-full">
                    <ResponsiveContainer width="100%" height="100%">
                      <AreaChart data={itemHistory}>
                        <defs>
                          <linearGradient id="colorCSGO" x1="0" y1="0" x2="0" y2="1">
                            <stop offset="5%" stopColor="var(--accent)" stopOpacity={0.3}/>
                            <stop offset="95%" stopColor="var(--accent)" stopOpacity={0}/>
                          </linearGradient>
                        </defs>
                        <XAxis dataKey="date" hide />
                        <YAxis domain={['auto', 'auto']} hide />
                        <Tooltip 
                          contentStyle={{ backgroundColor: 'var(--surface)', borderColor: 'var(--border)', borderRadius: '8px' }}
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

              {/* Modal Footer */}
              <div className="p-6 border-t border-[var(--border)] bg-[var(--surface)] flex justify-end gap-3">
                <button 
                  onClick={() => setSelectedItem(null)}
                  className="px-8 py-2 bg-[var(--accent)] text-white rounded-lg text-sm font-bold shadow-lg hover:bg-[var(--accent)]/90 transition-colors"
                >
                  Close Details
                </button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>
    </motion.div>
  );
}
