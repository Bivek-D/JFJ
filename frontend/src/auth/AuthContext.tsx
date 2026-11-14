import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { refreshSession, setAccessToken, setSessionLostHandler } from "../api/client";
import * as ep from "../api/endpoints";
import type { Me, Mode } from "../api/types";

type Status = "checking" | "anon" | "authed";

interface AuthValue {
  status: Status;
  me: Me | null;
  mode: Mode;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  switchMode: (mode: Mode) => Promise<void>;
  reloadMe: () => Promise<void>;
}

const AuthContext = createContext<AuthValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<Status>("checking");
  const [me, setMe] = useState<Me | null>(null);
  const [mode, setMode] = useState<Mode>("WORKER");

  const reloadMe = useCallback(async () => {
    const m = await ep.getMe();
    setMe(m);
    if (m.mode) setMode(m.mode);
  }, []);

  // On page load: restore the session from the HttpOnly refresh cookie (last-used mode comes back too).
  useEffect(() => {
    let alive = true;
    setSessionLostHandler(() => {
      setMe(null);
      setStatus("anon");
    });
    (async () => {
      try {
        // shared single-flight call, so StrictMode's double effect doesn't refresh twice
        const res = await refreshSession();
        if (res.mode) setMode(res.mode);
        const m = await ep.getMe();
        if (!alive) return;
        setMe(m);
        setStatus("authed");
      } catch {
        if (alive) setStatus("anon");
      }
    })();
    return () => {
      alive = false;
      setSessionLostHandler(null);
    };
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const res = await ep.login(email, password);
    setAccessToken(res.accessToken);
    setMode(res.mode ?? "WORKER");
    const m = await ep.getMe();
    setMe(m);
    setStatus("authed");
  }, []);

  const logout = useCallback(async () => {
    try {
      await ep.logout();
    } catch {
      /* clear locally regardless */
    }
    setAccessToken(null);
    setMe(null);
    setStatus("anon");
  }, []);

  const switchMode = useCallback(async (next: Mode) => {
    const res = await ep.switchMode(next);
    setAccessToken(res.accessToken);
    setMode(res.mode ?? next);
    setMe((m) => (m ? { ...m, mode: res.mode ?? next } : m));
  }, []);

  const value = useMemo(
    () => ({ status, me, mode, login, logout, switchMode, reloadMe }),
    [status, me, mode, login, logout, switchMode, reloadMe],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside AuthProvider");
  return ctx;
}
