import { ArrowUpRight, Download, Receipt, RefreshCw, ShieldCheck, Sparkles } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { SubscriptionStatusBadge } from '../../components/billing/SubscriptionStatusBadge';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { DataTable, type DataTableColumn } from '../../components/common/DataTable';
import { EmptyState } from '../../components/common/EmptyState';
import { PageHeader } from '../../components/common/PageHeader';
import { ProgressBar } from '../../components/common/ProgressBar';
import { StatusBadge } from '../../components/common/StatusBadge';
import { invoices } from '../../api/mockData';
import { useI18n } from '../../hooks/useI18n';
import {
  getMyCustomerPlan,
  isCustomerPlanMissingError,
  isSubscriptionUnauthorizedError,
} from '../../services/subscription.service';
import type { Invoice } from '../../types/billing';
import type { CustomerPlan } from '../../types/subscription';
import { formatCurrency, formatDisplayDate, formatNumber, formatVndCurrency } from '../../utils/format';

interface BillingMetricCardProps {
  label: string;
  value: string;
  detail?: string;
  progressValue?: number;
}

function BillingMetricCard({ label, value, detail, progressValue }: BillingMetricCardProps) {
  return (
    <Card>
      <div className="flex items-start justify-between gap-md">
        <div>
          <p className="label-uppercase">{label}</p>
          <p className="mt-sm text-2xl font-bold text-on-surface dark:text-slate-100">{value}</p>
          {detail && <p className="mt-xs text-sm text-on-surface-variant dark:text-slate-400">{detail}</p>}
        </div>
        {typeof progressValue === 'number' && (
          <span className="rounded-full bg-surface-container-high px-sm py-xs text-xs font-bold text-primary dark:bg-slate-800 dark:text-inverse-primary">
            {Math.round(progressValue)}%
          </span>
        )}
      </div>
      {typeof progressValue === 'number' && <ProgressBar className="mt-md" value={progressValue} />}
    </Card>
  );
}

const getQuotaPercent = (customerPlan: CustomerPlan) => {
  const limit = customerPlan.subscriptionPlan.maxQuota;

  if (limit <= 0) {
    return 0;
  }

  return Math.max(0, Math.min(100, Math.round((customerPlan.usedQuota / limit) * 100)));
};

