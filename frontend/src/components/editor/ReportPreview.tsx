import { Lock, PenLine, ShieldCheck } from 'lucide-react';
import { Badge } from '../common/Badge';
import { Card } from '../common/Card';
import { RiskBadge } from '../common/RiskBadge';
import type { Report } from '../../types/report';

interface ReportPreviewProps {
  report: Report;
}

export function ReportPreview({ report }: ReportPreviewProps) {
  return (
    <article className="document-paper max-w-4xl legal-text">
      <div className="mb-xl flex items-start justify-between border-b border-legal-border pb-lg dark:border-slate-700">
        <div>
          <p className="label-uppercase">LexiGuard</p>
          <h1 className="mt-xs text-3xl font-bold text-primary dark:text-inverse-primary">Legal Risk Review Report</h1>
          <p className="mt-sm text-sm text-on-surface-variant dark:text-slate-400">{report.documentName}</p>
        </div>
        <Badge tone="blue">Finalized</Badge>
      </div>

      <section className="mb-xl">
        <h2 className="mb-md text-2xl font-bold">Executive Summary</h2>
        <Card tone="ai">
          <p className="text-base leading-7 text-on-surface dark:text-slate-100">{report.summary}</p>
        </Card>
      </section>

      <section className="mb-xl grid gap-md md:grid-cols-3">
        <Card>
          <p className="text-3xl font-bold text-error">12%</p>
          <p className="label-uppercase mt-xs">Critical</p>
        </Card>
        <Card>
          <p className="text-3xl font-bold text-secondary dark:text-accent-gold">45%</p>
          <p className="label-uppercase mt-xs">Warning</p>
        </Card>
        <Card>
          <p className="text-3xl font-bold text-primary dark:text-inverse-primary">43%</p>
          <p className="label-uppercase mt-xs">Low Risk</p>
        </Card>
      </section>

      <section className="mb-xl">
        <h2 className="mb-md text-2xl font-bold">Clause Criticality Analysis</h2>
        <div className="overflow-hidden rounded-lg border border-legal-border dark:border-slate-700">
          <table className="w-full text-sm">
            <thead className="bg-surface-container-low dark:bg-slate-800">
              <tr>
                <th className="px-md py-sm text-left label-uppercase">Clause Ref</th>
                <th className="px-md py-sm text-left label-uppercase">Risk Level</th>
                <th className="px-md py-sm text-left label-uppercase">AI Suggestion</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-legal-border dark:divide-slate-700">
              <tr>
                <td className="px-md py-md font-semibold">Sec 8.2: IP Rights</td>
                <td className="px-md py-md"><RiskBadge level="critical" /></td>
                <td className="px-md py-md">Ensure “Work for Hire” language is explicitly bilateral.</td>
              </tr>
              <tr>
                <td className="px-md py-md font-semibold">Sec 14.1: Termination</td>
                <td className="px-md py-md"><RiskBadge level="high" /></td>
                <td className="px-md py-md">Cure period should be extended from 15 to 30 days.</td>
              </tr>
              <tr>
                <td className="px-md py-md font-semibold">Sec 22: Data Privacy</td>
                <td className="px-md py-md"><RiskBadge level="low" /></td>
                <td className="px-md py-md">Standard GDPR compliance detected. No changes needed.</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section className="grid gap-md md:grid-cols-2">
        <Card title="Reviewer Notes">
          <p className="text-sm leading-6 text-on-surface-variant dark:text-slate-400">
            Approved for final submission to GC. Minor tweaks to Section 14 requested but not blockers.
          </p>
        </Card>
        <Card title="Digital Signature">
          <div className="flex items-center gap-md">
            <div className="flex h-12 w-12 items-center justify-center rounded-lg bg-surface-container-high text-primary dark:bg-slate-800 dark:text-inverse-primary">
              <PenLine className="h-5 w-5" aria-hidden="true" />
            </div>
            <div>
              <p className="font-semibold">Marcus Aurelius, Esq.</p>
              <p className="text-xs text-on-surface-variant dark:text-slate-400">Hash: 8f2a...e91c</p>
            </div>
          </div>
          <div className="mt-md flex flex-wrap gap-xs">
            <Badge tone="green"><ShieldCheck className="h-3 w-3" /> AI validated</Badge>
            <Badge tone="slate"><Lock className="h-3 w-3" /> Restricted</Badge>
          </div>
        </Card>
      </section>
    </article>
  );
}
