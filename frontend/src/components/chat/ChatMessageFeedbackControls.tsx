import { ThumbsDown, ThumbsUp } from 'lucide-react';
import { useState } from 'react';
import { deleteChatMessageFeedback, submitChatMessageFeedback } from '../../api/chatApi';
import { getAccessToken } from '../../services/authSession';
import type { AiFeedbackType } from '../../types/chat';
import { cn } from '../../utils/cn';

export function ChatMessageFeedbackControls({ messageId, language }: { messageId: string; language: 'en' | 'vi' }) {
  const [selected, setSelected] = useState<AiFeedbackType | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const toggle = async (next: AiFeedbackType) => {
    setSaving(true);
    setError('');
    try {
      if (selected === next) {
        await deleteChatMessageFeedback(getAccessToken() ?? '', messageId);
        setSelected(null);
      } else {
        await submitChatMessageFeedback(getAccessToken() ?? '', messageId, { feedbackType: next });
        setSelected(next);
      }
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : (language === 'vi' ? 'Không thể lưu đánh giá.' : 'Could not save feedback.'));
    } finally {
      setSaving(false);
    }
  };

  return <div className="mt-sm border-t border-legal-border pt-xs dark:border-slate-700">
    <div className="flex items-center gap-1">
      {(['LIKE', 'DISLIKE'] as const).map((type) => {
        const Icon = type === 'LIKE' ? ThumbsUp : ThumbsDown;
        const label = type === 'LIKE' ? (language === 'vi' ? 'Hữu ích' : 'Helpful') : (language === 'vi' ? 'Chưa hữu ích' : 'Not helpful');
        return <button key={type} type="button" aria-label={label} title={label} disabled={saving}
          aria-pressed={selected === type} onClick={() => void toggle(type)}
          className={cn('rounded-md p-1.5 text-on-surface-variant transition hover:bg-primary/10 hover:text-primary disabled:opacity-50', selected === type && 'bg-primary/10 text-primary')}>
          <Icon className={cn('h-3.5 w-3.5', selected === type && 'fill-current')} />
        </button>;
      })}
    </div>
    {error && <p className="mt-1 text-[11px] text-error">{error}</p>}
  </div>;
}
