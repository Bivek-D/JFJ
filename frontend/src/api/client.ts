// Fetch wrapper: access token in memory only, one shared refresh on 401, typed errors,
// and a "server is waking up" signal for slow first requests (free backend cold starts).

const BASE = (import.meta.env.VITE_API_BASE as string | undefined) || "/api/v1";
const SLOW_MS = 3000;
const TIMEOUT_MS = 90_000;

let accessToken: string | null = null;
let refreshInFlight: Promise<{ accessToken: string; mode?: "WORKER" | "PROVIDER" }> | null = null;
let onSessionLost: (() => void) | null = null;

export function setAccessToken(token: string | null) {
  accessToken = token;
}
export function getAccessToken() {
  return accessToken;
}
export function setSessionLostHandler(fn: (() => void) | null) {
  onSessionLost = fn;
}

/* ---- slow-request tracking for the waking-up banner ---- */
type SlowListener = (slow: boolean) => void;
const slowListeners = new Set<SlowListener>();
let slowCount = 0;
export function subscribeSlow(fn: SlowListener) {
  slowListeners.add(fn);
  fn(slowCount > 0);
  return () => {
    slowListeners.delete(fn);
  };
}
function bumpSlow(delta: number) {
  slowCount = Math.max(0, slowCount + delta);
  slowListeners.forEach((l) => l(slowCount > 0));
}

export class ApiError extends Error {
  status: number;
  fieldErrors: Record<string, string>;
  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

/** Backend fieldErrors can be an object or a list; normalise to {field: message}. */
export function normaliseFieldErrors(raw: unknown): Record<string, string> {
  if (!raw) return {};
  if (Array.isArray(raw)) {
    const out: Record<string, string> = {};
    for (const f of raw) {
      if (f && typeof f === "object") {
        const o = f as Record<string, unknown>;
        const key = String(o.field ?? o.name ?? "form");
        out[key] = String(o.message ?? o.defaultMessage ?? "Invalid value");
      }
    }
    return out;
  }
  if (typeof raw === "object") {
    const out: Record<string, string> = {};
    for (const [k, v] of Object.entries(raw as Record<string, unknown>)) out[k] = String(v);
    return out;
  }
  return {};
}

/** One readable sentence for an error (used outside forms). */
export function errorMessage(err: unknown): string {
  if (err instanceof ApiError) {
    if (err.status === 0) return err.message;
    const fe = Object.values(err.fieldErrors);
    if (err.status === 400 && fe.length && !err.message) return fe.join(". ");
    if (err.status === 429) return err.message || "Too many requests. Wait a moment and try again.";
    return err.message || "Something went wrong. Try again.";
  }
  if (err instanceof Error) return err.message;
  return "Something went wrong. Try again.";
}

async function rawFetch(path: string, init: RequestInit, auth: boolean): Promise<Response> {
  const headers = new Headers(init.headers);
  if (init.body !== undefined && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
  if (auth && accessToken) headers.set("Authorization", `Bearer ${accessToken}`);

  const controller = new AbortController();
  const outer = init.signal;
  const onAbort = () => controller.abort(outer?.reason);
  if (outer) {
    if (outer.aborted) controller.abort(outer.reason);
    else outer.addEventListener("abort", onAbort, { once: true });
  }
  const timeout = setTimeout(() => controller.abort(new DOMException("Timeout", "TimeoutError")), TIMEOUT_MS);
  let marked = false;
  const slowTimer = setTimeout(() => {
    marked = true;
    bumpSlow(1);
  }, SLOW_MS);
  try {
    return await fetch(BASE + path, { ...init, headers, signal: controller.signal, credentials: "same-origin" });
  } catch (e) {
    if (outer?.aborted) throw e; // caller cancelled: let it propagate as AbortError
    if (e instanceof DOMException && e.name === "TimeoutError") {
      throw new ApiError(0, "The server is taking too long to answer. Check your connection and try again.");
    }
    if (controller.signal.reason instanceof DOMException && controller.signal.reason.name === "TimeoutError") {
      throw new ApiError(0, "The server is taking too long to answer. Check your connection and try again.");
    }
    throw new ApiError(0, "Can't reach the server. Check your connection and try again.");
  } finally {
    clearTimeout(timeout);
    clearTimeout(slowTimer);
    if (marked) bumpSlow(-1);
    outer?.removeEventListener("abort", onAbort);
  }
}

async function parse<T>(res: Response): Promise<T> {
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  let data: unknown = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = null;
    }
  }
  if (!res.ok) {
    const body = (data ?? {}) as Record<string, unknown>;
    const fieldErrors = normaliseFieldErrors(body.fieldErrors);
    let message = typeof body.message === "string" ? body.message : "";
    if (!message) {
      message =
        res.status === 401 ? "Please log in again." :
        res.status === 403 ? "You can't do that in this mode." :
        res.status === 404 ? "We couldn't find that." :
        res.status === 503 ? "This service isn't available right now." :
        "Something went wrong. Try again.";
    }
    throw new ApiError(res.status, message, fieldErrors);
  }
  return data as T;
}

/** POST /auth/refresh — one shared call for every request that hit a 401. */
export function refreshSession(): Promise<{ accessToken: string; mode?: "WORKER" | "PROVIDER" }> {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      const res = await rawFetch("/auth/refresh", { method: "POST" }, false);
      const data = await parse<{ accessToken: string; mode?: "WORKER" | "PROVIDER" }>(res);
      accessToken = data.accessToken;
      return data;
    })().finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

export interface RequestOptions {
  method?: string;
  body?: unknown;
  signal?: AbortSignal;
  /** skip auth + refresh (login, register, refresh) */
  publicCall?: boolean;
}

export async function api<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const init: RequestInit = {
    method: opts.method ?? (opts.body !== undefined ? "POST" : "GET"),
    body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
    signal: opts.signal,
  };
  let res = await rawFetch(path, init, !opts.publicCall);
  if (res.status === 401 && !opts.publicCall && !path.startsWith("/auth/")) {
    try {
      await refreshSession();
    } catch {
      accessToken = null;
      onSessionLost?.();
      return parse<T>(res);
    }
    res = await rawFetch(path, init, true);
  }
  return parse<T>(res);
}

/** Ping the health endpoint (no /api/v1 prefix). */
export async function pingHealth(signal?: AbortSignal): Promise<boolean> {
  try {
    const res = await fetch("/actuator/health", { signal });
    return res.ok;
  } catch {
    return false;
  }
}

export function qs(params: Record<string, string | number | boolean | null | undefined>) {
  const s = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v === null || v === undefined || v === "") continue;
    s.set(k, String(v));
  }
  const str = s.toString();
  return str ? `?${str}` : "";
}
