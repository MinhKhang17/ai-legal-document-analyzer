import { ArrowLeft, CheckCircle2, CreditCard, RefreshCw } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { SubscriptionStatusBadge } from '../../components/billing/SubscriptionStatusBadge';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { EmptyState } from '../../components/common/EmptyState';
import { Modal } from '../../components/common/Modal';
import { PageHeader } from '../../components/common/PageHeader';
import { useI18n } from '../../hooks/useI18n';
import { useToast } from '../../hooks/useToast';
import { createVnPayPaymentUrl } from '../../services/paymentTransaction.service';
import {
  getMyCustomerPlan,
  getSubscriptionPlans,
  isSubscriptionUnauthorizedError,
  subscribeCustomerPlan,
} from '../../services/subscription.service';
import type { CustomerPlan, PaymentMethod, SubscriptionPlan } from '../../types/subscription';
import { cn } from '../../utils/cn';

const PAYMENT_METHODS: PaymentMethod[] = [
  'VNPAY'
];

const planRank = (plan?: SubscriptionPlan | null) => {
  const name = (plan?.name ?? plan?.planType ?? '').toUpperCase();
  return name === 'PREMIUM' ? 2 : name === 'STANDARD' ? 1 : 0;
};

const getSubscriptionErrorMessage = (
  error: unknown,
  fallback: string,
  unauthorizedMessage: string,
) => {
  if (isSubscriptionUnauthorizedError(error)) {
    return unauthorizedMessage;
  }

  return fallback;
};

const PLAN_TYPES = ['FREE', 'STANDARD', 'PREMIUM'] as const;

