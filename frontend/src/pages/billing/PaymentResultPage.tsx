import { CheckCircle2, RefreshCw, XCircle } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { Badge } from "../../components/common/Badge";
import { Button } from "../../components/common/Button";
import { Card } from "../../components/common/Card";
import { PageHeader } from "../../components/common/PageHeader";
import { useI18n } from "../../hooks/useI18n";
import { useToast } from "../../hooks/useToast";
import { getMyPaymentTransactions } from "../../services/paymentTransaction.service";
import type { PaymentTransaction } from "../../types/paymentTransaction";
import { formatDisplayDate, formatVndCurrency } from "../../utils/format";

const isSuccessfulPayment = (transaction: PaymentTransaction | null) =>
  transaction?.paymentStatus?.toUpperCase() === "SUCCESS";

const getLatestTransaction = (transactions: PaymentTransaction[]) =>
  [...transactions].sort(
    (a, b) =>
      new Date(b.updatedAt ?? b.createdAt).getTime() -
      new Date(a.updatedAt ?? a.createdAt).getTime(),
  )[0] ?? null;

export function PaymentResultPage() {
  const { t } = useI18n();
  const toast = useToast();
  const [transaction, setTransaction] = useState<PaymentTransaction | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const notifiedResultRef = useRef("");

  const loadPaymentResult = useCallback(async () => {
    setLoading(true);
    setError("");

    try {
      const transactions = await getMyPaymentTransactions();
      const result = getLatestTransaction(transactions);

      if (!result) {
        setTransaction(null);
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
      const message = err instanceof Error ? err.message : t("payment.result.verifyError");
      setError(message);
      toast.error(message, t("toast.errorTitle"));
    } finally {
      setLoading(false);
    }
  }, [t, toast]);

  useEffect(() => {
    void loadPaymentResult();
  }, [loadPaymentResult]);

  const success = isSuccessfulPayment(transaction);

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
            : success
              ? t("payment.result.success")
              : t("payment.result.pending")
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
              <Badge tone={success ? "green" : "amber"}>{transaction.paymentStatus}</Badge>
              <Badge tone="blue">{transaction.paymentMethod}</Badge>
              {transaction.transactionCode && <Badge tone="purple">{transaction.transactionCode}</Badge>}
            </div>

            <dl className="grid gap-md rounded-xl bg-surface-container-low p-md text-sm dark:bg-slate-800 md:grid-cols-2">
              <div>
                <dt className="label-uppercase">{t("payment.result.plan")}</dt>
                <dd className="mt-xs font-semibold">{transaction.planName}</dd>
              </div>
              <div>
                <dt className="label-uppercase">{t("payment.result.amount")}</dt>
                <dd className="mt-xs font-semibold">{formatVndCurrency(transaction.amount)}</dd>
              </div>
              <div>
                <dt className="label-uppercase">{t("payment.result.createdAt")}</dt>
                <dd className="mt-xs">{formatDisplayDate(transaction.createdAt, "-")}</dd>
              </div>
              <div>
                <dt className="label-uppercase">{t("payment.result.paidAt")}</dt>
                <dd className="mt-xs">{formatDisplayDate(transaction.paidAt, t("payment.result.unpaid"))}</dd>
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