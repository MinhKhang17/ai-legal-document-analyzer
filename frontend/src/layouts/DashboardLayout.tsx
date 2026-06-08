import { type ReactNode } from 'react';
import { Sidebar } from './Sidebar';
import { Topbar } from './Topbar';
import { useAppStore } from '../store/AppStore';
import { cn } from '../utils/cn';

interface DashboardLayoutProps {
  children: ReactNode;
}

export function DashboardLayout({ children }: DashboardLayoutProps) {
  const { sidebarCollapsed } = useAppStore();

  return (
    <div className="min-h-screen bg-ivory text-on-surface dark:bg-slate-950 dark:text-slate-100">
      <Sidebar />
      <div className={cn('min-h-screen transition-all duration-300', sidebarCollapsed ? 'lg:pl-20' : 'lg:pl-72')}>
        <Topbar />
        <main className="mx-auto w-full max-w-[1480px] px-md py-xl sm:px-lg lg:px-margin">{children}</main>
      </div>
    </div>
  );
}
