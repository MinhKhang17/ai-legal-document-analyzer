import { UploadCloud } from 'lucide-react';
import { useCallback, useId, useState, type DragEvent } from 'react';
import { Button } from '../common/Button';
import { cn } from '../../utils/cn';
import { useI18n } from '../../hooks/useI18n';

interface FileUploadZoneProps {
  onUpload?: (file: File) => void | Promise<void>;
  compact?: boolean;
  disabled?: boolean;
}

export function FileUploadZone({
  onUpload,
  compact = false,
  disabled = false,
}: FileUploadZoneProps) {
  const { t } = useI18n();
  const inputId = useId();
  const [dragging, setDragging] = useState(false);

  const handleFiles = useCallback(
    (files: FileList | null) => {
      if (disabled || !files?.length) return;

      const file = files[0];
      onUpload?.(file);
    },
    [disabled, onUpload],
  );

  const handleDrop = useCallback(
    (event: DragEvent<HTMLDivElement>) => {
      event.preventDefault();
      setDragging(false);
      handleFiles(event.dataTransfer.files);
    },
    [handleFiles],
  );

  return (
    <div
      className={cn(
        'rounded-xl border-2 border-dashed border-outline-variant bg-white p-xl text-center transition dark:border-slate-700 dark:bg-slate-900',
        dragging && 'border-primary bg-surface-container-low dark:border-inverse-primary dark:bg-slate-800',
        compact && 'p-lg',
        disabled && 'cursor-not-allowed opacity-60',
      )}
      onDragOver={(event) => {
        event.preventDefault();
        if (!disabled) setDragging(true);
      }}
      onDragLeave={() => setDragging(false)}
      onDrop={handleDrop}
      role="button"
      tabIndex={0}
      aria-label={t('documents.dropzoneTitle')}
    >
      <input
        id={inputId}
        type="file"
        accept=".pdf,.doc,.docx,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        hidden
        disabled={disabled}
        onChange={(event) => {
          handleFiles(event.target.files);
          event.target.value = '';
        }}
      />

      <div className="mx-auto mb-md flex h-14 w-14 items-center justify-center rounded-xl bg-surface-container-high text-primary dark:bg-slate-800 dark:text-inverse-primary">
        <UploadCloud className="h-7 w-7" aria-hidden="true" />
      </div>

      <h3 className="text-title-lg font-semibold text-on-surface dark:text-slate-100">
        {t('documents.dropzoneTitle')}
      </h3>

      <p className="mt-xs text-sm text-on-surface-variant dark:text-slate-400">
        {t('documents.dropzoneHint')}
      </p>

      <label htmlFor={inputId} className="inline-block">
        <Button className="mt-lg" variant="primary" disabled={disabled}>
          {disabled ? 'Đang tải lên...' : t('actions.selectFiles')}
        </Button>
      </label>
    </div>
  );
}