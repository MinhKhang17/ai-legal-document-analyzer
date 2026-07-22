import { Edit3, MessageSquareText, Plus, RefreshCw, Send } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { Badge } from "../../components/common/Badge";
import { Button } from "../../components/common/Button";
import { Card } from "../../components/common/Card";
import { DataTable, type DataTableColumn } from "../../components/common/DataTable";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { useI18n } from "../../hooks/useI18n";
import { useToast } from "../../hooks/useToast";
import {
  createAiFeedbackReport,
  createFeedbackSurvey,
  getAdminAiFeedbackReports,
  getAdminFeedbackSurveys,
  updateFeedbackSurvey,
} from "../../services/feedback.service";
import { useAppStore } from "../../store/AppStore";
import type {
  AiReport,
  FeedbackSurvey,
  FeedbackSurveyStatus,
  FeedbackSurveyType,
} from "../../types/feedback";
import { formatDisplayDate } from "../../utils/format";
import { getAdminChatFeedback } from "../../api/chatApi";
import { getAccessToken } from "../../services/authSession";
import type { ChatMessageFeedback } from "../../types/chat";
import { AdminAiFeedbackPanel } from "../../components/admin/AdminAiFeedbackPanel";

const surveyTypes: FeedbackSurveyType[] = ["SATISFACTION", "PRODUCT", "BUG", "USABILITY"];
const surveyStatuses: FeedbackSurveyStatus[] = ["DRAFT", "ACTIVE", "CLOSED", "ARCHIVED"];

const surveyTypeKeys: Record<string, string> = {
  SATISFACTION: "feedback.surveyType.SATISFACTION",
  PRODUCT: "feedback.surveyType.PRODUCT",
  BUG: "feedback.surveyType.BUG",
  USABILITY: "feedback.surveyType.USABILITY",
};

const surveyStatusKeys: Record<string, string> = {
  DRAFT: "feedback.surveyStatus.DRAFT",
  ACTIVE: "feedback.surveyStatus.ACTIVE",
  CLOSED: "feedback.surveyStatus.CLOSED",
  ARCHIVED: "feedback.surveyStatus.ARCHIVED",
};

const reportStatusKeys: Record<string, string> = {
  OPEN: "feedback.reportStatus.OPEN",
  UNDER_REVIEW: "feedback.reportStatus.UNDER_REVIEW",
  RESOLVED: "feedback.reportStatus.RESOLVED",
  REJECTED: "feedback.reportStatus.REJECTED",
};

const reportTypeKeys: Record<string, string> = {
  AI_OUTPUT: "feedback.reportTypeValue.AI_OUTPUT",
};

const sourceTypeKeys: Record<string, string> = {
  CHAT: "feedback.sourceTypeValue.CHAT",
};

const targetTypeKeys: Record<string, string> = {
  GENERAL: "feedback.targetTypeValue.GENERAL",
};

const ratingKeys: Record<string, string> = {
  THUMBS_UP: "feedback.rating.THUMBS_UP",
  THUMBS_DOWN: "feedback.rating.THUMBS_DOWN",
};

const feedbackReasonKeys: Record<string, string> = {
  INCORRECT: "chat.feedback.reason.INCORRECT",
  WRONG_CITATION: "chat.feedback.reason.WRONG_CITATION",
  INCOMPLETE: "chat.feedback.reason.INCOMPLETE",
  NOT_HELPFUL: "chat.feedback.reason.NOT_HELPFUL",
  POOR_PHRASING: "chat.feedback.reason.POOR_PHRASING",
  OTHER: "chat.feedback.reason.OTHER",
};

const emptySurveyForm = {
  code: "",
  title: "",
  description: "",
  surveyType: "SATISFACTION" as FeedbackSurveyType,
  targetType: "GENERAL",
  status: "DRAFT" as FeedbackSurveyStatus,
  workspaceId: "",
};

const emptyReportForm = {
  reportType: "AI_OUTPUT",
  sourceType: "CHAT",
  sourceReferenceId: "",
  summary: "",
  detailsJson: "",
  workspaceId: "",
};

const getSurveyTone = (status?: string) => {
  if (status === "ACTIVE") return "green";
  if (status === "CLOSED" || status === "ARCHIVED") return "slate";
  return "amber";
};

