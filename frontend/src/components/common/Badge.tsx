import { type HTMLAttributes } from 'react';
import { cn } from '../../utils/cn';

export type BadgeTone = 'blue' | 'gold' | 'green' | 'red' | 'slate' | 'purple' | 'amber';

interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  tone?: BadgeTone;
}

const toneClasses: Record<BadgeTone, string> = {
  blue: 'bg-risk-low-bg text-risk-low-text dark:bg-blue-950 dark:text-blue-200',
  gold: 'bg-secondary-container text-secondary dark:bg-amber-900/60 dark:text-amber-200',
  green: 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-200',
  red: 'bg-error-container text-risk-high-text dark:bg-red-950 dark:text-red-200',
  slate: 'bg-surface-container-low text-on-surface-variant dark:bg-slate-800 dark:text-slate-300',
  purple: 'bg-violet-100 text-violet-800 dark:bg-violet-950 dark:text-violet-200',
  amber: 'bg-risk-medium-bg text-risk-medium-text dark:bg-amber-950 dark:text-amber-200',
};

export function Badge({ tone = 'slate', className, children, ...props }: BadgeProps) {
  return (
    <span
      className={cn('inline-flex items-center justify-center gap-xs rounded-full px-sm py-xs text-[11px] font-bold uppercase tracking-wider whitespace-nowrap shrink-0', toneClasses[tone], className)}
      {...props}
    >
      {children}
    </span>
  );
}
