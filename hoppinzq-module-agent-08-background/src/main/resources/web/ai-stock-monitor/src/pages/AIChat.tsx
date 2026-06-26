import { useState, useRef, useEffect } from "react";
import { motion, AnimatePresence } from "motion/react";
import { 
  Send, 
  Bot, 
  User, 
  Sparkles, 
  Trash2, 
  Plus, 
  Sword, 
  Shield, 
  Zap, 
  DollarSign, 
  TrendingUp, 
  Clock,
  CheckCircle2,
  AlertCircle,
  ArrowRight,
  Info
} from "lucide-react";
import { GoogleGenAI, Type, GenerateContentResponse } from "@google/genai";
import { cn } from "@/lib/utils";

// Types for the chat
interface Message {
  id: string;
  role: "user" | "model";
  content: string;
  timestamp: Date;
  toolCalls?: any[];
  toolResults?: any[];
}

interface TransactionDetails {
  itemName: string;
  price: number;
  type: "buy" | "sell";
  rarity: string;
  potentialProfit?: string;
  status: "pending" | "completed";
}

export function AIChat() {
  const [messages, setMessages] = useState<Message[]>([
    {
      id: "1",
      role: "model",
      content: "Hello! I'm your AI Trading Assistant. I can help you analyze the CSGO market, evaluate your inventory, and even execute simulated trades. What would you like to do today?",
      timestamp: new Date(),
    }
  ]);
  const [input, setInput] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [showMarketSidebar, setShowMarketSidebar] = useState(true);
  const scrollRef = useRef<HTMLDivElement>(null);

  // Auto-scroll to bottom
  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages]);

  const handleSend = async () => {
    if (!input.trim() || isLoading) return;

    const userMessage: Message = {
      id: Date.now().toString(),
      role: "user",
      content: input,
      timestamp: new Date(),
    };

    setMessages(prev => [...prev, userMessage]);
    setInput("");
    setIsLoading(true);

    try {
      const ai = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY! });
      
      // Define tools
      const buyTool = {
        name: "buy_csgo_item",
        parameters: {
          type: Type.OBJECT,
          description: "Execute a simulated purchase of a CSGO item.",
          properties: {
            itemName: { type: Type.STRING, description: "The full name of the CSGO item." },
            price: { type: Type.NUMBER, description: "The purchase price in USD." },
            rarity: { type: Type.STRING, description: "The rarity grade of the item." }
          },
          required: ["itemName", "price", "rarity"]
        }
      };

      const sellTool = {
        name: "sell_csgo_item",
        parameters: {
          type: Type.OBJECT,
          description: "Execute a simulated sale of a CSGO item.",
          properties: {
            itemName: { type: Type.STRING, description: "The full name of the CSGO item." },
            price: { type: Type.NUMBER, description: "The selling price in USD." },
            rarity: { type: Type.STRING, description: "The rarity grade of the item." }
          },
          required: ["itemName", "price", "rarity"]
        }
      };

      const response = await ai.models.generateContent({
        model: "gemini-3-flash-preview",
        contents: messages.concat(userMessage).map(m => ({
          role: m.role,
          parts: [{ text: m.content }]
        })),
        config: {
          systemInstruction: "You are a professional CSGO market analyst and trading bot. You can help users buy and sell items. When a user wants to buy or sell, use the appropriate tool. Be concise and technical. If the user asks for market trends, provide a detailed analysis based on current mock data.",
          tools: [{ functionDeclarations: [buyTool, sellTool] }]
        }
      });

      const modelResponse: Message = {
        id: (Date.now() + 1).toString(),
        role: "model",
        content: response.text || "I've processed your request.",
        timestamp: new Date(),
        toolCalls: response.functionCalls
      };

      setMessages(prev => [...prev, modelResponse]);
    } catch (error) {
      console.error("Chat Error:", error);
      setMessages(prev => [...prev, {
        id: Date.now().toString(),
        role: "model",
        content: "Sorry, I encountered an error. Please try again.",
        timestamp: new Date(),
      }]);
    } finally {
      setIsLoading(false);
    }
  };

  const clearChat = () => {
    setMessages([{
      id: "1",
      role: "model",
      content: "Chat cleared. How can I help you now?",
      timestamp: new Date(),
    }]);
  };

  return (
    <div className="flex h-[calc(100vh-12rem)] max-w-7xl mx-auto bg-[var(--surface)] border border-[var(--border)] rounded-2xl overflow-hidden shadow-xl relative">
      {/* Main Chat Area */}
      <div className="flex-1 flex flex-col min-w-0">
        {/* Chat Header */}
        <div className="px-6 py-4 border-b border-[var(--border)] bg-[var(--surface)]/50 backdrop-blur-md flex justify-between items-center">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-[var(--accent)]/10 flex items-center justify-center text-[var(--accent)]">
              <Bot className="w-6 h-6" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-[var(--text-primary)]">AI Trading Terminal</h2>
              <div className="flex items-center gap-2">
                <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
                <span className="text-xs text-[var(--text-secondary)]">System Online</span>
              </div>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <button 
              onClick={() => setShowMarketSidebar(!showMarketSidebar)}
              className={cn(
                "p-2 rounded-lg transition-all",
                showMarketSidebar ? "bg-[var(--accent)]/10 text-[var(--accent)]" : "text-[var(--text-secondary)] hover:bg-[var(--surface-hover)]"
              )}
              title="Toggle Market Sidebar"
            >
              <TrendingUp className="w-5 h-5" />
            </button>
            <button 
              onClick={clearChat}
              className="p-2 text-[var(--text-secondary)] hover:text-[var(--down)] hover:bg-[var(--down)]/5 rounded-lg transition-all"
              title="Clear Conversation"
            >
              <Trash2 className="w-5 h-5" />
            </button>
          </div>
        </div>

        {/* Messages Area */}
        <div 
          ref={scrollRef}
          className="flex-1 overflow-y-auto p-6 space-y-6 scroll-smooth"
        >
          <AnimatePresence initial={false}>
            {messages.map((msg) => (
              <motion.div
                key={msg.id}
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                className={cn(
                  "flex gap-4",
                  msg.role === "user" ? "flex-row-reverse" : "flex-row"
                )}
              >
                <div className={cn(
                  "w-8 h-8 rounded-lg flex items-center justify-center shrink-0 mt-1",
                  msg.role === "user" ? "bg-[var(--surface-hover)]" : "bg-[var(--accent)]/10"
                )}>
                  {msg.role === "user" ? <User className="w-4 h-4 text-[var(--text-secondary)]" /> : <Bot className="w-4 h-4 text-[var(--accent)]" />}
                </div>

                <div className={cn(
                  "flex flex-col gap-2 max-w-[85%]",
                  msg.role === "user" ? "items-end" : "items-start"
                )}>
                  <div className={cn(
                    "px-4 py-3 rounded-2xl text-sm leading-relaxed",
                    msg.role === "user" 
                      ? "bg-[var(--accent)] text-white rounded-tr-none" 
                      : "bg-[var(--bg)] border border-[var(--border)] text-[var(--text-primary)] rounded-tl-none"
                  )}>
                    {msg.content}
                  </div>

                  {/* Tool Call Rendering (Transaction Details) */}
                  {msg.toolCalls?.map((call, idx) => (
                    <TransactionCard key={idx} call={call} />
                  ))}

                  <span className="text-[10px] text-[var(--text-secondary)] font-mono px-1">
                    {msg.timestamp.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                  </span>
                </div>
              </motion.div>
            ))}
          </AnimatePresence>
          {isLoading && (
            <motion.div 
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              className="flex gap-4"
            >
              <div className="w-8 h-8 rounded-lg bg-[var(--accent)]/10 flex items-center justify-center shrink-0">
                <Bot className="w-4 h-4 text-[var(--accent)]" />
              </div>
              <div className="bg-[var(--bg)] border border-[var(--border)] px-4 py-3 rounded-2xl rounded-tl-none flex gap-1">
                <span className="w-1.5 h-1.5 bg-[var(--text-secondary)] rounded-full animate-bounce"></span>
                <span className="w-1.5 h-1.5 bg-[var(--text-secondary)] rounded-full animate-bounce [animation-delay:0.2s]"></span>
                <span className="w-1.5 h-1.5 bg-[var(--text-secondary)] rounded-full animate-bounce [animation-delay:0.4s]"></span>
              </div>
            </motion.div>
          )}
        </div>

        {/* Input Area */}
        <div className="p-4 bg-[var(--surface)] border-t border-[var(--border)]">
          <div className="max-w-4xl mx-auto relative">
            <div className="flex gap-2">
              <div className="flex-1 relative">
                <input
                  type="text"
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  onKeyDown={(e) => e.key === "Enter" && handleSend()}
                  placeholder="Ask about market trends or execute a trade..."
                  className="w-full bg-[var(--bg)] border border-[var(--border)] rounded-xl px-4 py-3 pr-12 text-sm focus:outline-none focus:border-[var(--accent)] transition-colors"
                />
                <div className="absolute right-3 top-1/2 -translate-y-1/2 flex items-center gap-2">
                  <Sparkles className="w-4 h-4 text-[var(--accent)] opacity-50" />
                </div>
              </div>
              <button
                onClick={handleSend}
                disabled={!input.trim() || isLoading}
                className="bg-[var(--accent)] text-white p-3 rounded-xl hover:bg-[var(--accent)]/90 disabled:opacity-50 disabled:cursor-not-allowed transition-all shadow-lg shadow-[var(--accent)]/20"
              >
                <Send className="w-5 h-5" />
              </button>
            </div>
            
            {/* Quick Suggestions */}
            <div className="flex gap-2 mt-3 overflow-x-auto pb-2 no-scrollbar">
              {[
                { label: "Analyze AK-47 Slate", icon: TrendingUp },
                { label: "Best buy under $50", icon: Zap },
                { label: "Sell my AWP Dragon Lore", icon: DollarSign },
                { label: "Market summary", icon: Info }
              ].map((s, i) => (
                <button
                  key={i}
                  onClick={() => setInput(s.label)}
                  className="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-[var(--bg)] border border-[var(--border)] text-[10px] font-bold uppercase tracking-wider text-[var(--text-secondary)] hover:border-[var(--accent)] hover:text-[var(--accent)] transition-all whitespace-nowrap"
                >
                  <s.icon className="w-3 h-3" />
                  {s.label}
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* Market Sidebar */}
      <AnimatePresence>
        {showMarketSidebar && (
          <motion.div 
            initial={{ width: 0, opacity: 0 }}
            animate={{ width: 320, opacity: 1 }}
            exit={{ width: 0, opacity: 0 }}
            className="border-l border-[var(--border)] bg-[var(--surface)] flex flex-col overflow-hidden"
          >
            <div className="p-4 border-b border-[var(--border)] flex justify-between items-center">
              <h3 className="font-bold text-sm uppercase tracking-wider text-[var(--text-primary)]">Market Insights</h3>
              <TrendingUp className="w-4 h-4 text-[var(--accent)]" />
            </div>
            <div className="flex-1 overflow-y-auto p-4 space-y-4">
              {/* Hot Items */}
              <div>
                <div className="text-[10px] font-black uppercase text-[var(--text-secondary)] mb-3 tracking-widest">Hot Deals</div>
                <div className="space-y-3">
                  {[
                    { name: "AK-47 | Slate", price: 12.50, change: "+5.2%", rarity: "Restricted" },
                    { name: "AWP | Asiimov", price: 145.00, change: "-2.1%", rarity: "Covert" },
                    { name: "M4A4 | Howl", price: 5400.00, change: "+1.5%", rarity: "Contraband" }
                  ].map((item, i) => (
                    <div key={i} className="p-3 rounded-xl bg-[var(--bg)] border border-[var(--border)] hover:border-[var(--accent)]/30 transition-all cursor-pointer group">
                      <div className="flex justify-between items-start mb-1">
                        <div className="text-xs font-bold text-[var(--text-primary)] group-hover:text-[var(--accent)] transition-colors">{item.name}</div>
                        <div className={cn("text-[10px] font-mono font-bold", item.change.startsWith('+') ? "text-[var(--up)]" : "text-[var(--down)]")}>
                          {item.change}
                        </div>
                      </div>
                      <div className="flex justify-between items-center">
                        <div className="text-[10px] text-[var(--text-secondary)]">{item.rarity}</div>
                        <div className="text-xs font-mono font-bold">${item.price.toLocaleString()}</div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Market Sentiment */}
              <div className="p-4 rounded-xl bg-[var(--accent)]/5 border border-[var(--accent)]/10">
                <div className="text-[10px] font-black uppercase text-[var(--accent)] mb-2 tracking-widest">AI Sentiment</div>
                <div className="flex items-center gap-3 mb-2">
                  <div className="text-2xl font-black text-[var(--accent)]">Bullish</div>
                  <TrendingUp className="w-6 h-6 text-[var(--accent)]" />
                </div>
                <p className="text-[10px] text-[var(--text-secondary)] leading-relaxed">
                  Market volume is up 12% in the last 24h. High demand for Covert items.
                </p>
              </div>

              {/* Recent Activity */}
              <div>
                <div className="text-[10px] font-black uppercase text-[var(--text-secondary)] mb-3 tracking-widest">Recent Activity</div>
                <div className="space-y-2">
                  {[
                    { user: "User_42", action: "Bought", item: "Glock-18 | Fade", time: "2m ago" },
                    { user: "TraderX", action: "Sold", item: "Desert Eagle | Blaze", time: "5m ago" }
                  ].map((act, i) => (
                    <div key={i} className="flex items-center gap-3 text-[10px]">
                      <div className="w-6 h-6 rounded bg-[var(--surface-hover)] flex items-center justify-center shrink-0">
                        <User className="w-3 h-3 text-[var(--text-secondary)]" />
                      </div>
                      <div className="flex-1">
                        <span className="font-bold text-[var(--text-primary)]">{act.user}</span>
                        <span className="text-[var(--text-secondary)] mx-1">{act.action.toLowerCase()}</span>
                        <span className="font-medium text-[var(--text-primary)]">{act.item}</span>
                      </div>
                      <div className="text-[var(--text-secondary)] font-mono">{act.time}</div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

function TransactionCard({ call }: { call: any }) {
  const isBuy = call.name === "buy_csgo_item";
  const { itemName, price, rarity } = call.args;

  return (
    <motion.div 
      initial={{ scale: 0.95, opacity: 0 }}
      animate={{ scale: 1, opacity: 1 }}
      className="w-full max-w-md bg-[var(--surface)] border border-[var(--border)] rounded-xl overflow-hidden shadow-lg mt-2"
    >
      <div className={cn(
        "px-4 py-2 flex justify-between items-center",
        isBuy ? "bg-[var(--up)]/10" : "bg-[var(--down)]/10"
      )}>
        <div className="flex items-center gap-2">
          {isBuy ? <Plus className="w-4 h-4 text-[var(--up)]" /> : <ArrowRight className="w-4 h-4 text-[var(--down)]" />}
          <span className={cn("text-xs font-black uppercase tracking-widest", isBuy ? "text-[var(--up)]" : "text-[var(--down)]")}>
            {isBuy ? "Purchase Order" : "Sale Order"}
          </span>
        </div>
        <div className="text-[10px] font-mono text-[var(--text-secondary)]">ID: {Math.random().toString(36).substr(2, 9).toUpperCase()}</div>
      </div>
      
      <div className="p-4 space-y-4">
        <div className="flex items-start justify-between gap-4">
          <div>
            <div className="text-[10px] text-[var(--text-secondary)] uppercase font-bold mb-1">Item Name</div>
            <div className="font-bold text-[var(--text-primary)]">{itemName}</div>
            <div className="mt-1">
              <span className="text-[9px] font-black px-1.5 py-0.5 rounded bg-[var(--border)]/50 text-[var(--text-secondary)] uppercase">
                {rarity}
              </span>
            </div>
          </div>
          <div className="text-right">
            <div className="text-[10px] text-[var(--text-secondary)] uppercase font-bold mb-1">Total Value</div>
            <div className="text-xl font-mono font-black text-[var(--text-primary)]">${price.toLocaleString()}</div>
          </div>
        </div>

        <div className="grid grid-cols-2 gap-3 pt-3 border-t border-[var(--border)]">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-[var(--bg)] flex items-center justify-center">
              <Zap className="w-4 h-4 text-[var(--accent)]" />
            </div>
            <div>
              <div className="text-[8px] text-[var(--text-secondary)] uppercase font-bold">Execution</div>
              <div className="text-[10px] font-bold">Instant</div>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-[var(--bg)] flex items-center justify-center">
              <Shield className="w-4 h-4 text-emerald-500" />
            </div>
            <div>
              <div className="text-[8px] text-[var(--text-secondary)] uppercase font-bold">Security</div>
              <div className="text-[10px] font-bold">Verified</div>
            </div>
          </div>
        </div>

        <div className="flex gap-2 pt-2">
          <button className="flex-1 bg-[var(--accent)] text-white py-2 rounded-lg text-xs font-bold hover:bg-[var(--accent)]/90 transition-all flex items-center justify-center gap-2">
            <CheckCircle2 className="w-3.5 h-3.5" />
            Confirm Transaction
          </button>
          <button className="px-3 py-2 bg-[var(--surface-hover)] border border-[var(--border)] rounded-lg text-xs font-bold text-[var(--text-secondary)] hover:text-[var(--down)] hover:border-[var(--down)]/30 transition-all">
            Cancel
          </button>
        </div>
      </div>
    </motion.div>
  );
}
