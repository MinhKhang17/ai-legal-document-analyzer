import { ThumbsDown, ThumbsUp } from 'lucide-react';
import { useState } from 'react';
import { submitChatMessageFeedback } from '../../api/chatApi';
import { useI18n } from '../../hooks/useI18n';
import { getAccessToken } from '../../services/authSession';
import type { ChatFeedbackReason, ChatFeedbackRating } from '../../types/chat';
import { Button } from '../common/Button';

const reasons: ChatFeedbackReason[] = [
  'INCORRECT',
  'WRONG_CITATION',
  'INCOMPLETE',
  'NOT_HELPFUL',
  'POOR_PHRASING',
  'OTHER',
];

interface ChatMessageFeedbackControlsProps {
  messageId: string;
}

export function ChatMessageFeedbackControls({ messageId }: ChatMessageFeedbackControlsProps) {
  const { t } = useI18n();
  const [rating, setRating] = useState<ChatFeedbackRating | null>(null);
  const [selectedReasons, setSelectedReasons] = useState<ChatFeedbackReason[]>([]);
  const [comment, setComment] = useState('');
  const [saving, setSaving] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState('');

  const submit = async (nextRating: ChatFeedbackRating) => {
    if (nextRating === 'THUMBS_DOWN' && rating !== 'THUMBS_DOWN') {
      setRating(nextRating);
      return;
    }

    setSaving(true);
    setError('');
    try {
      await submitChatMessageFeedback(getAccessToken() ?? '', messageId, {
        rating: nextRating,
        reasons: nextRating === 'THUMBS_DOWN' ? selectedReasons : [],
        comment: comment.trim() || undefined,
      });
      setRating(nextRating);
      setSubmitted(true);
    } catch (reason) {
      setError(reason instanceof Error
        ? reason.message
        : t('chat.feedback.saveError'));
    } finally {
      setSaving(false);
    }
  };

  if (submitted) {
    return (
      <p className="mt-sm text-xs font-semibold text-emerald-700 dark:text-emerald-300">
        {t('chat.feedback.thanks')}
      </p>
    );
  }

  return (
    <div className="mt-sm border-t border-legal-border pt-sm dark:border-slate-700">
      <div className="flex gap-xs">
        <Button size="sm" variant="ghost" aria-label={t('chat.feedback.helpful')} onClick={() => void submit('THUMBS_UP')} disabled={saving}>
          <ThumbsUp className="h-4 w-4" />
        </Button>
        <Button size="sm" variant="ghost" aria-label={t('chat.feedback.notHelpful')} onClick={() => void submit('THUMBS_DOWN')} disabled={saving}>
          <ThumbsDown className="h-4 w-4" />
        </Button>
      </div>

      {rating === 'THUMBS_DOWN' && (
        <div className="mt-sm space-y-sm">
          <p className="text-xs font-semibold">{t('chat.feedback.reasonPrompt')}</p>
          <div className="flex flex-wrap gap-xs">
            {reasons.map((reason) => (
              <button
                type="button"
                key={reason}
                onClick={() => setSelectedReasons((current) => current.includes(reason)
                  ? current.filter((item) => item !== reason)
                  : [...current, reason])}
                className={`rounded-full border px-2 py-1 text-[11px] ${selectedReasons.includes(reason) ? 'border-primary bg-primary text-white' : 'border-legal-border'}`}
              >
                {t(`chat.feedback.reason.${reason}`)}
              </button>
            ))}
          </div>
          <textarea
            className="form-field min-h-20 text-xs"
            maxLength={2000}
            placeholder={t('chat.feedback.commentPlaceholder')}
            value={comment}
            onChange={(event) => setComment(event.target.value)}
          />
          <Button size="sm" onClick={() => void submit('THUMBS_DOWN')} disabled={saving}>
            {t('chat.feedback.submit')}
          </Button>
        </div>
      )}

      {error && <p className="mt-1 text-[11px] text-error">{error}</p>}
    </div>
  );
}
