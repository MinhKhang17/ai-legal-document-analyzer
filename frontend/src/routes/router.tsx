import { Navigate, createBrowserRouter } from "react-router-dom";
import { AppShell } from "../components/common/AppShell";
import {
  AdminRoute,
  AuthenticatedRoute,
  CustomerRoute,
  PublicRoute,
  ExpertRoute,
} from "../components/auth/AuthGuards";
import { AuthLayout } from "../layouts/AuthLayout";
import { LoginPage } from "../pages/auth/LoginPage";
import { RegisterPage } from "../pages/auth/RegisterPage";
import { DashboardPage } from "../pages/dashboard/DashboardPage";
import { ProjectsPage } from "../pages/projects/ProjectsPage";
import { ProjectDetailPage } from "../pages/projects/ProjectDetailPage";
import { DocumentsPage } from "../pages/documents/DocumentsPage";
import { DocumentDetailPage } from "../pages/documents/DocumentDetailPage";
import { UploadPage } from "../pages/upload/UploadPage";
import { EditorPage } from "../pages/editor/EditorPage";
import { RiskReviewPage } from "../pages/editor/RiskReviewPage";
import { VersionComparisonPage } from "../pages/editor/VersionComparisonPage";
import { ComparisonHistoryPage } from "../pages/editor/ComparisonHistoryPage";
import { LegalChatPage } from "../pages/chat/LegalChatPage";
import { ChatHistoryPage } from "../pages/chat/ChatHistoryPage";
import { CustomerTicketDetailPage } from "../pages/tickets/CustomerTicketDetailPage";
import { CustomerTicketsPage } from "../pages/tickets/CustomerTicketsPage";
import { ReportsPage } from "../pages/reports/ReportsPage";
import { ReportDetailPage } from "../pages/reports/ReportDetailPage";
import { KnowledgeBasePage } from "../pages/knowledge-base/KnowledgeBasePage";
import { KnowledgeBaseDetailPage } from "../pages/knowledge-base/KnowledgeBaseDetailPage";
import { BillingPage } from "../pages/billing/BillingPage";
import { PaymentResultPage } from "../pages/billing/PaymentResultPage";
import { SubscribePlanPage } from "../pages/billing/SubscribePlanPage";
import { AdminConsolePage } from "../pages/admin/AdminConsolePage";
import { AdminFeedbackPage } from "../pages/admin/AdminFeedbackPage";
import { AdminTicketsPage } from "../pages/admin/AdminTicketsPage";
import { AdminTicketDetailPage } from "../pages/admin/AdminTicketDetailPage";
import { AuditLogsPage } from "../pages/admin/AuditLogsPage";
import { SystemHealthPage } from "../pages/admin/SystemHealthPage";
import { JobsPage } from "../pages/jobs/JobsPage";
import { TemplatesPage } from "../pages/templates/TemplatesPage";
import { ContractDetailPage } from "../pages/contracts/ContractDetailPage";
import { MyContractsPage } from "../pages/contracts/MyContractsPage";
import { SettingsPage } from "../pages/settings/SettingsPage";
import { PaymentResultRedirect } from "../pages/billing/PaymentResultRedirect";
import { LawyerTicketsPage } from "../pages/lawyer/LawyerTicketsPage";
import { LawyerTicketDetailPage } from "../pages/lawyer/LawyerTicketDetailPage";

