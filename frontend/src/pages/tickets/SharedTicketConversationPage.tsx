import { ArrowLeft, ShieldCheck } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Badge } from "../../components/common/Badge";
import { Card } from "../../components/common/Card";
import { PageHeader } from "../../components/common/PageHeader";
import { useI18n } from "../../hooks/useI18n";
import { getSharedTicketConversation } from "../../services/legalTicket.service";
import type { LegalTicket } from "../../types/legalTicket";
import { getLegalTicketStatusLabel } from "../../types/legalTicketStatus";

export function SharedTicketConversationPage() {
  const { t } = useI18n();
  const { token = "" } = useParams();
  const [ticket, setTicket] = useState<LegalTicket | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    void getSharedTicketConversation(token)
      .then((value) => { if (active) setTicket(value); })
      .catch(() => {
        if (active) setError(t("sharedTicket.loadError"));
      });
    return () => { active = false; };
  }, [t, token]);

  return <div className="space-y-gutter">
    <PageHeader title={ticket?.title || t("sharedTicket.title")}
      subtitle={t("sharedTicket.subtitle")}
      actions={<Link className="inline-flex items-center gap-xs text-sm font-semibold text-primary" to={ticket ? `/tickets/${ticket.id}` : "/tickets"}><ArrowLeft className="h-4 w-4" aria-hidden="true" />{t("sharedTicket.openTicket")}</Link>} />
    {error && <div className="rounded-xl border border-error/40 bg-error/10 p-md text-sm text-error" role="alert">{error}</div>}
    {!ticket && !error && <Card><p role="status">{t("sharedTicket.verifying")}</p></Card>}
    {ticket && <Card title={ticket.title || ticket.question || ticket.id}
      subtitle={ticket.description}
      actions={ticket.status ? <Badge>{getLegalTicketStatusLabel(ticket.status, t)}</Badge> : undefined}>
      <div className="flex items-center gap-xs text-sm text-success"><ShieldCheck className="h-4 w-4" aria-hidden="true" />{t("sharedTicket.verifiedNotice")}</div>
    </Card>}
  </div>;
}
