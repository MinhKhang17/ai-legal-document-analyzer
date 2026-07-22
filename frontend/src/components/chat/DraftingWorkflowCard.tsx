import { useEffect, useMemo, useState } from 'react';
import { ClipboardCheck, ExternalLink, PencilLine } from 'lucide-react';
import { Button } from '../common/Button';
import type { ChatMessage, DraftingRequestFields } from '../../types/chat';
import { copyDraftingPrompt, openChatGPT } from '../../utils/draftingActions';

const CONTRACT_OPTIONS = [
  ['EMPLOYMENT_CONTRACT', 'Hợp đồng lao động'],
  ['HOUSE_RENTAL_CONTRACT', 'Hợp đồng thuê nhà'],
  ['LOAN_CONTRACT', 'Hợp đồng vay tiền'],
  ['SALE_OF_GOODS_CONTRACT', 'Hợp đồng mua bán hàng hóa'],
  ['SERVICE_CONTRACT', 'Hợp đồng dịch vụ'],
  ['OTHER', 'Loại khác'],
] as const;

const contractLabel = (value?: string | null) =>
  CONTRACT_OPTIONS.find(([key]) => key === value)?.[1] ?? value ?? 'Chưa xác định';

type DraftingSubmission = DraftingRequestFields & { message: string };

interface Props {
  message: ChatMessage;
  disabled?: boolean;
  onSubmit: (submission: DraftingSubmission) => void;
}

