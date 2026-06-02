import { Check, Monitor, Moon, Sun } from 'lucide-react';
import { useEffect, useRef } from 'react';
import { useAppStore, type ThemeMode } from '../../store/AppStore';
import { useDisclosure } from '../../hooks/useDisclosure';
import { cn } from '../../utils/cn';
import { useI18n } from '../../hooks/useI18n';

const themeOptions: Array<{ mode: ThemeMode; icon: typeof Sun; labelKey: string }> = [
  { mode: 'light', icon: Sun, labelKey: 'theme.light' },
  { mode: 'dark', icon: Moon, labelKey: 'theme.dark' },
  { mode: 'system', icon: Monitor, labelKey: 'theme.system' },
];

export function ThemeToggle() {
  const { theme, setTheme } = useAppStore();
  const { t } = useI18n();
  const { open, setOpen, onToggle, onClose } = useDisclosure(false);
  const wrapperRef = useRef<HTMLDivElement>(null);
  const closeTimer = useRef<number | null>(null);
  const activeOption = themeOptions.find((option) => option.mode === theme) ?? themeOptions[0];
  const ActiveIcon = activeOption.icon;

  useEffect(() => () => {
    if (closeTimer.current) {
      window.clearTimeout(closeTimer.current);
    }
  }, []);

  const clearCloseTimer = () => {
    if (closeTimer.current) {
      window.clearTimeout(closeTimer.current);
      closeTimer.current = null;
    }
  };

  const scheduleClose = () => {
    clearCloseTimer();
    closeTimer.current = window.setTimeout(() => {
      onClose();
      closeTimer.current = null;
    }, 120);
  };

  return (
    <div
      ref={wrapperRef}
      className="relative"
      aria-label={t('theme.mode')}
      onMouseEnter={() => {
        clearCloseTimer();
        setOpen(true);
      }}
      onMouseLeave={scheduleClose}
      onFocusCapture={() => setOpen(true)}
      onBlurCapture={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget as Node | null)) {
          clearCloseTimer();
          onClose();
        }
      }}
      onKeyDown={(event) => {
        if (event.key === 'Escape') {
          event.preventDefault();
          clearCloseTimer();
          onClose();
          wrapperRef.current?.querySelector<HTMLButtonElement>('[data-theme-trigger]')?.focus();
        }
      }}
    >
      <button
        type="button"
        data-theme-trigger
        className="flex h-10 w-10 items-center justify-center rounded-xl border border-outline-variant bg-surface-container-low text-on-surface-variant transition hover:text-primary dark:border-slate-700 dark:bg-slate-900 dark:text-slate-400 dark:hover:text-inverse-primary"
        aria-label={t(activeOption.labelKey)}
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={onToggle}
      >
        <ActiveIcon className="h-4 w-4" aria-hidden="true" />
      </button>

      {open && (
        <div
          className="absolute right-0 top-full z-50 mt-1 min-w-[150px] rounded-xl border border-legal-border bg-white p-1 shadow-raised dark:border-slate-700 dark:bg-slate-900"
          role="menu"
          onMouseEnter={() => {
            clearCloseTimer();
            setOpen(true);
          }}
        >
          {themeOptions.map((option) => {
            const Icon = option.icon;
            const active = theme === option.mode;
            return (
              <button
                key={option.mode}
                type="button"
                role="menuitem"
                className={cn(
                  'flex w-full items-center justify-between rounded-lg px-2.5 py-1.5 text-left text-sm transition',
                  active
                    ? 'bg-surface-container-low text-primary dark:bg-slate-800 dark:text-inverse-primary'
                    : 'text-on-surface-variant hover:bg-surface-container-low hover:text-primary dark:text-slate-300 dark:hover:bg-slate-800 dark:hover:text-inverse-primary',
                )}
                onClick={() => {
                  clearCloseTimer();
                  setTheme(option.mode);
                  onClose();
                }}
              >
                <span className="flex items-center gap-2">
                  <Icon className="h-4 w-4" aria-hidden="true" />
                  {t(option.labelKey)}
                </span>
                {active && <Check className="h-4 w-4" aria-hidden="true" />}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
