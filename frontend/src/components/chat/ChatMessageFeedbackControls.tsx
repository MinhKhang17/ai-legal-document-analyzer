import { ThumbsDown, ThumbsUp } from 'lucide-react';
import { useState } from 'react';
import { submitChatMessageFeedback } from '../../api/chatApi';
import { getAccessToken } from '../../services/authSession';
import type { ChatFeedbackReason, ChatFeedbackRating } from '../../types/chat';
import { Button } from '../common/Button';

const reasons: ChatFeedbackReason[] = ['INCORRECT', 'WRONG_CITATION', 'INCOMPLETE', 'NOT_HELPFUL', 'POOR_PHRASING', 'OTHER'];

export function ChatMessageFeedbackControls({ messageId, language }: { messageId: string; language: 'en' | 'vi' }) {
  const [rating, setRating] = useState<ChatFeedbackRating | null>(null);
  const [selectedReasons, setSelectedReasons] = useState<ChatFeedbackReason[]>([]);
  const [comment, setComment] = useState('');
  const [saving, setSaving] = useState(false);
  const [submitted, setSubmitted] = useState(false);

  const submit = async (nextRating: ChatFeedbackRating) => {
    if (nextRating === 'THUMBS_DOWN' && rating !== 'THUMBS_DOWN') { setRating(nextRating); return; }
    setSaving(true);
    try {
      await submitChatMessageFeedback(getAccessToken() ?? '', messageId, { rating: nextRating, reasons: nextRating === 'THUMBS_DOWN' ? selectedReasons : [], comment: comment.trim() || undefined });
      setRating(nextRating); setSubmitted(true);
    } finally { setSaving(false); }
  };

  if (submitted) return <p className="mt-sm text-xs font-semibold text-emerald-700 dark:text-emerald-300">{language === 'vi' ? 'Cảm ơn bạn đã đánh giá.' : 'Thanks for your feedback.'}</p>;
  return <div className="mt-sm border-t border-legal-border pt-sm dark:border-slate-700">
    <div className="flex gap-xs"><Button size="sm" variant="ghost" aria-label="Helpful" onClick={() => void submit('THUMBS_UP')} disabled={saving}><ThumbsUp className="h-4 w-4" /></Button><Button size="sm" variant="ghost" aria-label="Not helpful" onClick={() => void submit('THUMBS_DOWN')} disabled={saving}><ThumbsDown className="h-4 w-4" /></Button></div>
    {rating === 'THUMBS_DOWN' && <div className="mt-sm space-y-sm"><p className="text-xs font-semibold">{language === 'vi' ? 'Lý do (có thể chọn nhiều)' : 'Reason (select all that apply)'}</p><div className="flex flex-wrap gap-xs">{reasons.map((reason) => <button type="button" key={reason} onClick={() => setSelectedReasons((current) => current.includes(reason) ? current.filter((item) => item !== reason) : [...current, reason])} className={`rounded-full border px-2 py-1 text-[11px] ${selectedReasons.includes(reason) ? 'border-primary bg-primary text-white' : 'border-legal-border'}`}>{reason}</button>)}</div><textarea className="form-field min-h-20 text-xs" maxLength={2000} placeholder={language === 'vi' ? 'Góp ý thêm (không bắt buộc)' : 'Optional comment'} value={comment} onChange={(e) => setComment(e.target.value)} /><Button size="sm" onClick={() => void submit('THUMBS_DOWN')} disabled={saving}>{language === 'vi' ? 'Gửi đánh giá' : 'Submit feedback'}</Button></div>}
  </div>;
}