export function SubscribePlanPage() {
  const { t, language } = useI18n();
  const toast = useToast();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const planRequired = searchParams.get('reason') === 'plan-required';
  const [plans, setPlans] = useState<SubscriptionPlan[]>([]);
  const [selectedPlanId, setSelectedPlanId] = useState<number | null>(null);
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('VNPAY');
  const [isLoadingPlans, setIsLoadingPlans] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [subscribedPlan, setSubscribedPlan] = useState<CustomerPlan | null>(null);
  const [refreshedCurrentPlan, setRefreshedCurrentPlan] = useState<CustomerPlan | null>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
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
  const getPlanTypeLabel = (planType: string) => {
    const normalized = planType.toUpperCase();
    return PLAN_TYPES.includes(normalized as (typeof PLAN_TYPES)[number])
      ? t(`billing.planType.${normalized}`)
      : t('billing.planType.UNKNOWN');
  };

  const selectedPlan = useMemo(
    () => plans.find((plan) => plan.id === selectedPlanId) ?? null,
    [plans, selectedPlanId],
  );

  const loadPlans = useCallback(async () => {
    setIsLoadingPlans(true);
    setErrorMessage(null);

    try {
      const response = await getSubscriptionPlans();
      setPlans(response.data ?? []);
    } catch (error) {
      console.error('Failed to load subscription plans:', error);
      setPlans([]);
      setErrorMessage(
        getSubscriptionErrorMessage(
          error,
          t('billing.subscribe.errors.loadPlans'),
          t('billing.errors.unauthorized'),
        ),
      );
    } finally {
      setIsLoadingPlans(false);
    }
  }, [t]);

  useEffect(() => {
    void loadPlans();
    void getMyCustomerPlan().then((response) => setRefreshedCurrentPlan(response.data ?? null)).catch(() => setRefreshedCurrentPlan(null));
  }, [loadPlans]);

  const openConfirmDialog = () => {
    setErrorMessage(null);
    setSuccessMessage(null);

    if (!selectedPlan) {
      setErrorMessage(t('billing.subscribe.errors.noPlanSelected'));
      toast.warning(t('billing.subscribe.errors.noPlanSelected'), t('toast.warningTitle'));
      return;
    }

    if (!selectedPlan.active) {
      setErrorMessage(t('billing.subscribe.errors.inactivePlan'));
      toast.warning(t('billing.subscribe.errors.inactivePlan'), t('toast.warningTitle'));
      return;
    }

    setConfirmOpen(true);
  };

  const handleSubscribe = async () => {
    if (!selectedPlan) {
      setErrorMessage(t('billing.subscribe.errors.noPlanSelected'));
      toast.warning(t('billing.subscribe.errors.noPlanSelected'), t('toast.warningTitle'));
      return;
    }

    setIsSubmitting(true);
    setErrorMessage(null);
    setSuccessMessage(null);

    try {
      const response = await subscribeCustomerPlan({
        subscriptionPlanId: selectedPlan.id,
        paymentMethod,
      });
      const nextCustomerPlan = response.data ?? null;

      setSubscribedPlan(nextCustomerPlan);
      const successMessage = t('billing.subscribe.successMessage');
      setSuccessMessage(successMessage);
      toast.success(successMessage, t('toast.successTitle'));
      setConfirmOpen(false);

      if (paymentMethod === 'VNPAY' && selectedPlan.price > 0 && nextCustomerPlan?.status !== 'ACTIVE') {
        if (nextCustomerPlan?.latestTransactionId !== null && typeof nextCustomerPlan?.latestTransactionId !== 'undefined') {
          try {
            const paymentUrlResponse = await createVnPayPaymentUrl(nextCustomerPlan.latestTransactionId);

            if (paymentUrlResponse.paymentUrl?.trim()) {
              toast.info(t('billing.pay'), t('toast.infoTitle'));
              window.location.assign(paymentUrlResponse.paymentUrl);
              return;
            }

            setErrorMessage(t('billing.subscribe.vnPayMissingUrl'));
            toast.warning(t('billing.subscribe.vnPayMissingUrl'), t('toast.warningTitle'));
          } catch (paymentUrlError) {
            console.error('Subscription succeeded but VNPAY URL creation failed:', paymentUrlError);
            const message = t('billing.subscribe.vnPayUrlError');
            setErrorMessage(message);
            toast.error(message, t('toast.errorTitle'));
          }
        } else {
          setErrorMessage(t('billing.subscribe.vnPayMissingTransaction'));
          toast.warning(t('billing.subscribe.vnPayMissingTransaction'), t('toast.warningTitle'));
        }
      }

      try {
        const currentPlanResponse = await getMyCustomerPlan();
        setRefreshedCurrentPlan(currentPlanResponse.data ?? null);
      } catch (refreshError) {
        console.error('Subscription succeeded but current customer plan refresh failed:', refreshError);
        setRefreshedCurrentPlan(null);
      }
    } catch (error) {
      console.error('Failed to subscribe customer plan:', error);
      const message =
        getSubscriptionErrorMessage(
          error,
          t('billing.subscribe.errors.subscribe'),
          t('billing.errors.unauthorized'),
        );
      setErrorMessage(message);
      toast.error(message, t('toast.errorTitle'));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div>
      <PageHeader
        title={t('billing.subscribe.title')}
        subtitle={t('billing.subscribe.subtitle')}
        actions={
          <>
            <Button
              variant="secondary"
              leftIcon={<ArrowLeft className="h-4 w-4" />}
              onClick={() => navigate('/billing')}
            >
              {t('billing.subscribe.backToBilling')}
            </Button>
            <Button
              variant="secondary"
              leftIcon={<RefreshCw className="h-4 w-4" />}
              onClick={() => void loadPlans()}
              disabled={isLoadingPlans}
            >
              {t('billing.refresh')}
            </Button>
          </>
        }
      />

      {planRequired && (
        <div className="mb-lg rounded-xl border border-amber-300 bg-amber-50 px-lg py-md text-sm font-semibold text-amber-900 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-100" role="alert">
          {t('legalTickets.planRequired')}
        </div>
      )}

      {errorMessage && (
        <div className="mb-lg rounded-xl border border-error/30 bg-error-container px-lg py-md text-sm font-semibold text-risk-high-text dark:border-red-900 dark:bg-red-950/40 dark:text-red-200">
          {errorMessage}
        </div>
      )}

      {successMessage && subscribedPlan && (
        <Card className="mb-lg border-emerald-200 bg-emerald-50 dark:border-emerald-900 dark:bg-emerald-950/40">
          <div className="flex flex-col gap-md md:flex-row md:items-start md:justify-between">
            <div>
              <div className="flex items-center gap-sm">
                <CheckCircle2 className="h-5 w-5 text-emerald-700 dark:text-emerald-300" aria-hidden="true" />
                <h2 className="font-sans text-title-lg font-semibold text-emerald-900 dark:text-emerald-100">
                  {t('billing.subscribe.successTitle')}
                </h2>
              </div>
              <p className="mt-sm text-sm text-emerald-800 dark:text-emerald-200">{successMessage}</p>
              <div className="mt-md flex flex-wrap gap-sm">
                <SubscriptionStatusBadge status={subscribedPlan.status} />
                {subscribedPlan.latestTransactionId !== null && (
                  <Badge tone="blue">
                    {t('billing.latestTransactionId')}: {subscribedPlan.latestTransactionId}
                  </Badge>
                )}
                {subscribedPlan.latestTransactionCode && (
                  <Badge tone="purple">
                    {t('billing.latestTransactionCode')}: {subscribedPlan.latestTransactionCode}
                  </Badge>
                )}
              </div>
              {refreshedCurrentPlan && (
                <p className="mt-sm text-xs font-semibold text-emerald-800 dark:text-emerald-200">
                  {t('billing.subscribe.currentPlanRefreshed')}: {refreshedCurrentPlan.subscriptionPlan.planName}
                </p>
              )}
            </div>
            <Button onClick={() => navigate('/billing')}>{t('billing.subscribe.viewBilling')}</Button>
          </div>
        </Card>
      )}

      {isLoadingPlans ? (
        <section className="grid gap-gutter md:grid-cols-2 xl:grid-cols-3">
          {Array.from({ length: 3 }).map((_, index) => (
            <Card key={index}>
              <div className="h-6 w-1/2 animate-pulse rounded bg-surface-container-high dark:bg-slate-800" />
              <div className="mt-md h-4 w-full animate-pulse rounded bg-surface-container-high dark:bg-slate-800" />
              <div className="mt-sm h-4 w-3/4 animate-pulse rounded bg-surface-container-high dark:bg-slate-800" />
              <div className="mt-lg h-10 w-full animate-pulse rounded bg-surface-container-high dark:bg-slate-800" />
            </Card>
          ))}
        </section>
      ) : plans.length === 0 ? (
        <EmptyState
          title={t('billing.subscribe.emptyTitle')}
          description={t('billing.subscribe.emptyDescription')}
          actionLabel={t('actions.retry')}
          onAction={() => void loadPlans()}
        />
      ) : (
        <>
          <section className="grid gap-gutter md:grid-cols-2 xl:grid-cols-3">
            {plans.map((plan) => {
              const isSelected = selectedPlanId === plan.id;
              const isCurrent = refreshedCurrentPlan?.subscriptionPlan.id === plan.id;
              return (
                <button
                  key={plan.id}
                  type="button"
                  className={cn(
                    'rounded-xl text-left transition focus-visible:outline focus-visible:outline-2 focus-visible:outline-primary',
                    !plan.active && 'opacity-70',
                  )}
                  disabled={isCurrent || !plan.active}
                  onClick={() => setSelectedPlanId(plan.id)}
                >
                  <Card
                    className={cn(
                      'h-full border-2 transition',
                      isSelected
                        ? 'border-primary shadow-raised dark:border-inverse-primary'
                        : 'border-transparent hover:border-primary/40 dark:hover:border-inverse-primary/40',
                    )}
                  >
                    <div className="flex items-start justify-between gap-md">
                      <div>
                        <h2 className="text-title-lg font-semibold text-on-surface dark:text-slate-100">{plan.planName}</h2>
                        <p className="mt-xs text-sm text-on-surface-variant dark:text-slate-400">
                          {plan.description || t('billing.subscribe.noDescription')}
                        </p>
                      </div>
                      <Badge tone={isCurrent ? 'blue' : plan.active ? 'green' : 'slate'}>
                        {isCurrent ? t('billing.currentPlan') : plan.active ? t('billing.activePlan') : t('billing.inactivePlan')}
                      </Badge>
                    </div>
                    <div className="mt-lg rounded-xl bg-surface-container-low p-md dark:bg-slate-800">
                      <p className="text-3xl font-bold text-primary dark:text-inverse-primary">
                        {formatPrice(plan.price)}
                      </p>
                      <p className="mt-xs text-sm text-on-surface-variant dark:text-slate-400">
                        {plan.durationDays} {t('billing.days')} · {getPlanTypeLabel(plan.planType)}
                      </p>
                    </div>
                    <dl className="mt-md grid gap-sm text-sm">
                      <div className="flex items-center justify-between gap-md">
                        <dt className="text-on-surface-variant dark:text-slate-400">{t('billing.maxQuota')}</dt>
                        <dd className="font-semibold text-on-surface dark:text-slate-100">
                          {numberFormatter.format(plan.maxQuota)} {t('billing.analyses')}
                        </dd>
                      </div>
                      <div className="flex items-center justify-between gap-md"><dt className="text-on-surface-variant">{t('billing.limit.aiTokens')}</dt><dd className="font-semibold">{numberFormatter.format(plan.aiTokenLimit ?? 0)}</dd></div>
                      <div className="flex items-center justify-between gap-md"><dt className="text-on-surface-variant">{t('billing.limit.workspaces')}</dt><dd className="font-semibold">{numberFormatter.format(plan.workspaceLimit ?? 0)}</dd></div>
                      <div className="flex items-center justify-between gap-md"><dt className="text-on-surface-variant">{t('billing.limit.documentsPerWorkspace')}</dt><dd className="font-semibold">{numberFormatter.format(plan.documentPerWorkspaceLimit ?? 0)}</dd></div>
                      <div className="flex items-center justify-between gap-md"><dt className="text-on-surface-variant">{t('billing.limit.storagePerFile')}</dt><dd className="font-semibold">{numberFormatter.format(plan.storageLimitMb ?? 0)} / {numberFormatter.format(plan.maxFileSizeMb ?? 0)} MB</dd></div>
                      <div className="flex items-center justify-between gap-md"><dt className="text-on-surface-variant">{t('billing.limit.attachmentsPerSession')}</dt><dd className="font-semibold">{numberFormatter.format(plan.maxAttachedDocumentsPerSession ?? 0)}</dd></div>
                      <div className="flex items-center justify-between gap-md"><dt className="text-on-surface-variant">{t('billing.limit.draftContracts')}</dt><dd className="font-semibold">{numberFormatter.format(plan.contractDraftLimit ?? 0)}</dd></div>
                      <div className="flex items-center justify-between gap-md"><dt className="text-on-surface-variant">{t('billing.limit.contactExpert')}</dt><dd className="font-semibold">{plan.allowContactExpertTicket ? t('billing.comparison.expertTicketsPerMonth', { count: numberFormatter.format(plan.expertTicketLimit ?? 0) }) : t('billing.comparison.premiumOnly')}</dd></div>
                      <div className="flex items-center justify-between gap-md">
                        <dt className="text-on-surface-variant dark:text-slate-400">{t('billing.planType')}</dt>
                        <dd className="font-semibold text-on-surface dark:text-slate-100">{getPlanTypeLabel(plan.planType)}</dd>
                      </div>
                    </dl>
                    {isSelected && (
                      <div className="mt-md flex items-center gap-sm text-sm font-semibold text-primary dark:text-inverse-primary">
                        <CheckCircle2 className="h-4 w-4" aria-hidden="true" />
                        {t('billing.subscribe.selectedPlan')}
                      </div>
                    )}
                    <div className="mt-md rounded-lg bg-primary/10 px-sm py-sm text-center text-sm font-semibold text-primary dark:text-inverse-primary">
                      {isCurrent ? t('billing.currentPlan') : refreshedCurrentPlan && planRank(plan) < planRank(refreshedCurrentPlan.subscriptionPlan) ? t('billing.comparison.downgradeScheduled') : t('billing.comparison.upgradeImmediate')}
                    </div>
                  </Card>
                </button>
              );
            })}
          </section>

          <Card className="mt-xl" title={t('billing.comparison.limitTableTitle')}>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[720px] text-sm">
                <thead><tr className="border-b border-legal-border text-left dark:border-slate-700"><th className="p-sm">{t('billing.comparison.limits')}</th>{plans.map((plan) => <th key={plan.id} className="p-sm">{plan.displayName ?? plan.planName}{refreshedCurrentPlan?.subscriptionPlan.id === plan.id ? ` · ${t('billing.comparison.current')}` : ''}</th>)}</tr></thead>
                <tbody>
                  {[
                    [t('billing.limit.contractAnalyses'), (plan: SubscriptionPlan) => numberFormatter.format(plan.contractAnalysisLimit ?? plan.maxQuota)],
                    [t('billing.limit.aiTokens'), (plan: SubscriptionPlan) => numberFormatter.format(plan.aiTokenLimit ?? 0)],
                    [t('billing.limit.workspaces'), (plan: SubscriptionPlan) => numberFormatter.format(plan.workspaceLimit ?? 0)],
                    [t('billing.limit.documentsPerWorkspace'), (plan: SubscriptionPlan) => numberFormatter.format(plan.documentPerWorkspaceLimit ?? 0)],
                    [t('billing.limit.storage'), (plan: SubscriptionPlan) => `${numberFormatter.format(plan.storageLimitMb ?? 0)} MB`],
                    [t('billing.limit.maxFileSize'), (plan: SubscriptionPlan) => `${numberFormatter.format(plan.maxFileSizeMb ?? 0)} MB`],
                    [t('billing.limit.attachmentsPerSession'), (plan: SubscriptionPlan) => numberFormatter.format(plan.maxAttachedDocumentsPerSession ?? 0)],
                    [t('billing.limit.draftContracts'), (plan: SubscriptionPlan) => numberFormatter.format(plan.contractDraftLimit ?? 0)],
                    [t('billing.limit.contactExpert'), (plan: SubscriptionPlan) => plan.allowContactExpertTicket ? t('billing.comparison.expertTicketsPerMonth', { count: numberFormatter.format(plan.expertTicketLimit ?? 0) }) : t('billing.comparison.premiumOnly')],
                  ].map(([label, render]) => <tr key={String(label)} className="border-b border-legal-border/60 dark:border-slate-800"><td className="p-sm font-semibold">{String(label)}</td>{plans.map((plan) => <td key={plan.id} className="p-sm text-on-surface-variant dark:text-slate-300">{(render as (value: SubscriptionPlan) => string)(plan)}</td>)}</tr>)}
                </tbody>
              </table>
            </div>
          </Card>

          <Card className="mt-xl" title={t('billing.subscribe.confirmPanelTitle')} subtitle={t('billing.subscribe.confirmPanelSubtitle')}>
            <div className="grid gap-lg lg:grid-cols-[1fr_0.8fr]">
              <div>
                <label className="text-sm font-semibold text-on-surface dark:text-slate-100" htmlFor="payment-method">
                  {t('billing.paymentMethod')}
                </label>
                <select
                  id="payment-method"
                  className="mt-sm w-full rounded-xl border border-outline-variant bg-white px-md py-sm text-sm outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/10 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100"
                  value={paymentMethod}
                  onChange={(event) => setPaymentMethod(event.target.value as PaymentMethod)}
                >
                  {PAYMENT_METHODS.map((method) => (
                    <option key={method} value={method}>
                      {t(`billing.paymentMethod.${method}`)}
                    </option>
                  ))}
                </select>
                <p className="mt-sm text-xs text-on-surface-variant dark:text-slate-400">
                  {t('billing.subscribe.paymentGatewayNote')}
                </p>
              </div>

              <div className="rounded-xl bg-surface-container-low p-md dark:bg-slate-800">
                <p className="text-sm font-semibold text-on-surface dark:text-slate-100">
                  {selectedPlan ? selectedPlan.planName : t('billing.subscribe.noSelectedPlan')}
                </p>
                <p className="mt-xs text-sm text-on-surface-variant dark:text-slate-400">
                  {selectedPlan
                    ? `${formatPrice(selectedPlan.price)} · ${selectedPlan.durationDays} ${t('billing.days')}`
                    : t('billing.subscribe.selectPlanHint')}
                </p>
                <Button
                  className="mt-md w-full"
                  leftIcon={<CreditCard className="h-4 w-4" />}
                  onClick={openConfirmDialog}
                  disabled={isSubmitting || !selectedPlan}
                >
                  {t('billing.subscribe.openConfirm')}
                </Button>
              </div>
            </div>
          </Card>
        </>
      )}

      <Modal
        open={confirmOpen}
        title={t('billing.subscribe.confirmTitle')}
        onClose={() => {
          if (!isSubmitting) {
            setConfirmOpen(false);
          }
        }}
        footer={
          <div className="flex flex-col-reverse gap-sm sm:flex-row sm:justify-end">
            <Button variant="secondary" onClick={() => setConfirmOpen(false)} disabled={isSubmitting}>
              {t('actions.cancel')}
            </Button>
            <Button onClick={() => void handleSubscribe()} disabled={isSubmitting || !selectedPlan}>
              {isSubmitting ? t('billing.subscribe.subscribing') : t('billing.subscribe.confirmSubmit')}
            </Button>
          </div>
        }
      >
        {selectedPlan && (
          <div className="space-y-md">
            <p className="text-sm text-on-surface-variant dark:text-slate-400">
              {t('billing.subscribe.confirmDescription')}
            </p>
            <div className="rounded-xl bg-surface-container-low p-md dark:bg-slate-800">
              <dl className="grid gap-sm text-sm">
                <div className="flex justify-between gap-md">
                  <dt className="text-on-surface-variant dark:text-slate-400">{t('billing.planName')}</dt>
                  <dd className="font-semibold text-on-surface dark:text-slate-100">{selectedPlan.planName}</dd>
                </div>
                <div className="flex justify-between gap-md">
                  <dt className="text-on-surface-variant dark:text-slate-400">{t('billing.price')}</dt>
                  <dd className="font-semibold text-on-surface dark:text-slate-100">
                    {formatPrice(selectedPlan.price)}
                  </dd>
                </div>
                <div className="flex justify-between gap-md">
                  <dt className="text-on-surface-variant dark:text-slate-400">{t('billing.duration')}</dt>
                  <dd className="font-semibold text-on-surface dark:text-slate-100">
                    {selectedPlan.durationDays} {t('billing.days')}
                  </dd>
                </div>
                <div className="flex justify-between gap-md">
                  <dt className="text-on-surface-variant dark:text-slate-400">{t('billing.paymentMethod')}</dt>
                  <dd className="font-semibold text-on-surface dark:text-slate-100">
                    {t(`billing.paymentMethod.${paymentMethod}`)}
                  </dd>
                </div>
              </dl>
            </div>
            <p className="text-xs text-on-surface-variant dark:text-slate-400">
              {t('billing.subscribe.pendingNotice')}
            </p>
          </div>
        )}
      </Modal>
    </div>
  );
}
