import { ApiRequestError } from "../services/http";

type TranslateFn = (key: string) => string;

export const getAdminTicketLoadErrorMessage = (
  error: unknown,
  t: TranslateFn,
): string => {
  if (!(error instanceof ApiRequestError)) {
    return t("legalTickets.errors.load");
  }

  if (error.status === 401) {
    return t("auth.errors.sessionExpired");
  }

  if (error.status === 403) {
    return t("legalTickets.errors.forbidden");
  }

  if (error.status === 0) {
    return t("legalTickets.errors.network");
  }

  if (error.status === 400) {
    return t("legalTickets.errors.invalidRequest");
  }

  if (error.status >= 500) {
    return t("legalTickets.errors.server");
  }

  return t("legalTickets.errors.load");
};

export const getSafeApiErrorStatus = (error: unknown): number | undefined =>
  error instanceof ApiRequestError ? error.status : undefined;
