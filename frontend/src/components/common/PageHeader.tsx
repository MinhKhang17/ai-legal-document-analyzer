import { type ReactNode } from 'react';
import { cn } from '../../utils/cn';

interface PageHeaderProps {
  title: string;
  subtitle?: string;
  eyebrow?: string;
  actions?: ReactNode;
  className?: string;
}

export function PageHeader({ title, subtitle, eyebrow, actions, className }: PageHeaderProps) {
  return (
    <header className={cn('mb-xl flex flex-col gap-md md:flex-row md:items-end md:justify-between', className)}>
      <div className="max-w-3xl">
        {eyebrow && <p className="label-uppercase mb-xs">{eyebrow}</p>}
        <h1 className="font-sans text-headline-lg font-semibold text-primary dark:text-inverse-primary">{title}</h1>
        {subtitle && <p className="mt-sm text-body-md text-on-surface-variant dark:text-slate-400">{subtitle}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-sm">{actions}</div>}
    </header>
  );
}
