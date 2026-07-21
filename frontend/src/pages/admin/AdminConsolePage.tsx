import { Cpu, Database, Monitor, Moon, MoreHorizontal, Palette, Receipt, RefreshCw, RotateCcw, ShieldCheck, Sun, Trash2, UserPlus, UsersRound } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { AdminMetricCard } from '../../components/admin/AdminMetricCard';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { DataTable, type DataTableColumn } from '../../components/common/DataTable';
import { EmptyState } from '../../components/common/EmptyState';
import { Modal } from '../../components/common/Modal';
import { PageHeader } from '../../components/common/PageHeader';
import { ProgressBar } from '../../components/common/ProgressBar';
import { StatusBadge } from '../../components/common/StatusBadge';
import { Tabs } from '../../components/common/Tabs';
import { LanguageToggle } from '../../components/common/LanguageToggle';
import { useI18n } from '../../hooks/useI18n';
import { useToast } from '../../hooks/useToast';
import { assignLawyerToLegalTicket, getAdminLegalTickets } from '../../services/legalTicket.service';
import { getAllPaymentTransactions } from '../../services/paymentTransaction.service';
import {
  createSubscriptionPlan,
  deleteSubscriptionPlan,
  getSubscriptionPlans,
  updateSubscriptionPlan,
} from '../../services/subscription.service';
import { createExpert, deleteUser, getAdminExperts, getUserDetail, getUsers, resendExpertActivation, restoreUser, type BackendUser } from '../../services/user.service';
import { useAppStore, type ThemeMode } from '../../store/AppStore';
import type { LegalTicket } from '../../types/legalTicket';
import { getLegalTicketStatusLabel } from '../../types/legalTicketStatus';
import type { PaymentTransaction } from '../../types/paymentTransaction';
import type { SubscriptionPlan, SubscriptionPlanRequest } from '../../types/subscription';
import type { WorkspaceUser } from '../../types/user';
import { formatDisplayDate, formatVndCurrency } from '../../utils/format';

type AdminTabId = 'users' | 'plans' | 'tickets' | 'workspace' | 'language' | 'theme' | 'security' | 'aiConfig';

const adminTabs: Array<{ id: AdminTabId; labelKey?: string; label?: string }> = [
  { id: 'users', labelKey: 'admin.tabs.users' },
  { id: 'plans', labelKey: 'admin.tabs.plans' },
  { id: 'tickets', labelKey: 'admin.tabs.tickets' },
  { id: 'workspace', labelKey: 'admin.tabs.workspace' },
  { id: 'language', labelKey: 'admin.tabs.language' },
  { id: 'theme', labelKey: 'admin.tabs.theme' },
  { id: 'security', labelKey: 'admin.tabs.security' },
  { id: 'aiConfig', labelKey: 'admin.tabs.aiConfig' },
];

const themeOptions: Array<{ mode: ThemeMode; labelKey: string; icon: typeof Sun }> = [
  { mode: 'light', labelKey: 'theme.light', icon: Sun },
  { mode: 'dark', labelKey: 'theme.dark', icon: Moon },
  { mode: 'system', labelKey: 'theme.system', icon: Monitor },
];

const legalTextSizes = ['16px', '18px', '20px'] as const;

const emptyPlanForm: SubscriptionPlanRequest = {
  planName: '',
  planType: 'MONTHLY',
  description: '',
  price: 0,
  durationDays: 30,
  maxQuota: 100,
  name: '',
  displayName: '',
  priceVnd: 0,
  billingCycleDays: 30,
  contractAnalysisLimit: 0,
  aiTokenLimit: 0,
  workspaceLimit: 0,
  documentPerWorkspaceLimit: 0,
  storageLimitMb: 0,
  maxFileSizeMb: 0,
  maxAttachedDocumentsPerSession: 0,
  contractDraftLimit: 0,
  expertTicketLimit: 0,
  allowSystemErrorTicket: true,
  allowQueryErrorTicket: true,
  allowContactExpertTicket: false,
  features: [],
  active: true,
};

const getTabId = (tab: AdminTabId) => `admin-tab-${tab}`;
const getPanelId = (tab: AdminTabId) => `admin-panel-${tab}`;

const getInitials = (user: BackendUser) => {
  const source = `${user.firstName} ${user.lastName}`.trim() || user.email;
  return source
    .split(/\s+/)
    .filter(Boolean)
    .map((segment) => segment[0]?.toUpperCase())
    .join('')
    .slice(0, 2);
};

const toWorkspaceUser = (user: BackendUser): WorkspaceUser => ({
  id: String(user.id),
  name: `${user.firstName} ${user.lastName}`.trim() || user.email,
  email: user.email,
  role: user.role,
  status: user.active ? 'active' : 'offline',
  lastAccess: 'Backend does not provide last access',
  initials: getInitials(user),
});

