import type { LawyerTicket } from "../../types/lawyerTicket";
import { useI18n } from "../../hooks/useI18n";
import { LawyerTicketCard } from "./LawyerTicketCard";

interface LawyerTicketListProps {
  tickets: LawyerTicket[];
}

export function LawyerTicketList({ tickets }: LawyerTicketListProps) {
  const { t } = useI18n();

  if (tickets.length === 0) {
    return (
      <div className="rounded-xl border border-dashed p-10 text-center">
        <h3 className="text-lg font-semibold">{t("lawyerTickets.noTicketsFound")}</h3>
        <p className="mt-1 text-sm text-on-surface-variant">
          {t("lawyerTickets.noTicketsFoundDescription")}
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {tickets.map((ticket) => (
        <LawyerTicketCard key={ticket.id} ticket={ticket} />
      ))}
    </div>
  );
}