export function DraftingWorkflowCard({ message, disabled = false, onSubmit }: Props) {
  const [values, setValues] = useState<Record<string, string>>(message.providedInformation ?? {});
  const [editablePrompt, setEditablePrompt] = useState(message.draftingPrompt ?? '');
  const [editingInformation, setEditingInformation] = useState(false);

  useEffect(() => {
    setValues(message.providedInformation ?? {});
    setEditablePrompt(message.draftingPrompt ?? '');
    setEditingInformation(false);
  }, [message.id, message.providedInformation, message.draftingPrompt]);

  const questions = message.questions ?? [];
  const labelByKey = useMemo(
    () => new Map(questions.map((question) => [question.key, question.label])),
    [questions],
  );
  const providedEntries = useMemo(
    () => Object.entries(message.providedInformation ?? {}).filter(([, value]) => value.trim()),
    [message.providedInformation],
  );
  const originalRequirement = message.draftingOriginalRequirement ?? '';

  const submitInformation = (action: 'ANSWER_QUESTIONS' | 'GENERATE_PROMPT' | 'CONTINUE_WITH_PLACEHOLDERS') => {
    const normalized = Object.fromEntries(
      Object.entries(values).map(([key, value]) => [key, value.trim()]).filter(([, value]) => value),
    );
    onSubmit({
      message: action === 'ANSWER_QUESTIONS'
        ? `Thông tin soạn ${contractLabel(message.contractType)} đã được cập nhật.`
        : `Tạo prompt cho ${contractLabel(message.contractType)} với thông tin hiện có.`,
      draftingAction: action,
      draftingContractType: message.contractType ?? undefined,
      draftingInformation: normalized,
      draftingOriginalRequirement: originalRequirement,
    });
  };

  if (message.draftingStatus === 'NEED_CONTRACT_TYPE') {
    return (
      <div className="mt-md rounded-lg border border-legal-border bg-surface-container-low p-md dark:border-slate-700 dark:bg-slate-800">
        <p className="text-sm font-semibold">Chọn loại hợp đồng</p>
        <div className="mt-sm grid gap-sm sm:grid-cols-2">
          {CONTRACT_OPTIONS.map(([value, label]) => (
            <Button
              key={value}
              variant="secondary"
              size="sm"
              disabled={disabled}
              onClick={() => onSubmit({
                message: `Tôi muốn soạn ${label.toLowerCase()}.`,
                draftingAction: 'SELECT_CONTRACT_TYPE',
                draftingContractType: value,
                draftingInformation: {},
                draftingOriginalRequirement: originalRequirement,
              })}
            >
              {label}
            </Button>
          ))}
        </div>
      </div>
    );
  }

  const showInformationForm = editingInformation
    || message.draftingStatus === 'NEED_MORE_INFORMATION'
    || message.draftingStatus === 'READY_TO_GENERATE_PROMPT';

  if (showInformationForm) {
    return (
      <div className="mt-md space-y-md rounded-lg border border-legal-border bg-surface-container-low p-md dark:border-slate-700 dark:bg-slate-800">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-on-surface-variant">Loại hợp đồng</p>
          <p className="font-semibold">{contractLabel(message.contractType)}</p>
        </div>
        {questions.length > 0 && (
          <div className="grid gap-sm sm:grid-cols-2">
            {questions.map((question) => (
              <label key={question.key} className="text-xs font-medium text-on-surface-variant">
                {question.label}
                <input
                  className="form-field mt-1"
                  value={values[question.key] ?? ''}
                  placeholder={question.placeholder ?? 'Có thể để trống'}
                  disabled={disabled}
                  onChange={(event) => setValues((current) => ({ ...current, [question.key]: event.target.value }))}
                />
              </label>
            ))}
          </div>
        )}
        <p className="text-xs text-on-surface-variant">
          Bạn có thể bỏ qua trường chưa biết; hệ thống sẽ dùng placeholder nhất quán.
        </p>
        <div className="flex flex-wrap gap-sm">
          <Button size="sm" disabled={disabled} onClick={() => submitInformation('GENERATE_PROMPT')}>
            Tạo prompt
          </Button>
          <Button variant="secondary" size="sm" disabled={disabled} onClick={() => submitInformation('CONTINUE_WITH_PLACEHOLDERS')}>
            Tiếp tục với placeholder
          </Button>
          {message.draftingStatus !== 'PROMPT_GENERATED' && (
            <Button variant="ghost" size="sm" disabled={disabled} onClick={() => submitInformation('ANSWER_QUESTIONS')}>
              Lưu để bổ sung sau
            </Button>
          )}
        </div>
      </div>
    );
  }

  if (message.draftingStatus !== 'PROMPT_GENERATED' || !message.draftingPrompt) return null;

  return (
    <div className="mt-md space-y-md rounded-lg border border-legal-border bg-surface-container-low p-md dark:border-slate-700 dark:bg-slate-800">
      <div className="grid gap-sm sm:grid-cols-2">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-on-surface-variant">Loại hợp đồng</p>
          <p className="font-semibold">{contractLabel(message.contractType)}</p>
        </div>
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-on-surface-variant">Thông tin còn thiếu</p>
          <p className="text-xs text-on-surface-variant">
            {message.draftingMissingInformation?.length
              ? message.draftingMissingInformation.join('; ')
              : 'Không có trong biểu mẫu hiện tại'}
          </p>
        </div>
      </div>
      {providedEntries.length > 0 && (
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-on-surface-variant">Thông tin đã cung cấp</p>
          <ul className="mt-1 list-disc pl-5 text-xs text-on-surface-variant">
            {providedEntries.map(([key, value]) => <li key={key}>{labelByKey.get(key) ?? key}: {value}</li>)}
          </ul>
        </div>
      )}
      <label className="block text-xs font-semibold uppercase tracking-wide text-on-surface-variant">
        Drafting prompt có thể chỉnh sửa
        <textarea
          className="form-field mt-sm min-h-80 whitespace-pre-wrap font-mono text-xs"
          value={editablePrompt}
          onChange={(event) => setEditablePrompt(event.target.value)}
        />
      </label>
      {message.privacyWarning && <p className="text-xs text-amber-700 dark:text-amber-300">{message.privacyWarning}</p>}
      <div className="flex flex-wrap gap-sm">
        <Button
          variant="secondary"
          size="sm"
          leftIcon={<ClipboardCheck className="h-4 w-4" />}
          disabled={!editablePrompt.trim()}
          onClick={() => void copyDraftingPrompt(editablePrompt)}
        >
          Sao chép prompt
        </Button>
        <Button
          variant="secondary"
          size="sm"
          leftIcon={<ExternalLink className="h-4 w-4" />}
          onClick={() => openChatGPT()}
        >
          Mở ChatGPT
        </Button>
        <Button variant="ghost" size="sm" leftIcon={<PencilLine className="h-4 w-4" />} onClick={() => setEditingInformation(true)}>
          Quay lại sửa thông tin
        </Button>
      </div>
    </div>
  );
}
