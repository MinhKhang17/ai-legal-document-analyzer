import { UploadCloud } from 'lucide-react';
import { useCallback, useRef, useState, type DragEvent } from 'react';
import { Button } from '../common/Button';
import { cn } from '../../utils/cn';
import { useI18n } from '../../hooks/useI18n';
import { DOCUMENT_UPLOAD_ACCEPT, validateDocumentFiles } from '../../config/upload';

interface FileUploadZoneProps {
  onFileSelect?: (file: File | null) => void;
  onUpload?: (file: File) => void | Promise<void>;
  compact?: boolean;
  disabled?: boolean;
  uploadState?: 'idle' | 'uploading' | 'success' | 'failed';
  uploadProgress?: number;
  uploadError?: string;
  onRetry?: () => void;
  selectedFile?: File | null;
  onClearFile?: () => void;
}

export function FileUploadZone({
  onFileSelect,
  onUpload,
  compact = false,
  disabled = false,
  uploadState = 'idle',
  uploadProgress = 0,
  uploadError = '',
  onRetry,
  selectedFile,
  onClearFile,
}: FileUploadZoneProps) {
  const { t } = useI18n();
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);
  const [validationError, setValidationError] = useState('');
  const [localSelectedFile, setLocalSelectedFile] = useState<File | null>(null);

  const activeFile = selectedFile !== undefined ? selectedFile : localSelectedFile;

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
      if (selectedFile === undefined) {
        setLocalSelectedFile(file);
      }
      onFileSelect?.(file);
      onUpload?.(file);
    },
    [disabled, onFileSelect, onUpload, selectedFile, t],
  );

  const handleDrop = useCallback(
    (event: DragEvent<HTMLDivElement>) => {
      event.preventDefault();
      setDragging(false);
      handleFiles(event.dataTransfer.files);
    },
    [handleFiles],
  );

  const clearSelection = useCallback(() => {
    if (selectedFile === undefined) {
      setLocalSelectedFile(null);
    }
    setValidationError('');
    onClearFile?.();
    onFileSelect?.(null);
  }, [onClearFile, onFileSelect, selectedFile]);

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
      
      {activeFile && (
        <div className="mt-sm space-y-md" aria-live="polite">
          {uploadState === 'idle' && (
            <div className="flex flex-wrap items-center justify-center gap-sm text-sm">
              <span className="font-medium text-on-surface dark:text-slate-200">
                {activeFile.name} · {(activeFile.size / 1024).toFixed(1)} KB
              </span>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                disabled={disabled}
                onClick={(event) => {
                  event.stopPropagation();
                  clearSelection();
                }}
              >
                {t('actions.remove')}
              </Button>
            </div>
          )}

          {uploadState === 'uploading' && (
            <div className="w-full max-w-md mx-auto space-y-xs">
              <div className="flex items-center justify-between text-xs text-on-surface-variant dark:text-slate-400">
                <span className="truncate max-w-[250px] font-medium">{activeFile.name}</span>
                <span>{uploadProgress}%</span>
              </div>
              <div className="h-2 w-full rounded-full bg-surface-container-high dark:bg-slate-800 overflow-hidden">
                <div
                  className="h-full bg-primary transition-all duration-300 rounded-full dark:bg-inverse-primary"
                  style={{ width: `${uploadProgress}%` }}
                />
              </div>
            </div>
          )}

          {uploadState === 'failed' && (
            <div className="flex flex-col items-center gap-sm">
              <span className="text-sm font-semibold text-on-surface dark:text-slate-200">
                {activeFile.name}
              </span>
              <p className="text-sm text-error font-medium" role="alert">
                {uploadError}
              </p>
              <div className="flex justify-center gap-sm">
                {onRetry && (
                  <Button
                    type="button"
                    variant="primary"
                    size="sm"
                    onClick={(event) => {
                      event.stopPropagation();
                      onRetry();
                    }}
                  >
                    {t('upload.retry')}
                  </Button>
                )}
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  onClick={(event) => {
                    event.stopPropagation();
                    clearSelection();
                  }}
                >
                  {t('actions.remove')}
                </Button>
              </div>
            </div>
          )}
        </div>
      )}

      {!activeFile && (
        <Button
          type="button"
          className="mt-lg"
          variant="primary"
          disabled={disabled}
          onClick={() => inputRef.current?.click()}
        >
          {t('actions.selectFiles')}
        </Button>
      )}
    </div>
  );
}
