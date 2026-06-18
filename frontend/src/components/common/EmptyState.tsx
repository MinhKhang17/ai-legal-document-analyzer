import { FileSearch } from 'lucide-react';
import { type ReactNode } from 'react';
import { Button } from './Button';

interface EmptyStateProps {
  title: string;
  description: string;
  actionLabel?: string;
  onAction?: () => void;
  icon?: ReactNode;
}

export function EmptyState({ title, description, actionLabel, onAction, icon }: EmptyStateProps) {
  return (
    <div className="rounded-xl border border-dashed border-outline-variant bg-white/70 p-xl text-center dark:border-slate-700 dark:bg-slate-900/70">
      <div className="mx-auto mb-md flex h-12 w-12 items-center justify-center rounded-lg bg-surface-container-high text-primary dark:bg-slate-800 dark:text-inverse-primary">
        {icon ?? <FileSearch className="h-6 w-6" aria-hidden="true" />}
      </div>
      <h3 className="text-title-lg font-semibold text-on-surface dark:text-slate-100">{title}</h3>
      <p className="mx-auto mt-sm max-w-md text-sm text-on-surface-variant dark:text-slate-400">{description}</p>
      {actionLabel && onAction && (
        <Button className="mt-lg" onClick={onAction}>
          {actionLabel}
        </Button>
      )}
    </div>
  );
}
