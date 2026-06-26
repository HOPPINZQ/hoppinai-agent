"use client";

import { useState } from "react";
import { Tabs as LibraryTabs } from "animal-island-ui";
import type { TabItem } from "animal-island-ui";

interface TabsProps {
  tabs: { id: string; label: string }[];
  defaultTab?: string;
  /**
   * Render prop: called with the active tab id, returns the panel content.
   * Only the active panel is rendered (inactive panels get null) so heavy
   * content or hooks inside inactive tabs are not eagerly evaluated.
   */
  children: (activeTab: string) => React.ReactNode;
  className?: string;
}

/**
 * Adapts animal-island-ui Tabs (items[] + activeKey/onChange) to the
 * existing render-prop API (`<Tabs={...}>{(active) => ...}</Tabs>`).
 * Library Tabs supplies the parchment tab-bar chrome + leaf animation;
 * we own the active-tab state and only render the active panel.
 */
export function Tabs({ tabs, defaultTab, children, className }: TabsProps) {
  const defaultValue = defaultTab || tabs[0]?.id || "";
  const [active, setActive] = useState(defaultValue);

  const items: TabItem[] = tabs.map((t) => ({
    key: t.id,
    label: t.label,
    children: t.id === active ? <>{children(t.id)}</> : null,
  }));

  return (
    <LibraryTabs
      items={items}
      activeKey={active}
      onChange={setActive}
      leafAnimation={false}
      className={className}
    />
  );
}
