import { Activity, AlertTriangle, Database, RefreshCw } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { EmptyState } from '../../components/common/EmptyState';
import { PageHeader } from '../../components/common/PageHeader';
import { StatusBadge } from '../../components/common/StatusBadge';
import { getAiHealthStatus, getAiRootStatus, getAiTechnologies } from '../../services/aiService.service';
import { useI18n } from '../../hooks/useI18n';
import type { AiHealthStatus, AiRootStatus, AiTechnologiesResponse } from '../../types/ai';
import { localeForLanguage } from '../../utils/format';

const stringifyTechnologyValue = (value: unknown): string => {
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  if (Array.isArray(value)) return value.map((item) => stringifyTechnologyValue(item)).join(', ');
  if (value && typeof value === 'object') return JSON.stringify(value);
  return '-';
};

export function SystemHealthPage() {
  const { t, language } = useI18n();
  const locale = localeForLanguage(language);
  const numberFormatter = new Intl.NumberFormat(locale);
  const [aiRootStatus, setAiRootStatus] = useState<AiRootStatus | null>(null);
  const [aiHealthStatus, setAiHealthStatus] = useState<AiHealthStatus | null>(null);
  const [technologies, setTechnologies] = useState<AiTechnologiesResponse | null>(null);
  const [aiLoading, setAiLoading] = useState(true);
  const [aiError, setAiError] = useState('');

  const loadAiServiceStatus = useCallback(async () => {
    setAiLoading(true);
    setAiError('');

    const [rootResult, healthResult, technologiesResult] = await Promise.allSettled([
      getAiRootStatus(),
      getAiHealthStatus(),
      getAiTechnologies(),
    ]);

    setAiRootStatus(rootResult.status === 'fulfilled' ? rootResult.value : null);
    setAiHealthStatus(healthResult.status === 'fulfilled' ? healthResult.value : null);
    setTechnologies(technologiesResult.status === 'fulfilled' ? technologiesResult.value : null);

    const hasError = [rootResult, healthResult, technologiesResult]
      .some((result) => result.status === 'rejected');

    setAiError(hasError ? t('system.aiServiceUnavailableMessage') : '');
    setAiLoading(false);
  }, [t]);

  useEffect(() => {
    void loadAiServiceStatus();
  }, [loadAiServiceStatus]);

  const technologyLabels = useMemo(() => {
    if (!technologies) return [];
    if (Array.isArray(technologies)) {
      return technologies.map((technology, index) => {
        if (typeof technology === 'string') {
          return technology;
        }
        return stringifyTechnologyValue(technology.name ?? technology.status ?? technology.version ?? t('system.technologyFallback', { index: numberFormatter.format(index + 1) }));
      });
    }
    return Object.entries(technologies).map(([key, value]) => `${key}: ${stringifyTechnologyValue(value)}`);
  }, [numberFormatter, t, technologies]);

  const rootStatusLabel = aiRootStatus?.status === 'running'
    ? t('system.status.running')
    : (aiLoading ? t('common.loading') : t('status.offline'));

  return (
    <div>
      <PageHeader
        title={t('system.title')}
        subtitle={t('system.subtitle')}
        actions={
          <Button
            variant="secondary"
            leftIcon={<RefreshCw className="h-4 w-4" />}
            onClick={() => void loadAiServiceStatus()}
            disabled={aiLoading}
          >
            {t('billing.refresh')}
          </Button>
        }
      />

      <section className="grid gap-gutter md:grid-cols-3">
        <Card
          title={t('system.aiRoot')}
          actions={<Badge tone={aiRootStatus?.status === 'running' ? 'green' : 'amber'}>{rootStatusLabel}</Badge>}
        >
          <p className="text-sm font-semibold">{aiRootStatus?.service ?? t('system.noAiRootData')}</p>
          <p className="mt-xs text-sm text-on-surface-variant dark:text-slate-400">
            {t('system.version')}: {aiRootStatus?.version ?? '-'}
          </p>
        </Card>

        <Card
          title={t('system.aiHealth')}
          actions={<StatusBadge status={aiLoading ? 'processing' : aiHealthStatus?.status === 'healthy' ? 'ready' : 'offline'} />}
        >
          <p className="text-sm text-on-surface-variant dark:text-slate-400">
            {t('system.aiHealthDescription')}
          </p>
          <p className="mt-xs font-semibold">{aiHealthStatus?.service ?? '-'}</p>
        </Card>

        <Card title={t('system.neo4jTechnologies')} actions={<Badge tone="blue">{numberFormatter.format(technologyLabels.length)}</Badge>}>
          {technologyLabels.length > 0 ? (
            <div className="flex flex-wrap gap-xs">
              {technologyLabels.slice(0, 8).map((technology) => (
                <Badge key={technology} tone="slate">{technology}</Badge>
              ))}
            </div>
          ) : (
            <p className="text-sm text-on-surface-variant dark:text-slate-400">
              {aiLoading ? t('system.loadingTechnologies') : t('system.noTechnologyNodes')}
            </p>
          )}
        </Card>
      </section>

      {aiError && (
        <div className="mt-lg rounded-xl border border-amber-300 bg-amber-50 p-md text-sm text-amber-900 dark:border-amber-700 dark:bg-amber-950/30 dark:text-amber-100">
          <div className="flex flex-col gap-md md:flex-row md:items-start md:justify-between">
            <div className="flex gap-sm">
              <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0" aria-hidden="true" />
              <div>
                <p className="font-semibold">{t('system.aiServiceUnavailableTitle')}</p>
                <p className="mt-xs leading-6">{aiError}</p>
                <p className="mt-xs text-xs opacity-80">{t('system.aiServiceUnavailableHint')}</p>
              </div>
            </div>
            <Button variant="secondary" size="sm" onClick={() => void loadAiServiceStatus()} disabled={aiLoading}>
              {t('billing.refresh')}
            </Button>
          </div>
        </div>
      )}

      <section className="mt-xl grid gap-gutter xl:grid-cols-2">
        <Card title={t('system.backendServices')}>
          <EmptyState
            icon={<Database className="h-6 w-6" aria-hidden="true" />}
            title={t('system.noBackendServicesTitle')}
            description={t('system.noBackendServicesDescription')}
          />
        </Card>

        <Card title={t('system.liveProcessingQueue')}>
          <EmptyState
            icon={<Activity className="h-6 w-6" aria-hidden="true" />}
            title={t('system.noJobsTitle')}
            description={t('system.noJobsDescription')}
          />
        </Card>
      </section>

      <Card className="mt-xl" title={t('system.operationalNote')} actions={<AlertTriangle className="h-5 w-5 text-secondary" />}>
        <p className="text-sm text-on-surface-variant dark:text-slate-400">{t('common.realDataOnly')}</p>
      </Card>
    </div>
  );
}
