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
      isLegalChat && 'lg:h-screen lg:overflow-hidden',
    )}>
      <Sidebar />
      <div className={cn(
        'min-h-screen transition-all duration-300',
        sidebarCollapsed ? 'lg:pl-20' : 'lg:pl-72',
        isLegalChat && 'lg:flex lg:h-screen lg:min-h-0 lg:flex-col lg:overflow-hidden',
      )}>
        <Topbar />
        <main className={isLegalChat
          ? 'w-full max-w-none px-md py-xl sm:px-lg lg:flex lg:min-h-0 lg:flex-1 lg:flex-col lg:overflow-hidden lg:px-lg lg:py-md 2xl:px-margin'
          : 'mx-auto w-full max-w-[1480px] px-md py-xl sm:px-lg lg:px-margin'}>
          {children}
        </main>
      </div>
    </div>
  );
}
