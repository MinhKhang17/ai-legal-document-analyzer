import { UploadCloud } from 'lucide-react';
import { useCallback, useState, type DragEvent } from 'react';
import { Button } from '../common/Button';
import { cn } from '../../utils/cn';
import { useI18n } from '../../hooks/useI18n';

interface FileUploadZoneProps {
  onFakeUpload?: () => void;
  compact?: boolean;
}

export function FileUploadZone({ onFakeUpload, compact = false }: FileUploadZoneProps) {
  const { t } = useI18n();
  const [dragging, setDragging] = useState(false);

  const handleDrop = useCallback(
    (event: DragEvent<HTMLDivElement>) => {
      event.preventDefault();
      setDragging(false);
      onFakeUpload?.();
    },
    [onFakeUpload],
  );

  return (
    <div
      className={cn(
        'rounded-xl border-2 border-dashed border-outline-variant bg-white p-xl text-center transition dark:border-slate-700 dark:bg-slate-900',
        dragging && 'border-primary bg-surface-container-low dark:border-inverse-primary dark:bg-slate-800',
        compact && 'p-lg',
      )}
      onDragOver={(event) => {
        event.preventDefault();
        setDragging(true);
      }}
      onDragLeave={() => setDragging(false)}
      onDrop={handleDrop}
      role="button"
      tabIndex={0}
      aria-label={t('documents.dropzoneTitle')}
    >
      <div className="mx-auto mb-md flex h-14 w-14 items-center justify-center rounded-xl bg-surface-container-high text-primary dark:bg-slate-800 dark:text-inverse-primary">
        <UploadCloud className="h-7 w-7" aria-hidden="true" />
      </div>
      <h3 className="text-title-lg font-semibold text-on-surface dark:text-slate-100">{t('documents.dropzoneTitle')}</h3>
      <p className="mt-xs text-sm text-on-surface-variant dark:text-slate-400">{t('documents.dropzoneHint')}</p>
      <Button className="mt-lg" variant="primary" onClick={onFakeUpload}>
        {t('actions.selectFiles')}
      </Button>
    </div>
  );
}
