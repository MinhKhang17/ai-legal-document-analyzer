import { type ReactNode } from 'react';
import { TrendingDown, TrendingUp } from 'lucide-react';
import { Card } from './Card';
import { cn } from '../../utils/cn';

interface StatCardProps {
  label: string;
  value: string;
  change?: string;
  trend?: 'up' | 'down' | 'neutral';
  icon?: ReactNode;
  accent?: 'blue' | 'gold' | 'red' | 'green';
}

const accentClasses = {
  blue: 'border-b-primary',
  gold: 'border-b-secondary',
  red: 'border-b-error',
  green: 'border-b-emerald-600',
};

export function StatCard({ label, value, change, trend = 'neutral', icon, accent = 'blue' }: StatCardProps) {
  const TrendIcon = trend === 'down' ? TrendingDown : TrendingUp;
  return (
    <Card className={cn('border-b-4', accentClasses[accent])}>
      <div className="flex items-start justify-between gap-md">
        <div>
          <p className="label-uppercase">{label}</p>
          <p className="mt-md text-3xl font-bold text-on-surface dark:text-slate-100">{value}</p>
        </div>
        {icon && <div className="rounded-lg bg-surface-container-high p-sm text-primary dark:bg-slate-800 dark:text-inverse-primary">{icon}</div>}
      </div>
      {change && (
        <p
          className={cn(
            'mt-md inline-flex items-center gap-xs text-xs font-semibold',
            trend === 'down' ? 'text-emerald-700 dark:text-emerald-300' : trend === 'up' ? 'text-emerald-700 dark:text-emerald-300' : 'text-outline',
          )}
        >
          {trend !== 'neutral' && <TrendIcon className="h-3.5 w-3.5" aria-hidden="true" />}
          {change}
        </p>
      )}
    </Card>
  );
}
