import { ChevronDown } from 'lucide-react';
import { type ReactNode } from 'react';
import { useDisclosure } from '../../hooks/useDisclosure';
import { cn } from '../../utils/cn';

interface DropdownProps {
  label: ReactNode;
  children: ReactNode;
  align?: 'left' | 'right';
  className?: string;
}

export function Dropdown({ label, children, align = 'right', className }: DropdownProps) {
  const { open, onToggle, onClose } = useDisclosure(false);
  return (
    <div className={cn('relative', className)} onBlur={(event) => {
      if (!event.currentTarget.contains(event.relatedTarget)) onClose();
    }}>
      <button
        type="button"
        className="inline-flex items-center gap-xs rounded-lg border border-outline-variant bg-white px-sm py-xs text-sm text-on-surface-variant hover:bg-surface-container-low dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300 dark:hover:bg-slate-800"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={onToggle}
      >
        {label}
        <ChevronDown className="h-4 w-4" aria-hidden="true" />
      </button>
      {open && (
        <div
          className={cn(
            'absolute top-full z-50 mt-xs min-w-48 rounded-xl border border-legal-border bg-white p-xs shadow-raised dark:border-slate-700 dark:bg-slate-900',
            align === 'right' ? 'right-0' : 'left-0',
          )}
          role="menu"
        >
          {children}
        </div>
      )}
    </div>
  );
}
