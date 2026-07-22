import { afterEach, describe, expect, it, vi } from "vitest";
import { createWorkspace } from "./workspace.service";

describe("createWorkspace", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("sends only one POST for repeated concurrent submissions", async () => {
    let finishRequest: (() => void) | undefined;
    const gate = new Promise<void>((resolve) => {
      finishRequest = resolve;
    });
    const fetchMock = vi.fn(async () => {
      await gate;
      return new Response(JSON.stringify({
        code: 201,
        message: "Created",
        data: {
          workspaceId: "ws_1",
          name: "Matter A",
          description: "Description",
          status: "ACTIVE",
          createdAt: "2026-07-22T00:00:00",
        },
      }), { status: 201, headers: { "content-type": "application/json" } });
    });
    vi.stubGlobal("fetch", fetchMock);

    const first = createWorkspace("token", { name: " Matter A ", description: " Description " });
    const second = createWorkspace("token", { name: "Matter A", description: "Description" });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    finishRequest?.();
    const [firstResult, secondResult] = await Promise.all([first, second]);

    expect(firstResult).toEqual(secondResult);
    expect(firstResult.status).toBe("active");
  });
});
