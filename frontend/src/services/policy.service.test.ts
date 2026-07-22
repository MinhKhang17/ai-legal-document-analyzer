import { afterEach, describe, expect, it, vi } from "vitest";

import { API_BASE_URL } from "../config/api";
import { acceptCurrentPolicies, getCurrentPolicyStatus } from "./policy.service";

describe("policy service URLs", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("adds the backend base URL exactly once", async () => {
    const fetchMock = vi.fn().mockImplementation(async () => new Response(JSON.stringify({
        code: 200,
        message: "Success",
        data: {
          termsVersion: "2026-07-22",
          privacyPolicyVersion: "2026-07-22",
          accepted: true,
        },
      }), { status: 200, headers: { "Content-Type": "application/json" } }));
    vi.stubGlobal("fetch", fetchMock);

    await getCurrentPolicyStatus("token");
    await acceptCurrentPolicies("token");

    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      `${API_BASE_URL}/api/v1/policies/current`,
      `${API_BASE_URL}/api/v1/policies/accept`,
    ]);
  });
});
