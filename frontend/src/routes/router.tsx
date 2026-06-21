import { Navigate, createBrowserRouter } from 'react-router-dom';
import { AppShell } from '../components/common/AppShell';
import { AdminRoute, AuthenticatedRoute, PublicRoute } from '../components/auth/AuthGuards';
import { AuthLayout } from '../layouts/AuthLayout';
import { LoginPage } from '../pages/auth/LoginPage';
import { RegisterPage } from '../pages/auth/RegisterPage';
import { DashboardPage } from '../pages/dashboard/DashboardPage';
import { UploadPage } from '../pages/upload/UploadPage';
import { EditorPage } from '../pages/editor/EditorPage';
import { RiskReviewPage } from '../pages/editor/RiskReviewPage';
import { VersionComparisonPage } from '../pages/editor/VersionComparisonPage';
import { ComparisonHistoryPage } from '../pages/editor/ComparisonHistoryPage';
import { ReportsPage } from '../pages/reports/ReportsPage';
import { ReportDetailPage } from '../pages/reports/ReportDetailPage';
import { KnowledgeBasePage } from '../pages/knowledge-base/KnowledgeBasePage';
import { KnowledgeBaseDetailPage } from '../pages/knowledge-base/KnowledgeBaseDetailPage';
import { BillingPage } from '../pages/billing/BillingPage';
import { SubscribePlanPage } from '../pages/billing/SubscribePlanPage';
import { AdminConsolePage } from '../pages/admin/AdminConsolePage';
import { AuditLogsPage } from '../pages/admin/AuditLogsPage';
import { SystemHealthPage } from '../pages/admin/SystemHealthPage';
import { JobsPage } from '../pages/jobs/JobsPage';
import { TemplatesPage } from '../pages/templates/TemplatesPage';
import { SettingsPage } from '../pages/settings/SettingsPage';
import { WorkspaceListPage } from '../pages/workspaces/WorkspaceListPage';
import { WorkspaceDetailPage } from '../pages/workspaces/WorkspaceDetailPage';
import { ChatSessionListPage } from '../pages/chat/ChatSessionListPage';
import { ChatSessionDetailPage } from '../pages/chat/ChatSessionDetailPage';


export const router = createBrowserRouter([
  {
    path: '/',
    element: <Navigate to="/login" replace />,
  },
  {
    element: (
      <PublicRoute>
        <AuthLayout />
      </PublicRoute>
    ),
    children: [
      { path: '/login', element: <LoginPage /> },
      { path: '/register', element: <RegisterPage /> },
    ],
  },
  {
    element: (
      <AuthenticatedRoute>
        <AppShell />
      </AuthenticatedRoute>
    ),
    children: [
      { path: '/dashboard', element: <DashboardPage /> },
      { path: '/workspaces', element: <WorkspaceListPage /> },
      { path: '/workspaces/:workspaceId', element: <WorkspaceDetailPage /> },
      { path: '/workspaces/:workspaceId/chat-sessions', element: <ChatSessionListPage /> },
      { path: '/chat-sessions/:chatSessionId', element: <ChatSessionDetailPage /> },
      { path: '/upload', element: <UploadPage /> },
      { path: '/editor', element: <EditorPage /> },
      { path: '/editor/risk-review', element: <RiskReviewPage /> },
      { path: '/editor/version-comparison', element: <VersionComparisonPage /> },
      { path: '/editor/comparison-history', element: <ComparisonHistoryPage /> },
      { path: '/comparison-history', element: <Navigate to="/editor/comparison-history" replace /> },
      { path: '/reports', element: <ReportsPage /> },
      { path: '/reports/:id', element: <ReportDetailPage /> },
      { path: '/knowledge-base', element: <KnowledgeBasePage /> },
      { path: '/knowledge-base/:id', element: <KnowledgeBaseDetailPage /> },
      { path: '/billing', element: <BillingPage /> },
      { path: '/billing/subscribe', element: <SubscribePlanPage /> },
      { path: '/jobs', element: <JobsPage /> },
      { path: '/templates', element: <TemplatesPage /> },
      { path: '/settings', element: <SettingsPage /> },
      {
        path: '/admin',
        element: (
          <AdminRoute>
            <AdminConsolePage />
          </AdminRoute>
        ),
      },
      {
        path: '/admin/audit-logs',
        element: (
          <AdminRoute>
            <AuditLogsPage />
          </AdminRoute>
        ),
      },
      {
        path: '/admin/system-health',
        element: (
          <AdminRoute>
            <SystemHealthPage />
          </AdminRoute>
        ),
      },
    ],
  },
  {
    path: '*',
    element: <Navigate to="/dashboard" replace />,
  },
]);
