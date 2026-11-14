import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api, ApiError, normaliseFieldErrors, qs, setAccessToken, getAccessToken } from "../api/client";

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

describe("api client", () => {
  beforeEach(() => setAccessToken("old"));
  afterEach(() => vi.restoreAllMocks());

  it("runs ONE refresh for parallel 401s and retries each request once", async () => {
    let refreshCalls = 0;
    const fetchMock = vi.fn(async (url: string, init: RequestInit) => {
      const auth = new Headers(init.headers).get("Authorization");
      if (url.endsWith("/auth/refresh")) {
        refreshCalls++;
        await new Promise((r) => setTimeout(r, 20));
        return json(200, { accessToken: "new", mode: "WORKER" });
      }
      if (auth === "Bearer old") return json(401, { message: "expired" });
      return json(200, { ok: url });
    });
    vi.stubGlobal("fetch", fetchMock);
    const results = await Promise.all([api("/a"), api("/b"), api("/c")]);
    expect(refreshCalls).toBe(1);
    expect(results).toHaveLength(3);
    expect(getAccessToken()).toBe("new");
  });

  it("throws ApiError with fieldErrors on 400", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => json(400, { message: "Invalid", fieldErrors: { phone: "Bad phone" } })));
    await expect(api("/x", { body: {} })).rejects.toMatchObject({ status: 400, fieldErrors: { phone: "Bad phone" } });
  });

  it("does not refresh for public calls", async () => {
    const f = vi.fn(async () => json(401, { message: "Wrong password" }));
    vi.stubGlobal("fetch", f);
    await expect(api("/auth/login", { body: {}, publicCall: true })).rejects.toBeInstanceOf(ApiError);
    expect(f).toHaveBeenCalledTimes(1);
  });

  it("handles 204 with no body", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response(null, { status: 204 })));
    await expect(api("/gigs/1", { method: "DELETE" })).resolves.toBeUndefined();
  });
});

describe("helpers", () => {
  it("normalises list and object fieldErrors", () => {
    expect(normaliseFieldErrors([{ field: "email", message: "taken" }])).toEqual({ email: "taken" });
    expect(normaliseFieldErrors({ pincode: "6 digits" })).toEqual({ pincode: "6 digits" });
    expect(normaliseFieldErrors(null)).toEqual({});
  });
  it("builds query strings and skips empty values", () => {
    expect(qs({ a: 1, b: "", c: undefined, d: "x y" })).toBe("?a=1&d=x+y");
    expect(qs({})).toBe("");
  });
});
