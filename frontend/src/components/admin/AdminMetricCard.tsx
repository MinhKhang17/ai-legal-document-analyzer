import { type ReactNode } from 'react';
import { Card } from '../common/Card';
import { cn } from '../../utils/cn';

interface AdminMetricCardProps {
  label: string;
  value: string;
  detail: string;
  icon: ReactNode;
  tone?: 'blue' | 'gold' | 'red' | 'green';
}

const toneClasses = {
  blue: 'text-primary bg-surface-container-high dark:text-inverse-primary dark:bg-slate-800',
  gold: 'text-secondary bg-secondary-container dark:text-accent-gold dark:bg-amber-950/50',
  red: 'text-error bg-error-container dark:text-red-200 dark:bg-red-950/50',
  green: 'text-emerald-700 bg-emerald-100 dark:text-emerald-200 dark:bg-emerald-950/50',
};

export function AdminMetricCard({ label, value, detail, icon, tone = 'blue' }: AdminMetricCardProps) {
  return (
    <Card>
      <div className="flex items-start gap-md">
        <div className={cn('flex h-11 w-11 shrink-0 items-center justify-center rounded-lg', toneClasses[tone])}>{icon}</div>
        <div>
          <p className="label-uppercase">{label}</p>
          <p className="mt-xs text-2xl font-bold text-on-surface dark:text-slate-100">{value}</p>
          <p className="mt-xs text-sm text-on-surface-variant dark:text-slate-400">{detail}</p>
        </div>
      </div>
    </Card>
  );
}
