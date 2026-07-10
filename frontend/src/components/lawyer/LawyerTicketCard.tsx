import { Link } from "react-router-dom";
import { Badge } from "../common/Badge";
import { useI18n } from "../../hooks/useI18n";
import type { LawyerTicket } from "../../types/lawyerTicket";

interface LawyerTicketCardProps {
  ticket: LawyerTicket;
}

export function LawyerTicketCard({ ticket }: LawyerTicketCardProps) {
  const { t } = useI18n();

  return (
    <Link
      to={`/lawyer/tickets/${ticket.id}`}
      className="block rounded-2xl border bg-surface p-5 transition hover:border-primary"
    >
      <div className="flex items-start justify-between gap-4">
        <div>
          <h3 className="font-semibold">
            {ticket.issue_title || ticket.question || `${t("legalTickets.table.ticket")} #${ticket.id}`}
          </h3>

          <p className="mt-2 text-sm text-on-surface-variant">
            {ticket.issue_summary || ticket.customer_note || ticket.document_name}
          </p>

          <p className="mt-2 text-xs text-on-surface-variant">
            {t("lawyerTickets.workspace")}: {ticket.workspace_name || t("common.unknown")}
          </p>
        </div>

        <Badge>{ticket.status}</Badge>
      </div>
    </Link>
  );
}