export function BillingPage() {
  const { t, language } = useI18n();
  const navigate = useNavigate();
  const [customerPlan, setCustomerPlan] = useState<CustomerPlan | null>(null);
  const [isLoadingPlan, setIsLoadingPlan] = useState(true);
  const [hasNoPlan, setHasNoPlan] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const locale = language === 'vi' ? 'vi-VN' : 'en-US';

  const loadCustomerPlan = useCallback(async () => {
    setIsLoadingPlan(true);
    setHasNoPlan(false);
    setErrorMessage(null);

    try {
      const response = await getMyCustomerPlan();
      const nextCustomerPlan = response.data ?? null;

      setCustomerPlan(nextCustomerPlan);
      setHasNoPlan(nextCustomerPlan === null);
    } catch (error) {
      console.error('Failed to load current customer plan:', error);
      setCustomerPlan(null);

      if (isCustomerPlanMissingError(error)) {
        setHasNoPlan(true);
        return;
      }

      setErrorMessage(
        isSubscriptionUnauthorizedError(error)
          ? t('billing.errors.unauthorized')
          : t('billing.errors.loadCurrentPlan'),
      );
    } finally {
      setIsLoadingPlan(false);
    }
  }, [t]);

  useEffect(() => {
    void loadCustomerPlan();
  }, [loadCustomerPlan]);

  const quotaPercent = useMemo(() => (customerPlan ? getQuotaPercent(customerPlan) : 0), [customerPlan]);

  const columns: DataTableColumn<Invoice>[] = [
    { header: t('billing.invoice'), cell: (invoice) => <span className="font-semibold text-primary dark:text-inverse-primary">{invoice.id}</span> },
    { header: t('table.date'), cell: (invoice) => invoice.date },
    { header: t('billing.amount'), cell: (invoice) => formatCurrency(invoice.amount) },
    { header: t('table.status'), cell: (invoice) => <StatusBadge status={invoice.status} /> },
    {
      header: t('table.actions'),
      cell: () => (
        <Button variant="ghost" size="icon" aria-label={t('billing.downloadInvoice')}>
          <Download className="h-4 w-4" />
        </Button>
      ),
    },
  ];

  const renderPlanContent = () => {
    if (isLoadingPlan) {
      return (
        <section className="grid gap-gutter md:grid-cols-2 xl:grid-cols-4">
          {Array.from({ length: 4 }).map((_, index) => (
            <Card key={index}>
              <div className="h-4 w-1/3 animate-pulse rounded bg-surface-container-high dark:bg-slate-800" />
              <div className="mt-md h-8 w-2/3 animate-pulse rounded bg-surface-container-high dark:bg-slate-800" />
              <div className="mt-md h-2 w-full animate-pulse rounded bg-surface-container-high dark:bg-slate-800" />
            </Card>
          ))}
        </section>
      );
    }

    if (errorMessage) {
      return (
        <Card className="border-error/30 bg-error-container dark:border-red-900 dark:bg-red-950/40">
          <div className="flex flex-col gap-md md:flex-row md:items-center md:justify-between">
            <div>
              <h2 className="text-title-lg font-semibold text-risk-high-text dark:text-red-200">
                {t('billing.errors.loadCurrentPlan')}
              </h2>
              <p className="mt-sm text-sm text-risk-high-text dark:text-red-200">{errorMessage}</p>
            </div>
            <Button variant="secondary" leftIcon={<RefreshCw className="h-4 w-4" />} onClick={() => void loadCustomerPlan()}>
              {t('actions.retry')}
            </Button>
          </div>
        </Card>
      );
    }

    if (hasNoPlan || !customerPlan) {
      return (
        <EmptyState
          title={t('billing.noPlanTitle')}
          description={t('billing.errors.noPlan')}
          actionLabel={t('billing.choosePlan')}
          onAction={() => navigate('/billing/subscribe')}
        />
      );
    }

    return (
      <>
        <section className="grid gap-gutter md:grid-cols-2 xl:grid-cols-4">
          <BillingMetricCard
            label={t('billing.usedQuota')}
            value={formatNumber(customerPlan.usedQuota)}
            detail={`${t('billing.maxQuota')}: ${formatNumber(customerPlan.subscriptionPlan.maxQuota)} ${t('billing.analyses')}`}
            progressValue={quotaPercent}
          />
          <BillingMetricCard
            label={t('billing.remainingQuota')}
            value={formatNumber(customerPlan.remainingQuota)}
            detail={`${t('billing.maxQuota')}: ${formatNumber(customerPlan.subscriptionPlan.maxQuota)} ${t('billing.analyses')}`}
          />
          <BillingMetricCard
            label={t('billing.maxQuota')}
            value={formatNumber(customerPlan.subscriptionPlan.maxQuota)}
            detail={customerPlan.subscriptionPlan.planType}
          />
          <Card>
            <div className="flex items-start justify-between gap-md">
              <div>
                <p className="label-uppercase">{t('billing.status')}</p>
                <div className="mt-sm">
                  <SubscriptionStatusBadge status={customerPlan.status} />
                </div>
                <p className="mt-xs text-sm text-on-surface-variant dark:text-slate-400">
                  {customerPlan.status === 'PENDING' ? t('billing.status.pending') : customerPlan.status}
                </p>
              </div>
              <Sparkles className="h-5 w-5 text-primary dark:text-inverse-primary" aria-hidden="true" />
            </div>
          </Card>
        </section>

        <section className="mt-xl grid gap-gutter xl:grid-cols-[0.9fr_1.1fr]">
          <Card
            className="bg-primary text-white dark:bg-slate-900"
            title={<span className="text-white dark:text-slate-100">{customerPlan.subscriptionPlan.planName}</span>}
            actions={<SubscriptionStatusBadge status={customerPlan.status} />}
          >
            <div className="space-y-md">
              <p className="text-sm text-surface-container">
                {customerPlan.subscriptionPlan.description || t('billing.subscribe.noDescription')}
              </p>
              <div className="rounded-xl bg-white/10 p-md">
                <p className="text-4xl font-bold">
                  {formatVndCurrency(customerPlan.subscriptionPlan.price, t('billing.free'))}
                </p>
                <p className="text-sm text-surface-container">
                  {customerPlan.subscriptionPlan.durationDays} {t('billing.days')} · {customerPlan.subscriptionPlan.planType}
                </p>
              </div>
              <div className="flex flex-wrap gap-xs">
                <Badge tone="gold"><ShieldCheck className="h-3 w-3" /> {t('billing.planType')}: {customerPlan.subscriptionPlan.planType}</Badge>
                <Badge tone="gold"><Sparkles className="h-3 w-3" /> {t('billing.maxQuota')}: {formatNumber(customerPlan.subscriptionPlan.maxQuota)}</Badge>
              </div>
              <dl className="grid gap-sm rounded-xl bg-white/10 p-md text-sm md:grid-cols-2">
                <div>
                  <dt className="text-surface-container">{t('billing.startDate')}</dt>
                  <dd className="font-semibold">
                    {formatDisplayDate(customerPlan.startDate, t('billing.notActivated'), locale)}
                  </dd>
                </div>
                <div>
                  <dt className="text-surface-container">{t('billing.endDate')}</dt>
                  <dd className="font-semibold">
                    {formatDisplayDate(customerPlan.endDate, t('billing.noEndDate'), locale)}
                  </dd>
                </div>
                <div>
                  <dt className="text-surface-container">{t('billing.autoRenew')}</dt>
                  <dd className="font-semibold">{customerPlan.autoRenew ? t('billing.yes') : t('billing.no')}</dd>
                </div>
                <div>
                  <dt className="text-surface-container">{t('billing.quotaUsage')}</dt>
                  <dd className="font-semibold">
                    {formatNumber(customerPlan.usedQuota)} / {formatNumber(customerPlan.subscriptionPlan.maxQuota)}
                  </dd>
                </div>
              </dl>
            </div>
          </Card>

          <Card
            title={t('billing.invoices')}
            subtitle={t('billing.mockInvoicesNote')}
            actions={<Receipt className="h-5 w-5 text-primary dark:text-inverse-primary" />}
          >
            <DataTable columns={columns} data={invoices} getRowKey={(invoice) => invoice.id} />
          </Card>
        </section>
      </>
    );
  };

  return (
    <div>
      <PageHeader
        title={t('billing.title')}
        subtitle={t('billing.subtitle')}
        actions={
          <>
            <Button
              variant="secondary"
              leftIcon={<RefreshCw className="h-4 w-4" />}
              onClick={() => void loadCustomerPlan()}
              disabled={isLoadingPlan}
            >
              {t('billing.refresh')}
            </Button>
            <Button
              leftIcon={<ArrowUpRight className="h-4 w-4" />}
              onClick={() => navigate('/billing/subscribe')}
            >
              {t('billing.subscribeOrUpgrade')}
            </Button>
          </>
        }
      />

      {renderPlanContent()}
    </div>
  );
}
