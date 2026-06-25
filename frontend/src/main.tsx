import React from 'react';
import ReactDOM from 'react-dom/client';
import { RouterProvider } from 'react-router-dom';
import { ToastProvider } from './components/ui/ToastProvider';
import { AppStoreProvider } from './store/AppStore';
import { router } from './routes/router';
import './styles/index.css';

ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
  <React.StrictMode>
    <AppStoreProvider>
      <ToastProvider>
        <RouterProvider router={router} />
      </ToastProvider>
    </AppStoreProvider>
  </React.StrictMode>,
);
