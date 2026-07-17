import { ArrowUpRight, Receipt, RefreshCw, ShieldCheck, Sparkles } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { SubscriptionStatusBadge } from '../../components/billing/SubscriptionStatusBadge';
import { RefundRequestModal } from '../../components/billing/RefundRequestModal';
import { Badge, type BadgeTone } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { DataTable, type DataTableColumn } from '../../components/common/DataTable';
import { EmptyState } from '../../components/common/EmptyState';
import { PageHeader } from '../../components/common/PageHeader';
import { ProgressBar } from '../../components/common/ProgressBar';
import { useI18n } from '../../hooks/useI18n';
import { useToast } from '../../hooks/useToast';
import { createVnPayPaymentUrl, getMyPaymentTransactions } from '../../services/paymentTransaction.service';
import {
  cancelCustomerPlan,
  getMyCustomerPlan,
  getMySubscriptionUsage,
  isCustomerPlanMissingError,
  isSubscriptionUnauthorizedError,
  requestSubscriptionRefund,
} from '../../services/subscription.service';
import type { PaymentTransaction } from '../../types/paymentTransaction';
import type { CustomerPlan, SubscriptionUsage } from '../../types/subscription';
import { formatDisplayDate, formatNumber, formatVndCurrency } from '../../utils/format';

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

const getPaymentStatusTone = (status: string): BadgeTone => {
  switch (status.toUpperCase()) {
    case 'SUCCESS':
    case 'PAID':
      return 'green';
    case 'FAILED':
    case 'CANCELLED':
      return 'red';
    case 'PENDING':
      return 'amber';
    default:
      return 'slate';
  }
};

