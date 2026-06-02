import { Card } from '../common/Card';
import { ProgressBar } from '../common/ProgressBar';
import type { BillingUsage } from '../../types/billing';
import { formatNumber } from '../../utils/format';

interface BillingUsageCardProps {
  usage: BillingUsage;
}

export function BillingUsageCard({ usage }: BillingUsageCardProps) {
  const percent = (usage.used / usage.limit) * 100;
  return (
    <Card>
      <div className="flex items-start justify-between gap-md">
        <div>
          <p className="label-uppercase">{usage.label}</p>
          <p className="mt-sm text-2xl font-bold text-on-surface dark:text-slate-100">
            {formatNumber(usage.used)} <span className="text-sm font-medium text-on-surface-variant dark:text-slate-400">/ {formatNumber(usage.limit)} {usage.unit}</span>
          </p>
        </div>
        <span className="rounded-full bg-surface-container-high px-sm py-xs text-xs font-bold text-primary dark:bg-slate-800 dark:text-inverse-primary">
          {Math.round(percent)}%
        </span>
      </div>
      <ProgressBar className="mt-md" value={percent} />
    </Card>
  );
}