const getReportTone = (status?: string) => {
  if (status === "RESOLVED") return "green";
  if (status === "REJECTED") return "red";
  if (status === "UNDER_REVIEW") return "amber";
  return "blue";
};

const parseOptionalNumber = (value: string) => {
  if (!value.trim()) return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
};

export function AdminFeedbackPage() {
  const { t, language } = useI18n();
  const toast = useToast();
  const { user } = useAppStore();
  const locale = language === "vi" ? "vi-VN" : "en-US";
  const numberFormatter = new Intl.NumberFormat(locale);
  const translateEnum = (value: string | null | undefined, keys: Record<string, string>, fallback = "-") => {
    if (!value) return fallback;
    const key = keys[value];
    return key ? t(key) : value;
  };
  const [surveys, setSurveys] = useState<FeedbackSurvey[]>([]);
  const [reports, setReports] = useState<AiReport[]>([]);
  const [chatRatings, setChatRatings] = useState<ChatMessageFeedback[]>([]);
  const [selectedSurvey, setSelectedSurvey] = useState<FeedbackSurvey | null>(null);
  const [surveyForm, setSurveyForm] = useState(emptySurveyForm);
  const [reportForm, setReportForm] = useState(emptyReportForm);
  const [loading, setLoading] = useState(true);
  const [savingSurvey, setSavingSurvey] = useState(false);
  const [savingReport, setSavingReport] = useState(false);
  const [error, setError] = useState("");

  const loadFeedback = useCallback(async () => {
    setLoading(true);
    setError("");

    const [surveysResult, reportsResult, chatRatingsResult] = await Promise.allSettled([
      getAdminFeedbackSurveys(0, 50),
      getAdminAiFeedbackReports(0, 50),
      getAdminChatFeedback(getAccessToken() ?? '', 0, 50),
    ]);

    if (surveysResult.status === "fulfilled") {
      setSurveys(surveysResult.value.items ?? []);
    } else {
      const message = t("feedback.loadSurveysError");
      setError(message);
      toast.error(message);
      setSurveys([]);
    }

    setReports(reportsResult.status === "fulfilled" ? reportsResult.value.items ?? [] : []);
    setChatRatings(chatRatingsResult.status === "fulfilled" ? chatRatingsResult.value.items ?? [] : []);
    setLoading(false);
  }, [toast, t]);

  useEffect(() => {
    void loadFeedback();
  }, [loadFeedback]);

  const selectSurvey = (survey: FeedbackSurvey) => {
    setSelectedSurvey(survey);
    setSurveyForm({
      code: survey.code,
      title: survey.title,
      description: survey.description ?? "",
      surveyType: survey.surveyType,
      targetType: survey.targetType,
      status: survey.status,
      workspaceId: survey.workspaceId ? String(survey.workspaceId) : "",
    });
  };

  const resetSurveyForm = () => {
    setSelectedSurvey(null);
    setSurveyForm(emptySurveyForm);
  };

  const handleSaveSurvey = async () => {
    if (!user?.id) {
      toast.warning(t("feedback.currentUserRequiredSurvey"));
      return;
    }

    if (!surveyForm.title.trim() || !surveyForm.targetType.trim()) {
      toast.warning(t("feedback.surveyRequiredFields"));
      return;
    }

    if (!selectedSurvey && !surveyForm.code.trim()) {
      toast.warning(t("feedback.surveyCodeRequired"));
      return;
    }

    setSavingSurvey(true);
    setError("");

    try {
      const workspaceId = parseOptionalNumber(surveyForm.workspaceId);

      const savedSurvey = selectedSurvey
        ? await updateFeedbackSurvey(selectedSurvey.id, {
            title: surveyForm.title.trim(),
            description: surveyForm.description.trim() || null,
            surveyType: surveyForm.surveyType,
            targetType: surveyForm.targetType.trim(),
            status: surveyForm.status,
          })
        : await createFeedbackSurvey({
            code: surveyForm.code.trim(),
            title: surveyForm.title.trim(),
            description: surveyForm.description.trim() || null,
            surveyType: surveyForm.surveyType,
            targetType: surveyForm.targetType.trim(),
            createdById: user.id,
            workspaceId,
      });

      selectSurvey(savedSurvey);
      toast.success(selectedSurvey ? t("feedback.surveyUpdated") : t("feedback.surveyCreated"));
      await loadFeedback();
    } catch {
      const message = t("feedback.surveySaveError");
      setError(message);
      toast.error(message);
    } finally {
      setSavingSurvey(false);
    }
  };

  const handleCreateReport = async () => {
    if (!user?.id) {
      toast.warning(t("feedback.currentUserRequiredReport"));
      return;
    }

    if (
      !reportForm.reportType.trim() ||
      !reportForm.sourceType.trim() ||
      !reportForm.sourceReferenceId.trim() ||
      !reportForm.summary.trim()
    ) {
      toast.warning(t("feedback.reportRequiredFields"));
      return;
    }

    setSavingReport(true);
    setError("");

    try {
      await createAiFeedbackReport({
        reportType: reportForm.reportType.trim(),
        sourceType: reportForm.sourceType.trim(),
        sourceReferenceId: reportForm.sourceReferenceId.trim(),
        summary: reportForm.summary.trim(),
        detailsJson: reportForm.detailsJson.trim() || null,
        submittedById: user.id,
        workspaceId: parseOptionalNumber(reportForm.workspaceId),
      });
      setReportForm(emptyReportForm);
      toast.success(t("feedback.reportCreated"));
      await loadFeedback();
    } catch {
      const message = t("feedback.reportCreateError");
      setError(message);
      toast.error(message);
    } finally {
      setSavingReport(false);
    }
  };

  const surveyColumns: DataTableColumn<FeedbackSurvey>[] = [
    {
      header: t("feedback.survey"),
      cell: (survey) => (
        <div>
          <p className="font-semibold">{survey.title}</p>
          <p className="text-xs text-on-surface-variant dark:text-slate-400">{survey.code}</p>
        </div>
      ),
    },
    { header: t("contracts.type"), cell: (survey) => translateEnum(survey.surveyType, surveyTypeKeys) },
    { header: t("feedback.target"), cell: (survey) => translateEnum(survey.targetType, targetTypeKeys) },
    {
      header: t("table.status"),
      cell: (survey) => <Badge tone={getSurveyTone(survey.status)}>{translateEnum(survey.status, surveyStatusKeys)}</Badge>,
    },
    {
      header: t("table.updated"),
      cell: (survey) => formatDisplayDate(survey.updatedAt, "-", locale),
    },
    {
      header: "",
      className: "text-right",
      cell: (survey) => (
        <div className="flex justify-end gap-xs">
          <Button
            size="sm"
            variant="secondary"
            leftIcon={<Edit3 className="h-4 w-4" />}
            onClick={() => selectSurvey(survey)}
          >
            {t("actions.edit")}
          </Button>
        </div>
      ),
    },
  ];

  const reportColumns: DataTableColumn<AiReport>[] = [
    {
      header: t("feedback.report"),
      cell: (report) => (
        <div>
          <p className="font-semibold">{report.summary}</p>
          <p className="text-xs text-on-surface-variant dark:text-slate-400">
            {translateEnum(report.sourceType, sourceTypeKeys)} / {report.sourceReferenceId}
          </p>
        </div>
      ),
    },
    { header: t("contracts.type"), cell: (report) => translateEnum(report.reportType, reportTypeKeys) },
    {
      header: t("table.status"),
      cell: (report) => <Badge tone={getReportTone(report.status)}>{translateEnum(report.status, reportStatusKeys)}</Badge>,
    },
    {
      header: t("contracts.created"),
      cell: (report) => formatDisplayDate(report.createdAt, "-", locale),
    },
  ];

  const ratingColumns: DataTableColumn<ChatMessageFeedback>[] = [
    { header: t('feedback.ratings.customer'), cell: (item) => item.submittedByName || `#${item.submittedById}` },
    { header: t('feedback.ratings.rating'), cell: (item) => <Badge tone={item.rating === 'THUMBS_UP' ? 'green' : 'red'}>{translateEnum(item.rating, ratingKeys)}</Badge> },
    { header: t('feedback.ratings.answer'), cell: (item) => <p className="max-w-md line-clamp-3">{item.messageContent}</p> },
    { header: t('feedback.ratings.reasons'), cell: (item) => <span>{[...(item.reasons ?? []).map((reason) => translateEnum(reason, feedbackReasonKeys)), item.comment].filter(Boolean).join(', ') || '-'}</span> },
    { header: t('table.date'), cell: (item) => formatDisplayDate(item.createdAt, '-', locale) },
  ];

  return (
    <div>
      <PageHeader
        title={t("feedback.title")}
        subtitle={t("feedback.subtitle")}
        actions={
          <>
            <Button
              variant="secondary"
              leftIcon={<Plus className="h-4 w-4" />}
              onClick={resetSurveyForm}
            >
              {t("feedback.newSurvey")}
            </Button>
            <Button
              variant="secondary"
              leftIcon={<RefreshCw className="h-4 w-4" />}
              onClick={() => void loadFeedback()}
              disabled={loading}
            >
              {t("common.refresh")}
            </Button>
          </>
        }
      />

      {error && (
        <div className="mb-lg rounded-xl border border-error/40 bg-error/10 p-md text-sm text-error">
          {error}
        </div>
      )}

      <div className="mb-gutter">
        <AdminAiFeedbackPanel />
      </div>

      <div className="grid gap-gutter xl:grid-cols-[minmax(0,1fr)_420px]">
        <main className="space-y-gutter">
          <Card title={t('feedback.ratings.title')} actions={<Badge tone="blue">{numberFormatter.format(chatRatings.length)}</Badge>}>
            <DataTable columns={ratingColumns} data={chatRatings} getRowKey={(item) => item.id} emptyMessage={t('feedback.ratings.empty')} />
          </Card>
          <Card title={t("feedback.surveys")} actions={<Badge tone="blue">{numberFormatter.format(surveys.length)}</Badge>}>
            {loading ? (
              <p className="text-sm text-on-surface-variant dark:text-slate-400">
                {t("feedback.loadingSurveys")}
              </p>
            ) : surveys.length === 0 ? (
              <EmptyState
                icon={<MessageSquareText className="h-6 w-6" />}
                title={t("feedback.emptySurveysTitle")}
                description={t("feedback.emptySurveysDescription")}
              />
            ) : (
              <DataTable columns={surveyColumns} data={surveys} getRowKey={(survey) => survey.id} />
            )}
          </Card>

          <Card title={t("feedback.aiReports")} actions={<Badge tone="blue">{numberFormatter.format(reports.length)}</Badge>}>
            <DataTable
              columns={reportColumns}
              data={reports}
              getRowKey={(report) => report.id}
              emptyMessage={t("feedback.emptyReports")}
            />
          </Card>
        </main>

        <aside className="space-y-gutter">
          <Card
            title={selectedSurvey ? t("feedback.editSurvey") : t("feedback.createSurvey")}
            subtitle={selectedSurvey ? selectedSurvey.id : t("feedback.adminSurveyEndpoint")}
          >
            <div className="space-y-md">
              <label className="block text-sm font-semibold">
                {t("knowledge.code")}
                <input
                  className="form-field mt-xs"
                  value={surveyForm.code}
                  disabled={Boolean(selectedSurvey)}
                  onChange={(event) =>
                    setSurveyForm((current) => ({ ...current, code: event.target.value }))
                  }
                />
              </label>

              <label className="block text-sm font-semibold">
                {t("contracts.titleField")}
                <input
                  className="form-field mt-xs"
                  value={surveyForm.title}
                  onChange={(event) =>
                    setSurveyForm((current) => ({ ...current, title: event.target.value }))
                  }
                />
              </label>

              <div className="grid gap-md sm:grid-cols-2">
                <label className="block text-sm font-semibold">
                  {t("contracts.type")}
                  <select
                    className="form-field mt-xs"
                    value={surveyForm.surveyType}
                    onChange={(event) =>
                      setSurveyForm((current) => ({
                        ...current,
                        surveyType: event.target.value as FeedbackSurveyType,
                      }))
                    }
                  >
                    {surveyTypes.map((type) => (
                      <option key={type} value={type}>
                        {translateEnum(type, surveyTypeKeys)}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="block text-sm font-semibold">
                  {t("table.status")}
                  <select
                    className="form-field mt-xs"
                    value={surveyForm.status}
                    disabled={!selectedSurvey}
                    onChange={(event) =>
                      setSurveyForm((current) => ({
                        ...current,
                        status: event.target.value as FeedbackSurveyStatus,
                      }))
                    }
                  >
                    {surveyStatuses.map((status) => (
                      <option key={status} value={status}>
                        {translateEnum(status, surveyStatusKeys)}
                      </option>
                    ))}
                  </select>
                </label>
              </div>

              <label className="block text-sm font-semibold">
                {t("feedback.targetType")}
                <input
                  className="form-field mt-xs"
                  value={surveyForm.targetType}
                  onChange={(event) =>
                    setSurveyForm((current) => ({ ...current, targetType: event.target.value }))
                  }
                />
              </label>

              <label className="block text-sm font-semibold">
                {t("workspace.workspaceId")}
                <input
                  className="form-field mt-xs"
                  value={surveyForm.workspaceId}
                  onChange={(event) =>
                    setSurveyForm((current) => ({
                      ...current,
                      workspaceId: event.target.value,
                    }))
                  }
                />
              </label>

              <label className="block text-sm font-semibold">
                {t("common.description")}
                <textarea
                  className="form-field mt-xs min-h-24"
                  value={surveyForm.description}
                  onChange={(event) =>
                    setSurveyForm((current) => ({
                      ...current,
                      description: event.target.value,
                    }))
                  }
                />
              </label>

              <Button onClick={() => void handleSaveSurvey()} disabled={savingSurvey}>
                {savingSurvey ? t("contracts.saving") : selectedSurvey ? t("feedback.updateSurvey") : t("feedback.createSurvey")}
              </Button>
            </div>
          </Card>

          <Card title={t("feedback.createAiReport")}>
            <div className="space-y-md">
              <div className="grid gap-md sm:grid-cols-2">
                <label className="block text-sm font-semibold">
                  {t("feedback.reportType")}
                  <input
                    className="form-field mt-xs"
                    value={reportForm.reportType}
                    onChange={(event) =>
                      setReportForm((current) => ({
                        ...current,
                        reportType: event.target.value,
                      }))
                    }
                  />
                </label>
                <label className="block text-sm font-semibold">
                  {t("feedback.sourceType")}
                  <input
                    className="form-field mt-xs"
                    value={reportForm.sourceType}
                    onChange={(event) =>
                      setReportForm((current) => ({
                        ...current,
                        sourceType: event.target.value,
                      }))
                    }
                  />
                </label>
              </div>

              <label className="block text-sm font-semibold">
                {t("feedback.sourceReferenceId")}
                <input
                  className="form-field mt-xs"
                  value={reportForm.sourceReferenceId}
                  onChange={(event) =>
                    setReportForm((current) => ({
                      ...current,
                      sourceReferenceId: event.target.value,
                    }))
                  }
                />
              </label>

              <label className="block text-sm font-semibold">
                {t("workspace.workspaceId")}
                <input
                  className="form-field mt-xs"
                  value={reportForm.workspaceId}
                  onChange={(event) =>
                    setReportForm((current) => ({
                      ...current,
                      workspaceId: event.target.value,
                    }))
                  }
                />
              </label>

              <label className="block text-sm font-semibold">
                {t("contracts.summary")}
                <textarea
                  className="form-field mt-xs min-h-24"
                  value={reportForm.summary}
                  onChange={(event) =>
                    setReportForm((current) => ({
                      ...current,
                      summary: event.target.value,
                    }))
                  }
                />
              </label>

              <label className="block text-sm font-semibold">
                {t("feedback.detailsJson")}
                <textarea
                  className="form-field mt-xs min-h-24 font-mono text-xs"
                  value={reportForm.detailsJson}
                  onChange={(event) =>
                    setReportForm((current) => ({
                      ...current,
                      detailsJson: event.target.value,
                    }))
                  }
                />
              </label>

              <Button
                leftIcon={<Send className="h-4 w-4" />}
                onClick={() => void handleCreateReport()}
                disabled={savingReport}
              >
                {savingReport ? t("feedback.submitting") : t("feedback.submitReport")}
              </Button>
            </div>
          </Card>
        </aside>
      </div>
    </div>
  );
}
