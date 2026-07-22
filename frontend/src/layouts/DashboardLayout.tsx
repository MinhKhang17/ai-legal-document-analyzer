import { type ReactNode } from 'react';
import { useLocation } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { Topbar } from './Topbar';
import { useAppStore } from '../store/AppStore';
import { cn } from '../utils/cn';

interface DashboardLayoutProps {
  children: ReactNode;
}

export function DashboardLayout({ children }: DashboardLayoutProps) {
  const { sidebarCollapsed } = useAppStore();
  const location = useLocation();
  const isLegalChat = location.pathname === '/chat';

  return (
    <div className={cn(
      'min-h-screen bg-ivory text-on-surface dark:bg-slate-950 dark:text-slate-100',
      isLegalChat && 'h-screen h-[100dvh] min-h-0 overflow-hidden',
    )}>
      <Sidebar />
      <div className={cn(
        'transition-all duration-300',
        sidebarCollapsed ? 'lg:pl-20' : 'lg:pl-72',
        isLegalChat
          ? 'flex h-full min-h-0 flex-col overflow-hidden'
          : 'min-h-screen',
      )}>
        <Topbar />
        <main className={isLegalChat
          ? 'flex min-h-0 w-full max-w-none flex-1 flex-col overflow-hidden px-md py-sm sm:px-lg lg:py-md 2xl:px-margin'
          : 'mx-auto w-full max-w-[1480px] px-md py-xl sm:px-lg lg:px-margin'}>
          {children}
        </main>
      </div>
    </div>
  );
}
