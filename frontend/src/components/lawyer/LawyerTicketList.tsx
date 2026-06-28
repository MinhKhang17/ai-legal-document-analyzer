import type { LawyerTicket } from "../../types/lawyerTicket";
import { LawyerTicketCard } from "./LawyerTicketCard";

interface LawyerTicketListProps {
  tickets: LawyerTicket[];
}

export function LawyerTicketList({ tickets }: LawyerTicketListProps) {
  if (tickets.length === 0) {
    return (
      <div className="rounded-xl border border-dashed p-10 text-center">
        <h3 className="text-lg font-semibold">No tickets found</h3>
        <p className="mt-1 text-sm text-on-surface-variant">
          Assigned legal tickets will appear here.
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