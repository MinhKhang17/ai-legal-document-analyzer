import { type HTMLAttributes, type ReactNode } from 'react';
import { cn } from '../../utils/cn';

interface CardProps extends Omit<HTMLAttributes<HTMLDivElement>, 'title'> {
  title?: ReactNode;
  subtitle?: ReactNode;
  actions?: ReactNode;
  tone?: 'default' | 'muted' | 'ai' | 'dark';
  padded?: boolean;
}

const toneClasses = {
  default: 'paper-card',
  muted: 'paper-card-muted',
  ai: 'ai-insight-card',
  dark: 'rounded-xl border border-slate-700 bg-slate-900 text-slate-100 shadow-none',
};

export function Card({
  className,
  children,
  title,
  subtitle,
  actions,
  tone = 'default',
  padded = true,
  ...props
}: CardProps) {
  return (
    <section className={cn(toneClasses[tone], padded && tone !== 'ai' && 'p-lg', className)} {...props}>
      {(title || subtitle || actions) && (
        <div className="mb-md flex items-start justify-between gap-md">
          <div>
            {title && <h2 className="font-sans text-title-lg font-semibold text-on-surface dark:text-slate-100">{title}</h2>}
            {subtitle && <p className="mt-xs text-sm text-on-surface-variant dark:text-slate-400">{subtitle}</p>}
          </div>
          {actions && <div className="shrink-0">{actions}</div>}
        </div>
      )}
      {children}
    </section>
  );
}
