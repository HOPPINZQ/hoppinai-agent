export interface StockData {
  id: string;
  symbol: string;
  name: string;
  price: number;
  change: number;
  changePercent: number;
  aiScore: number; // 1-5
  aiScoreDetails: {
    volatility: number;
    momentum: number;
    trend: number;
  };
  aiProfitLoss: number;
  aiProfitLossPercent: number;
  aiAction: 'Buy' | 'Hold' | 'Sell';
  updatedAt: string;
}

export interface ChatHistoryItem {
  id: string;
  timestamp: string;
  topic: string;
  symbol: string;
  userSummary: string;
  aiSummary: string;
  fullContent: {
    user: string;
    ai: string;
  };
  toolCall?: {
    name: string;
    args: {
      itemName: string;
      price: number;
      rarity: string;
    };
  };
}

export const generateMockStocks = (count: number = 20): StockData[] => {
  const symbols = ['AAPL', 'MSFT', 'GOOGL', 'AMZN', 'TSLA', 'META', 'NVDA', 'JPM', 'V', 'JNJ', 'WMT', 'PG', 'MA', 'UNH', 'DIS', 'HD', 'BAC', 'VZ', 'CMCSA', 'ADBE'];
  const names = ['Apple Inc.', 'Microsoft Corp.', 'Alphabet Inc.', 'Amazon.com Inc.', 'Tesla Inc.', 'Meta Platforms', 'NVIDIA Corp.', 'JPMorgan Chase', 'Visa Inc.', 'Johnson & Johnson', 'Walmart Inc.', 'Procter & Gamble', 'Mastercard Inc.', 'UnitedHealth Group', 'Walt Disney Co.', 'Home Depot', 'Bank of America', 'Verizon Comm.', 'Comcast Corp.', 'Adobe Inc.'];
  
  return Array.from({ length: count }).map((_, i) => {
    const isPositive = Math.random() > 0.4;
    const price = +(Math.random() * 500 + 50).toFixed(2);
    const change = +(Math.random() * 10 * (isPositive ? 1 : -1)).toFixed(2);
    const changePercent = +((change / price) * 100).toFixed(2);
    
    const aiIsPositive = Math.random() > 0.3;
    const aiProfitLoss = +(Math.random() * 5000 * (aiIsPositive ? 1 : -1)).toFixed(2);
    const aiProfitLossPercent = +(Math.random() * 20 * (aiIsPositive ? 1 : -1)).toFixed(2);
    
    let aiAction: 'Buy' | 'Hold' | 'Sell' = 'Hold';
    if (aiProfitLossPercent > 5) aiAction = 'Buy';
    else if (aiProfitLossPercent < -5) aiAction = 'Sell';

    return {
      id: `stock-${i}`,
      symbol: symbols[i % symbols.length],
      name: names[i % names.length],
      price,
      change,
      changePercent,
      aiScore: Math.floor(Math.random() * 5) + 1,
      aiScoreDetails: {
        volatility: Math.floor(Math.random() * 5) + 1,
        momentum: Math.floor(Math.random() * 5) + 1,
        trend: Math.floor(Math.random() * 5) + 1,
      },
      aiProfitLoss,
      aiProfitLossPercent,
      aiAction,
      updatedAt: new Date(Date.now() - Math.random() * 10000000).toISOString(),
    };
  });
};

export interface CSGOItem {
  id: string;
  name: string;
  type: string;
  rarity: 'Consumer Grade' | 'Industrial Grade' | 'Mil-Spec Grade' | 'Restricted' | 'Classified' | 'Covert' | 'Contraband';
  aiPurchasePrice: number;
  marketPrice: number;
  floatValue: number;
  aiScore: number;
  aiAction: 'Buy' | 'Hold' | 'Sell';
  aiScoreDetails: {
    volatility: number;
    momentum: number;
    trend: number;
  };
  updatedAt: string;
}

