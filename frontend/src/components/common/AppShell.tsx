import { Outlet } from 'react-router-dom';
import { DashboardLayout } from '../../layouts/DashboardLayout';

export function AppShell() {
  return (
    <DashboardLayout>
      <Outlet />
    </DashboardLayout>
  );
}
