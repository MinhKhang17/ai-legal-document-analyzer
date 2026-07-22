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
import { Modal } from '../../components/common/Modal';
import { PageHeader } from '../../components/common/PageHeader';
import { ProgressBar } from '../../components/common/ProgressBar';
import { useI18n } from '../../hooks/useI18n';
import { useToast } from '../../hooks/useToast';
import { createVnPayPaymentUrl, getMyPaymentTransactions } from '../../services/paymentTransaction.service';
import {
  cancelCustomerPlan,
  getMyCustomerPlan,
  getMySubscriptionUsage,
  getSubscriptionUsageSummary,
  getSubscriptionPlans,
  isCustomerPlanMissingError,
  isSubscriptionUnauthorizedError,
  requestSubscriptionRefund,
} from '../../services/subscription.service';
import type { PaymentTransaction } from '../../types/paymentTransaction';
import type { CustomerPlan, SubscriptionPlan, SubscriptionUsage, SubscriptionUsageSummary } from '../../types/subscription';
import { formatDisplayDate } from '../../utils/format';

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

const PAYMENT_STATUSES = ['PENDING', 'SUCCESS', 'FAILED', 'CANCELLED', 'REFUNDED'] as const;
const USAGE_TYPES = ['AI_QUERY', 'CONTRACT_GENERATION', 'TICKET_CREATE', 'KNOWLEDGE_SEARCH', 'CHAT_MESSAGE'] as const;
const PLAN_TYPES = ['FREE', 'STANDARD', 'PREMIUM'] as const;
const CUSTOMER_PLAN_STATUS_KEYS: Record<string, string> = {
  PENDING: 'billing.status.pending',
  ACTIVE: 'billing.status.active',
  EXPIRED: 'billing.status.expired',
  CANCELLED: 'billing.status.cancelled',
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
  const [isCancelPlanModalOpen, setIsCancelPlanModalOpen] = useState(false);
  const [isCancellingPlan, setIsCancellingPlan] = useState(false);
  const [usageRows, setUsageRows] = useState<SubscriptionUsage[]>([]);
  const [isLoadingUsage, setIsLoadingUsage] = useState(true);
  const [usageSummary, setUsageSummary] = useState<SubscriptionUsageSummary | null>(null);
  const [plans, setPlans] = useState<SubscriptionPlan[]>([]);
  const locale = language === 'vi' ? 'vi-VN' : 'en-US';
  const numberFormatter = useMemo(
    () => new Intl.NumberFormat(locale, { maximumFractionDigits: 1 }),
    [locale],
  );
  const currencyFormatter = useMemo(
    () => new Intl.NumberFormat(locale, { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }),
    [locale],
  );
  const formatPrice = (value: number) => value <= 0 ? t('billing.free') : currencyFormatter.format(value);
  const getPaymentStatusLabel = (status: string) => {
    const normalized = status.toUpperCase();
    return PAYMENT_STATUSES.includes(normalized as (typeof PAYMENT_STATUSES)[number])
      ? t(`billing.paymentStatus.${normalized}`)
      : t('billing.paymentStatus.UNKNOWN');
  };
  const getUsageTypeLabel = (usageType: string) => {
    const normalized = usageType.toUpperCase();
    return USAGE_TYPES.includes(normalized as (typeof USAGE_TYPES)[number])
      ? t(`billing.usageType.${normalized}`)
      : t('billing.usageType.UNKNOWN');
  };
  const getPlanTypeLabel = (planType: string) => {
    const normalized = planType.toUpperCase();
    return PLAN_TYPES.includes(normalized as (typeof PLAN_TYPES)[number])
      ? t(`billing.planType.${normalized}`)
      : t('billing.planType.UNKNOWN');
  };

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
      setTransactionError(t('billing.errors.loadTransactions'));
    } finally {
      setIsLoadingTransactions(false);
    }
  }, [t]);

  const loadUsage = useCallback(async () => {
    setIsLoadingUsage(true);

    try {
      const [history, summary, availablePlans] = await Promise.all([
        getMySubscriptionUsage(0, 8),
        getSubscriptionUsageSummary(),
        getSubscriptionPlans(),
      ]);
      setUsageRows(history.data?.items ?? []);
      setUsageSummary(summary.data ?? null);
      setPlans(availablePlans.data ?? []);
    } catch (error) {
      console.error('Failed to load subscription usage:', error);
      setUsageRows([]);
      setUsageSummary(null);
    } finally {
      setIsLoadingUsage(false);
    }
  }, []);

  useEffect(() => {
    void loadCustomerPlan();
    void loadPaymentTransactions();
    void loadUsage();
  }, [loadCustomerPlan, loadPaymentTransactions, loadUsage]);

  const usagePercent = (used: number, limit: number) => limit > 0 ? Math.min(100, (used / limit) * 100) : 0;
  const bytesToMb = (bytes: number) => bytes / 1024 / 1024;

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
      console.error('Failed to create VNPAY payment URL:', error);
      const message = t('billing.paymentUrlError');
      setTransactionError(message);
      toast.error(message, t('toast.errorTitle'));
    } finally {
      setPayingTransactionId(null);
    }
  };

  const handleCancelPlan = async () => {
    if (!customerPlan || isCancellingPlan) {
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
      setIsCancelPlanModalOpen(false);
      const message = t('billing.cancelSuccess');
      setTransactionActionMessage(message);
      toast.success(message, t('toast.successTitle'));
      await loadPaymentTransactions();
    } catch (error) {
      console.error('Failed to cancel customer plan:', error);
      const message = t('billing.cancelError');
      setErrorMessage(message);
      toast.error(message, t('toast.errorTitle'));
    } finally {
      setIsCancellingPlan(false);
    }
  };

  const handleRequestRefund = async (form: { amount: number; reason: string; bankName: string; accountNumber: string; accountHolderName: string }) => {
    const transaction = refundTransaction;
    if (!transaction) return;
    setRefundingTransactionId(transaction.id);
    setTransactionError(null);
    setTransactionActionMessage(null);

    try {
      await requestSubscriptionRefund({
        paymentTransactionId: transaction.id,
        customerPlanId: transaction.customerPlanId,
        reason: form.reason,
        refundReason: form.reason,
        amount: form.amount,
        bankName: form.bankName,
        accountNumber: form.accountNumber,
        accountHolderName: form.accountHolderName,
        transactionId: transaction.transactionCode || String(transaction.id),
        invoiceId: transaction.transactionCode || String(transaction.id),
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
      console.error('Failed to request subscription refund:', error);
      const message = t('billing.refundError');
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
      cell: (transaction) => formatPrice(transaction.amount),
    },
    {
      header: t('billing.paymentMethod'),
      cell: (transaction) => t(`billing.paymentMethod.${transaction.paymentMethod}`),
    },
    {
      header: t('table.status'),
      cell: (transaction) => (
        <Badge tone={getPaymentStatusTone(transaction.paymentStatus)}>
          {getPaymentStatusLabel(transaction.paymentStatus)}
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
    { header: t('billing.usage'), cell: (usage) => <span className="font-semibold">{getUsageTypeLabel(usage.usageType)}</span> },
    { header: t('billing.reference'), cell: (usage) => usage.referenceId ?? '-' },
    { header: t('billing.units'), cell: (usage) => numberFormatter.format(usage.consumedUnits) },
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
        <section className="grid gap-gutter md:grid-cols-2">
          <Card>
            <div className="flex items-start justify-between gap-md">
              <div>
                <p className="label-uppercase">{t('billing.status')}</p>
                <div className="mt-sm">
                  <SubscriptionStatusBadge status={customerPlan.status} />
                </div>
                <p className="mt-xs text-sm text-on-surface-variant dark:text-slate-400">
                  {t(CUSTOMER_PLAN_STATUS_KEYS[customerPlan.status.toUpperCase()] ?? 'billing.status.unknown')}
                </p>
              </div>
              <Sparkles className="h-5 w-5 text-primary dark:text-inverse-primary" aria-hidden="true" />
            </div>
          </Card>
        </section>

        {usageSummary && (
          <section className="mt-xl">
            <h2 className="mb-md text-title-lg font-semibold">{t('billing.usageSummary.title')}</h2>
            <div className="grid gap-gutter md:grid-cols-3">
              {[
                [t('billing.limit.aiTokens'), usageSummary.aiTokensUsed, usageSummary.aiTokensLimit],
                [t('billing.limit.storageMb'), bytesToMb(usageSummary.storageUsedBytes), bytesToMb(usageSummary.storageLimitBytes)],
                [t('billing.limit.expertTickets'), usageSummary.expertTicketsUsed, usageSummary.expertTicketsLimit],
              ].map(([label, used, limit]) => (
                <BillingMetricCard key={String(label)} label={String(label)} value={`${numberFormatter.format(Number(used))} / ${numberFormatter.format(Number(limit))}`} progressValue={usagePercent(Number(used), Number(limit))} />
              ))}
            </div>
          </section>
        )}

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
                    onClick={() => setIsCancelPlanModalOpen(true)}
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
                  {formatPrice(customerPlan.subscriptionPlan.price)}
                </p>
                <p className="text-sm text-surface-container">
                  {customerPlan.subscriptionPlan.durationDays} {t('billing.days')} · {getPlanTypeLabel(customerPlan.subscriptionPlan.planType)}
                </p>
              </div>
              <div className="flex flex-wrap gap-xs">
                <Badge tone="gold"><ShieldCheck className="h-3 w-3" /> {t('billing.planType')}: {getPlanTypeLabel(customerPlan.subscriptionPlan.planType)}</Badge>
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
              </dl>
            </div>
          </Card>

          {renderTransactionsCard()}
        </section>

        {renderUsageCard()}

        {plans.length > 0 && (
          <Card className="mt-xl" title={t('billing.comparison.title')} subtitle={t('billing.comparison.subtitle')}>
            <div className="grid gap-md md:grid-cols-3">
              {plans.map((plan) => {
                const current = plan.id === customerPlan.subscriptionPlan.id;
                return <div key={plan.id} className={`rounded-xl border p-md ${current ? 'border-primary bg-primary/5' : 'border-legal-border dark:border-slate-700'}`}>
                  <div className="flex items-center justify-between gap-sm"><h3 className="font-bold">{plan.displayName ?? plan.planName}</h3>{current && <Badge tone="green">{t('billing.currentPlan')}</Badge>}</div>
                  <p className="mt-sm text-2xl font-bold text-primary">{formatPrice(plan.priceVnd ?? plan.price)}</p>
                  <ul className="mt-md space-y-xs text-sm text-on-surface-variant dark:text-slate-300">
                    <li>{t('billing.comparison.aiTokenCount', { count: numberFormatter.format(plan.aiTokenLimit ?? 0) })}</li>
                    <li>{t('billing.limit.storageMb')}: {numberFormatter.format(plan.storageLimitMb ?? 0)} MB</li>
                    <li>{t('billing.limit.contactExpert')}: {plan.allowContactExpertTicket ? t('billing.comparison.expertTicketsPerMonth', { count: numberFormatter.format(plan.expertTicketLimit ?? 0) }) : t('billing.comparison.premiumOnly')}</li>
                  </ul>
                  <Button className="mt-md w-full" disabled={current || !plan.active} onClick={() => navigate('/billing/subscribe')}>{current ? t('billing.comparison.inUse') : t('billing.choosePlan')}</Button>
                </div>;
              })}
            </div>
          </Card>
        )}
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
      <Modal
        open={isCancelPlanModalOpen}
        size="sm"
        title={t('billing.cancelPlanModalTitle')}
        onClose={() => !isCancellingPlan && setIsCancelPlanModalOpen(false)}
        footer={
          <div className="flex justify-end gap-sm">
            <Button variant="secondary" disabled={isCancellingPlan} onClick={() => setIsCancelPlanModalOpen(false)}>
              {t('actions.cancel')}
            </Button>
            <Button variant="danger" disabled={isCancellingPlan} onClick={() => void handleCancelPlan()}>
              {isCancellingPlan ? t('billing.cancellingPlan') : t('billing.cancelPlan')}
            </Button>
          </div>
        }
      >
        <p className="text-sm text-on-surface-variant dark:text-slate-400">{t('billing.cancelConfirm')}</p>
      </Modal>
      <RefundRequestModal transaction={refundTransaction} submitting={refundingTransactionId !== null} onClose={() => setRefundTransaction(null)} onSubmit={handleRequestRefund} />
    </div>
  );
}
