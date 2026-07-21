import {
  Check,
  ChevronRight,
  Eye,
  EyeOff,
  KeyRound,
  Languages,
  Lock,
  Mail,
  Monitor,
  Moon,
  Palette,
  Save,
  Shield,
  Sparkles,
  Sun,
  UserCheck,
  UserRound,
  Zap,
} from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { PageHeader } from '../../components/common/PageHeader';
import { ProgressBar } from '../../components/common/ProgressBar';
import { Tabs } from '../../components/common/Tabs';
import { useI18n } from '../../hooks/useI18n';
import { useToast } from '../../hooks/useToast';
import { changePassword, updateProfile } from '../../services/user.service';
import { getMyCustomerPlan, getSubscriptionUsageSummary } from '../../services/subscription.service';
import { useAppStore, type Language, type ThemeMode } from '../../store/AppStore';
import { cn } from '../../utils/cn';
import { translate } from '../../utils/i18n';
import type { SubscriptionUsageSummary } from '../../types/subscription';

type ProfileTab = 'info' | 'workspace' | 'security' | 'appearance';

const themeOptions: Array<{ mode: ThemeMode; labelKey: string; icon: typeof Sun }> = [
  { mode: 'light', labelKey: 'theme.light', icon: Sun },
  { mode: 'dark', labelKey: 'theme.dark', icon: Moon },
  { mode: 'system', labelKey: 'theme.system', icon: Monitor },
];

const languageOptions: Array<{ language: Language; labelKey: string; shortLabel: string }> = [
  { language: 'vi', labelKey: 'language.vietnamese', shortLabel: 'VI' },
  { language: 'en', labelKey: 'language.english', shortLabel: 'EN' },
];

const calcPercent = (used: number, limit: number) =>
  limit <= 0 ? 0 : Math.min(100, Math.round((used / limit) * 100));