export function AdminConsolePage() {
  const { t, language } = useI18n();
  const toast = useToast();
  const { theme, setTheme } = useAppStore();
  const locale = language === 'vi' ? 'vi-VN' : 'en-US';
  const [activeTab, setActiveTab] = useState<AdminTabId>('users');
  const [legalTextSize, setLegalTextSize] = useState<(typeof legalTextSizes)[number]>('18px');
  const [backendUsers, setBackendUsers] = useState<BackendUser[]>([]);
  const [adminUsers, setAdminUsers] = useState<WorkspaceUser[]>([]);
  const [paymentTransactions, setPaymentTransactions] = useState<PaymentTransaction[]>([]);
  const [subscriptionPlans, setSubscriptionPlans] = useState<SubscriptionPlan[]>([]);
  const [legalTickets, setLegalTickets] = useState<LegalTicket[]>([]);
  const [isLoadingAdminData, setIsLoadingAdminData] = useState(true);
  const [adminError, setAdminError] = useState<string | null>(null);
  const [selectedUserDetail, setSelectedUserDetail] = useState<BackendUser | null>(null);
  const [userDetailOpen, setUserDetailOpen] = useState(false);
  const [userDetailLoading, setUserDetailLoading] = useState(false);
  const [planForm, setPlanForm] = useState<SubscriptionPlanRequest>(emptyPlanForm);
  const [editingPlanId, setEditingPlanId] = useState<number | null>(null);
  const [planActionMessage, setPlanActionMessage] = useState<string | null>(null);
  const [planError, setPlanError] = useState<string | null>(null);
  const [savingPlan, setSavingPlan] = useState(false);
  const [ticketAssigneeById, setTicketAssigneeById] = useState<Record<string, string>>({});
  const [ticketActionMessage, setTicketActionMessage] = useState<string | null>(null);
  const [ticketError, setTicketError] = useState<string | null>(null);
  const [assigningTicketId, setAssigningTicketId] = useState<string | null>(null);
  const [createExpertOpen, setCreateExpertOpen] = useState(false);
  const [savingExpert, setSavingExpert] = useState(false);
  const [expertForm, setExpertForm] = useState({ firstName: '', lastName: '', email: '', specialty: '', legalDomain: '', description: '' });
  const [isDeleteUserModalOpen, setIsDeleteUserModalOpen] = useState(false);
  const [userToDelete, setUserToDelete] = useState<WorkspaceUser | null>(null);
  const [deletingUserId, setDeletingUserId] = useState<string | null>(null);

  const loadAdminData = useCallback(async () => {
    setIsLoadingAdminData(true);
    setAdminError(null);

    const [usersResult, paymentsResult, plansResult, ticketsResult, expertsResult] = await Promise.allSettled([
      getUsers(),
      getAllPaymentTransactions(),
      getSubscriptionPlans(),
      getAdminLegalTickets(),
      getAdminExperts(),
    ]);

    if (usersResult.status === 'fulfilled') {
      setBackendUsers(usersResult.value);
      setAdminUsers(usersResult.value.map(toWorkspaceUser));
    } else {
      setBackendUsers([]);
      setAdminUsers([]);
      setAdminError(usersResult.reason instanceof Error ? usersResult.reason.message : t('admin.errors.loadUsers'));
    }

    if (paymentsResult.status === 'fulfilled') {
      setPaymentTransactions(paymentsResult.value);
    } else {
      setPaymentTransactions([]);
      setAdminError((previous) => {
        const nextMessage =
          paymentsResult.reason instanceof Error
            ? paymentsResult.reason.message
            : t('admin.errors.loadPayments');
        return previous ? `${previous} ${nextMessage}` : nextMessage;
      });
    }

    if (plansResult.status === 'fulfilled') {
      setSubscriptionPlans(plansResult.value.data ?? []);
    } else {
      setSubscriptionPlans([]);
      setAdminError((previous) => {
        const nextMessage =
          plansResult.reason instanceof Error
            ? plansResult.reason.message
            : t('admin.errors.loadPlans');
        return previous ? `${previous} ${nextMessage}` : nextMessage;
      });
    }

    if (ticketsResult.status === 'fulfilled') {
      setLegalTickets(ticketsResult.value.items ?? []);
    } else {
      setLegalTickets([]);
      setTicketError(ticketsResult.reason instanceof Error ? ticketsResult.reason.message : t('admin.errors.loadTickets'));
    }

    if (expertsResult.status === 'fulfilled') {
      setBackendUsers((current) => {
        const nonExperts = current.filter((entry) => entry.role !== 'EXPERT');
        return [...nonExperts, ...expertsResult.value];
      });
    }

    setIsLoadingAdminData(false);
  }, [t]);

  useEffect(() => {
    void loadAdminData();
  }, [loadAdminData]);

  const activeUsersCount = useMemo(
    () => adminUsers.filter((user) => user.status === 'active').length,
    [adminUsers],
  );
  const expertUsers = useMemo(
    () => backendUsers.filter((user) => user.active && user.role === 'EXPERT'),
    [backendUsers],
  );

  const handleOpenUserDetail = async (userId: string) => {
    setUserDetailOpen(true);
    setUserDetailLoading(true);
    setSelectedUserDetail(null);

    try {
      const user = await getUserDetail(userId);
      setSelectedUserDetail(user);
    } catch (error) {
      const message = error instanceof Error ? error.message : t('admin.userDetailError');
      setAdminError(message);
      toast.error(message, t('toast.errorTitle'));
    } finally {
      setUserDetailLoading(false);
    }
  };

  const handleCreateExpert = async () => {
    if (!expertForm.firstName.trim() || !expertForm.lastName.trim() || !expertForm.email.trim() || !expertForm.specialty.trim() || !expertForm.legalDomain.trim()) {
      toast.warning(language === 'vi' ? 'Vui lòng nhập họ, tên, email, chuyên môn và lĩnh vực pháp lý.' : 'First name, last name, email, specialty and legal domain are required.');
      return;
    }
    setSavingExpert(true);
    try {
      await createExpert({ ...expertForm, active: true });
      setCreateExpertOpen(false);
      setExpertForm({ firstName: '', lastName: '', email: '', specialty: '', legalDomain: '', description: '' });
      toast.success(language === 'vi' ? 'Đã tạo Expert với mật khẩu tạm 12345678; Expert phải đổi trong 7 ngày.' : 'Expert created with temporary password 12345678; it must be changed within 7 days.');
      await loadAdminData();
    } catch (error) { toast.error(error instanceof Error ? error.message : 'Unable to create expert.'); }
    finally { setSavingExpert(false); }
  };

  const handleResendExpert = async (email: string) => {
    if (!window.confirm(language === 'vi' ? `Reset mật khẩu tạm và gửi lại email cho ${email}?` : `Reset temporary password and resend activation to ${email}?`)) return;
    try {
      await resendExpertActivation(email);
      toast.success(language === 'vi' ? 'Đã gửi lại thông tin kích hoạt.' : 'Activation details resent.');
      await loadAdminData();
    } catch (error) { toast.error(error instanceof Error ? error.message : 'Unable to resend activation.'); }
  };

  const handleSoftDeleteUserClick = (user: WorkspaceUser) => {
    setUserToDelete(user);
    setIsDeleteUserModalOpen(true);
  };

  const handleConfirmSoftDeleteUser = async () => {
    if (!userToDelete) return;
    setDeletingUserId(userToDelete.id);
    try {
      await deleteUser(userToDelete.id);
      toast.success(
        language === 'vi' ? 'Đã xóa (vô hiệu hóa) người dùng thành công.' : 'User deactivated successfully.',
        t('toast.successTitle'),
      );
      await loadAdminData();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Unable to deactivate user.', t('toast.errorTitle'));
    } finally {
      setDeletingUserId(null);
      setIsDeleteUserModalOpen(false);
      setUserToDelete(null);
    }
  };

  const handleRestoreUser = async (userId: string) => {
    try {
      await restoreUser(userId);
      toast.success(
        language === 'vi' ? 'Đã khôi phục tài khoản người dùng thành công.' : 'User restored successfully.',
        t('toast.successTitle'),
      );
      await loadAdminData();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Unable to restore user.', t('toast.errorTitle'));
    }
  };

  const resetPlanForm = () => {
    setEditingPlanId(null);
    setPlanForm(emptyPlanForm);
    setPlanError(null);
  };

  const handleSavePlan = async () => {
    if (!planForm.planName.trim() || !planForm.planType.trim()) {
      setPlanError(t('admin.planNameRequired'));
      toast.warning(t('admin.planNameRequired'), t('toast.warningTitle'));
      return;
    }
    const numericLimits = [
      planForm.priceVnd ?? planForm.price,
      planForm.contractAnalysisLimit ?? planForm.maxQuota,
      planForm.aiTokenLimit,
      planForm.workspaceLimit,
      planForm.documentPerWorkspaceLimit,
      planForm.storageLimitMb,
      planForm.maxFileSizeMb,
      planForm.maxAttachedDocumentsPerSession,
      planForm.contractDraftLimit,
      planForm.expertTicketLimit,
    ];
    if ((planForm.billingCycleDays ?? planForm.durationDays) <= 0 || numericLimits.some((value) => value < 0)) {
      setPlanError(language === 'vi' ? 'Chu kỳ phải lớn hơn 0 và mọi giới hạn phải từ 0 trở lên.' : 'Billing cycle must be positive and every limit must be non-negative.');
      return;
    }

    setSavingPlan(true);
    setPlanError(null);
    setPlanActionMessage(null);

    try {
      const response = editingPlanId
        ? await updateSubscriptionPlan(editingPlanId, planForm)
        : await createSubscriptionPlan(planForm);

      const savedPlan = response.data;
      if (savedPlan) {
        setSubscriptionPlans((previous) =>
          editingPlanId
            ? previous.map((plan) => (plan.id === savedPlan.id ? savedPlan : plan))
            : [savedPlan, ...previous],
        );
      }
      const message = editingPlanId ? t('admin.planUpdated') : t('admin.planCreated');
      setPlanActionMessage(message);
      toast.success(message, t('toast.successTitle'));
      resetPlanForm();
    } catch (error) {
      const message = error instanceof Error ? error.message : t('admin.planSaveError');
      setPlanError(message);
      toast.error(message, t('toast.errorTitle'));
    } finally {
      setSavingPlan(false);
    }
  };

  const handleEditPlan = (plan: SubscriptionPlan) => {
    setEditingPlanId(plan.id);
    setPlanForm({
      planName: plan.planName,
      planType: plan.planType,
      description: plan.description ?? '',
      price: plan.price,
      durationDays: plan.durationDays,
      maxQuota: plan.maxQuota,
      name: plan.name ?? plan.planType,
      displayName: plan.displayName ?? plan.planName,
      priceVnd: plan.priceVnd ?? plan.price,
      billingCycleDays: plan.billingCycleDays ?? plan.durationDays,
      contractAnalysisLimit: plan.contractAnalysisLimit ?? plan.maxQuota,
      aiTokenLimit: plan.aiTokenLimit ?? 0,
      workspaceLimit: plan.workspaceLimit ?? 0,
      documentPerWorkspaceLimit: plan.documentPerWorkspaceLimit ?? 0,
      storageLimitMb: plan.storageLimitMb ?? 0,
      maxFileSizeMb: plan.maxFileSizeMb ?? 0,
      maxAttachedDocumentsPerSession: plan.maxAttachedDocumentsPerSession ?? 0,
      contractDraftLimit: plan.contractDraftLimit ?? 0,
      expertTicketLimit: plan.expertTicketLimit ?? 0,
      allowSystemErrorTicket: plan.allowSystemErrorTicket ?? true,
      allowQueryErrorTicket: plan.allowQueryErrorTicket ?? true,
      allowContactExpertTicket: plan.allowContactExpertTicket ?? false,
      features: plan.features ?? [],
      active: plan.active,
    });
    setActiveTab('plans');
  };

  const handleDeletePlan = async (planId: number) => {
    if (!window.confirm(t('admin.deletePlanConfirm'))) {
      return;
    }

    setSavingPlan(true);
    setPlanError(null);
    setPlanActionMessage(null);

    try {
      await deleteSubscriptionPlan(planId);
      setSubscriptionPlans((previous) => previous.filter((plan) => plan.id !== planId));
      const message = t('admin.planDeleted');
      setPlanActionMessage(message);
      toast.success(message, t('toast.successTitle'));
    } catch (error) {
      const message = error instanceof Error ? error.message : t('admin.planDeleteError');
      setPlanError(message);
      toast.error(message, t('toast.errorTitle'));
    } finally {
      setSavingPlan(false);
    }
  };

  const handleAssignTicket = async (ticketId: string) => {
    const lawyerId = ticketAssigneeById[ticketId]?.trim();

    if (!lawyerId) {
      setTicketError(t('admin.ticketSelectExpert'));
      toast.warning(t('admin.ticketSelectExpert'), t('toast.warningTitle'));
      return;
    }

    setAssigningTicketId(ticketId);
    setTicketError(null);
    setTicketActionMessage(null);

    try {
      const updatedTicket = await assignLawyerToLegalTicket(ticketId, {
        lawyer_id: Number(lawyerId),
        admin_note: 'Assigned from Admin Console',
        force_reassign: false,
      });
      setLegalTickets((previous) =>
        previous.map((ticket) => (ticket.id === ticketId ? updatedTicket : ticket)),
      );
      const message = `${t('admin.ticketAssigned')} ${ticketId}.`;
      setTicketActionMessage(message);
      toast.success(message, t('toast.successTitle'));
    } catch (error) {
      const message = error instanceof Error ? error.message : t('admin.ticketAssignError');
      setTicketError(message);
      toast.error(message, t('toast.errorTitle'));
    } finally {
      setAssigningTicketId(null);
    }
  };

  const columns: DataTableColumn<WorkspaceUser>[] = [
    {
      header: t('table.user'),
      cell: (user) => (
        <div className="flex items-center gap-md">
          <div className="flex h-10 w-10 items-center justify-center rounded-full bg-primary text-xs font-bold text-white">{user.initials}</div>
          <div>
            <p className="font-semibold">{user.name}</p>
            <p className="text-xs text-on-surface-variant dark:text-slate-400">{user.email}</p>
          </div>
        </div>
      ),
    },
    { header: t('table.role'), cell: (user) => user.role },
    {
      header: t('table.status'),
      headerClassName: 'text-center align-middle',
      className: 'text-center align-middle',
      cell: (user) => (
        <div className="flex justify-center">
          <StatusBadge
            status={user.status}
            className="min-w-[88px] justify-center text-center whitespace-nowrap"
          />
        </div>
      ),
    },
    { header: t('admin.users.lastAccess'), cell: (user) => <span className="whitespace-nowrap">{user.lastAccess}</span> },
    {
      header: t('table.actions'),
      cell: (user) => {
        const rawUser = backendUsers.find((b) => String(b.id) === user.id);
        const isActive = rawUser ? rawUser.active : user.status === 'active';
        return (
          <div className="flex items-center gap-xs">
            <Button
              variant="ghost"
              size="icon"
              title={language === 'vi' ? 'Xem chi tiết' : 'View details'}
              aria-label={t('admin.users.userActions')}
              onClick={() => void handleOpenUserDetail(user.id)}
            >
              <MoreHorizontal className="h-4 w-4" />
            </Button>
            {isActive ? (
              <Button
                variant="ghost"
                size="icon"
                title={language === 'vi' ? 'Xóa người dùng (Soft Delete)' : 'Deactivate user'}
                aria-label={language === 'vi' ? 'Xóa người dùng' : 'Deactivate user'}
                className="text-error hover:bg-error/10"
                onClick={() => handleSoftDeleteUserClick(user)}
              >
                <Trash2 className="h-4 w-4" />
              </Button>
            ) : (
              <Button
                variant="ghost"
                size="icon"
                title={language === 'vi' ? 'Khôi phục người dùng' : 'Restore user'}
                aria-label={language === 'vi' ? 'Khôi phục người dùng' : 'Restore user'}
                className="text-emerald-600 hover:bg-emerald-50 dark:text-emerald-400 dark:hover:bg-emerald-950/30"
                onClick={() => void handleRestoreUser(user.id)}
              >
                <RotateCcw className="h-4 w-4" />
              </Button>
            )}
          </div>
        );
      },
    },
  ];

  const paymentColumns: DataTableColumn<PaymentTransaction>[] = [
    {
      header: t('admin.transaction'),
      cell: (transaction) => (
        <span className="font-semibold text-primary dark:text-inverse-primary">
          {transaction.transactionCode || `#${transaction.id}`}
        </span>
      ),
    },
    { header: t('admin.plan'), cell: (transaction) => transaction.planName },
    { header: t('billing.amount'), cell: (transaction) => formatVndCurrency(transaction.amount, t('billing.free')) },
    { header: t('table.status'), cell: (transaction) => <Badge tone={transaction.paymentStatus === 'SUCCESS' ? 'green' : transaction.paymentStatus === 'FAILED' ? 'red' : 'amber'}>{transaction.paymentStatus}</Badge> },
    { header: t('table.date'), cell: (transaction) => formatDisplayDate(transaction.createdAt, '-', locale) },
  ];

  const planColumns: DataTableColumn<SubscriptionPlan>[] = [
    { header: t('billing.planName'), cell: (plan) => <span className="font-semibold">{plan.planName}</span> },
    { header: t('billing.planType'), cell: (plan) => plan.planType },
    { header: t('billing.price'), cell: (plan) => formatVndCurrency(plan.price, t('billing.free')) },
    { header: t('billing.maxQuota'), cell: (plan) => plan.maxQuota.toLocaleString() },
    { header: t('admin.duration'), cell: (plan) => `${plan.durationDays} ${t('billing.days')}` },
    { header: t('table.status'), cell: (plan) => <Badge tone={plan.active ? 'green' : 'slate'}>{plan.active ? t('admin.active') : t('admin.inactive')}</Badge> },
    {
      header: t('table.actions'),
      cell: (plan) => (
        <div className="flex flex-wrap gap-xs">
          <Button variant="ghost" size="sm" onClick={() => handleEditPlan(plan)}>
            {t('actions.edit')}
          </Button>
          <Button variant="ghost" size="sm" onClick={() => void handleDeletePlan(plan.id)}>
            {t('actions.delete')}
          </Button>
        </div>
      ),
    },
  ];

  const ticketColumns: DataTableColumn<LegalTicket>[] = [
    {
      header: t('admin.ticket'),
      cell: (ticket) => (
        <Link className="break-all font-semibold text-primary hover:underline dark:text-inverse-primary" to={`/admin/tickets/${ticket.id}`}>
          {ticket.id}
        </Link>
      ),
    },
    { header: t('table.status'), cell: (ticket) => <Badge tone="amber">{getLegalTicketStatusLabel(ticket.status, t)}</Badge> },
    {
      header: t('table.risk'),
      cell: (ticket) =>
        ticket.risk_level ? (
          <Badge tone={ticket.risk_level === 'HIGH' ? 'red' : 'amber'}>
            {t(`risk.${ticket.risk_level.toLowerCase()}`)}
          </Badge>
        ) : '-',
    },
    { header: t('chat.legalDomain'), cell: (ticket) => ticket.legal_domain ?? '-' },
    { header: t('workspace.createdAt'), cell: (ticket) => formatDisplayDate(ticket.created_at, '-', locale) },
    {
      header: t('admin.assign'),
      cell: (ticket) => (
        <div className="flex min-w-56 flex-wrap gap-xs">
          <select
            className="rounded-lg border border-outline-variant bg-white px-sm py-xs text-xs dark:border-slate-700 dark:bg-slate-900"
            value={ticketAssigneeById[ticket.id] ?? ticket.assigned_lawyer_id ?? ''}
            onChange={(event) =>
              setTicketAssigneeById((previous) => ({
                ...previous,
                [ticket.id]: event.target.value,
              }))
            }
            disabled={expertUsers.length === 0}
          >
            <option value="">{expertUsers.length === 0 ? t('admin.noExpertUsers') : t('admin.selectExpert')}</option>
            {expertUsers.map((user) => (
              <option key={user.id} value={String(user.id)}>
                {`${user.firstName} ${user.lastName}`.trim() || user.email}
              </option>
            ))}
          </select>
          <Button
            variant="ghost"
            size="sm"
            disabled={assigningTicketId === ticket.id || expertUsers.length === 0}
            onClick={() => void handleAssignTicket(ticket.id)}
          >
            {t('admin.assign')}
          </Button>
        </div>
      ),
    },
  ];

  const renderUsersTab = () => (
    <>
      {adminError && (
        <div className="mb-lg rounded-xl border border-error/40 bg-error/10 p-md text-sm text-error">
          {adminError}
        </div>
      )}
      <section className="grid gap-gutter md:grid-cols-3">
        <AdminMetricCard label={t('admin.users.activeUsersLabel')} value={isLoadingAdminData ? '...' : String(activeUsersCount)} detail={t('admin.users.activeUsersDetail')} icon={<UsersRound className="h-5 w-5" />} />
        <AdminMetricCard label={t('admin.users.totalUsersLabel')} value={isLoadingAdminData ? '...' : String(adminUsers.length)} detail={t('admin.users.totalUsersDetail')} icon={<ShieldCheck className="h-5 w-5" />} tone="green" />
        <AdminMetricCard label={t('admin.users.paymentRowsLabel')} value={isLoadingAdminData ? '...' : String(paymentTransactions.length)} detail={t('admin.users.paymentRowsDetail')} icon={<Receipt className="h-5 w-5" />} tone="gold" />
      </section>

      <div className="mt-xl grid gap-gutter xl:grid-cols-[1.2fr_0.8fr]">
        <div className="space-y-gutter">
          <Card
            title={t('admin.users')}
            actions={
              <div className="flex items-center gap-sm">
                <Badge tone="blue">{isLoadingAdminData ? '...' : `${adminUsers.length} users`}</Badge>
                <Button
                  size="sm"
                  leftIcon={<UserPlus className="h-4 w-4" />}
                  onClick={() => setCreateExpertOpen(true)}
                >
                  {language === 'vi' ? 'Tạo Expert' : 'Create Expert'}
                </Button>
              </div>
            }
          >
            {isLoadingAdminData ? (
              <p className="text-sm text-on-surface-variant dark:text-slate-400">{t('admin.loadingUsers')}</p>
            ) : (
              <DataTable
                columns={columns}
                data={adminUsers}
                getRowKey={(user) => user.id}
                emptyMessage={t('admin.noUsers')}
              />
            )}
          </Card>

          <Card
            title={t('admin.paymentTransactions')}
            subtitle={t('admin.paymentTransactionsSubtitle')}
            actions={<Receipt className="h-5 w-5 text-primary dark:text-inverse-primary" />}
          >
            {isLoadingAdminData ? (
              <p className="text-sm text-on-surface-variant dark:text-slate-400">{t('admin.loadingPayments')}</p>
            ) : (
              <DataTable
                columns={paymentColumns}
                data={paymentTransactions}
                getRowKey={(transaction) => String(transaction.id)}
                emptyMessage={t('admin.noPayments')}
              />
            )}
          </Card>
        </div>

        <aside className="space-y-gutter">
          <Card tone="ai">
            <EmptyState
              title={t('admin.users.aiRecommendationUnavailableTitle')}
              description={t('admin.users.aiRecommendationUnavailableDescription')}
            />
          </Card>

          <Card title={t('admin.security')}>
            <EmptyState
              title={t('admin.securityMetricsUnavailableTitle')}
              description={t('admin.securityMetricsUnavailableDescription')}
            />
          </Card>
        </aside>
      </div>
    </>
  );

  const renderPlansTab = () => (
    <div className="grid gap-gutter xl:grid-cols-[0.9fr_1.1fr]">
      <Card title={editingPlanId ? t('admin.updatePlan') : t('admin.createPlan')}>
        <div className="space-y-md">
          {planError && (
            <p className="rounded-lg bg-error-container px-md py-sm text-sm font-semibold text-risk-high-text dark:bg-red-950/40 dark:text-red-200">
              {planError}
            </p>
          )}
          {planActionMessage && (
            <p className="rounded-lg bg-emerald-50 px-md py-sm text-sm font-semibold text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-200">
              {planActionMessage}
            </p>
          )}
          <div className="grid gap-md md:grid-cols-2">
            <label className="text-sm font-semibold">
              {t('billing.planName')}
              <input
                className="form-field mt-xs"
                value={planForm.planName}
                onChange={(event) => setPlanForm((previous) => ({ ...previous, planName: event.target.value, displayName: event.target.value }))}
              />
            </label>
            <label className="text-sm font-semibold">
              {t('billing.planType')}
              <input
                className="form-field mt-xs"
                value={planForm.planType}
                onChange={(event) => setPlanForm((previous) => ({ ...previous, planType: event.target.value, name: event.target.value }))}
              />
            </label>
            <label className="text-sm font-semibold">
              {t('billing.price')}
              <input
                className="form-field mt-xs"
                type="number"
                min={0}
                value={planForm.price}
                onChange={(event) => setPlanForm((previous) => ({ ...previous, price: Number(event.target.value), priceVnd: Number(event.target.value) }))}
              />
            </label>
            <label className="text-sm font-semibold">
              {t('admin.duration')}
              <input
                className="form-field mt-xs"
                type="number"
                min={1}
                value={planForm.durationDays}
                onChange={(event) => setPlanForm((previous) => ({ ...previous, durationDays: Number(event.target.value), billingCycleDays: Number(event.target.value) }))}
              />
            </label>
            <label className="text-sm font-semibold">
              {t('billing.maxQuota')}
              <input
                className="form-field mt-xs"
                type="number"
                min={0}
                value={planForm.maxQuota}
                onChange={(event) => setPlanForm((previous) => ({ ...previous, maxQuota: Number(event.target.value), contractAnalysisLimit: Number(event.target.value) }))}
              />
            </label>
            <label className="flex items-center gap-sm pt-lg text-sm font-semibold">
              <input
                type="checkbox"
                checked={Boolean(planForm.active)}
                onChange={(event) => setPlanForm((previous) => ({ ...previous, active: event.target.checked }))}
              />
              {t('admin.active')}
            </label>
          </div>
          <div className="grid gap-md md:grid-cols-2">
            {([
              ['aiTokenLimit', 'AI token limit'],
              ['workspaceLimit', 'Workspace limit'],
              ['documentPerWorkspaceLimit', 'Documents / workspace'],
              ['storageLimitMb', 'Storage limit (MB)'],
              ['maxFileSizeMb', 'Max file size (MB)'],
              ['maxAttachedDocumentsPerSession', 'Attached docs / session'],
              ['contractDraftLimit', 'Contract draft limit'],
              ['expertTicketLimit', 'Expert ticket limit'],
            ] as const).map(([field, label]) => (
              <label key={field} className="text-sm font-semibold">
                {label}
                <input
                  className="form-field mt-xs"
                  type="number"
                  min={0}
                  value={planForm[field]}
                  onChange={(event) => setPlanForm((previous) => ({ ...previous, [field]: Number(event.target.value) }))}
                />
              </label>
            ))}
          </div>
          <div className="grid gap-sm rounded-xl border border-outline-variant/60 p-md md:grid-cols-3 dark:border-slate-700">
            {([
              ['allowSystemErrorTicket', 'SYSTEM_ERROR'],
              ['allowQueryErrorTicket', 'QUERY_ERROR'],
              ['allowContactExpertTicket', 'CONTACT_EXPERT'],
            ] as const).map(([field, label]) => (
              <label key={field} className="flex items-center gap-sm text-sm font-semibold">
                <input type="checkbox" checked={planForm[field]} onChange={(event) => setPlanForm((previous) => ({ ...previous, [field]: event.target.checked }))} />
                Cho phép {label}
              </label>
            ))}
          </div>
          <label className="block text-sm font-semibold">
            Features (mỗi dòng một tính năng)
            <textarea
              className="form-field mt-xs min-h-24"
              value={planForm.features.join('\n')}
              onChange={(event) => setPlanForm((previous) => ({ ...previous, features: event.target.value.split('\n').map((item) => item.trim()).filter(Boolean) }))}
            />
          </label>
          <label className="block text-sm font-semibold">
            {t('common.description')}
            <textarea
              className="form-field mt-xs min-h-24"
              value={planForm.description ?? ''}
              onChange={(event) => setPlanForm((previous) => ({ ...previous, description: event.target.value }))}
            />
          </label>
          <div className="flex flex-wrap gap-sm">
            <Button onClick={() => void handleSavePlan()} disabled={savingPlan}>
              {savingPlan ? t('admin.saving') : editingPlanId ? t('admin.updatePlan') : t('admin.createPlan')}
            </Button>
            <Button variant="secondary" onClick={resetPlanForm} disabled={savingPlan}>
              {t('actions.reset')}
            </Button>
          </div>
        </div>
      </Card>

      <Card title={t('admin.plans')} actions={<Badge tone="blue">{subscriptionPlans.length} {t('admin.tabs.plans')}</Badge>}>
        {isLoadingAdminData ? (
          <p className="text-sm text-on-surface-variant dark:text-slate-400">{t('admin.loadingPlans')}</p>
        ) : (
          <DataTable
            columns={planColumns}
            data={subscriptionPlans}
            getRowKey={(plan) => String(plan.id)}
            emptyMessage={t('admin.noPlans')}
          />
        )}
      </Card>
    </div>
  );

  const renderTicketsTab = () => (
    <div className="grid gap-gutter xl:grid-cols-[1.2fr_0.8fr]">
      <Card title={t('admin.tabs.tickets')} subtitle={t('admin.tickets.persistedSubtitle')}>
        {ticketError && (
          <p className="mb-md rounded-lg bg-error-container px-md py-sm text-sm font-semibold text-risk-high-text dark:bg-red-950/40 dark:text-red-200">
            {ticketError}
          </p>
        )}
        {ticketActionMessage && (
          <p className="mb-md rounded-lg bg-emerald-50 px-md py-sm text-sm font-semibold text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-200">
            {ticketActionMessage}
          </p>
        )}
        {isLoadingAdminData ? (
          <p className="text-sm text-on-surface-variant dark:text-slate-400">{t('admin.loadingTickets')}</p>
        ) : (
          <DataTable
            columns={ticketColumns}
            data={legalTickets}
            getRowKey={(ticket) => ticket.id}
            emptyMessage={t('admin.noTickets')}
          />
        )}
      </Card>

      <aside className="space-y-gutter">
        <Card tone="ai">
          <h2 className="text-title-lg font-semibold">{t('admin.ticketAssignmentContract')}</h2>
          <p className="mt-sm text-sm leading-6 text-on-surface-variant dark:text-slate-300">
            {t('admin.ticketAssignmentDescription')}
          </p>
        </Card>
        <Card title={t('admin.availableExperts')} actions={<Badge tone="blue">{expertUsers.length}</Badge>}>
          <div className="space-y-sm">
            {expertUsers.length === 0 ? (
              <p className="text-sm text-on-surface-variant dark:text-slate-400">{t('admin.noExpertsReturned')}</p>
            ) : (
              expertUsers.map((user) => (
                <div key={user.id} className="rounded-lg border border-legal-border p-sm text-sm dark:border-slate-700">
                  <p className="font-semibold">{`${user.firstName} ${user.lastName}`.trim() || user.email}</p>
                  <p className="text-xs text-on-surface-variant dark:text-slate-400">{user.email}</p>
                </div>
              ))
            )}
          </div>
        </Card>
      </aside>
    </div>
  );

  const renderWorkspaceTab = () => (
    <div className="grid gap-gutter xl:grid-cols-[1.15fr_0.85fr]">
      <Card title={t('admin.workspace')} subtitle={t('admin.workspace.subtitle')}>
        <div className="grid gap-md sm:grid-cols-2">
          <div className="rounded-lg bg-surface-container-low p-md dark:bg-slate-800">
            <p className="label-uppercase">{t('admin.workspace.workspaceName')}</p>
            <p className="mt-xs text-sm font-semibold">{t('admin.workspace.legalTeamName')}</p>
          </div>
          <div className="rounded-lg bg-surface-container-low p-md dark:bg-slate-800">
            <p className="label-uppercase">{t('admin.workspace.dataRegion')}</p>
            <p className="mt-xs text-sm font-semibold">{t('admin.workspace.dataRegionValue')}</p>
          </div>
          <div className="rounded-lg bg-surface-container-low p-md dark:bg-slate-800">
            <p className="label-uppercase">{t('admin.workspace.defaultJurisdiction')}</p>
            <p className="mt-xs text-sm font-semibold">{t('admin.workspace.defaultJurisdictionValue')}</p>
          </div>
          <div className="rounded-lg bg-surface-container-low p-md dark:bg-slate-800">
            <p className="label-uppercase">{t('admin.workspace.retentionPolicy')}</p>
            <p className="mt-xs text-sm font-semibold">{t('admin.workspace.retentionPolicyValue')}</p>
          </div>
        </div>
        <div className="mt-md flex flex-wrap gap-sm">
          <Button variant="secondary" onClick={() => toast.success(t('admin.workspaceSaved'), t('toast.successTitle'))}>{t('actions.save')}</Button>
          <Button variant="ghost">{t('actions.cancel')}</Button>
        </div>
      </Card>

      <aside className="space-y-gutter">
        <Card title={t('admin.workspace.connectedSystems')}>
          <div className="space-y-sm text-sm">
            <p className="rounded-lg border border-legal-border p-sm dark:border-slate-700">{t('admin.workspace.connectedSystemsDms')}</p>
            <p className="rounded-lg border border-legal-border p-sm dark:border-slate-700">{t('admin.workspace.connectedSystemsSso')}</p>
            <p className="rounded-lg border border-legal-border p-sm dark:border-slate-700">{t('admin.workspace.connectedSystemsAudit')}</p>
          </div>
        </Card>
        <Card title={t('admin.workspace.policyBaseline')}>
          <p className="text-sm leading-6 text-on-surface-variant dark:text-slate-400">
            {t('admin.workspace.policyBaselineText')}
          </p>
        </Card>
      </aside>
    </div>
  );

  const renderLanguageTab = () => (
    <div className="grid gap-gutter xl:grid-cols-[1.05fr_0.95fr]">
      <Card title={t('admin.language')} subtitle={t('admin.language.subtitle')}>
        <div className="space-y-md">
          <div>
            <p className="label-uppercase mb-sm">{t('admin.language.interfaceLanguage')}</p>
            <LanguageToggle />
          </div>
          <div className="rounded-lg bg-surface-container-low p-md text-sm leading-6 dark:bg-slate-800">
            {t('admin.language.helperText')}
          </div>
        </div>
      </Card>

      <Card title={t('admin.language.translationQuality')}>
        <div className="space-y-md text-sm">
          <div>
            <div className="mb-xs flex items-center justify-between">
              <span>{t('admin.language.uiLabelsCoverage')}</span>
              <span className="font-semibold">98%</span>
            </div>
            <ProgressBar value={98} />
          </div>
          <div>
            <div className="mb-xs flex items-center justify-between">
              <span>{t('admin.language.legalGlossaryConsistency')}</span>
              <span className="font-semibold">95%</span>
            </div>
            <ProgressBar value={95} />
          </div>
          <Button variant="secondary" className="w-full">
            {t('admin.language.exportLanguageReport')}
          </Button>
        </div>
      </Card>
    </div>
  );

  const renderThemeTab = () => (
    <div className="grid gap-gutter xl:grid-cols-[1.05fr_0.95fr]">
      <Card title={t('admin.appearance')} actions={<Palette className="h-5 w-5 text-secondary dark:text-accent-gold" />}>
        <div className="space-y-md">
          <div>
            <p className="label-uppercase mb-sm">{t('admin.theme.themeMode')}</p>
            <div className="grid grid-cols-3 gap-2">
              {themeOptions.map((option) => {
                const Icon = option.icon;
                const active = theme === option.mode;
                return (
                  <button
                    key={option.mode}
                    type="button"
                    aria-pressed={active}
                    className={`inline-flex h-11 items-center justify-center gap-2 rounded-lg border text-sm font-semibold transition ${
                      active
                        ? 'border-primary bg-primary text-white shadow-sm dark:border-inverse-primary dark:bg-inverse-primary dark:text-slate-950'
                        : 'border-legal-border bg-white text-on-surface-variant hover:bg-surface-container-low dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300 dark:hover:bg-slate-800'
                    }`}
                    onClick={() => setTheme(option.mode)}
                  >
                    <Icon className="h-4 w-4" aria-hidden="true" />
                    <span>{t(option.labelKey)}</span>
                  </button>
                );
              })}
            </div>
          </div>

          <div>
            <p className="label-uppercase mb-sm">{t('admin.theme.legalTextSize')}</p>
            <div className="grid grid-cols-3 gap-sm">
              {legalTextSizes.map((size) => {
                const active = legalTextSize === size;
                return (
                  <button
                    key={size}
                    type="button"
                    className={`h-10 rounded-lg border text-sm font-semibold transition ${
                      active
                        ? 'border-primary bg-surface-container text-primary dark:border-inverse-primary dark:bg-slate-800 dark:text-inverse-primary'
                        : 'border-legal-border bg-white hover:bg-surface-container-low dark:border-slate-700 dark:bg-slate-900 dark:hover:bg-slate-800'
                    }`}
                    onClick={() => setLegalTextSize(size)}
                  >
                    {size}
                  </button>
                );
              })}
            </div>
          </div>
        </div>
      </Card>

      <Card title={t('admin.theme.preview')}>
        <p style={{ fontSize: legalTextSize }} className="leading-7 text-on-surface-variant dark:text-slate-300">
          {t('admin.theme.previewText')}
        </p>
      </Card>
    </div>
  );

  const renderSecurityTab = () => (
    <div className="grid gap-gutter xl:grid-cols-[1.15fr_0.85fr]">
      <Card title={t('admin.security')} subtitle={t('admin.security.subtitle')}>
        <EmptyState
          title={t('admin.securityMetricsUnavailableTitle')}
          description={t('admin.securityMetricsUnavailableDescription')}
        />
      </Card>

      <Card tone="ai">
        <EmptyState
          title={t('admin.securityGuidanceUnavailableTitle')}
          description={t('admin.securityGuidanceUnavailableDescription')}
        />
      </Card>
    </div>
  );

  const renderAiConfigTab = () => (
    <div className="grid gap-gutter xl:grid-cols-[1.15fr_0.85fr]">
      <Card title={t('admin.aiConfig')} subtitle={t('admin.aiConfig.subtitle')}>
        <div className="grid gap-md md:grid-cols-2">
          <div className="rounded-lg border border-legal-border p-md dark:border-slate-700">
            <div className="mb-sm flex items-center gap-sm">
              <Cpu className="h-4 w-4 text-primary dark:text-inverse-primary" />
              <p className="font-semibold">{t('admin.aiConfig.primaryModel')}</p>
            </div>
            <p className="text-sm text-on-surface-variant dark:text-slate-400">{t('admin.aiConfig.primaryModelText')}</p>
          </div>
          <div className="rounded-lg border border-legal-border p-md dark:border-slate-700">
            <div className="mb-sm flex items-center gap-sm">
              <Database className="h-4 w-4 text-primary dark:text-inverse-primary" />
              <p className="font-semibold">{t('admin.aiConfig.retrievalStrategy')}</p>
            </div>
            <p className="text-sm text-on-surface-variant dark:text-slate-400">{t('admin.aiConfig.retrievalStrategyText')}</p>
          </div>
        </div>
        <div className="mt-md flex flex-wrap gap-sm">
          <Button variant="secondary" onClick={() => toast.success(t('editor.saveQueued'), t('toast.successTitle'))}>{t('actions.save')}</Button>
          <Button variant="ghost" onClick={() => toast.info(t('admin.validationQueued'), t('toast.infoTitle'))}>{t('admin.aiConfig.runValidation')}</Button>
        </div>
      </Card>

      <Card title={t('admin.aiConfig.guardrails')} actions={<ShieldCheck className="h-5 w-5 text-primary dark:text-inverse-primary" />}>
        <ul className="space-y-sm text-sm text-on-surface-variant dark:text-slate-400">
          <li className="rounded-lg bg-surface-container-low p-sm dark:bg-slate-800">{t('admin.aiConfig.guardrailsCitations')}</li>
          <li className="rounded-lg bg-surface-container-low p-sm dark:bg-slate-800">{t('admin.aiConfig.guardrailsPii')}</li>
          <li className="rounded-lg bg-surface-container-low p-sm dark:bg-slate-800">{t('admin.aiConfig.guardrailsConfidence')}</li>
        </ul>
      </Card>
    </div>
  );

  const renderActiveTab = () => {
    switch (activeTab) {
      case 'users':
        return renderUsersTab();
      case 'plans':
        return renderPlansTab();
      case 'tickets':
        return renderTicketsTab();
      case 'workspace':
        return renderWorkspaceTab();
      case 'language':
        return renderLanguageTab();
      case 'theme':
        return renderThemeTab();
      case 'security':
        return renderSecurityTab();
      case 'aiConfig':
        return renderAiConfigTab();
      default:
        return renderUsersTab();
    }
  };

  return (
    <div>
      <PageHeader
        title={t('admin.title')}
        subtitle={t('admin.subtitle')}
        actions={
          <>
            <Button
              variant="secondary"
              leftIcon={<RefreshCw className="h-4 w-4" />}
              onClick={() => void loadAdminData()}
              disabled={isLoadingAdminData}
            >
              {t('billing.refresh')}
            </Button>
          </>
        }
      />

      <Tabs
        variant="pill"
        activeId={activeTab}
        tabListLabel={t('admin.title')}
        getTabId={(id) => getTabId(id as AdminTabId)}
        getPanelId={(id) => getPanelId(id as AdminTabId)}
        onChange={(id) => setActiveTab(id as AdminTabId)}
        items={adminTabs.map((tab) => ({ id: tab.id, label: tab.label ?? t(tab.labelKey ?? '') }))}
      />

      <section
        className="mt-xl"
        role="tabpanel"
        id={getPanelId(activeTab)}
        aria-labelledby={getTabId(activeTab)}
      >
        {renderActiveTab()}
      </section>

      <Modal
        open={userDetailOpen}
        title={t('admin.userDetailTitle')}
        onClose={() => setUserDetailOpen(false)}
        footer={
          <Button variant="secondary" onClick={() => setUserDetailOpen(false)}>
            {t('actions.close')}
          </Button>
        }
      >
        {userDetailLoading ? (
          <p className="text-sm text-on-surface-variant dark:text-slate-400">{t('admin.loadingUserDetail')}</p>
        ) : selectedUserDetail ? (
          <dl className="grid gap-md text-sm sm:grid-cols-2">
            <div>
              <dt className="label-uppercase">{t('common.id')}</dt>
              <dd className="mt-xs font-semibold">{selectedUserDetail.id}</dd>
            </div>
            <div>
              <dt className="label-uppercase">{t('table.status')}</dt>
              <dd className="mt-xs">
                <StatusBadge status={selectedUserDetail.active ? 'active' : 'offline'} />
              </dd>
            </div>
            <div>
              <dt className="label-uppercase">{t('common.name')}</dt>
              <dd className="mt-xs font-semibold">
                {`${selectedUserDetail.firstName} ${selectedUserDetail.lastName}`.trim() || selectedUserDetail.email}
              </dd>
            </div>
            <div>
              <dt className="label-uppercase">{t('auth.corporateEmail')}</dt>
              <dd className="mt-xs">{selectedUserDetail.email}</dd>
            </div>
            <div>
              <dt className="label-uppercase">{t('table.role')}</dt>
              <dd className="mt-xs">{selectedUserDetail.role}</dd>
            </div>
            {selectedUserDetail.role === 'EXPERT' && (
              <div className="sm:col-span-2">
                <p className="text-sm text-on-surface-variant dark:text-slate-400">{selectedUserDetail.specialty || '-'} · {selectedUserDetail.legalDomain || '-'}</p>
                <Button className="mt-sm" variant="secondary" onClick={() => void handleResendExpert(selectedUserDetail.email)}>{language === 'vi' ? 'Gửi lại kích hoạt' : 'Resend activation'}</Button>
              </div>
            )}
          </dl>
        ) : (
          <p className="text-sm text-on-surface-variant dark:text-slate-400">{t('admin.noUserDetail')}</p>
        )}
      </Modal>

      <Modal open={createExpertOpen} title={language === 'vi' ? 'Tạo tài khoản Expert' : 'Create Expert account'} onClose={() => setCreateExpertOpen(false)} footer={<><Button variant="secondary" onClick={() => setCreateExpertOpen(false)}>{t('actions.close')}</Button><Button onClick={() => void handleCreateExpert()} disabled={savingExpert}>{savingExpert ? (language === 'vi' ? 'Đang tạo…' : 'Creating…') : (language === 'vi' ? 'Tạo Expert' : 'Create Expert')}</Button></>}>
        <p className="mb-md text-sm text-on-surface-variant dark:text-slate-400">{language === 'vi' ? 'Không cần nhập mật khẩu. Hệ thống dùng mật khẩu tạm mặc định và yêu cầu Expert đổi trong 7 ngày.' : 'No password input is needed. The system issues a temporary password that must be changed within 7 days.'}</p>
        <div className="grid gap-md sm:grid-cols-2">
          <label className="text-sm font-semibold">{language === 'vi' ? 'Họ' : 'First name'}<input className="form-field mt-xs" value={expertForm.firstName} onChange={(e) => setExpertForm((v) => ({ ...v, firstName: e.target.value }))} /></label>
          <label className="text-sm font-semibold">{language === 'vi' ? 'Tên' : 'Last name'}<input className="form-field mt-xs" value={expertForm.lastName} onChange={(e) => setExpertForm((v) => ({ ...v, lastName: e.target.value }))} /></label>
          <label className="text-sm font-semibold sm:col-span-2">Email<input className="form-field mt-xs" type="email" value={expertForm.email} onChange={(e) => setExpertForm((v) => ({ ...v, email: e.target.value }))} /></label>
          <label className="text-sm font-semibold">{language === 'vi' ? 'Chuyên môn' : 'Specialty'}<input className="form-field mt-xs" value={expertForm.specialty} onChange={(e) => setExpertForm((v) => ({ ...v, specialty: e.target.value }))} /></label>
          <label className="text-sm font-semibold">{language === 'vi' ? 'Lĩnh vực pháp lý' : 'Legal domain'}<input className="form-field mt-xs" value={expertForm.legalDomain} onChange={(e) => setExpertForm((v) => ({ ...v, legalDomain: e.target.value }))} /></label>
          <label className="text-sm font-semibold sm:col-span-2">{language === 'vi' ? 'Mô tả kinh nghiệm' : 'Experience description'}<textarea className="form-field mt-xs min-h-24" value={expertForm.description} onChange={(e) => setExpertForm((v) => ({ ...v, description: e.target.value }))} /></label>
        </div>
      </Modal>

      {/* Custom Soft Delete User Confirmation Modal */}
      {isDeleteUserModalOpen && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center bg-slate-950/70 p-md" role="dialog" aria-modal="true">
          <div className="w-full max-w-md overflow-hidden rounded-2xl border border-slate-800 bg-[#202123] p-lg shadow-2xl text-left">
            <h3 className="text-lg font-bold text-white">
              {t("admin.users.deleteConfirmTitle")}
            </h3>
            <p className="mt-md text-sm text-slate-300">
              {t("admin.users.deleteConfirmPrefix")}
              <strong className="font-semibold text-white">{userToDelete?.name || userToDelete?.email}</strong>
              {t("admin.users.deleteConfirmSuffix")}
            </p>
            <div className="flex justify-end gap-sm mt-lg">
              <button
                type="button"
                className="px-lg py-sm rounded-full border border-slate-600 text-slate-200 hover:bg-slate-800 text-sm font-semibold transition"
                onClick={() => {
                  setIsDeleteUserModalOpen(false);
                  setUserToDelete(null);
                }}
              >
                {t("actions.cancel")}
              </button>
              <button
                type="button"
                className="px-lg py-sm rounded-full bg-[#ff003c] text-white hover:bg-red-700 text-sm font-semibold transition disabled:opacity-50"
                onClick={() => void handleConfirmSoftDeleteUser()}
                disabled={deletingUserId === userToDelete?.id}
              >
                {t("actions.delete")}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
