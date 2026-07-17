export const VALID_LEGAL_TICKET_STATUSES = [
  "DRAFT",
  "PENDING_ADMIN_REVIEW",
  "REJECTED_BY_ADMIN",
  "ASSIGNED_TO_LAWYER",
  "IN_REVIEW",
  "NEED_MORE_INFO",
  "CUSTOMER_RESPONDED",
  "RESOLVED",
  "CLOSED",
  "CANCELLED",
  "REOPENED",
] as const;

export type LegalTicketStatus = (typeof VALID_LEGAL_TICKET_STATUSES)[number];
export type LegalTicketFilter = "ALL" | LegalTicketStatus;
export type TranslateFn = (key: string) => string;

export const LEGAL_TICKET_STATUS_LABEL_KEYS: Record<LegalTicketStatus, string> = {
  DRAFT: "legalTickets.filters.DRAFT",
  PENDING_ADMIN_REVIEW: "legalTickets.filters.PENDING_ADMIN_REVIEW",
  REJECTED_BY_ADMIN: "legalTickets.filters.REJECTED_BY_ADMIN",
  ASSIGNED_TO_LAWYER: "legalTickets.filters.ASSIGNED_TO_LAWYER",
  IN_REVIEW: "legalTickets.filters.IN_REVIEW",
  NEED_MORE_INFO: "legalTickets.filters.NEED_MORE_INFO",
  CUSTOMER_RESPONDED: "legalTickets.filters.CUSTOMER_RESPONDED",
  RESOLVED: "legalTickets.filters.RESOLVED",
  CLOSED: "legalTickets.filters.CLOSED",
  CANCELLED: "legalTickets.filters.CANCELLED",
  REOPENED: "legalTickets.filters.REOPENED",
};

export const LEGAL_TICKET_FILTER_LABEL_KEYS: Record<LegalTicketFilter, string> = {
  ALL: "legalTickets.filters.ALL",
  ...LEGAL_TICKET_STATUS_LABEL_KEYS,
};

export type LegalTicketFilterOption = {
  value: LegalTicketFilter;
  label: string;
};

export const getLegalTicketFilterOptions = (t: TranslateFn): LegalTicketFilterOption[] => [
  { value: "ALL", label: t(LEGAL_TICKET_FILTER_LABEL_KEYS.ALL) },
  ...VALID_LEGAL_TICKET_STATUSES.map(
    (status): LegalTicketFilterOption => ({
      value: status,
      label: t(LEGAL_TICKET_STATUS_LABEL_KEYS[status]),
    }),
  ),
];

export const LEGAL_TICKET_TERMINAL_STATUSES = [
  "REJECTED_BY_ADMIN",
  "RESOLVED",
  "CLOSED",
  "CANCELLED",
] as const satisfies readonly LegalTicketStatus[];

export const isLegalTicketStatus = (value: string): value is LegalTicketStatus =>
  VALID_LEGAL_TICKET_STATUSES.includes(value as LegalTicketStatus);

export const normalizeLegalTicketStatus = (
  value?: string | null,
): LegalTicketStatus | undefined => {
  const normalizedValue = value?.trim().toUpperCase();

  if (!normalizedValue) {
    return undefined;
  }

  return isLegalTicketStatus(normalizedValue) ? normalizedValue : undefined;
};

export const toLegalTicketFilter = (value?: string | null): LegalTicketFilter => {
  const normalizedValue = value?.trim().toUpperCase();

  if (!normalizedValue || normalizedValue === "ALL") {
    return "ALL";
  }

  return isLegalTicketStatus(normalizedValue) ? normalizedValue : "ALL";
};

export const getLegalTicketStatusLabel = (value: string | null | undefined, t: TranslateFn): string => {
  const status = normalizeLegalTicketStatus(value);
  return status ? t(LEGAL_TICKET_STATUS_LABEL_KEYS[status]) : t("legalTickets.status.unknown");
};

export const getLegalTicketFilterLabel = (filter: LegalTicketFilter, t: TranslateFn): string =>
  t(LEGAL_TICKET_FILTER_LABEL_KEYS[filter]);

export const isTerminalLegalTicketStatus = (value?: string | null): boolean => {
  const status = normalizeLegalTicketStatus(value);
  return status
    ? (LEGAL_TICKET_TERMINAL_STATUSES as readonly LegalTicketStatus[]).includes(status)
    : false;
};
