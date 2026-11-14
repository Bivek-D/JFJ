import { useCallback, useEffect, useRef, useState } from "react";

/** Load data with loading / error / reload. Ignores results from stale runs. */
export function useAsync<T>(fn: () => Promise<T>, deps: unknown[], enabled = true) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState<boolean>(enabled);
  const run = useRef(0);

  const load = useCallback(async () => {
    const id = ++run.current;
    setLoading(true);
    setError(null);
    try {
      const d = await fn();
      if (id === run.current) setData(d);
    } catch (e) {
      if (id === run.current) setError(e);
    } finally {
      if (id === run.current) setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  useEffect(() => {
    if (enabled) load();
  }, [load, enabled]);

  return { data, setData, error, loading, reload: load };
}

/** Module-level cache for catalog data that never changes during a session. */
const cache = new Map<string, Promise<unknown>>();
export function cached<T>(key: string, fn: () => Promise<T>): Promise<T> {
  if (!cache.has(key)) {
    const p = fn().catch((e) => {
      cache.delete(key);
      throw e;
    });
    cache.set(key, p);
  }
  return cache.get(key) as Promise<T>;
}