export const generateMockCSGOItems = (count: number = 20): CSGOItem[] => {
  const skins = [
    { name: 'Dragon Lore', type: 'AWP', rarity: 'Covert' },
    { name: 'Asiimov', type: 'AWP', rarity: 'Covert' },
    { name: 'Howl', type: 'M4A4', rarity: 'Contraband' },
    { name: 'Fire Serpent', type: 'AK-47', rarity: 'Covert' },
    { name: 'Printstream', type: 'M4A1-S', rarity: 'Covert' },
    { name: 'Doppler', type: 'Knife', rarity: 'Covert' },
    { name: 'Fade', type: 'Knife', rarity: 'Covert' },
    { name: 'Wild Lotus', type: 'AK-47', rarity: 'Covert' },
    { name: 'Gungnir', type: 'AWP', rarity: 'Covert' },
    { name: 'Hyper Beast', type: 'M4A1-S', rarity: 'Covert' },
    { name: 'Neo-Noir', type: 'USP-S', rarity: 'Covert' },
    { name: 'Kill Confirmed', type: 'USP-S', rarity: 'Covert' },
    { name: 'Empress', type: 'AK-47', rarity: 'Covert' },
    { name: 'Blood in the Water', type: 'SSG 08', rarity: 'Covert' },
    { name: 'Golden Coil', type: 'M4A1-S', rarity: 'Classified' },
    { name: 'Mecha Industries', type: 'FAMAS', rarity: 'Classified' },
    { name: 'Fuel Injector', type: 'AK-47', rarity: 'Covert' },
    { name: 'Desolate Space', type: 'M4A4', rarity: 'Classified' },
    { name: 'Point Disarray', type: 'AK-47', rarity: 'Classified' },
    { name: 'Frontside Misty', type: 'AK-47', rarity: 'Classified' },
  ];

  return Array.from({ length: count }).map((_, i) => {
    const skin = skins[i % skins.length];
    const marketPrice = +(Math.random() * 2000 + 10).toFixed(2);
    const aiPurchasePrice = +(marketPrice * (0.8 + Math.random() * 0.4)).toFixed(2);
    
    return {
      id: `csgo-${i}`,
      name: skin.name,
      type: skin.type,
      rarity: skin.rarity as any,
      aiPurchasePrice,
      marketPrice,
      floatValue: +(Math.random() * 0.1).toFixed(5),
      aiScore: Math.floor(Math.random() * 5) + 1,
      aiAction: (['Buy', 'Hold', 'Sell'] as const)[Math.floor(Math.random() * 3)],
      aiScoreDetails: {
        volatility: Math.floor(Math.random() * 5) + 1,
        momentum: Math.floor(Math.random() * 5) + 1,
        trend: Math.floor(Math.random() * 5) + 1,
      },
      updatedAt: new Date(Date.now() - Math.random() * 10000000).toISOString(),
    };
  });
};

export const generateMockChatHistory = (count: number = 15): ChatHistoryItem[] => {
  const topics = ['Earnings Report Analysis', 'Market Trend Prediction', 'Portfolio Rebalancing', 'Risk Assessment', 'Sector Rotation', 'CSGO Trade Execution', 'Inventory Valuation'];
  const symbols = ['AAPL', 'TSLA', 'NVDA', 'SPY', 'QQQ', 'CSGO:AK47', 'CSGO:AWP'];
  
  return Array.from({ length: count }).map((_, i) => {
    const topic = topics[Math.floor(Math.random() * topics.length)];
    const symbol = symbols[Math.floor(Math.random() * symbols.length)];
    const isCSGO = topic.includes('CSGO') || symbol.includes('CSGO');
    
    let toolCall = undefined;
    if (isCSGO && Math.random() > 0.3) {
      const isBuy = Math.random() > 0.5;
      toolCall = {
        name: isBuy ? 'buy_csgo_item' : 'sell_csgo_item',
        args: {
          itemName: isBuy ? 'AK-47 | Slate' : 'AWP | Asiimov',
          price: isBuy ? 12.50 : 145.00,
          rarity: isBuy ? 'Restricted' : 'Covert'
        }
      };
    }

    return {
      id: `chat-${i}`,
      timestamp: new Date(Date.now() - Math.random() * 30 * 24 * 60 * 60 * 1000).toISOString(),
      topic,
      symbol,
      userSummary: isCSGO ? `Execute ${toolCall?.name === 'buy_csgo_item' ? 'purchase' : 'sale'} for ${toolCall?.args.itemName || 'CSGO item'}` : `What is the outlook for ${symbol} considering recent ${topic.toLowerCase()}?`,
      aiSummary: isCSGO ? `Transaction order for ${toolCall?.args.itemName} has been prepared.` : `Based on current indicators, ${symbol} shows strong potential. Recommend holding or accumulating on dips.`,
      fullContent: {
        user: isCSGO ? `I want to ${toolCall?.name === 'buy_csgo_item' ? 'buy' : 'sell'} the ${toolCall?.args.itemName}.` : `Can you provide a detailed analysis of ${symbol}? I'm specifically interested in the ${topic.toLowerCase()} and how it might affect the stock price over the next quarter. Should I adjust my position?`,
        ai: isCSGO ? `I've prepared the transaction details for the ${toolCall?.args.itemName}. Please review the order card below to confirm the trade.` : `Certainly. Analyzing ${symbol} in the context of ${topic.toLowerCase()}:\n\n1. **Technical Indicators**: The stock is currently trading above its 50-day moving average, indicating a bullish short-term trend.\n2. **Fundamental Factors**: Recent earnings beat expectations by 12%, driven by strong growth in their cloud segment.\n3. **Market Sentiment**: Overall sentiment remains positive, though there's some volatility expected due to macroeconomic factors.\n\n**Recommendation**: The AI model predicts a 8-12% upside over the next 3 months. If you currently hold the stock, it's advisable to maintain your position. If looking to enter, consider buying on minor pullbacks. Set a stop-loss at 5% below current support levels to manage downside risk.`
      },
      toolCall
    };
  }).sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime());
};

