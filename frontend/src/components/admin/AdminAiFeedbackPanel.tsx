import { useCallback, useEffect, useState } from 'react';
import { getAdminAiFeedback, getAdminAiFeedbackSummary } from '../../api/chatApi';
import { getAccessToken } from '../../services/authSession';
import type { AiFeedbackSummary, AiFeedbackType, ChatMessageFeedback, ChatMode } from '../../types/chat';
import { formatDisplayDate } from '../../utils/format';
import { Badge } from '../common/Badge';
import { Card } from '../common/Card';
import { DataTable, type DataTableColumn } from '../common/DataTable';
import { useI18n } from '../../hooks/useI18n';

export function AdminAiFeedbackPanel() {
  const { t } = useI18n();
  const [items, setItems] = useState<ChatMessageFeedback[]>([]);
  const [summary, setSummary] = useState<AiFeedbackSummary>({ total: 0, likes: 0, dislikes: 0, likeRate: 0, dislikeRate: 0 });
  const [feedbackType, setFeedbackType] = useState<AiFeedbackType | ''>('');
  const [resolvedMode, setResolvedMode] = useState<Exclude<ChatMode, 'AUTO'> | ''>('');
  const [riskLevel, setRiskLevel] = useState('');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [keyword, setKeyword] = useState('');
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setError('');
    try {
      const token = getAccessToken() ?? '';
      const [page, totals] = await Promise.all([
        getAdminAiFeedback(token, {
          feedbackType: feedbackType || undefined,
          resolvedMode: resolvedMode || undefined,
          riskLevel: riskLevel || undefined,
          fromDate: fromDate || undefined,
          toDate: toDate || undefined,
          keyword: keyword.trim() || undefined,
          size: 50,
        }),
        getAdminAiFeedbackSummary(token),
      ]);
      setItems(page.items ?? []);
      setSummary(totals);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : t('feedback.admin.loadError'));
    }
  }, [feedbackType, resolvedMode, riskLevel, fromDate, toDate, keyword, t]);

  useEffect(() => { void load(); }, [load]);

  const columns: DataTableColumn<ChatMessageFeedback>[] = [
    { header: t('feedback.admin.user'), cell: (item) => item.userEmail || item.submittedByName || '-' },
    { header: t('feedback.admin.feedback'), cell: (item) => <Badge tone={(item.feedbackType === 'LIKE' || item.rating === 'THUMBS_UP') ? 'green' : 'red'}>{item.feedbackType || item.rating}</Badge> },
    { header: t('feedback.admin.question'), cell: (item) => <p className="max-w-xs line-clamp-3">{item.questionSnippet || '-'}</p> },
    { header: t('feedback.admin.answer'), cell: (item) => <p className="max-w-md line-clamp-3">{item.answerSnippet || item.messageContent}</p> },
    { header: t('feedback.admin.modeRisk'), cell: (item) => <span className="text-xs">{item.resolvedMode || '-'} / {item.riskLevel || '-'}</span> },
    { header: t('feedback.admin.sources'), cell: (item) => item.sourceCount ?? 0 },
    { header: t('feedback.admin.created'), cell: (item) => formatDisplayDate(item.createdAt, '-') },
  ];

  return <section className="space-y-gutter">
    <div className="grid gap-md sm:grid-cols-2 xl:grid-cols-4">
      <Card title={t('feedback.admin.total')}><p className="text-3xl font-bold">{summary.total}</p></Card>
      <Card title={t('feedback.admin.likes')}><p className="text-3xl font-bold text-success">{summary.likes}</p></Card>
      <Card title={t('feedback.admin.dislikes')}><p className="text-3xl font-bold text-error">{summary.dislikes}</p></Card>
      <Card title={t('feedback.admin.likeRate')}><p className="text-3xl font-bold">{summary.likeRate.toFixed(1)}%</p></Card>
    </div>
    <Card title={t('feedback.admin.title')} actions={<Badge tone="blue">{items.length}</Badge>}>
      <div className="mb-md grid gap-sm md:grid-cols-3 xl:grid-cols-6">
        <select className="form-field" value={feedbackType} onChange={(event) => setFeedbackType(event.target.value as AiFeedbackType | '')}><option value="">{t('feedback.admin.likeDislike')}</option><option value="LIKE">LIKE</option><option value="DISLIKE">DISLIKE</option></select>
        <select className="form-field" value={resolvedMode} onChange={(event) => setResolvedMode(event.target.value as Exclude<ChatMode, 'AUTO'> | '')}><option value="">{t('feedback.admin.mode')}</option><option value="LEGAL_QA">LEGAL_QA</option><option value="DOCUMENT_ANALYSIS">DOCUMENT_ANALYSIS</option></select>
        <select className="form-field" value={riskLevel} onChange={(event) => setRiskLevel(event.target.value)}>
          <option value="">{t('feedback.admin.riskLevel')}</option>
          {['NONE', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL', 'UNKNOWN'].map((level) => <option key={level} value={level}>{level}</option>)}
        </select>
        <input className="form-field" type="date" value={fromDate} onChange={(event) => setFromDate(event.target.value)} />
        <input className="form-field" type="date" value={toDate} onChange={(event) => setToDate(event.target.value)} />
        <input className="form-field" placeholder={t('feedback.admin.searchPlaceholder')} value={keyword} onChange={(event) => setKeyword(event.target.value)} />
      </div>
      {error && <p className="mb-sm text-sm text-error">{error}</p>}
      <DataTable columns={columns} data={items} getRowKey={(item) => item.id} emptyMessage={t('feedback.admin.empty')} />
    </Card>
  </section>;
}
