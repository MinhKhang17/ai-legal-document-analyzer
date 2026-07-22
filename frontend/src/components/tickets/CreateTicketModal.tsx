import { FileText, Paperclip, RotateCcw, Trash2 } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Button } from "../common/Button";
import { Modal } from "../common/Modal";
import { useI18n } from "../../hooks/useI18n";
import { getAttachmentPolicy, removeTicketAttachment, uploadTicketAttachment } from "../../services/legalTicket.service";
import type { AttachmentPolicy, ConversationScope, CreateLegalTicketRequest, TicketPriority, TicketRecipientType } from "../../types/legalTicket";
import { ApiRequestError } from "../../services/http";

type SharedDocument = { id: string; name: string };
type PendingFile = { key: string; file: File; status: "SELECTED" | "UPLOADING" | "UPLOADED" | "FAILED"; progress: number; attachmentId?: string; error?: string };

interface Props {
  open: boolean;
  workspaceId: string;
  sessionId: string;
  userMessageId?: string;
  assistantMessageId: string;
  requestId?: string;
  question: string;
  answer: string;
  documents: SharedDocument[];
  focusedDocumentId?: string;
  citationIds: string[];
  submitting: boolean;
  onClose: () => void;
  onSubmit: (request: CreateLegalTicketRequest) => Promise<void>;
}

const defaultPolicy: AttachmentPolicy = { maxAttachmentSizeKb: 500, maxAttachmentsPerMessage: 5, maxAttachmentsPerTicket: 30, allowedMimeTypes: [] };
const allowedExtensions = new Set(["jpg", "jpeg", "png", "webp", "pdf", "docx", "txt"]);
const sizeKb = (size: number) => Math.ceil(size / 1024);

