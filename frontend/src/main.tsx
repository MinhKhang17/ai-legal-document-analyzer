import React, { Suspense } from 'react';
import ReactDOM from 'react-dom/client';
import { RouterProvider } from 'react-router-dom';
import { ToastProvider } from './components/ui/ToastProvider';
import { AppStoreProvider } from './store/AppStore';
import { router } from './routes/router';
import './styles/index.css';
import { useI18n } from './hooks/useI18n';

function AppLoading() {
  const { t } = useI18n();
  return <div className="flex min-h-screen items-center justify-center" role="status">{t('common.loading')}</div>;
}

ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
  <React.StrictMode>
    <AppStoreProvider>
      <ToastProvider>
        <Suspense fallback={<AppLoading />}><RouterProvider router={router} /></Suspense>
      </ToastProvider>
    </AppStoreProvider>
  </React.StrictMode>,
);
