import { Banknote, Percent, RefreshCw, WalletCards } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Button } from "../../components/common/Button";
import { Card } from "../../components/common/Card";
import { DataTable, type DataTableColumn } from "../../components/common/DataTable";
import { PageHeader } from "../../components/common/PageHeader";
import { useI18n } from "../../hooks/useI18n";
import { useToast } from "../../hooks/useToast";
import { getAdminRevenueOverview, getRevenueSetting, updateRevenueSetting } from "../../services/expertRevenue.service";
import type { AdminRevenueOverview, ExpertRevenueBreakdown, RevenueSetting } from "../../types/expertRevenue";
import { formatDisplayDateTime, formatVndCurrency, localeForLanguage } from "../../utils/format";

const emptyOverview: AdminRevenueOverview = {
  totalTicketCount: 0, paidTicketCount: 0, pendingPaymentTicketCount: 0,
  totalConsultationFee: 0, totalPlatformFee: 0, totalExpertPayout: 0, byExpert: [],
};

export function AdminRevenuePage() {
  const { t, language } = useI18n();
  const locale = localeForLanguage(language);
  const toast = useToast();
  const [overview, setOverview] = useState(emptyOverview);
  const [setting, setSetting] = useState<RevenueSetting | null>(null);
  const [ratePercent, setRatePercent] = useState("20");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true); setError("");
    const [overviewResult, settingResult] = await Promise.allSettled([getAdminRevenueOverview(), getRevenueSetting()]);
    if (overviewResult.status === "fulfilled") setOverview(overviewResult.value);
    if (settingResult.status === "fulfilled") {
      setSetting(settingResult.value); setRatePercent(String(Number(settingResult.value.commissionRate) * 100));
    }
    if (overviewResult.status === "rejected" || settingResult.status === "rejected") setError(t("adminRevenue.loadError"));
    setLoading(false);
  }, [t]);

  useEffect(() => { void load(); }, [load]);

  const saveRate = async () => {
    const percent = Number(ratePercent);
    if (!Number.isFinite(percent) || percent < 0 || percent > 100) {
      setError(t("adminRevenue.invalidRate")); return;
    }
    setSaving(true); setError("");
    try {
      const updated = await updateRevenueSetting(percent / 100);
      setSetting(updated); setRatePercent(String(Number(updated.commissionRate) * 100));
      toast.success(t("adminRevenue.saveSuccess"), t("toast.successTitle"));
    } catch { setError(t("adminRevenue.saveError")); toast.error(t("adminRevenue.saveError")); }
    finally { setSaving(false); }
  };

  const columns = useMemo<DataTableColumn<ExpertRevenueBreakdown>[]>(() => [
    { header: t("adminRevenue.expert"), cell: (item) => <span className="font-semibold">{item.expertName}</span> },
    { header: t("adminRevenue.tickets"), cell: (item) => item.ticketCount },
    { header: t("adminRevenue.consultationFee"), cell: (item) => formatVndCurrency(Number(item.totalConsultationFee), "0 ₫", locale) },
    { header: t("adminRevenue.expertPayout"), cell: (item) => formatVndCurrency(Number(item.totalExpertPayout), "0 ₫", locale) },
  ], [locale, t]);

  return <div className="space-y-xl">
    <PageHeader title={t("adminRevenue.title")} subtitle={t("adminRevenue.subtitle")} actions={<Button onClick={() => void load()} disabled={loading} leftIcon={<RefreshCw className="h-4 w-4" />}>{t("common.refresh")}</Button>} />
    {error && <div role="alert" className="rounded-xl border border-error/40 bg-error/10 p-md text-sm text-error">{error}</div>}
    <div className="grid gap-md md:grid-cols-3">
      <Metric icon={Banknote} label={t("adminRevenue.consultationFee")} value={formatVndCurrency(Number(overview.totalConsultationFee), "0 ₫", locale)} />
      <Metric icon={Percent} label={t("adminRevenue.platformFee")} value={formatVndCurrency(Number(overview.totalPlatformFee), "0 ₫", locale)} />
      <Metric icon={WalletCards} label={t("adminRevenue.expertPayout")} value={formatVndCurrency(Number(overview.totalExpertPayout), "0 ₫", locale)} />
    </div>
    <div className="grid gap-gutter xl:grid-cols-[minmax(0,1fr)_360px]">
      <Card title={t("adminRevenue.breakdown")}><DataTable columns={columns} data={overview.byExpert ?? []} getRowKey={(item) => String(item.expertId)} emptyMessage={t("adminRevenue.empty")} /></Card>
      <Card title={t("adminRevenue.settingTitle")} subtitle={t("adminRevenue.settingHint")}>
        <label className="block text-sm font-semibold">{t("adminRevenue.commissionRate")}<div className="relative mt-xs"><input className="form-field pr-10" type="number" min="0" max="100" step="0.01" value={ratePercent} onChange={(event) => setRatePercent(event.target.value)} /><span className="absolute right-3 top-1/2 -translate-y-1/2">%</span></div></label>
        <p className="mt-sm text-xs text-on-surface-variant">{t("adminRevenue.snapshotNotice")}</p>
        {setting?.updatedAt && <p className="mt-sm text-xs text-on-surface-variant">{t("adminRevenue.lastUpdated")}: {formatDisplayDateTime(setting.updatedAt, "-", locale)} · {setting.updatedByName || "-"}</p>}
        <Button className="mt-md w-full" onClick={() => void saveRate()} disabled={saving}>{saving ? t("common.loading") : t("actions.save")}</Button>
      </Card>
    </div>
  </div>;
}

function Metric({ icon: Icon, label, value }: { icon: typeof Banknote; label: string; value: string }) {
  return <Card className="p-lg"><Icon className="h-7 w-7 text-primary" /><p className="mt-md text-xs font-semibold uppercase tracking-wide text-on-surface-variant">{label}</p><p className="mt-xs text-2xl font-bold">{value}</p></Card>;
}
