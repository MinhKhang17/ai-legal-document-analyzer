import { type ReactNode } from 'react';
import { cn } from '../../utils/cn';

interface PageHeaderProps {
  title: string;
  subtitle?: string;
  eyebrow?: string;
  actions?: ReactNode;
  className?: string;
  compact?: boolean;
}

export function PageHeader({ title, subtitle, eyebrow, actions, className, compact = false }: PageHeaderProps) {
  return (
    <header className={cn(
      'flex flex-col md:flex-row md:justify-between',
      compact
        ? 'mb-sm gap-sm md:items-center'
        : 'mb-xl gap-md md:items-end',
      className,
    )}>
      <div className={cn('min-w-0 max-w-3xl', compact && 'md:flex-1')}>
        {eyebrow && <p className="label-uppercase mb-xs">{eyebrow}</p>}
        <h1 className={cn(
          'break-words font-sans font-semibold text-primary dark:text-inverse-primary',
          compact
            ? 'text-[clamp(1.25rem,2vw,1.5rem)] leading-tight'
            : 'text-headline-lg',
        )}>{title}</h1>
        {subtitle && <p className={cn(
          'text-on-surface-variant dark:text-slate-400',
          compact ? 'mt-xs text-sm' : 'mt-sm text-body-md',
        )}>{subtitle}</p>}
      </div>
      {actions && <div className={cn(
        'flex flex-wrap gap-sm',
        compact && 'w-full min-w-0 flex-nowrap gap-xs overflow-x-auto pb-xs [&>button]:shrink-0 md:w-auto md:flex-none md:justify-end',
      )}>{actions}</div>}
    </header>
  );
}
