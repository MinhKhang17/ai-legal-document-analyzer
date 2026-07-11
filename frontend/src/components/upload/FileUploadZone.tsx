import { UploadCloud } from 'lucide-react';
import { useCallback, useRef, useState, type DragEvent } from 'react';
import { Button } from '../common/Button';
import { cn } from '../../utils/cn';
import { useI18n } from '../../hooks/useI18n';
import { DOCUMENT_UPLOAD_ACCEPT, validateDocumentFiles } from '../../config/upload';

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
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);
  const [validationError, setValidationError] = useState('');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);

  const handleFiles = useCallback(
    (files: FileList | null) => {
      if (disabled || !files?.length) return;

      const result = validateDocumentFiles(files);
      if (!result.valid) {
        setValidationError(t(result.messageKey));
        return;
      }
      setValidationError('');
      const file = files[0];
      setSelectedFile(file);
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
        dragging &&
          'border-primary bg-surface-container-low dark:border-inverse-primary dark:bg-slate-800',
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
      aria-disabled={disabled}
      onKeyDown={(event) => {
        if (!disabled && (event.key === 'Enter' || event.key === ' ')) inputRef.current?.click();
      }}
    >
      <input
        ref={inputRef}
        type="file"
        accept={DOCUMENT_UPLOAD_ACCEPT}
        className="hidden"
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
      {validationError && <p className="mt-sm text-sm text-error" role="alert">{validationError}</p>}
      {selectedFile && (
        <div className="mt-sm flex flex-wrap items-center justify-center gap-sm text-sm" aria-live="polite">
          <span>{selectedFile.name} · {(selectedFile.size / 1024).toFixed(1)} KB · {selectedFile.type || t('files.mimeUnknown')}</span>
          <Button type="button" variant="ghost" size="sm" disabled={disabled} onClick={(event) => { event.stopPropagation(); setSelectedFile(null); setValidationError(''); }}>{t('actions.remove')}</Button>
        </div>
      )}

      <Button
        type="button"
        className="mt-lg"
        variant="primary"
        disabled={disabled}
        onClick={() => inputRef.current?.click()}
      >
        {disabled ? t('common.uploading') : t('actions.selectFiles')}
      </Button>
    </div>
  );
}