export function ProfilePage() {
  const { t, language } = useI18n();
  const toast = useToast();
  const { user, updateUser, theme, setTheme, setLanguage } = useAppStore();

  const [activeTab, setActiveTab] = useState<ProfileTab>('info');

  // Profile Form state
  const [firstName, setFirstName] = useState(user?.firstName ?? '');
  const [lastName, setLastName] = useState(user?.lastName ?? '');

  const [savingProfile, setSavingProfile] = useState(false);

  // Security Password Form state
  const [oldPassword, setOldPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmNewPassword, setConfirmNewPassword] = useState('');
  const [showOldPassword, setShowOldPassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [changingPassword, setChangingPassword] = useState(false);

  // Subscription Usage state
  const [planName, setPlanName] = useState('FREE');
  const [usageSummary, setUsageSummary] = useState<SubscriptionUsageSummary | null>(null);
  const [loadingUsage, setLoadingUsage] = useState(false);

  useEffect(() => {
    if (user) {
      setFirstName(user.firstName ?? '');
      setLastName(user.lastName ?? '');

    }
  }, [user]);

  useEffect(() => {
    if (user?.role === 'CUSTOMER') {
      setLoadingUsage(true);
      Promise.all([getMyCustomerPlan(), getSubscriptionUsageSummary()])
        .then(([planRes, usageRes]) => {
          setPlanName(planRes.data?.subscriptionPlan.displayName ?? planRes.data?.subscriptionPlan.planName ?? 'FREE');
          setUsageSummary(usageRes.data ?? null);
        })
        .catch(() => {
          setPlanName('FREE');
        })
        .finally(() => setLoadingUsage(false));
    }
  }, [user]);

  const displayName = user ? `${user.firstName} ${user.lastName}`.trim() || user.email : t('common.guest');
  const initialsSource = user ? `${user.firstName} ${user.lastName}`.trim() : '';
  const avatarInitials =
    initialsSource.length > 1
      ? initialsSource
          .split(' ')
          .filter(Boolean)
          .map((segment) => segment[0]?.toUpperCase())
          .join('')
          .slice(0, 2)
      : user && user.email
        ? user.email.slice(0, 2).toUpperCase()
        : '--';

  const roleLabel =
    user?.role === 'ADMIN'
      ? 'Quản trị viên (Admin)'
      : user?.role === 'EXPERT'
        ? 'Chuyên gia pháp lý (Lawyer)'
        : 'Khách hàng (Customer)';

  const handleSaveProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!firstName.trim() || !lastName.trim()) {
      toast.warning(
        language === 'vi' ? 'Họ và tên không được để trống' : 'First and last name are required',
        t('toast.warningTitle'),
      );
      return;
    }

    setSavingProfile(true);
    try {
      const updatedUser = await updateProfile({
        firstName: firstName.trim(),
        lastName: lastName.trim(),
      });

      updateUser({
        firstName: updatedUser.firstName,
        lastName: updatedUser.lastName,
      });

      toast.success(
        language === 'vi' ? 'Đã cập nhật thông tin hồ sơ thành công!' : 'Profile updated successfully!',
        t('toast.successTitle'),
      );
    } catch (err) {
      toast.error(
        err instanceof Error ? err.message : language === 'vi' ? 'Không thể cập nhật hồ sơ' : 'Failed to update profile',
        t('toast.errorTitle'),
      );
    } finally {
      setSavingProfile(false);
    }
  };

  const handleChangePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!oldPassword || !newPassword || !confirmNewPassword) {
      toast.warning(
        language === 'vi' ? 'Vui lòng điền đầy đủ tất cả các trường mật khẩu' : 'Please fill out all password fields',
        t('toast.warningTitle'),
      );
      return;
    }

    if (newPassword !== confirmNewPassword) {
      toast.warning(
        language === 'vi' ? 'Mật khẩu mới và xác nhận mật khẩu không khớp' : 'New passwords do not match',
        t('toast.warningTitle'),
      );
      return;
    }

    if (newPassword.length < 6) {
      toast.warning(
        language === 'vi' ? 'Mật khẩu phải có ít nhất 6 ký tự' : 'Password must be at least 6 characters',
        t('toast.warningTitle'),
      );
      return;
    }

    setChangingPassword(true);
    try {
      await changePassword({ oldPassword, newPassword, confirmNewPassword });
      toast.success(
        language === 'vi' ? 'Đã đổi mật khẩu thành công!' : 'Password changed successfully!',
        t('toast.successTitle'),
      );
      setOldPassword('');
      setNewPassword('');
      setConfirmNewPassword('');
    } catch (err) {
      toast.error(
        err instanceof Error ? err.message : language === 'vi' ? 'Đổi mật khẩu thất bại' : 'Failed to change password',
        t('toast.errorTitle'),
      );
    } finally {
      setChangingPassword(false);
    }
  };

  const tabsItems = [
    { id: 'info', label: language === 'vi' ? 'Thông tin cá nhân' : 'Profile Details' },
    { id: 'workspace', label: language === 'vi' ? 'Workspace & Gói dịch vụ' : 'Workspace & Plan' },
    { id: 'security', label: language === 'vi' ? 'Bảo mật tài khoản' : 'Security' },
    { id: 'appearance', label: language === 'vi' ? 'Giao diện & Ngôn ngữ' : 'Theme & Language' },
  ];

  return (
    <div className="space-y-xl">
      <PageHeader
        title={language === 'vi' ? 'Hồ sơ & Tài khoản' : 'Profile & Account'}
        subtitle={language === 'vi' ? 'Quản lý thông tin cá nhân, cài đặt bảo mật và tài nguyên workspace.' : 'Manage personal details, security settings, and workspace resources.'}
      />

      {/* Hero Profile Banner Card matching Dashboard styling */}
      <div className="relative overflow-hidden rounded-2xl border border-legal-border bg-gradient-to-r from-slate-900 via-primary/95 to-slate-950 p-lg text-white shadow-xl dark:border-slate-800 dark:from-slate-950 dark:via-slate-900 dark:to-slate-950">
        <div className="absolute -right-10 -top-10 h-64 w-64 rounded-full bg-primary/20 blur-3xl" aria-hidden="true" />
        <div className="relative flex flex-col gap-lg md:flex-row md:items-center md:justify-between">
          <div className="flex items-center gap-md sm:gap-lg">
            <div className="flex h-20 w-20 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br from-primary via-indigo-600 to-purple-600 text-2xl font-extrabold text-white shadow-lg ring-4 ring-white/10 dark:ring-slate-800">
              {avatarInitials}
            </div>
            <div className="min-w-0 space-y-xs">
              <div className="flex flex-wrap items-center gap-sm">
                <h2 className="text-xl font-bold tracking-tight sm:text-2xl">{displayName}</h2>
                <Badge tone={user?.role === 'ADMIN' ? 'red' : user?.role === 'EXPERT' ? 'purple' : 'blue'}>
                  {roleLabel}
                </Badge>
              </div>
              <p className="flex items-center gap-xs text-sm text-slate-300">
                <Mail className="h-4 w-4 shrink-0 text-slate-400" />
                <span className="truncate">{user?.email}</span>
              </p>
              <div className="flex flex-wrap items-center gap-xs text-xs text-slate-400">
                <UserCheck className="h-3.5 w-3.5 text-emerald-400" />
                <span>{user?.emailVerified ? (language === 'vi' ? 'Đã xác thực Email' : 'Email Verified') : (language === 'vi' ? 'Chưa xác thực Email' : 'Email Unverified')}</span>
                <span>•</span>
                <span>ID: #{user?.id}</span>
              </div>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-md border-t border-white/10 pt-md md:border-t-0 md:pt-0">
            <div className="rounded-xl border border-white/10 bg-white/5 px-md py-sm backdrop-blur">
              <p className="text-xs uppercase tracking-wider text-slate-400">{language === 'vi' ? 'Gói hiện tại' : 'Active Plan'}</p>
              <p className="mt-xs text-lg font-bold text-inverse-primary">{planName}</p>
            </div>
            <div className="rounded-xl border border-white/10 bg-white/5 px-md py-sm backdrop-blur">
              <p className="text-xs uppercase tracking-wider text-slate-400">{language === 'vi' ? 'Trạng thái' : 'Status'}</p>
              <div className="mt-xs flex items-center gap-xs">
                <span className="h-2 w-2 rounded-full bg-emerald-400 animate-pulse" />
                <span className="text-sm font-semibold text-emerald-300">{language === 'vi' ? 'Hoạt động' : 'Active'}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Tabs */}
      <Tabs
        variant="pill"
        activeId={activeTab}
        onChange={(id) => setActiveTab(id as ProfileTab)}
        items={tabsItems}
      />

      {/* Tab 1: Profile Information */}
      {activeTab === 'info' && (
        <Card
          title={language === 'vi' ? 'Cập nhật thông tin cá nhân' : 'Edit Profile Information'}
          subtitle={language === 'vi' ? 'Thay đổi họ tên và thông tin giới thiệu cá nhân.' : 'Update your name and professional details.'}
          actions={<UserRound className="h-5 w-5 text-primary dark:text-inverse-primary" />}
        >
          <form onSubmit={handleSaveProfile} className="space-y-lg">
            <div className="grid gap-md sm:grid-cols-2">
              <div>
                <label className="block text-sm font-semibold text-on-surface dark:text-slate-200">
                  {language === 'vi' ? 'Họ và tên đệm' : 'First Name'} <span className="text-error">*</span>
                </label>
                <input
                  type="text"
                  value={firstName}
                  onChange={(e) => setFirstName(e.target.value)}
                  className="mt-xs w-full rounded-xl border border-outline-variant bg-white px-md py-sm text-sm outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/10 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-inverse-primary"
                  placeholder=""
                  required
                />
              </div>

              <div>
                <label className="block text-sm font-semibold text-on-surface dark:text-slate-200">
                  {language === 'vi' ? 'Tên' : 'Last Name'} <span className="text-error">*</span>
                </label>
                <input
                  type="text"
                  value={lastName}
                  onChange={(e) => setLastName(e.target.value)}
                  className="mt-xs w-full rounded-xl border border-outline-variant bg-white px-md py-sm text-sm outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/10 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-inverse-primary"
                  placeholder=""
                  required
                />
              </div>
            </div>

            <div>
              <label className="block text-sm font-semibold text-on-surface dark:text-slate-200">
                {language === 'vi' ? 'Địa chỉ Email' : 'Email Address'}
              </label>
              <div className="relative mt-xs">
                <input
                  type="email"
                  value={user?.email ?? ''}
                  disabled
                  className="w-full rounded-xl border border-outline-variant bg-surface-container-low px-md py-sm pr-10 text-sm text-on-surface-variant opacity-80 cursor-not-allowed dark:border-slate-800 dark:bg-slate-950 dark:text-slate-400"
                />
                <Lock className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-outline dark:text-slate-500" />
              </div>
              <p className="mt-xs text-xs text-on-surface-variant dark:text-slate-400">
                {language === 'vi' ? 'Email là định danh tài khoản và không thể tự thay đổi.' : 'Email is your account identifier and cannot be changed.'}
              </p>
            </div>



            <div className="flex justify-end pt-sm">
              <Button type="submit" disabled={savingProfile} leftIcon={<Save className="h-4 w-4" />}>
                {language === 'vi' ? 'Lưu thay đổi' : 'Save Changes'}
              </Button>
            </div>
          </form>
        </Card>
      )}

      {/* Tab 2: Workspace & Subscription */}
      {activeTab === 'workspace' && (
        <div className="grid gap-gutter xl:grid-cols-[1.2fr_0.8fr]">
          <Card
            title={language === 'vi' ? 'Gói dịch vụ & Hạn ngạch sử dụng' : 'Subscription & Quota Usage'}
            subtitle={language === 'vi' ? 'Thông tin hạn ngạch AI và các tính năng khả dụng.' : 'AI usage limits and enabled features.'}
            actions={<Zap className="h-5 w-5 text-primary dark:text-inverse-primary" />}
          >
            {loadingUsage ? (
              <p className="text-sm text-on-surface-variant dark:text-slate-400">{t('common.loading')}</p>
            ) : usageSummary ? (
              <div className="space-y-lg">
                <div className="flex items-center justify-between rounded-xl border border-legal-border bg-surface-container-low p-md dark:border-slate-800 dark:bg-slate-900">
                  <div>
                    <p className="text-xs uppercase tracking-wider text-on-surface-variant dark:text-slate-400">{language === 'vi' ? 'Gói hiện tại' : 'Current Plan'}</p>
                    <p className="mt-xs text-xl font-bold text-primary dark:text-inverse-primary">{planName}</p>
                  </div>
                  <Link to="/billing/subscribe">
                    <Button variant="primary" size="sm" leftIcon={<Sparkles className="h-4 w-4" />}>
                      {language === 'vi' ? 'Nâng cấp gói' : 'Upgrade Plan'}
                    </Button>
                  </Link>
                </div>

                <div className="space-y-md">
                  <div>
                    <div className="mb-xs flex justify-between text-sm">
                      <span className="font-semibold text-on-surface dark:text-slate-200">{language === 'vi' ? 'Phân tích hợp đồng' : 'Contract Analyses'}</span>
                      <span className="text-on-surface-variant dark:text-slate-400">
                        {usageSummary.contractAnalysisUsed} / {usageSummary.contractAnalysisLimit}
                      </span>
                    </div>
                    <ProgressBar
                      value={calcPercent(usageSummary.contractAnalysisUsed, usageSummary.contractAnalysisLimit)}
                    />
                  </div>

                  <div>
                    <div className="mb-xs flex justify-between text-sm">
                      <span className="font-semibold text-on-surface dark:text-slate-200">{language === 'vi' ? 'Token AI' : 'AI Tokens'}</span>
                      <span className="text-on-surface-variant dark:text-slate-400">
                        {usageSummary.aiTokensUsed.toLocaleString()} / {usageSummary.aiTokensLimit.toLocaleString()}
                      </span>
                    </div>
                    <ProgressBar
                      value={calcPercent(usageSummary.aiTokensUsed, usageSummary.aiTokensLimit)}
                    />
                  </div>

                  <div>
                    <div className="mb-xs flex justify-between text-sm">
                      <span className="font-semibold text-on-surface dark:text-slate-200">{language === 'vi' ? 'Số lượng Workspace' : 'Workspaces'}</span>
                      <span className="text-on-surface-variant dark:text-slate-400">
                        {usageSummary.workspacesUsed} / {usageSummary.workspacesLimit}
                      </span>
                    </div>
                    <ProgressBar
                      value={calcPercent(usageSummary.workspacesUsed, usageSummary.workspacesLimit)}
                    />
                  </div>
                </div>
              </div>
            ) : (
              <div className="space-y-md text-sm text-on-surface-variant dark:text-slate-400">
                <p>{language === 'vi' ? 'Gói của bạn hiện là:' : 'Current plan:'} <span className="font-bold text-primary dark:text-inverse-primary">{planName}</span></p>
                <Link to="/billing/subscribe">
                  <Button variant="secondary" size="sm">{language === 'vi' ? 'Xem các gói dịch vụ' : 'View Subscription Plans'}</Button>
                </Link>
              </div>
            )}
          </Card>

          <Card
            title={language === 'vi' ? 'Truy cập nhanh Workspace' : 'Quick Workspace Access'}
            subtitle={language === 'vi' ? 'Quản lý tài liệu và các dự án phân tích.' : 'Manage your analysis projects and documents.'}
            actions={<Shield className="h-5 w-5 text-primary dark:text-inverse-primary" />}
          >
            <div className="space-y-sm">
              <Link
                to="/dashboard"
                className="group flex items-center justify-between rounded-xl border border-legal-border p-md transition hover:border-primary hover:bg-surface-container-low dark:border-slate-800 dark:hover:border-inverse-primary dark:hover:bg-slate-900"
              >
                <div>
                  <p className="font-semibold text-on-surface dark:text-slate-100">{language === 'vi' ? 'Trang tổng quan Dashboard' : 'Main Dashboard'}</p>
                  <p className="text-xs text-on-surface-variant dark:text-slate-400">{language === 'vi' ? 'Xem thống kê và hoạt động gần đây' : 'View metrics & recent activities'}</p>
                </div>
                <ChevronRight className="h-5 w-5 text-outline transition-transform group-hover:translate-x-1 dark:text-slate-500" />
              </Link>

              <Link
                to="/projects"
                className="group flex items-center justify-between rounded-xl border border-legal-border p-md transition hover:border-primary hover:bg-surface-container-low dark:border-slate-800 dark:hover:border-inverse-primary dark:hover:bg-slate-900"
              >
                <div>
                  <p className="font-semibold text-on-surface dark:text-slate-100">{language === 'vi' ? 'Danh sách Dự án' : 'Projects List'}</p>
                  <p className="text-xs text-on-surface-variant dark:text-slate-400">{language === 'vi' ? 'Quản lý dự án pháp lý' : 'Manage legal workspaces'}</p>
                </div>
                <ChevronRight className="h-5 w-5 text-outline transition-transform group-hover:translate-x-1 dark:text-slate-500" />
              </Link>

              <Link
                to="/contracts"
                className="group flex items-center justify-between rounded-xl border border-legal-border p-md transition hover:border-primary hover:bg-surface-container-low dark:border-slate-800 dark:hover:border-inverse-primary dark:hover:bg-slate-900"
              >
                <div>
                  <p className="font-semibold text-on-surface dark:text-slate-100">{language === 'vi' ? 'Hợp đồng của tôi' : 'My Contracts'}</p>
                  <p className="text-xs text-on-surface-variant dark:text-slate-400">{language === 'vi' ? 'Quản lý hợp đồng đã soạn thảo' : 'Generated contracts & drafts'}</p>
                </div>
                <ChevronRight className="h-5 w-5 text-outline transition-transform group-hover:translate-x-1 dark:text-slate-500" />
              </Link>
            </div>
          </Card>
        </div>
      )}

      {/* Tab 3: Account Security */}
      {activeTab === 'security' && (
        <Card
          title={language === 'vi' ? 'Đổi mật khẩu tài khoản' : 'Change Password'}
          subtitle={language === 'vi' ? 'Xác minh mật khẩu hiện tại và nhập mật khẩu mới.' : 'Verify current password and set a new password.'}
          actions={<KeyRound className="h-5 w-5 text-primary dark:text-inverse-primary" />}
        >
          <form onSubmit={handleChangePassword} className="max-w-xl space-y-md">
            <div>
              <label className="block text-sm font-semibold text-on-surface dark:text-slate-200">
                {language === 'vi' ? 'Mật khẩu hiện tại' : 'Current Password'} <span className="text-error">*</span>
              </label>
              <div className="relative mt-xs">
                <input
                  type={showOldPassword ? 'text' : 'password'}
                  value={oldPassword}
                  onChange={(e) => setOldPassword(e.target.value)}
                  className="w-full rounded-xl border border-outline-variant bg-white px-md py-sm pr-10 text-sm outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/10 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100"
                  required
                />
                <button
                  type="button"
                  onClick={() => setShowOldPassword(!showOldPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-outline hover:text-on-surface dark:text-slate-400"
                >
                  {showOldPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
            </div>

            <div>
              <label className="block text-sm font-semibold text-on-surface dark:text-slate-200">
                {language === 'vi' ? 'Mật khẩu mới' : 'New Password'} <span className="text-error">*</span>
              </label>
              <div className="relative mt-xs">
                <input
                  type={showNewPassword ? 'text' : 'password'}
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  className="w-full rounded-xl border border-outline-variant bg-white px-md py-sm pr-10 text-sm outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/10 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100"
                  required
                />
                <button
                  type="button"
                  onClick={() => setShowNewPassword(!showNewPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-outline hover:text-on-surface dark:text-slate-400"
                >
                  {showNewPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
            </div>

            <div>
              <label className="block text-sm font-semibold text-on-surface dark:text-slate-200">
                {language === 'vi' ? 'Xác nhận mật khẩu mới' : 'Confirm New Password'} <span className="text-error">*</span>
              </label>
              <div className="relative mt-xs">
                <input
                  type={showConfirmPassword ? 'text' : 'password'}
                  value={confirmNewPassword}
                  onChange={(e) => setConfirmNewPassword(e.target.value)}
                  className="w-full rounded-xl border border-outline-variant bg-white px-md py-sm pr-10 text-sm outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/10 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100"
                  required
                />
                <button
                  type="button"
                  onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-outline hover:text-on-surface dark:text-slate-400"
                >
                  {showConfirmPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
            </div>

            <div className="pt-sm">
              <Button type="submit" disabled={changingPassword} leftIcon={<KeyRound className="h-4 w-4" />}>
                {language === 'vi' ? 'Cập nhật mật khẩu' : 'Update Password'}
              </Button>
            </div>
          </form>
        </Card>
      )}

      {/* Tab 4: Theme & Language */}
      {activeTab === 'appearance' && (
        <div className="grid gap-gutter xl:grid-cols-2">
          <Card
            title={t('settings.appearance.title')}
            subtitle={t('settings.appearance.subtitle')}
            actions={<Palette className="h-5 w-5 text-primary dark:text-inverse-primary" />}
          >
            <div className="grid gap-sm sm:grid-cols-3">
              {themeOptions.map((option) => {
                const Icon = option.icon;
                const active = theme === option.mode;

                return (
                  <button
                    key={option.mode}
                    type="button"
                    aria-pressed={active}
                    className={cn(
                      'flex min-h-28 flex-col items-start justify-between rounded-xl border p-md text-left transition',
                      active
                        ? 'border-primary bg-primary text-white shadow-sm dark:border-inverse-primary dark:bg-inverse-primary dark:text-slate-950'
                        : 'border-legal-border bg-white text-on-surface hover:bg-surface-container-low dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:hover:bg-slate-800',
                    )}
                    onClick={() => {
                      setTheme(option.mode);
                      toast.success(t('settings.themeChanged'), t('toast.successTitle'));
                    }}
                  >
                    <span className="flex w-full items-center justify-between gap-sm">
                      <Icon className="h-5 w-5" aria-hidden="true" />
                      {active && <Check className="h-4 w-4" aria-hidden="true" />}
                    </span>
                    <span>
                      <span className="block text-sm font-semibold">{t(option.labelKey)}</span>
                      <span className={cn('mt-xs block text-xs', active ? 'text-white/80 dark:text-slate-800' : 'text-on-surface-variant dark:text-slate-400')}>
                        {t(`settings.appearance.${option.mode}`)}
                      </span>
                    </span>
                  </button>
                );
              })}
            </div>
          </Card>

          <Card
            title={t('settings.language.title')}
            subtitle={t('settings.language.subtitle')}
            actions={<Languages className="h-5 w-5 text-primary dark:text-inverse-primary" />}
          >
            <div className="grid gap-sm sm:grid-cols-2">
              {languageOptions.map((option) => {
                const active = language === option.language;

                return (
                  <button
                    key={option.language}
                    type="button"
                    aria-pressed={active}
                    className={cn(
                      'flex min-h-24 items-center gap-md rounded-xl border p-md text-left transition',
                      active
                        ? 'border-primary bg-primary text-white shadow-sm dark:border-inverse-primary dark:bg-inverse-primary dark:text-slate-950'
                        : 'border-legal-border bg-white text-on-surface hover:bg-surface-container-low dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:hover:bg-slate-800',
                    )}
                    onClick={() => {
                      setLanguage(option.language);
                      toast.success(
                        translate(option.language, 'settings.languageChanged'),
                        translate(option.language, 'toast.successTitle'),
                      );
                    }}
                  >
                    <span
                      className={cn(
                        'flex h-11 w-11 shrink-0 items-center justify-center rounded-full text-sm font-bold',
                        active ? 'bg-white/15 text-white dark:bg-slate-950/10 dark:text-slate-950' : 'bg-surface-container-high text-primary dark:bg-slate-800 dark:text-inverse-primary',
                      )}
                    >
                      {option.shortLabel}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block text-sm font-semibold">{t(option.labelKey)}</span>
                      <span className={cn('mt-xs block text-xs', active ? 'text-white/80 dark:text-slate-800' : 'text-on-surface-variant dark:text-slate-400')}>
                        {t(`settings.language.${option.language}`)}
                      </span>
                    </span>
                    {active && <Check className="h-4 w-4 shrink-0" aria-hidden="true" />}
                  </button>
                );
              })}
            </div>
          </Card>
        </div>
      )}
    </div>
  );
}