export const router = createBrowserRouter([
  {
    path: "/",
    element: <Navigate to="/login" replace />,
  },
  {
    element: (
      <PublicRoute>
        <AuthLayout />
      </PublicRoute>
    ),
    children: [
      { path: "/login", element: <LoginPage /> },
      { path: "/register", element: <RegisterPage /> },
    ],
  },
  {
    element: (
      <AuthenticatedRoute>
        <AppShell />
      </AuthenticatedRoute>
    ),
    children: [
      {
        path: "/dashboard",
        element: (
          <CustomerRoute>
            <DashboardPage />
          </CustomerRoute>
        ),
      },
      {
        path: "/projects",
        element: (
          <CustomerRoute>
            <ProjectsPage />
          </CustomerRoute>
        ),
      },
      {
        path: "/projects/:id",
        element: (
          <CustomerRoute>
            <ProjectDetailPage />
          </CustomerRoute>
        ),
      },
      {
        path: "/documents",
        element: (
          <CustomerRoute>
            <DocumentsPage />
          </CustomerRoute>
        ),
      },
      {
        path: "/documents/:id",
        element: (
          <CustomerRoute>
            <DocumentDetailPage />
          </CustomerRoute>
        ),
      },
      {
        path: "/upload",
        element: (
          <CustomerRoute>
            <UploadPage />
          </CustomerRoute>
        ),
      },
      {
        path: "/editor",
        element: (
          <CustomerRoute>
            <EditorPage />
          </CustomerRoute>
        ),
      },
      {
        path: "/editor/risk-review",
        element: (
          <AdminRoute>
            <RiskReviewPage />
          </AdminRoute>
        ),
      },
      {
        path: "/editor/version-comparison",
        element: <VersionComparisonPage />,
      },
      {
        path: "/editor/comparison-history",
        element: <ComparisonHistoryPage />,
      },
      {
        path: "/comparison-history",
        element: <Navigate to="/editor/comparison-history" replace />,
      },
      {
        path: "/chat",
        element: (
          <CustomerRoute>
            <LegalChatPage />
          </CustomerRoute>
        ),
      },
      {
        path: "/chat/history",
        element: (
          <CustomerRoute>
            <ChatHistoryPage />
          </CustomerRoute>
        ),
      },
      {
        path: "/tickets",
        element: (
          <CustomerRoute>
            <CustomerTicketsPage />
          </CustomerRoute>
        ),
      },
      {
        path: "/tickets/:id",
        element: (
          <CustomerRoute>
            <CustomerTicketDetailPage />
          </CustomerRoute>
        ),
      },
      { path: "/reports", element: <ReportsPage /> },
      { path: "/reports/:id", element: <ReportDetailPage /> },
      {
        path: "/knowledge-base",
        element: (
          <AdminRoute>
            <KnowledgeBasePage />
          </AdminRoute>
        ),
      },
      {
        path: "/knowledge-base/:id",
        element: (
          <AdminRoute>
            <KnowledgeBaseDetailPage />
          </AdminRoute>
        ),
      },
      {
        path: "/billing",
        element: (
          <CustomerRoute>
            <BillingPage />
          </CustomerRoute>
        ),
      },
      {
        path: "/billing/subscribe",
        element: (
          <CustomerRoute>
            <SubscribePlanPage />
          </CustomerRoute>
        ),
      },
      {
        path: "/billing/payment-result",
        element: (
          <CustomerRoute>
            <PaymentResultPage />
          </CustomerRoute>
        ),
      },
      {
        path: "/payment-result",
        element: <PaymentResultRedirect />,
      },
      {
        path: "*",
        element: <Navigate to="/dashboard" replace />,
      },
      {
        path: "/jobs",
        element: (
          <AdminRoute>
            <JobsPage />
          </AdminRoute>
        ),
      },
      { path: "/templates", element: <TemplatesPage /> },
      {
        path: "/contracts",
        element: (
          <CustomerRoute>
            <MyContractsPage />
          </CustomerRoute>
        ),
      },
      {
        path: "/contracts/:id",
        element: (
          <CustomerRoute>
            <ContractDetailPage />
          </CustomerRoute>
        ),
      },
      { path: "/settings", element: <SettingsPage /> },
      {
        path: "/admin",
        element: (
          <AdminRoute>
            <AdminConsolePage />
          </AdminRoute>
        ),
      },
      {
        path: "/admin/tickets",
        element: (
          <AdminRoute>
            <AdminTicketsPage />
          </AdminRoute>
        ),
      },
      {
        path: "/admin/tickets/:ticketId",
        element: (
          <AdminRoute>
            <AdminTicketDetailPage />
          </AdminRoute>
        ),
      },
      {
        path: "/admin/feedback",
        element: (
          <AdminRoute>
            <AdminFeedbackPage />
          </AdminRoute>
        ),
      },
      {
        path: "/admin/audit-logs",
        element: (
          <AdminRoute>
            <AuditLogsPage />
          </AdminRoute>
        ),
      },
      {
        path: "/admin/system-health",
        element: (
          <AdminRoute>
            <SystemHealthPage />
          </AdminRoute>
        ),
      },
      {
        path: "/lawyer/tickets",
        element: (
          <ExpertRoute>
            <LawyerTicketsPage />
          </ExpertRoute>
        ),
      },
      {
        path: "/lawyer/tickets/:ticketId",
        element: (
          <ExpertRoute>
            <LawyerTicketDetailPage />
          </ExpertRoute>
        ),
      },
    ],
  },
  {
    path: "*",
    element: <Navigate to="/dashboard" replace />,
  },
]);
