import { CheckCircle2, RefreshCw, XCircle } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { Badge } from "../../components/common/Badge";
import { Button } from "../../components/common/Button";
import { Card } from "../../components/common/Card";
import { PageHeader } from "../../components/common/PageHeader";
import { useI18n } from "../../hooks/useI18n";
import { useToast } from "../../hooks/useToast";
import { getMyPaymentTransactions } from "../../services/paymentTransaction.service";
import type { PaymentTransaction } from "../../types/paymentTransaction";
import { formatDisplayDate, formatVndCurrency } from "../../utils/format";
import { findVnPayTransaction, getPaymentResultState, parseVnPayResultReference } from "../../utils/paymentResult";

const isSuccessfulPayment = (transaction: PaymentTransaction | null) =>
  transaction?.paymentStatus?.toUpperCase() === "SUCCESS";

export function PaymentResultPage() {
  const { t, language } = useI18n();
  const toast = useToast();
  const [searchParams] = useSearchParams();
  const [transaction, setTransaction] = useState<PaymentTransaction | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notFound, setNotFound] = useState(false);
  const notifiedResultRef = useRef("");

  const loadPaymentResult = useCallback(async () => {
    setLoading(true);
    setError("");
    setNotFound(false);

    try {
      const reference = parseVnPayResultReference(searchParams);
      if (!reference.transactionCode) {
        setTransaction(null);
        setError(t("payment.result.missingParams"));
        return;
      }
      const transactions = await getMyPaymentTransactions();
      const result = findVnPayTransaction(transactions, reference);

      if (!result) {
        setTransaction(null);
        setNotFound(true);
        setError(t("payment.result.noTransaction"));
        return;
      }

      setTransaction(result);

      const status = result.paymentStatus?.toUpperCase() ?? "UNKNOWN";
      const resultKey = `${result.id}:${status}`;

      if (notifiedResultRef.current !== resultKey) {
        if (isSuccessfulPayment(result)) {
          toast.success(t("payment.result.success"), t("toast.successTitle"));
        } else {
          toast.warning(t("payment.result.pending"), t("toast.warningTitle"));
        }

        notifiedResultRef.current = resultKey;
      }
    } catch (err) {
      setTransaction(null);
      console.error("Failed to verify payment result:", err);
      const message = t("payment.result.verifyError");
      setError(message);
      toast.error(message, t("toast.errorTitle"));
    } finally {
      setLoading(false);
    }
  }, [searchParams, t, toast]);

  useEffect(() => {
    void loadPaymentResult();
  }, [loadPaymentResult]);

  const success = isSuccessfulPayment(transaction);
  const reference = parseVnPayResultReference(searchParams);
  const resultState = getPaymentResultState(transaction, Boolean(reference.transactionCode), notFound);
  const stateLabel = t(`payment.result.state.${resultState}`);
  const locale = language === "vi" ? "vi-VN" : "en-US";

  return (
    <div>
      <PageHeader
        title={t("payment.result.title")}
        subtitle={t("payment.result.subtitle")}
        actions={
          <Button
            variant="secondary"
            leftIcon={<RefreshCw className="h-4 w-4" />}
            onClick={() => void loadPaymentResult()}
            disabled={loading}
          >
            {t("billing.refresh")}
          </Button>
        }
      />

      <Card
        className={success ? "border-emerald-200" : error ? "border-error/30" : undefined}
        title={
          loading
            ? t("payment.result.verifying")
            : stateLabel
        }
        actions={
          loading ? (
            <RefreshCw className="h-5 w-5 animate-spin text-primary dark:text-inverse-primary" />
          ) : success ? (
            <CheckCircle2 className="h-5 w-5 text-emerald-700 dark:text-emerald-300" />
          ) : (
            <XCircle className="h-5 w-5 text-error" />
          )
        }
      >
        {loading ? (
          <p className="text-sm text-on-surface-variant dark:text-slate-400">
            {t("payment.result.verifyingDescription")}
          </p>
        ) : error ? (
          <p className="rounded-lg bg-error-container px-md py-sm text-sm font-semibold text-risk-high-text dark:bg-red-950/40 dark:text-red-200">
            {error}
          </p>
        ) : transaction ? (
          <div className="space-y-md">
            <div className="flex flex-wrap gap-xs">
              <Badge tone={success ? "green" : resultState === "PENDING" ? "amber" : "red"}>{stateLabel}</Badge>
              <Badge tone="blue">{t(`billing.paymentMethod.${transaction.paymentMethod}`)}</Badge>
              {transaction.transactionCode && <Badge tone="purple">{transaction.transactionCode}</Badge>}
            </div>

            <dl className="grid gap-md rounded-xl bg-surface-container-low p-md text-sm dark:bg-slate-800 md:grid-cols-2">
              <div>
                <dt className="label-uppercase">{t("payment.result.plan")}</dt>
                <dd className="mt-xs font-semibold">{transaction.planName}</dd>
              </div>
              <div>
                <dt className="label-uppercase">{t("payment.result.amount")}</dt>
                 <dd className="mt-xs font-semibold">{formatVndCurrency(transaction.amount, t("billing.free"), locale)}</dd>
              </div>
              <div>
                <dt className="label-uppercase">{t("payment.result.createdAt")}</dt>
                 <dd className="mt-xs">{formatDisplayDate(transaction.createdAt, "-", locale)}</dd>
              </div>
              <div>
                <dt className="label-uppercase">{t("payment.result.paidAt")}</dt>
                 <dd className="mt-xs">{formatDisplayDate(transaction.paidAt, t("payment.result.unpaid"), locale)}</dd>
              </div>
            </dl>
          </div>
        ) : (
          <p className="text-sm text-on-surface-variant dark:text-slate-400">
            {t("payment.result.noTransaction")}
          </p>
        )}

        <div className="mt-lg flex flex-wrap gap-sm">
          <Link to="/billing">
            <Button>{t("payment.result.backToBilling")}</Button>
          </Link>
          <Link to="/dashboard">
            <Button variant="secondary">{t("payment.result.backToDashboard")}</Button>
          </Link>
        </div>
      </Card>
    </div>
  );
}
