import { useCallback } from 'react';
import { useAppStore } from '../store/AppStore';
import { translate } from '../utils/i18n';

export const useI18n = () => {
  const { language, setLanguage } = useAppStore();
  const t = useCallback((key: string, params?: Record<string, string | number>) => translate(language, key, params), [language]);
  return { t, language, setLanguage };
};
