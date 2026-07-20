import React, { Suspense } from 'react';
import ReactDOM from 'react-dom/client';
import { RouterProvider } from 'react-router-dom';
import { ToastProvider } from './components/ui/ToastProvider';
import { AppStoreProvider } from './store/AppStore';
import { router } from './routes/router';
import './styles/index.css';
import { useI18n } from './hooks/useI18n';

const CHUNK_RELOAD_KEY = 'lexiguard.chunkReloadAt';

// An already-open tab can still reference a removed hashed chunk after a new
// frontend image is deployed. Vite emits this event when a dynamic import
// fails; reload once to fetch the current, non-cached index.html.
window.addEventListener('vite:preloadError', (event) => {
  event.preventDefault();
  const previousReloadAt = Number(window.sessionStorage.getItem(CHUNK_RELOAD_KEY) ?? '0');
  if (Date.now() - previousReloadAt < 10_000) return;
  window.sessionStorage.setItem(CHUNK_RELOAD_KEY, String(Date.now()));
  window.location.reload();
});

window.setTimeout(() => window.sessionStorage.removeItem(CHUNK_RELOAD_KEY), 10_000);

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