export function BillingPage() {
  const { t, language } = useI18n();
  const toast = useToast();
  const navigate = useNavigate();
  const [customerPlan, setCustomerPlan] = useState<CustomerPlan | null>(null);
  const [isLoadingPlan, setIsLoadingPlan] = useState(true);
  const [paymentTransactions, setPaymentTransactions] = useState<PaymentTransaction[]>([]);
  const [isLoadingTransactions, setIsLoadingTransactions] = useState(true);
  const [hasNoPlan, setHasNoPlan] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [transactionError, setTransactionError] = useState<string | null>(null);
  const [transactionActionMessage, setTransactionActionMessage] = useState<string | null>(null);
  const [payingTransactionId, setPayingTransactionId] = useState<number | null>(null);
  const [refundingTransactionId, setRefundingTransactionId] = useState<number | null>(null);
  const [refundTransaction, setRefundTransaction] = useState<PaymentTransaction | null>(null);
  const [isCancellingPlan, setIsCancellingPlan] = useState(false);
  const [usageRows, setUsageRows] = useState<SubscriptionUsage[]>([]);
  const [isLoadingUsage, setIsLoadingUsage] = useState(true);
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

  const loadPaymentTransactions = useCallback(async () => {
    setIsLoadingTransactions(true);
    setTransactionError(null);

    try {
      const transactions = await getMyPaymentTransactions();
      setPaymentTransactions(transactions);
    } catch (error) {
      console.error('Failed to load payment transactions:', error);
      setPaymentTransactions([]);
      setTransactionError(error instanceof Error ? error.message : t('billing.errors.loadTransactions'));
    } finally {
      setIsLoadingTransactions(false);
    }
  }, [t]);

  const loadUsage = useCallback(async () => {
    setIsLoadingUsage(true);

    try {
      const response = await getMySubscriptionUsage(0, 8);
      setUsageRows(response.data?.items ?? []);
    } catch (error) {
      console.error('Failed to load subscription usage:', error);
      setUsageRows([]);
    } finally {
      setIsLoadingUsage(false);
    }
  }, []);

  useEffect(() => {
    void loadCustomerPlan();
    void loadPaymentTransactions();
    void loadUsage();
  }, [loadCustomerPlan, loadPaymentTransactions, loadUsage]);

  const quotaPercent = useMemo(() => (customerPlan ? getQuotaPercent(customerPlan) : 0), [customerPlan]);

  const handleCreatePaymentUrl = async (transaction: PaymentTransaction) => {
    setPayingTransactionId(transaction.id);
    setTransactionError(null);
    setTransactionActionMessage(null);

    try {
      const response = await createVnPayPaymentUrl(transaction.id);

      if (response.paymentUrl?.trim()) {
        toast.info(t('billing.pay'), t('toast.infoTitle'));
        window.location.assign(response.paymentUrl);
        return;
      }

      setTransactionError(t('billing.paymentUrlMissing'));
      toast.warning(t('billing.paymentUrlMissing'), t('toast.warningTitle'));
    } catch (error) {
      const message = error instanceof Error ? error.message : t('billing.paymentUrlError');
      setTransactionError(message);
      toast.error(message, t('toast.errorTitle'));
    } finally {
      setPayingTransactionId(null);
    }
  };

  const handleCancelPlan = async () => {
    if (!customerPlan) {
      return;
    }

    if (!window.confirm(t('billing.cancelConfirm'))) {
      return;
    }

    setIsCancellingPlan(true);
    setErrorMessage(null);
    setTransactionActionMessage(null);

    try {
      const response = await cancelCustomerPlan(customerPlan.id);
      const nextCustomerPlan = response.data ?? null;
      setCustomerPlan(nextCustomerPlan);
      setHasNoPlan(nextCustomerPlan === null);
      const message = t('billing.cancelSuccess');
      setTransactionActionMessage(message);
      toast.success(message, t('toast.successTitle'));
      await loadPaymentTransactions();
    } catch (error) {
      const message = error instanceof Error ? error.message : t('billing.cancelError');
      setErrorMessage(message);
      toast.error(message, t('toast.errorTitle'));
    } finally {
      setIsCancellingPlan(false);
    }
  };

  const handleRequestRefund = async (amount: number, reason: string) => {
    const transaction = refundTransaction;
    if (!transaction) return;
    setRefundingTransactionId(transaction.id);
    setTransactionError(null);
    setTransactionActionMessage(null);

    try {
      await requestSubscriptionRefund({
        paymentTransactionId: transaction.id,
        customerPlanId: transaction.customerPlanId,
        reason,
        amount,
      });
      const message = t('billing.refundSuccess');
      setTransactionActionMessage(message);
      toast.success(message, t('toast.successTitle'));
      setRefundTransaction(null);
      const refreshResults = await Promise.allSettled([
        loadPaymentTransactions(),
        loadCustomerPlan(),
        loadUsage(),
      ]);
      if (refreshResults.some((result) => result.status === 'rejected')) {
        toast.warning(t('common.partialDataError'), t('toast.warningTitle'));
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : t('billing.refundError');
      setTransactionError(message);
      toast.error(message, t('toast.errorTitle'));
    } finally {
      setRefundingTransactionId(null);
    }
  };

  const columns: DataTableColumn<PaymentTransaction>[] = [
    {
      header: t('billing.invoice'),
      cell: (transaction) => (
        <span className="font-semibold text-primary dark:text-inverse-primary">
          {transaction.transactionCode || `#${transaction.id}`}
        </span>
      ),
    },
    {
      header: t('table.date'),
      cell: (transaction) => formatDisplayDate(transaction.createdAt, '-', locale),
    },
    {
      header: t('billing.amount'),
      cell: (transaction) => formatVndCurrency(transaction.amount, t('billing.free')),
    },
    {
      header: t('billing.paymentMethod'),
      cell: (transaction) => t(`billing.paymentMethod.${transaction.paymentMethod}`),
    },
    {
      header: t('table.status'),
      cell: (transaction) => (
        <Badge tone={getPaymentStatusTone(transaction.paymentStatus)}>
          {transaction.paymentStatus}
        </Badge>
      ),
    },
    {
      header: t('table.actions'),
      cell: (transaction) => (
        <div className="flex flex-wrap gap-xs">
          <Button
            variant="ghost"
            size="sm"
            disabled={
              payingTransactionId === transaction.id ||
              transaction.paymentMethod !== 'VNPAY' ||
              transaction.paymentStatus.toUpperCase() !== 'PENDING'
            }
            onClick={() => void handleCreatePaymentUrl(transaction)}
          >
            {payingTransactionId === transaction.id ? t('billing.creatingPaymentUrl') : t('billing.pay')}
          </Button>
          <Button
            variant="ghost"
            size="sm"
            disabled={
              refundingTransactionId === transaction.id ||
              transaction.paymentStatus.toUpperCase() !== 'SUCCESS'
            }
            onClick={() => setRefundTransaction(transaction)}
          >
            {refundingTransactionId === transaction.id ? t('common.loading') : t('billing.refund')}
          </Button>
        </div>
      ),
    },
  ];

  const usageColumns: DataTableColumn<SubscriptionUsage>[] = [
    { header: t('billing.usage'), cell: (usage) => <span className="font-semibold">{usage.usageType}</span> },
    { header: t('billing.reference'), cell: (usage) => usage.referenceId ?? '-' },
    { header: t('billing.units'), cell: (usage) => formatNumber(usage.consumedUnits) },
    { header: t('table.date'), cell: (usage) => formatDisplayDate(usage.createdAt, '-', locale) },
  ];

  const renderTransactionsCard = () => (
    <Card
      title={t('billing.invoices')}
      subtitle={t('billing.paymentTransactionsSubtitle')}
      actions={<Receipt className="h-5 w-5 text-primary dark:text-inverse-primary" />}
    >
      {transactionActionMessage && (
        <p className="mb-md rounded-lg bg-emerald-50 px-md py-sm text-sm font-semibold text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-200">
          {transactionActionMessage}
        </p>
      )}
      {transactionError && (
        <p className="mb-md rounded-lg bg-error-container px-md py-sm text-sm font-semibold text-risk-high-text dark:bg-red-950/40 dark:text-red-200">
          {transactionError}
        </p>
      )}
      {isLoadingTransactions ? (
        <p className="text-sm text-on-surface-variant dark:text-slate-400">
          {t('billing.loadingPaymentHistory')}
        </p>
      ) : (
        <DataTable
          columns={columns}
          data={paymentTransactions}
          getRowKey={(transaction) => String(transaction.id)}
          emptyMessage={t('billing.noTransactions')}
        />
      )}
    </Card>
  );

  const renderUsageCard = () => (
    <Card className="mt-xl" title={t('billing.subscriptionUsage')} subtitle={t('billing.subscriptionUsageSubtitle')}>
      {isLoadingUsage ? (
        <p className="text-sm text-on-surface-variant dark:text-slate-400">
          {t('common.loading')}
        </p>
      ) : (
        <DataTable
          columns={usageColumns}
          data={usageRows}
          getRowKey={(usage) => String(usage.id)}
          emptyMessage={t('billing.noUsageHistory')}
        />
      )}
    </Card>
  );

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
        <>
          <EmptyState
            title={t('billing.noPlanTitle')}
            description={t('billing.errors.noPlan')}
            actionLabel={t('billing.choosePlan')}
            onAction={() => navigate('/billing/subscribe')}
          />

          <section className="mt-xl">
            {renderTransactionsCard()}
          </section>

          {renderUsageCard()}
        </>
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
            actions={
              <div className="flex flex-wrap items-center justify-end gap-sm">
                <SubscriptionStatusBadge status={customerPlan.status} />
                {!['CANCELLED', 'EXPIRED'].includes(customerPlan.status.toUpperCase()) && (
                  <Button
                    variant="danger"
                    size="sm"
                    onClick={() => void handleCancelPlan()}
                    disabled={isCancellingPlan}
                  >
                    {isCancellingPlan ? t('billing.cancellingPlan') : t('billing.cancelPlan')}
                  </Button>
                )}
              </div>
            }
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

          {renderTransactionsCard()}
        </section>

        {renderUsageCard()}
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
            <Link to="/billing/refunds"><Button variant="secondary">{t('refund.history.title')}</Button></Link>
            <Button
              variant="secondary"
              leftIcon={<RefreshCw className="h-4 w-4" />}
              onClick={() => {
                void loadCustomerPlan();
                void loadPaymentTransactions();
                void loadUsage();
              }}
              disabled={isLoadingPlan || isLoadingTransactions || isLoadingUsage}
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
      <RefundRequestModal transaction={refundTransaction} submitting={refundingTransactionId !== null} onClose={() => setRefundTransaction(null)} onSubmit={handleRequestRefund} />
    </div>
  );
}
