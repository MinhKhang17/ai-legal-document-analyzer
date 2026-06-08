import { Badge } from './Badge';
import { useI18n } from '../../hooks/useI18n';
import type { RiskLevel } from '../../types/risk';

interface RiskBadgeProps {
  level: RiskLevel;
  count?: number;
  className?: string;
}

const toneByLevel: Record<RiskLevel, 'red' | 'amber' | 'blue' | 'slate'> = {
  critical: 'red',
  high: 'red',
  medium: 'amber',
  low: 'blue',
  none: 'slate',
};

export function RiskBadge({ level, count, className }: RiskBadgeProps) {
  const { t } = useI18n();
  return (
    <Badge tone={toneByLevel[level]} className={className}>
      {t(`risk.${level}`)}{typeof count === 'number' ? ` (${count})` : ''}
    </Badge>
  );
}
