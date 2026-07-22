import { Navigate, createBrowserRouter } from "react-router-dom";
import { lazy } from "react";
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
import { VerifyEmailPage } from "../pages/auth/VerifyEmailPage";
import { CheckEmailPage } from "../pages/auth/CheckEmailPage";
import { ForgotPasswordPage } from "../pages/auth/ForgotPasswordPage";
import { ResetPasswordPage } from "../pages/auth/ResetPasswordPage";
import { DashboardPage } from "../pages/dashboard/DashboardPage";
import { ProjectsPage } from "../pages/projects/ProjectsPage";
import { ProjectDetailPage } from "../pages/projects/ProjectDetailPage";
import { DocumentsPage } from "../pages/documents/DocumentsPage";
import { DocumentDetailPage } from "../pages/documents/DocumentDetailPage";
import { UploadPage } from "../pages/upload/UploadPage";
import { EditorPage } from "../pages/editor/EditorPage";
import { CustomerTicketDetailPage } from "../pages/tickets/CustomerTicketDetailPage";
import { CustomerTicketsPage } from "../pages/tickets/CustomerTicketsPage";
import { KnowledgeBasePage } from "../pages/knowledge-base/KnowledgeBasePage";
import { KnowledgeBaseDetailPage } from "../pages/knowledge-base/KnowledgeBaseDetailPage";
import { BillingPage } from "../pages/billing/BillingPage";
import { PaymentResultPage } from "../pages/billing/PaymentResultPage";
import { RefundHistoryPage } from "../pages/billing/RefundHistoryPage";
import { RefundDetailPage } from "../pages/billing/RefundDetailPage";
import { RefundEmailConfirmationPage } from "../pages/billing/RefundEmailConfirmationPage";
import { SubscribePlanPage } from "../pages/billing/SubscribePlanPage";
import { AdminFeedbackPage } from "../pages/admin/AdminFeedbackPage";
import { AdminTicketsPage } from "../pages/admin/AdminTicketsPage";
import { AdminTicketDetailPage } from "../pages/admin/AdminTicketDetailPage";
import { AdminRefundsPage } from "../pages/admin/AdminRefundsPage";
import { AdminRefundDetailPage } from "../pages/admin/AdminRefundDetailPage";
import { SettingsPage } from "../pages/settings/SettingsPage";
import { AccountSecurityPage } from "../pages/settings/AccountSecurityPage";
import { ProfilePage } from "../pages/settings/ProfilePage";
import { PaymentResultRedirect } from "../pages/billing/PaymentResultRedirect";
import { LawyerTicketsPage } from "../pages/lawyer/LawyerTicketsPage";
import { LawyerTicketDetailPage } from "../pages/lawyer/LawyerTicketDetailPage";
import { LawyerRevenuePage } from "../pages/lawyer/LawyerRevenuePage";
import { AdminRevenuePage } from "../pages/admin/AdminRevenuePage";
import { AdminEarlyPayoutDetailPage } from "../pages/admin/AdminEarlyPayoutDetailPage";
import { CommissionVerificationPage } from "../pages/admin/CommissionVerificationPage";
import { RevenueStatementDetailPage } from "../pages/revenue/RevenueStatementDetailPage";
import { CreateCustomerTicketPage } from "../pages/tickets/CreateCustomerTicketPage";
import { SharedChatPage } from "../pages/chat/SharedChatPage";
import { SharedTicketConversationPage } from "../pages/tickets/SharedTicketConversationPage";

const LegalChatPage = lazy(() => import("../pages/chat/LegalChatPage").then((module) => ({ default: module.LegalChatPage })));
const ChatHistoryPage = lazy(() => import("../pages/chat/ChatHistoryPage").then((module) => ({ default: module.ChatHistoryPage })));
const AdminConsolePage = lazy(() => import("../pages/admin/AdminConsolePage").then((module) => ({ default: module.AdminConsolePage })));

export const router = createBrowserRouter([
  { path: "/verify-email", element: <VerifyEmailPage /> },
  { path: "/billing/refunds/confirm", element: <RefundEmailConfirmationPage /> },
  { path: "/shared/chat/:shareToken", element: <SharedChatPage /> },
  {
    path: "/",
    element: <Navigate to="/login" replace />,
  },
  {
    element: <AuthLayout />,
    children: [
      { path: "/forgot-password", element: <ForgotPasswordPage /> },
      { path: "/reset-password", element: <ResetPasswordPage /> },
    ],
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
      { path: "/auth/check-email", element: <CheckEmailPage /> },
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
        path: "/chat/contract-assistant",
        element: <Navigate to="/chat" replace />,
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
        path: "/tickets/create",
        element: (
          <CustomerRoute>
            <CreateCustomerTicketPage />
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
      { path: "/billing/refunds", element: <CustomerRoute><RefundHistoryPage /></CustomerRoute> },
      { path: "/billing/refunds/:id", element: <CustomerRoute><RefundDetailPage /></CustomerRoute> },
      {
        path: "/payment-result",
        element: <PaymentResultRedirect />,
      },
      {
        path: "*",
        element: <Navigate to="/dashboard" replace />,
      },

      { path: "/settings", element: <SettingsPage /> },
      { path: "/settings/security", element: <AccountSecurityPage /> },
      { path: "/profile", element: <ProfilePage /> },
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
      { path: "/admin/refunds", element: <AdminRoute><AdminRefundsPage /></AdminRoute> },
      { path: "/admin/refunds/:id", element: <AdminRoute><AdminRefundDetailPage /></AdminRoute> },
      { path: "/admin/revenue", element: <AdminRoute><AdminRevenuePage /></AdminRoute> },
      { path: "/admin/revenue/statements/:statementId", element: <AdminRoute><RevenueStatementDetailPage admin /></AdminRoute> },
      { path: "/admin/revenue/early-payouts/:id", element: <AdminRoute><AdminEarlyPayoutDetailPage /></AdminRoute> },
      { path: "/admin/revenue/commission/verify", element: <AdminRoute><CommissionVerificationPage /></AdminRoute> },
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
      {
        path: "/lawyer/revenue",
        element: (
          <ExpertRoute>
            <LawyerRevenuePage />
          </ExpertRoute>
        ),
      },
      { path: "/lawyer/revenue/:statementId", element: <ExpertRoute><RevenueStatementDetailPage /></ExpertRoute> },
      { path: "/shared-conversation/:token", element: <SharedTicketConversationPage /> },
    ],
  },
  {
    path: "*",
    element: <Navigate to="/dashboard" replace />,
  },
]);
