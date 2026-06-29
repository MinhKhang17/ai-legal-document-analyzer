import { useState } from "react";
import { Button } from "../common/Button";
import { Card } from "../common/Card";
import type { CreateLegalTicketRequest } from "../../types/legalTicket";

interface LegalTicketComposerCardProps {
  title: string;
  subtitle?: string;
  submitLabel: string;
  busy?: boolean;
  defaultWorkspaceId?: string;
  defaultDocumentId?: string;
  onSubmit: (payload: CreateLegalTicketRequest) => Promise<void>;
}

const emptyForm = (defaultWorkspaceId = "", defaultDocumentId = "") => ({
  request_id: "",
  workspace_id: defaultWorkspaceId,
  document_id: defaultDocumentId,
  question: "",
  answer: "",
  confidence_score: "",
  should_suggest_ticket: true,
  suggestion_type: "",
  suggestion_reason: "",
  missing_information: "",
  risk_level: "",
  legal_domain: "",
  user_action_hint: "CREATE_TICKET",
});

export function LegalTicketComposerCard({
  title,
  subtitle,
  submitLabel,
  busy = false,
  defaultWorkspaceId = "",
  defaultDocumentId = "",
  onSubmit,
}: LegalTicketComposerCardProps) {
  const [form, setForm] = useState(() => emptyForm(defaultWorkspaceId, defaultDocumentId));
  const [error, setError] = useState("");

  const reset = () => setForm(emptyForm(defaultWorkspaceId, defaultDocumentId));

  return (
    <Card title={title} subtitle={subtitle}>
      <div className="space-y-md">
        {error && (
          <p className="rounded-lg bg-error-container px-md py-sm text-sm font-semibold text-risk-high-text dark:bg-red-950/40 dark:text-red-200">
            {error}
          </p>
        )}

        <div className="grid gap-md md:grid-cols-2">
          <label className="text-sm font-semibold">
            Request ID
            <input
              className="form-field mt-xs"
              value={form.request_id}
              onChange={(event) => setForm((previous) => ({ ...previous, request_id: event.target.value }))}
              placeholder="Optional request id"
            />
          </label>
          <label className="text-sm font-semibold">
            Workspace ID
            <input
              className="form-field mt-xs"
              value={form.workspace_id}
              onChange={(event) => setForm((previous) => ({ ...previous, workspace_id: event.target.value }))}
              required
            />
          </label>
          <label className="text-sm font-semibold">
            Document ID
            <input
              className="form-field mt-xs"
              value={form.document_id}
              onChange={(event) => setForm((previous) => ({ ...previous, document_id: event.target.value }))}
              placeholder="Optional document id"
            />
          </label>
          <label className="text-sm font-semibold">
            Confidence score
            <input
              className="form-field mt-xs"
              type="number"
              min={0}
              max={1}
              step="0.01"
              value={form.confidence_score}
              onChange={(event) => setForm((previous) => ({ ...previous, confidence_score: event.target.value }))}
              placeholder="0.00 - 1.00"
            />
          </label>
          <label className="text-sm font-semibold">
            Risk level
            <input
              className="form-field mt-xs"
              value={form.risk_level}
              onChange={(event) => setForm((previous) => ({ ...previous, risk_level: event.target.value }))}
              placeholder="HIGH / MEDIUM / LOW"
            />
          </label>
          <label className="text-sm font-semibold">
            Legal domain
            <input
              className="form-field mt-xs"
              value={form.legal_domain}
              onChange={(event) => setForm((previous) => ({ ...previous, legal_domain: event.target.value }))}
              placeholder="Contract / labor / tenancy..."
            />
          </label>
        </div>

        <label className="block text-sm font-semibold">
          Question
          <textarea
            className="form-field mt-xs min-h-24"
            value={form.question}
            onChange={(event) => setForm((previous) => ({ ...previous, question: event.target.value }))}
            required
          />
        </label>

        <label className="block text-sm font-semibold">
          Answer
          <textarea
            className="form-field mt-xs min-h-24"
            value={form.answer}
            onChange={(event) => setForm((previous) => ({ ...previous, answer: event.target.value }))}
            required
          />
        </label>

        <div className="grid gap-md md:grid-cols-2">
          <label className="block text-sm font-semibold">
            Suggestion type
            <input
              className="form-field mt-xs"
              value={form.suggestion_type}
              onChange={(event) => setForm((previous) => ({ ...previous, suggestion_type: event.target.value }))}
              placeholder="SUGGEST_LAWYER"
            />
          </label>
          <label className="block text-sm font-semibold">
            Action hint
            <input
              className="form-field mt-xs"
              value={form.user_action_hint}
              onChange={(event) => setForm((previous) => ({ ...previous, user_action_hint: event.target.value }))}
              placeholder="CREATE_TICKET"
            />
          </label>
        </div>

        <label className="block text-sm font-semibold">
          Suggestion reason
          <textarea
            className="form-field mt-xs min-h-20"
            value={form.suggestion_reason}
            onChange={(event) => setForm((previous) => ({ ...previous, suggestion_reason: event.target.value }))}
          />
        </label>

        <label className="block text-sm font-semibold">
          Missing information
          <textarea
            className="form-field mt-xs min-h-20"
            value={form.missing_information}
            onChange={(event) => setForm((previous) => ({ ...previous, missing_information: event.target.value }))}
          />
        </label>

        <label className="flex items-center gap-sm text-sm font-semibold">
          <input
            type="checkbox"
            checked={form.should_suggest_ticket}
            onChange={(event) => setForm((previous) => ({ ...previous, should_suggest_ticket: event.target.checked }))}
          />
          Should suggest ticket
        </label>

        <div className="flex flex-wrap gap-sm">
          <Button
            onClick={async () => {
              const workspaceId = form.workspace_id.trim();
              const question = form.question.trim();
              const answer = form.answer.trim();

              if (!workspaceId || !question || !answer) {
                setError("Workspace, question, and answer are required.");
                return;
              }

              setError("");

              await onSubmit({
                request_id: form.request_id.trim() || null,
                workspace_id: workspaceId,
                document_id: form.document_id.trim() || null,
                question,
                answer,
                confidence_score:
                  form.confidence_score.trim().length > 0
                    ? Number(form.confidence_score)
                    : null,
                should_suggest_ticket: form.should_suggest_ticket,
                suggestion_type: form.suggestion_type.trim() || null,
                suggestion_reason: form.suggestion_reason.trim() || null,
                missing_information: form.missing_information.trim() || null,
                risk_level: form.risk_level.trim() || null,
                legal_domain: form.legal_domain.trim() || null,
                user_action_hint: form.user_action_hint.trim() || null,
              });

              reset();
            }}
            disabled={busy}
          >
            {busy ? "Saving..." : submitLabel}
          </Button>
          <Button variant="secondary" onClick={reset} disabled={busy}>
            Reset
          </Button>
        </div>
      </div>
    </Card>
  );
}

