import { type ReactNode } from 'react';
import { cn } from '../../utils/cn';

export interface TabItem {
  id: string;
  label: string;
  icon?: ReactNode;
}

interface TabsProps {
  items: TabItem[];
  activeId: string;
  onChange: (id: string) => void;
  variant?: 'line' | 'pill';
  tabListLabel?: string;
  getTabId?: (id: string) => string;
  getPanelId?: (id: string) => string;
}

export function Tabs({ items, activeId, onChange, variant = 'line', tabListLabel, getTabId, getPanelId }: TabsProps) {
  return (
    <div
      className={cn(
        'flex flex-wrap gap-xs',
        variant === 'line' ? 'border-b border-legal-border dark:border-slate-700' : 'rounded-lg bg-surface-container-low p-xs dark:bg-slate-800',
      )}
      role="tablist"
      aria-label={tabListLabel}
    >
      {items.map((item) => {
        const active = item.id === activeId;
        const tabId = getTabId?.(item.id);
        const panelId = getPanelId?.(item.id);
        return (
          <button
            key={item.id}
            type="button"
            role="tab"
            id={tabId}
            aria-selected={active}
            aria-controls={panelId}
            tabIndex={active ? 0 : -1}
            className={cn(
              'inline-flex items-center gap-xs px-md py-sm text-sm font-semibold transition',
              variant === 'line'
                ? active
                  ? 'border-b-2 border-primary text-primary dark:border-inverse-primary dark:text-inverse-primary'
                  : 'text-on-surface-variant hover:text-primary dark:text-slate-400 dark:hover:text-inverse-primary'
                : active
                  ? 'rounded-md bg-white text-primary shadow-sm dark:bg-slate-900 dark:text-inverse-primary'
                  : 'rounded-md text-on-surface-variant hover:bg-white/70 dark:text-slate-400 dark:hover:bg-slate-900',
            )}
            onClick={() => onChange(item.id)}
          >
            {item.icon}
            {item.label}
          </button>
        );
      })}
    </div>
  );
}
