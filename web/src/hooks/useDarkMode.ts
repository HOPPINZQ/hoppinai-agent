"use client";

import { useState, useEffect } from "react";

export function useDarkMode(): boolean {
  const [isDark, setIsDark] = useState(false);

  useEffect(() => {
    const html = document.documentElement;
    setIsDark(html.classList.contains("dark"));

    const observer = new MutationObserver(() => {
      setIsDark(html.classList.contains("dark"));
    });

    observer.observe(html, { attributes: true, attributeFilter: ["class"] });
    return () => observer.disconnect();
  }, []);

  return isDark;
}

export interface SvgPalette {
  nodeFill: string;
  nodeStroke: string;
  nodeText: string;
  activeNodeFill: string;
  activeNodeStroke: string;
  activeNodeText: string;
  endNodeFill: string;
  endNodeStroke: string;
  endNodeText: string;
  edgeStroke: string;
  activeEdgeStroke: string;
  arrowFill: string;
  labelFill: string;
  bgSubtle: string;
}

export function useSvgPalette(): SvgPalette {
  // Light parchment (animal-island-ui) is the only mode now.
  // Cool slate greys and neon blue/purple are replaced with warm browns,
  // parchment fills, and mint-teal / NookPhone-purple accents.
  return {
    nodeFill: "#fbf7eb",
    nodeStroke: "#d4c9b4",
    nodeText: "#5a4a30",
    activeNodeFill: "#19c8b9",
    activeNodeStroke: "#11a89b",
    activeNodeText: "#ffffff",
    endNodeFill: "#b77dee",
    endNodeStroke: "#9070d0",
    endNodeText: "#ffffff",
    edgeStroke: "#c4b89e",
    activeEdgeStroke: "#19c8b9",
    arrowFill: "#9f927d",
    labelFill: "#8a7b66",
    bgSubtle: "#f6efe0",
  };
}
