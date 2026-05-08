/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import { BrowserRouter as Router, Routes, Route } from "react-router-dom";
import { Layout } from "./components/Layout";
import { Dashboard } from "./pages/Dashboard";
import { AIChat } from "./pages/AIChat";
import { ChatHistory } from "./pages/ChatHistory";
import { CSGOInventory } from "./pages/CSGOInventory";
import { CSGOPurchaseHistory } from "./pages/CSGOPurchaseHistory";
import { Settings } from "./pages/Settings";
import { ThemeProvider } from "./lib/useTheme";

export default function App() {
  return (
    <ThemeProvider>
      <Router>
        <Layout>
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/chat" element={<AIChat />} />
            <Route path="/history" element={<ChatHistory />} />
            <Route path="/csgo-inventory" element={<CSGOInventory />} />
            <Route path="/csgo-purchase-history" element={<CSGOPurchaseHistory />} />
            <Route path="/settings" element={<Settings />} />
          </Routes>
        </Layout>
      </Router>
    </ThemeProvider>
  );
}
