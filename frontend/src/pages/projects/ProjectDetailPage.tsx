import { Edit3, FileText, History, MessageSquareText, Share2, ShieldAlert, UsersRound } from 'lucide-react';
import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { DataTable, type DataTableColumn } from '../../components/common/DataTable';
import { PageHeader } from '../../components/common/PageHeader';
import { ProgressBar } from '../../components/common/ProgressBar';
import { RiskBadge } from '../../components/common/RiskBadge';
import { StatusBadge } from '../../components/common/StatusBadge';
import { Tabs } from '../../components/common/Tabs';
import { documents, projects, reports } from '../../api/mockData';
import { useI18n } from '../../hooks/useI18n';
import type { LegalDocument } from '../../types/document';

const tabs = [
  { id: 'overview', label: 'Overview' },
  { id: 'documents', label: 'Documents' },
  { id: 'reviews', label: 'Reviews' },
  { id: 'reports', label: 'Reports' },
  { id: 'activity', label: 'Activity' },
];

export function ProjectDetailPage() {
  const { id = 'phoenix' } = useParams();
  const { t } = useI18n();
  const [activeTab, setActiveTab] = useState('overview');
  const project = projects.find((item) => item.id === id) ?? projects[0];
  const projectDocuments = documents.filter((document) => document.projectId === project.id || project.id === 'phoenix');

  const columns: DataTableColumn<LegalDocument>[] = [
    {
      header: t('table.document'),
      cell: (document) => (
        <Link to={`/documents/${document.id}`} className="font-semibold text-primary hover:underline dark:text-inverse-primary">
          {document.name}
        </Link>
      ),
    },
    { header: 'Type', cell: (document) => document.type },
    { header: t('table.risk'), cell: (document) => <RiskBadge level={document.riskLevel} /> },
    { header: t('table.status'), cell: (document) => <StatusBadge status={document.status} /> },
    { header: t('table.updated'), cell: (document) => document.updatedAt },
  ];

  return (
    <div>
      <PageHeader
        title={project.name}
        subtitle={project.description}
        eyebrow={`${project.client} · ${project.jurisdiction}`}
        actions={
          <>
            <Button variant="secondary" leftIcon={<Share2 className="h-4 w-4" />}>{t('actions.export')}</Button>
            <Button leftIcon={<Edit3 className="h-4 w-4" />}>Edit Project</Button>
          </>
        }
      />

      <Card className="mb-xl">
        <div className="grid gap-lg lg:grid-cols-[1fr_320px]">
          <div>
            <div className="flex flex-wrap gap-xs">
              {project.tags.map((tag) => (
                <Badge key={tag} tone="blue">{tag}</Badge>
              ))}
            </div>
            <div className="mt-lg grid gap-md sm:grid-cols-3">
              <div className="rounded-lg bg-surface-container-low p-md dark:bg-slate-800">
                <p className="label-uppercase">Documents</p>
                <p className="mt-xs text-2xl font-bold">{project.documentCount}</p>
              </div>
              <div className="rounded-lg bg-surface-container-low p-md dark:bg-slate-800">
                <p className="label-uppercase">High risks</p>
                <p className="mt-xs text-2xl font-bold text-error">{project.highRiskCount}</p>
              </div>
              <div className="rounded-lg bg-surface-container-low p-md dark:bg-slate-800">
                <p className="label-uppercase">Owner</p>
                <p className="mt-xs text-lg font-bold">{project.owner}</p>
              </div>
            </div>
            <div className="mt-lg">
              <div className="mb-sm flex items-center justify-between text-sm">
                <span className="font-semibold">Review progress</span>
                <span>{project.progress}%</span>
              </div>
              <ProgressBar value={project.progress} />
            </div>
          </div>
          <Card tone="ai" className="m-0">
            <div className="flex items-center gap-sm">
              <ShieldAlert className="h-5 w-5 text-secondary dark:text-accent-gold" />
              <h2 className="text-title-lg font-semibold">AI Risk Intelligence Summary</h2>
            </div>
            <p className="mt-sm text-sm leading-6 text-on-surface-variant dark:text-slate-300">
              High-risk exposure concentrates in indemnity, limitation of liability, and change-of-control clauses. Review critical documents before external counsel handoff.
            </p>
            <Link to="/editor/risk-review">
              <Button className="mt-md" variant="gold">Open AI Review</Button>
            </Link>
          </Card>
        </div>
      </Card>

      <Tabs items={tabs} activeId={activeTab} onChange={setActiveTab} />

      <div className="mt-lg grid gap-gutter xl:grid-cols-[1fr_360px]">
        <div className="space-y-gutter">
          {(activeTab === 'overview' || activeTab === 'documents') && (
            <Card title="Critical Documents" actions={<Link className="text-sm font-semibold text-primary dark:text-inverse-primary" to="/documents">{t('actions.viewAll')}</Link>}>
              <DataTable columns={columns} data={projectDocuments.slice(0, 5)} getRowKey={(document) => document.id} />
            </Card>
          )}

          {activeTab === 'reviews' && (
            <Card title="AI Reviews">
              <div className="grid gap-md md:grid-cols-2">
                {projectDocuments.slice(0, 4).map((document) => (
                  <Link key={document.id} to="/editor/risk-review" className="rounded-xl border border-legal-border p-md hover:bg-surface-container-low dark:border-slate-700 dark:hover:bg-slate-800">
                    <div className="flex items-start justify-between gap-md">
                      <div>
                        <p className="font-semibold">{document.name}</p>
                        <p className="text-sm text-on-surface-variant dark:text-slate-400">{document.summary}</p>
                      </div>
                      <RiskBadge level={document.riskLevel} />
                    </div>
                  </Link>
                ))}
              </div>
            </Card>
          )}

          {activeTab === 'reports' && (
            <Card title="Reports">
              <div className="space-y-md">
                {reports.map((report) => (
                  <Link key={report.id} to={`/reports/${report.id}`} className="flex items-center justify-between rounded-lg border border-legal-border p-md hover:bg-surface-container-low dark:border-slate-700 dark:hover:bg-slate-800">
                    <div>
                      <p className="font-semibold">{report.title}</p>
                      <p className="text-sm text-on-surface-variant dark:text-slate-400">{report.createdAt}</p>
                    </div>
                    <StatusBadge status={report.status} />
                  </Link>
                ))}
              </div>
            </Card>
          )}

          {activeTab === 'activity' && (
            <Card title="Activity Timeline">
              <div className="space-y-md">
                {['AI risk scan completed on Service_Agreement_v4.pdf', 'Jane Doe generated executive report', 'Marcus Aurelius flagged liability cap increase'].map((item, index) => (
                  <div key={item} className="flex gap-md">
                    <div className="flex h-8 w-8 items-center justify-center rounded-full bg-surface-container-high text-primary dark:bg-slate-800 dark:text-inverse-primary">{index + 1}</div>
                    <div>
                      <p className="font-semibold">{item}</p>
                      <p className="text-sm text-on-surface-variant dark:text-slate-400">{index + 1} hours ago</p>
                    </div>
                  </div>
                ))}
              </div>
            </Card>
          )}
        </div>

        <aside className="space-y-gutter">
          <Card title="Team Members" actions={<UsersRound className="h-5 w-5 text-primary dark:text-inverse-primary" />}>
            <div className="space-y-md">
              {project.members.map((member) => (
                <div key={member.id} className="flex items-center gap-md">
                  <div className="flex h-10 w-10 items-center justify-center rounded-full bg-primary text-xs font-bold text-white">{member.initials}</div>
                  <div>
                    <p className="font-semibold">{member.name}</p>
                    <p className="text-sm text-on-surface-variant dark:text-slate-400">{member.role}</p>
                  </div>
                </div>
              ))}
            </div>
          </Card>

          <Card title="Activity Timeline" actions={<History className="h-5 w-5 text-secondary dark:text-accent-gold" />}>
            <div className="space-y-md">
              {['Document intake complete', 'Risk queue updated', 'External counsel memo attached'].map((activity) => (
                <div key={activity} className="border-l-2 border-primary pl-md dark:border-inverse-primary">
                  <p className="text-sm font-semibold">{activity}</p>
                  <p className="text-xs text-on-surface-variant dark:text-slate-400">Today</p>
                </div>
              ))}
            </div>
          </Card>

          <Card title="Project Shortcuts">
            <div className="grid gap-sm">
              <Link to="/documents" className="flex items-center gap-sm rounded-lg p-sm hover:bg-surface-container-low dark:hover:bg-slate-800">
                <FileText className="h-4 w-4 text-primary dark:text-inverse-primary" /> Documents
              </Link>
              <Link to="/chat" className="flex items-center gap-sm rounded-lg p-sm hover:bg-surface-container-low dark:hover:bg-slate-800">
                <MessageSquareText className="h-4 w-4 text-primary dark:text-inverse-primary" /> Ask Legal AI
              </Link>
            </div>
          </Card>
        </aside>
      </div>
    </div>
  );
}