export function CreateTicketModal(props: Props) {
  const { t } = useI18n();
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [legalIssueCategory, setLegalIssueCategory] = useState("");
  const [contractType, setContractType] = useState("");
  const [expectedOutcome, setExpectedOutcome] = useState("");
  const [sharedProfileFields, setSharedProfileFields] = useState<Array<"DISPLAY_NAME" | "EMAIL">>(["DISPLAY_NAME"]);
  const [consentGranted, setConsentGranted] = useState(false);
  const [recipientType, setRecipientType] = useState<TicketRecipientType>("EXPERT");
  const [priority, setPriority] = useState<TicketPriority>("NORMAL");
  const [scope, setScope] = useState<ConversationScope>("SELECTED_RESPONSE");
  const [selectedDocumentIds, setSelectedDocumentIds] = useState<string[]>([]);
  const [files, setFiles] = useState<PendingFile[]>([]);
  const [policy, setPolicy] = useState(defaultPolicy);
  const [error, setError] = useState("");
  const [draftId, setDraftId] = useState("");

  useEffect(() => {
    if (!props.open) return;
    setTitle(t("ticketComposer.defaultTitle", { question: props.question.slice(0, 90) }));
    setDescription(props.question);
    setLegalIssueCategory("");
    setContractType("");
    setExpectedOutcome("");
    setSharedProfileFields(["DISPLAY_NAME"]);
    setConsentGranted(false);
    setScope("SELECTED_RESPONSE");
    setSelectedDocumentIds(props.documents.map((item) => item.id));
    setFiles([]);
    setError("");
    setDraftId(`draft_${crypto.randomUUID().replaceAll("-", "")}`);
    void getAttachmentPolicy().then(setPolicy).catch(() => setPolicy(defaultPolicy));
  }, [props.open, props.question, props.documents, t]);

  const uploading = files.some((item) => item.status === "UPLOADING");
  const policyText = useMemo(() => t("ticketComposer.attachmentPolicy", { size: policy.maxAttachmentSizeKb, count: policy.maxAttachmentsPerMessage }), [policy, t]);

  const selectFiles = (selected: FileList | null) => {
    if (!selected) return;
    const incoming = Array.from(selected);
    if (files.length + incoming.length > policy.maxAttachmentsPerMessage) {
      setError(t("ticketComposer.tooManyFiles", { count: policy.maxAttachmentsPerMessage })); return;
    }
    const additions: PendingFile[] = [];
    for (const file of incoming) {
      const ext = file.name.split(".").pop()?.toLowerCase() ?? "";
      if (!allowedExtensions.has(ext) || (policy.allowedMimeTypes.length > 0 && !policy.allowedMimeTypes.includes(file.type))) {
        setError(t("ticketComposer.invalidFileType", { file: file.name })); continue;
      }
      if (file.size > policy.maxAttachmentSizeKb * 1024) {
        setError(t("ticketComposer.fileTooLarge", { file: file.name, size: sizeKb(file.size), max: policy.maxAttachmentSizeKb })); continue;
      }
      additions.push({ key: crypto.randomUUID(), file, status: "SELECTED", progress: 0 });
    }
    setFiles((current) => [...current, ...additions]);
  };

  const uploadOne = async (item: PendingFile) => {
    setFiles((current) => current.map((file) => file.key === item.key ? { ...file, status: "UPLOADING", progress: 0, error: undefined } : file));
    try {
      const uploaded = await uploadTicketAttachment(item.file, draftId, (progress) =>
        setFiles((current) => current.map((file) => file.key === item.key ? { ...file, progress } : file)));
      setFiles((current) => current.map((file) => file.key === item.key ? { ...file, status: "UPLOADED", progress: 100, attachmentId: uploaded.id } : file));
      return uploaded.id;
    } catch {
      const message = t("ticketComposer.uploadError", { file: item.file.name });
      setFiles((current) => current.map((file) => file.key === item.key ? { ...file, status: "FAILED", error: message } : file));
      throw new Error(message);
    }
  };

  const removeFile = async (item: PendingFile) => {
    setFiles((current) => current.filter((file) => file.key !== item.key));
    if (!item.attachmentId) return;
    try { await removeTicketAttachment(item.attachmentId); }
    catch { setError(t("ticketComposer.removeDraftError", { file: item.file.name })); }
  };

  const submit = async () => {
    if (!title.trim() || !description.trim() || !legalIssueCategory.trim() || !expectedOutcome.trim()) {
      setError(t("ticketComposer.requiredFields")); return;
    }
    if (!consentGranted) { setError("Bạn cần xác nhận phạm vi dữ liệu được chia sẻ."); return; }
    setError("");
    try {
      const attachmentIds: string[] = [];
      for (const file of files) attachmentIds.push(file.attachmentId ?? await uploadOne(file));
      await props.onSubmit({
        creationSource: "AI_CHAT", ticket_type: "CONTACT_EXPERT", title: title.trim(), description: description.trim(), recipientType,
        priority, conversationScope: scope, chat_session_id: props.sessionId, chat_message_id: props.assistantMessageId,
        userMessageId: props.userMessageId, assistantMessageId: props.assistantMessageId, request_id: props.requestId,
        workspace_id: props.workspaceId, focusedDocumentId: props.focusedDocumentId,
        documentIds: selectedDocumentIds, citationIds: props.citationIds, attachmentIds,
        document_id: props.focusedDocumentId || selectedDocumentIds[0] || null,
        question: props.question, customer_note: description.trim(), issue_fingerprint: props.assistantMessageId,
        legalIssueCategory: legalIssueCategory.trim(), contractType: contractType.trim() || null,
        userExpectedOutcome: expectedOutcome.trim(), aiAnswerSummary: props.answer,
        sharedProfileFields, consentGranted,
      });
    } catch (cause) {
      if (cause instanceof ApiRequestError && cause.details?.data && typeof cause.details.data === "object") {
        setError(Object.values(cause.details.data as Record<string, string>).join(" · "));
      } else {
        setError(cause instanceof Error ? cause.message : t("legalTickets.createError"));
      }
    }
  };

  return <Modal open={props.open} title={t("ticketComposer.title")} onClose={uploading ? () => undefined : props.onClose}
    footer={<><Button variant="secondary" onClick={props.onClose} disabled={uploading || props.submitting}>{t("actions.cancel")}</Button><Button onClick={() => void submit()} disabled={uploading || props.submitting}>{props.submitting ? t("legalTickets.createSubmitting") : t("legalTickets.createSubmit")}</Button></>}>
    <div className="space-y-md">
      {error && <p className="rounded-lg bg-error/10 p-sm text-sm text-error">{error}</p>}
      <div><label className="mb-xs block text-sm font-semibold">{t("ticketComposer.subject")}</label><input className="form-field" maxLength={255} value={title} onChange={(e) => setTitle(e.target.value)} /></div>
      <div><label className="mb-xs block text-sm font-semibold">{t("ticketComposer.description")}</label><textarea className="form-field min-h-24" maxLength={5000} value={description} onChange={(e) => setDescription(e.target.value)} /></div>
      <div className="grid gap-sm sm:grid-cols-2">
        <div><label className="mb-xs block text-sm font-semibold">Nhóm vấn đề pháp lý *</label><input className="form-field" maxLength={255} value={legalIssueCategory} onChange={(e) => setLegalIssueCategory(e.target.value)} placeholder="Ví dụ: Tranh chấp hợp đồng" /></div>
        <div><label className="mb-xs block text-sm font-semibold">Loại hợp đồng</label><input className="form-field" maxLength={255} value={contractType} onChange={(e) => setContractType(e.target.value)} placeholder="Không bắt buộc" /></div>
      </div>
      <div><label className="mb-xs block text-sm font-semibold">Kết quả bạn mong muốn *</label><textarea className="form-field min-h-20" maxLength={5000} value={expectedOutcome} onChange={(e) => setExpectedOutcome(e.target.value)} /></div>
      <div className="grid gap-sm sm:grid-cols-2">
        <div><label className="mb-xs block text-sm font-semibold">{t("ticketComposer.recipient")}</label><select className="form-field" value={recipientType} onChange={(e) => setRecipientType(e.target.value as TicketRecipientType)}><option value="EXPERT">{t("role.expert")}</option><option value="ADMIN">{t("role.admin")}</option></select></div>
        <div><label className="mb-xs block text-sm font-semibold">{t("ticketComposer.priority")}</label><select className="form-field" value={priority} onChange={(e) => setPriority(e.target.value as TicketPriority)}><option value="LOW">{t("priority.LOW")}</option><option value="NORMAL">{t("priority.NORMAL")}</option><option value="HIGH">{t("priority.HIGH")}</option><option value="URGENT">{t("priority.URGENT")}</option></select></div>
      </div>
      <div><label className="mb-xs block text-sm font-semibold">{t("ticketComposer.scope")}</label><select className="form-field" value={scope} onChange={(e) => setScope(e.target.value as ConversationScope)}><option value="SELECTED_RESPONSE">{t("conversationScope.SELECTED_RESPONSE")}</option><option value="RELATED_MESSAGES">{t("conversationScope.RELATED_MESSAGES")}</option><option value="FULL_CONVERSATION">{t("conversationScope.FULL_CONVERSATION")}</option><option value="TICKET_CONTEXT_ONLY">{t("conversationScope.TICKET_CONTEXT_ONLY")}</option></select><p className="mt-xs text-xs text-on-surface-variant">{t("ticketComposer.scopeHint")}</p></div>
      <div><p className="mb-xs text-sm font-semibold">{t("ticketComposer.sharedDocuments")}</p><div className="space-y-xs">{props.documents.length === 0 ? <p className="text-xs text-on-surface-variant">{t("ticketComposer.noSharedDocuments")}</p> : props.documents.map((doc) => <label key={doc.id} className="flex items-center gap-sm rounded-lg border border-legal-border p-sm"><input type="checkbox" checked={selectedDocumentIds.includes(doc.id)} onChange={(e) => setSelectedDocumentIds((current) => e.target.checked ? [...current, doc.id] : current.filter((id) => id !== doc.id))} /><FileText className="h-4 w-4" /><span className="truncate text-sm">{doc.name}</span></label>)}</div></div>
      <div className="rounded-lg border border-legal-border p-sm">
        <p className="text-sm font-semibold">Thông tin hồ sơ được chia sẻ</p>
        <div className="mt-xs flex flex-wrap gap-md text-sm">
          {(["DISPLAY_NAME", "EMAIL"] as const).map((field) => <label key={field} className="flex items-center gap-xs"><input type="checkbox" checked={sharedProfileFields.includes(field)} onChange={(event) => setSharedProfileFields((current) => event.target.checked ? [...current, field] : current.filter((item) => item !== field))} />{field === "DISPLAY_NAME" ? "Tên hiển thị" : "Email"}</label>)}
        </div>
        <p className="mt-sm text-xs text-on-surface-variant">Chuyên gia chỉ nhận các tài liệu đã chọn ở trên và các trường hồ sơ đã đánh dấu.</p>
        <label className="mt-sm flex items-start gap-sm text-sm"><input className="mt-1" type="checkbox" checked={consentGranted} onChange={(event) => setConsentGranted(event.target.checked)} /><span>Tôi đồng ý chia sẻ đúng các dữ liệu được liệt kê để chuyên gia thực hiện tư vấn.</span></label>
      </div>
      <div><div className="flex items-center justify-between"><div><p className="text-sm font-semibold">{t("ticketComposer.attachments")}</p><p className="text-xs text-on-surface-variant">{policyText}</p></div><label className="cursor-pointer"><input type="file" multiple className="hidden" accept=".jpg,.jpeg,.png,.webp,.pdf,.docx,.txt" onChange={(e) => selectFiles(e.target.files)} /><span className="inline-flex items-center gap-xs rounded-lg border px-sm py-xs text-sm font-semibold"><Paperclip className="h-4 w-4" />{t("actions.selectFiles")}</span></label></div>
        <div className="mt-sm space-y-xs">{files.map((item) => <div key={item.key} className="rounded-lg border border-legal-border p-sm"><div className="flex items-center gap-sm"><span className="min-w-0 flex-1 truncate text-sm font-medium">{item.file.name} · {sizeKb(item.file.size)} KB</span>{item.status === "FAILED" && <button type="button" title={t("common.retry")} onClick={() => void uploadOne(item)}><RotateCcw className="h-4 w-4" /></button>}<button type="button" title={t("ticketComposer.removeFile")} disabled={item.status === "UPLOADING"} onClick={() => void removeFile(item)}><Trash2 className="h-4 w-4" /></button></div>{item.status === "UPLOADING" && <div className="mt-xs h-1.5 overflow-hidden rounded bg-slate-200"><div className="h-full bg-primary transition-all" style={{ width: `${item.progress}%` }} /></div>}{item.error && <p className="mt-xs text-xs text-error">{item.error}</p>}</div>)}</div>
      </div>
    </div>
  </Modal>;
}
