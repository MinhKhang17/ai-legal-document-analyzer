import { CreditCard, Download, Receipt, ShieldCheck, Sparkles } from 'lucide-react';
import { BillingUsageCard } from '../../components/billing/BillingUsageCard';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { DataTable, type DataTableColumn } from '../../components/common/DataTable';
import { PageHeader } from '../../components/common/PageHeader';
import { StatusBadge } from '../../components/common/StatusBadge';
import { billingUsage, invoices } from '../../api/mockData';
import { useI18n } from '../../hooks/useI18n';
import type { Invoice } from '../../types/billing';
import { formatCurrency } from '../../utils/format';

export function BillingPage() {
  const { t } = useI18n();
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

  return (
    <div>
      <PageHeader
        title={t('billing.title')}
        subtitle={t('billing.subtitle')}
        actions={<Button leftIcon={<CreditCard className="h-4 w-4" />}>{t('billing.managePaymentMethod')}</Button>}
      />

      <section className="grid gap-gutter md:grid-cols-2 xl:grid-cols-4">
        {billingUsage.map((usage) => (
          <BillingUsageCard key={usage.id} usage={usage} />
        ))}
      </section>

      <section className="mt-xl grid gap-gutter xl:grid-cols-[0.9fr_1.1fr]">
        <Card className="bg-primary text-white dark:bg-slate-900" title={t('billing.enterprisePlan')}>
          <div className="space-y-md">
            <p className="text-sm text-surface-container">
              {t('billing.planDescription')}
            </p>
            <div className="rounded-xl bg-white/10 p-md">
              <p className="text-4xl font-bold">$4,200</p>
              <p className="text-sm text-surface-container">{t('billing.perMonth')}</p>
            </div>
            <div className="flex flex-wrap gap-xs">
              <Badge tone="gold"><ShieldCheck className="h-3 w-3" /> {t('billing.soc2Ready')}</Badge>
              <Badge tone="gold"><Sparkles className="h-3 w-3" /> {t('billing.legalAiModels')}</Badge>
            </div>
          </div>
        </Card>

        <Card title={t('billing.invoices')} actions={<Receipt className="h-5 w-5 text-primary dark:text-inverse-primary" />}>
          <DataTable columns={columns} data={invoices} getRowKey={(invoice) => invoice.id} />
        </Card>
      </section>
    </div>
  );
}
