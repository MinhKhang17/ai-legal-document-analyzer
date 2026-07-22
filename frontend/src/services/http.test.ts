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
});
