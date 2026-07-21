import { Banknote, BriefcaseBusiness, CircleDollarSign, Clock3, RefreshCw, WalletCards } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { Badge, type BadgeTone } from "../../components/common/Badge";
import { Button } from "../../components/common/Button";
import { Card } from "../../components/common/Card";
import { DataTable, type DataTableColumn } from "../../components/common/DataTable";
import { PageHeader } from "../../components/common/PageHeader";
import { Pagination } from "../../components/common/Pagination";
import { useI18n } from "../../hooks/useI18n";
import { useToast } from "../../hooks/useToast";
import { getExpertRevenueSummary, getExpertRevenueTickets } from "../../services/expertRevenue.service";
import type { ExpertPaymentStatus, ExpertRevenueSummary, ExpertRevenueTicket } from "../../types/expertRevenue";
import { getLegalTicketStatusLabel } from "../../types/legalTicketStatus";
import { formatDisplayDateTime, formatVndCurrency, localeForLanguage } from "../../utils/format";
import { parsePageParam, toPageParam } from "../../utils/pagination";

const paymentTone: Record<ExpertPaymentStatus, BadgeTone> = {
  UNPAID: "slate",
  PENDING: "amber",
  PAID: "green",
};

const emptySummary: ExpertRevenueSummary = {
  assignedTicketCount: 0, resolvedTicketCount: 0, paidTicketCount: 0,
  pendingPaymentTicketCount: 0, totalRevenue: 0, paidRevenue: 0,
  pendingRevenue: 0, totalPlatformFee: 0, totalExpertPayout: 0,
};

export function LawyerRevenuePage() {
  const { t, language } = useI18n();
  const toast = useToast();
  const locale = localeForLanguage(language);
  const [searchParams, setSearchParams] = useSearchParams();
  const [page, setPage] = useState(() => parsePageParam(searchParams.get("page")));
  const [summary, setSummary] = useState<ExpertRevenueSummary>(emptySummary);
  const [tickets, setTickets] = useState<ExpertRevenueTicket[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalItems, setTotalItems] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    const [summaryResult, ticketsResult] = await Promise.allSettled([
      getExpertRevenueSummary(), getExpertRevenueTickets(page, 10),
    ]);
    if (summaryResult.status === "fulfilled") setSummary(summaryResult.value);
    if (ticketsResult.status === "fulfilled") {
      setTickets(ticketsResult.value.items ?? []);
      setTotalPages(ticketsResult.value.totalPages ?? 0);
      setTotalItems(ticketsResult.value.totalItems ?? 0);
    }
    if (summaryResult.status === "rejected" || ticketsResult.status === "rejected") {
      setError(t("expertRevenue.loadError"));
      toast.error(t("expertRevenue.loadError"), t("toast.errorTitle"));
    }
    setLoading(false);
  }, [page, t, toast]);

  useEffect(() => { void load(); }, [load]);

  const columns = useMemo<DataTableColumn<ExpertRevenueTicket>[]>(() => [
    {
      header: t("expertRevenue.ticket"),
      cell: (item) => <Link className="font-semibold text-primary" to={`/lawyer/tickets/${item.ticketId}`}>{item.ticketCode || item.ticketId}</Link>,
    },
    { header: t("table.status"), cell: (item) => <Badge>{getLegalTicketStatusLabel(item.ticketStatus, t)}</Badge> },
    { header: t("expertRevenue.consultationFee"), cell: (item) => formatVndCurrency(Number(item.consultationFee ?? 0), "0 ₫", locale) },
    { header: t("expertRevenue.platformFee"), cell: (item) => formatVndCurrency(Number(item.platformFee ?? 0), "0 ₫", locale) },
    { header: t("expertRevenue.expertPayout"), cell: (item) => <span className="font-semibold text-emerald-700 dark:text-emerald-300">{formatVndCurrency(Number(item.expertPayout ?? 0), "0 ₫", locale)}</span> },
    { header: t("expertRevenue.paymentStatus"), cell: (item) => <Badge tone={paymentTone[item.paymentStatus]}>{t(`expertRevenue.payment.${item.paymentStatus}`)}</Badge> },
    { header: t("expertRevenue.paidAt"), cell: (item) => formatDisplayDateTime(item.paidAt, "-", locale) },
  ], [locale, t]);

  return <div className="space-y-xl">
    <PageHeader
      eyebrow={t("nav.expertRevenue")}
      title={t("expertRevenue.title")}
      subtitle={t("expertRevenue.subtitle")}
      actions={<Button onClick={() => void load()} disabled={loading} leftIcon={<RefreshCw className="h-4 w-4" />}>{t("common.refresh")}</Button>}
    />
    {error && <div role="alert" className="rounded-xl border border-error/40 bg-error/10 p-md text-sm text-error">{error}</div>}
    <div className="grid gap-md sm:grid-cols-2 xl:grid-cols-5">
      <Metric icon={BriefcaseBusiness} label={t("expertRevenue.assigned")} value={String(summary.assignedTicketCount)} />
      <Metric icon={Clock3} label={t("expertRevenue.pendingTickets")} value={String(summary.pendingPaymentTicketCount)} />
      <Metric icon={CircleDollarSign} label={t("expertRevenue.totalRevenue")} value={formatVndCurrency(Number(summary.totalRevenue), "0 ₫", locale)} />
      <Metric icon={Banknote} label={t("expertRevenue.platformFee")} value={formatVndCurrency(Number(summary.totalPlatformFee), "0 ₫", locale)} />
      <Metric icon={WalletCards} label={t("expertRevenue.totalPayout")} value={formatVndCurrency(Number(summary.totalExpertPayout), "0 ₫", locale)} highlight />
    </div>
    <Card title={t("expertRevenue.ticketBreakdown")} subtitle={t("expertRevenue.ticketBreakdownHint")}>
      <DataTable columns={columns} data={tickets} getRowKey={(item) => item.ticketId} emptyMessage={loading ? t("common.loading") : t("expertRevenue.empty")} />
      <Pagination page={page} totalPages={totalPages} totalItems={totalItems} disabled={loading} onPageChange={(nextPage) => {
        setPage(nextPage); const next = new URLSearchParams(searchParams); next.set("page", toPageParam(nextPage)); setSearchParams(next);
      }} />
    </Card>
    <p className="text-xs text-on-surface-variant">{t("expertRevenue.manualPayoutNotice")}</p>
  </div>;
}

function Metric({ icon: Icon, label, value, highlight = false }: { icon: typeof Banknote; label: string; value: string; highlight?: boolean }) {
  return <Card className="p-lg"><Icon className={`h-7 w-7 ${highlight ? "text-emerald-600" : "text-primary"}`} /><p className="mt-md text-xs font-semibold uppercase tracking-wide text-on-surface-variant">{label}</p><p className="mt-xs text-xl font-bold">{value}</p></Card>;
}
