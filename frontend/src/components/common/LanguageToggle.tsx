import { useAppStore, type Language } from '../../store/AppStore';
import { useI18n } from '../../hooks/useI18n';
import { cn } from '../../utils/cn';

const options: Language[] = ['en', 'vi'];

export function LanguageToggle() {
  const { t } = useI18n();
  const { language, setLanguage } = useAppStore();
  const baseButtonClass =
    'inline-flex h-9 w-9 items-center justify-center rounded-full font-sans text-sm font-semibold leading-none transition';

  return (
    <div
      className="inline-flex items-center rounded-full border border-outline-variant bg-surface-container-low p-1 dark:border-slate-700 dark:bg-slate-900"
      aria-label={t('language.toggle')}
    >
      {options.map((option) => {
        const active = language === option;
        return (
          <button
            key={option}
            type="button"
            className={cn(
              baseButtonClass,
              active ? 'bg-primary text-white shadow-sm dark:bg-inverse-primary dark:text-slate-950' : 'text-on-surface-variant hover:text-primary dark:text-slate-400 dark:hover:text-inverse-primary',
            )}
            aria-pressed={active}
            onClick={() => setLanguage(option)}
          >
            {option.toUpperCase()}
          </button>
        );
      })}
    </div>
  );
}
