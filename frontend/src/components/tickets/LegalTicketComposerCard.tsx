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
  issue_fingerprint: "",
  customer_note: "",
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
            Issue fingerprint
            <input
              className="form-field mt-xs"
              value={form.issue_fingerprint}
              onChange={(event) => setForm((previous) => ({ ...previous, issue_fingerprint: event.target.value }))}
              placeholder="Optional issue fingerprint"
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
          Customer note
          <textarea
            className="form-field mt-xs min-h-20"
            value={form.customer_note}
            onChange={(event) => setForm((previous) => ({ ...previous, customer_note: event.target.value }))}
          />
        </label>

        <div className="flex flex-wrap gap-sm">
          <Button
            onClick={async () => {
              const workspaceId = form.workspace_id.trim();
              const question = form.question.trim();

              if (!workspaceId || !question) {
                setError("Workspace and question are required.");
                return;
              }

              setError("");

              await onSubmit({
                request_id: form.request_id.trim() || null,
                workspace_id: workspaceId,
                document_id: form.document_id.trim() || null,
                question,
                issue_fingerprint: form.issue_fingerprint.trim() || null,
                customer_note: form.customer_note.trim() || null,
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

