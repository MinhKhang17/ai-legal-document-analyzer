import { X } from 'lucide-react';
import { type ReactNode, useEffect } from 'react';
import { Button } from './Button';
import { cn } from '../../utils/cn';

interface ModalProps {
  open: boolean;
  title: string;
  children: ReactNode;
  onClose: () => void;
  footer?: ReactNode;
  size?: 'sm' | 'md' | 'lg';
}

const sizeClasses = {
  sm: 'max-w-md',
  md: 'max-w-2xl',
  lg: 'max-w-4xl',
};

export function Modal({ open, title, children, onClose, footer, size = 'md' }: ModalProps) {
  useEffect(() => {
    if (!open) return undefined;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-[90] flex items-center justify-center bg-slate-950/50 p-md" role="dialog" aria-modal="true" aria-label={title}>
      <div className={cn('max-h-[90vh] w-full overflow-hidden rounded-xl border border-legal-border bg-white shadow-raised dark:border-slate-700 dark:bg-slate-900', sizeClasses[size])}>
        <div className="flex items-center justify-between border-b border-legal-border px-lg py-md dark:border-slate-700">
          <h2 className="text-title-lg font-semibold text-on-surface dark:text-slate-100">{title}</h2>
          <Button variant="ghost" size="icon" aria-label="Close modal" onClick={onClose}>
            <X className="h-5 w-5" />
          </Button>
        </div>
        <div className="max-h-[65vh] overflow-y-auto p-lg">{children}</div>
        {footer && <div className="border-t border-legal-border px-lg py-md dark:border-slate-700">{footer}</div>}
      </div>
    </div>
  );
}
