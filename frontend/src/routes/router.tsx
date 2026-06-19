import { Navigate, createBrowserRouter } from 'react-router-dom';
import { AppShell } from '../components/common/AppShell';
import { AuthLayout } from '../layouts/AuthLayout';
import { LoginPage } from '../pages/auth/LoginPage';
import { RegisterPage } from '../pages/auth/RegisterPage';
import { DashboardPage } from '../pages/dashboard/DashboardPage';
import { ProjectsPage } from '../pages/projects/ProjectsPage';
import { ProjectDetailPage } from '../pages/projects/ProjectDetailPage';
import { DocumentsPage } from '../pages/documents/DocumentsPage';
import { DocumentDetailPage } from '../pages/documents/DocumentDetailPage';
import { UploadPage } from '../pages/upload/UploadPage';
import { EditorPage } from '../pages/editor/EditorPage';
import { RiskReviewPage } from '../pages/editor/RiskReviewPage';
import { VersionComparisonPage } from '../pages/editor/VersionComparisonPage';
import { ComparisonHistoryPage } from '../pages/editor/ComparisonHistoryPage';
import { LegalChatPage } from '../pages/chat/LegalChatPage';
import { ChatHistoryPage } from '../pages/chat/ChatHistoryPage';
import { ReportsPage } from '../pages/reports/ReportsPage';
import { ReportDetailPage } from '../pages/reports/ReportDetailPage';
import { KnowledgeBasePage } from '../pages/knowledge-base/KnowledgeBasePage';
import { KnowledgeBaseDetailPage } from '../pages/knowledge-base/KnowledgeBaseDetailPage';
import { BillingPage } from '../pages/billing/BillingPage';
import { AdminConsolePage } from '../pages/admin/AdminConsolePage';
import { AuditLogsPage } from '../pages/admin/AuditLogsPage';
import { SystemHealthPage } from '../pages/admin/SystemHealthPage';
import { JobsPage } from '../pages/jobs/JobsPage';
import { TemplatesPage } from '../pages/templates/TemplatesPage';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <Navigate to="/dashboard" replace />,
  },
  {
    element: <AuthLayout />,
    children: [
      { path: '/login', element: <LoginPage /> },
      { path: '/register', element: <RegisterPage /> },
    ],
  },
  {
    element: <AppShell />,
    children: [
      { path: '/dashboard', element: <DashboardPage /> },
      { path: '/projects', element: <ProjectsPage /> },
      { path: '/projects/:id', element: <ProjectDetailPage /> },
      { path: '/documents', element: <DocumentsPage /> },
      { path: '/documents/:id', element: <DocumentDetailPage /> },
      { path: '/upload', element: <UploadPage /> },
      { path: '/editor', element: <EditorPage /> },
      { path: '/editor/risk-review', element: <RiskReviewPage /> },
      { path: '/editor/version-comparison', element: <VersionComparisonPage /> },
      { path: '/editor/comparison-history', element: <ComparisonHistoryPage /> },
      { path: '/comparison-history', element: <Navigate to="/editor/comparison-history" replace /> },
      { path: '/chat', element: <LegalChatPage /> },
      { path: '/chat/history', element: <ChatHistoryPage /> },
      { path: '/reports', element: <ReportsPage /> },
      { path: '/reports/:id', element: <ReportDetailPage /> },
      { path: '/knowledge-base', element: <KnowledgeBasePage /> },
      { path: '/knowledge-base/:id', element: <KnowledgeBaseDetailPage /> },
      { path: '/billing', element: <BillingPage /> },
      { path: '/jobs', element: <JobsPage /> },
      { path: '/templates', element: <TemplatesPage /> },
      { path: '/settings', element: <Navigate to="/admin" replace /> },
      { path: '/admin', element: <AdminConsolePage /> },
      { path: '/admin/audit-logs', element: <AuditLogsPage /> },
      { path: '/admin/system-health', element: <SystemHealthPage /> },
    ],
  },
  {
    path: '*',
    element: <Navigate to="/dashboard" replace />,
  },
]);