export const mockPortfolioTrend = Array.from({ length: 30 }).map((_, i) => {
  const date = new Date();
  date.setDate(date.getDate() - (30 - i));
  return {
    date: date.toISOString().split('T')[0],
    value: 100000 + (Math.sin(i / 5) * 5000) + (i * 200) + (Math.random() * 1000 - 500)
  };
});

export interface CSGOPurchaseRecord {
  id: string;
  name: string;
  type: string;
  aiPurchasePrice: number;
  marketPrice: number;
  purchaseDate: string;
  rarity: string;
  aiScore: number;
  aiScoreDetails: {
    volatility: number;
    momentum: number;
    trend: number;
  };
}

export const generateMockStockHistory = (basePrice: number, days: number = 30) => {
  return Array.from({ length: days }).map((_, i) => {
    const date = new Date();
    date.setDate(date.getDate() - (days - i));
    const volatility = 0.02;
    const change = basePrice * volatility * (Math.random() - 0.5);
    const price = basePrice + change + (Math.sin(i / 3) * (basePrice * 0.05));
    return {
      date: date.toISOString().split('T')[0],
      price: +price.toFixed(2)
    };
  });
};

export const generateMockCSGOPurchaseHistory = (count: number = 20): CSGOPurchaseRecord[] => {
  const skins = [
    { name: 'Dragon Lore', type: 'AWP', rarity: 'Covert' },
    { name: 'Asiimov', type: 'AWP', rarity: 'Covert' },
    { name: 'Howl', type: 'M4A4', rarity: 'Contraband' },
    { name: 'Fire Serpent', type: 'AK-47', rarity: 'Covert' },
    { name: 'Printstream', type: 'M4A1-S', rarity: 'Covert' },
    { name: 'Doppler', type: 'Knife', rarity: 'Covert' },
    { name: 'Fade', type: 'Knife', rarity: 'Covert' },
    { name: 'Wild Lotus', type: 'AK-47', rarity: 'Covert' },
    { name: 'Gungnir', type: 'AWP', rarity: 'Covert' },
    { name: 'Hyper Beast', type: 'M4A1-S', rarity: 'Covert' },
  ];

  return Array.from({ length: count }).map((_, i) => {
    const skin = skins[i % skins.length];
    const marketPrice = +(Math.random() * 2000 + 100).toFixed(2);
    const aiPurchasePrice = +(marketPrice * (0.85 + Math.random() * 0.1)).toFixed(2);
    
    return {
      id: `purchase-${i}`,
      name: skin.name,
      type: skin.type,
      aiPurchasePrice,
      marketPrice,
      purchaseDate: new Date(Date.now() - Math.random() * 30 * 24 * 60 * 60 * 1000).toISOString(),
      rarity: skin.rarity,
      aiScore: Math.floor(Math.random() * 5) + 1,
      aiScoreDetails: {
        volatility: Math.floor(Math.random() * 5) + 1,
        momentum: Math.floor(Math.random() * 5) + 1,
        trend: Math.floor(Math.random() * 5) + 1,
      },
    };
  }).sort((a, b) => new Date(b.purchaseDate).getTime() - new Date(a.purchaseDate).getTime());
};
