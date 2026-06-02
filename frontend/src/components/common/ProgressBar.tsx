import { cn } from '../../utils/cn';

interface ProgressBarProps {
  value: number;
  label?: string;
  className?: string;
}

export function ProgressBar({ value, label, className }: ProgressBarProps) {
  const width = Math.max(0, Math.min(100, value));
  return (
    <div className={cn('space-y-xs', className)}>
      {label && <div className="text-xs font-semibold text-on-surface-variant dark:text-slate-400">{label}</div>}
      <div className="h-2 overflow-hidden rounded-full bg-outline-variant/60 dark:bg-slate-700" aria-hidden="true">
        <div className="h-full rounded-full bg-primary dark:bg-inverse-primary" style={{ width: `${width}%` }} />
      </div>
      <span className="sr-only">{width}%</span>
    </div>
  );
}
