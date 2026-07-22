import { describe, expect, it } from "vitest";
import { ticketDate, ticketList, ticketMoney, ticketText } from "./ticketDisplay";

describe("ticket display fallbacks", () => {
  it("never renders blank, invalid date, NaN or a missing list", () => {
    expect(ticketText("   ")).toBe("Not provided");
    expect(ticketDate("not-a-date")).toBe("Not scheduled");
    expect(ticketMoney(Number.NaN)).toBe("Pending pricing");
    expect(ticketList(undefined)).toEqual([]);
  });
});
