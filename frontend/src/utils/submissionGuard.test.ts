import { describe, expect, it } from "vitest";
import { finishSubmission, tryStartSubmission } from "./submissionGuard";

describe("submissionGuard", () => {
  it("rejects a repeated submission until the pending request finishes", () => {
    const state = { current: false };

    expect(tryStartSubmission(state)).toBe(true);
    expect(tryStartSubmission(state)).toBe(false);

    finishSubmission(state);
    expect(tryStartSubmission(state)).toBe(true);
  });
});
