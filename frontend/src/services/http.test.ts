import { describe, expect, it } from "vitest";
import { ApiRequestError, isPlanEntitlementError } from "./http";

describe("isPlanEntitlementError", () => {
  it("recognizes plan-related 403 and 409 ticket failures", () => {
    expect(isPlanEntitlementError(new ApiRequestError(
      403,
      "An upgraded plan is required",
      { errorCode: "EXPERT_TICKET_REQUIRES_PREMIUM" },
      "",
    ))).toBe(true);
    expect(isPlanEntitlementError(new ApiRequestError(
      409,
      "Free support ticket limit reached",
      { errorCode: "FREE_SUPPORT_TICKET_LIMIT_REACHED" },
      "",
    ))).toBe(true);
  });

  it("does not redirect unrelated conflicts", () => {
    expect(isPlanEntitlementError(new ApiRequestError(
      409,
      "Duplicate ticket",
      { errorCode: "DUPLICATE_TICKET" },
      "",
    ))).toBe(false);
  });

  it.each([
    "WORKSPACE_LIMIT_REACHED",
    "CONTRACT_ANALYSIS_QUOTA_EXCEEDED",
    "CONTRACTS_PER_WORKSPACE_LIMIT_EXCEEDED",
    "MAX_FILE_SIZE_EXCEEDED",
    "STORAGE_LIMIT_EXCEEDED",
    "ATTACHED_DOCUMENT_LIMIT_EXCEEDED",
    "AI_TOKEN_QUOTA_EXCEEDED",
    "TOKEN_QUOTA_EXCEEDED",
    "DRAFT_CONTRACT_QUOTA_EXCEEDED",
    "EXPERT_TICKET_QUOTA_EXCEEDED",
    "EXPERT_TICKET_CREDIT_UNAVAILABLE_REQUIRES_PAID_QUOTE",
    "FREE_SUPPORT_TICKET_LIMIT_REACHED",
  ])("recognizes plan quota conflict %s", (errorCode) => {
    expect(isPlanEntitlementError(new ApiRequestError(
      409,
      "Plan quota exhausted",
      { errorCode },
      "",
    ))).toBe(true);
  });
});
