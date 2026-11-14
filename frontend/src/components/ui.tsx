import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from "react";
import { Icon, type IconName } from "./Icon";
import { errorMessage, subscribeSlow } from "../api/client";
import { initials } from "../lib/format";
import { Link } from "react-router-dom";

/** "Job: <gig title>" line on a review; links to the gig when `to` is given. */
export function ReviewJobTag({ title, to }: { title: string; to?: string }) {
  const body = (
    <>
      <Icon name="briefcase" size={14} color="#495572" />
      <span className="review-job-title">{title}</span>
    </>
  );
  return to ? <Link to={to} className="review-job" title={title}>{body}</Link> : <span className="review-job" title={title}>{body}</span>;
}

/* ---------- Field ---------- */
export function Field({
  label, hint, error, children, className,
}: { label?: string; hint?: ReactNode; error?: string; children: ReactNode; className?: string }) {
  return (
    <label className={`field ${error ? "error" : ""} ${className ?? ""}`}>
      {label && <span className="field-label">{label}</span>}
      {children}
      {error ? <span className="field-error" role="alert">{error}</span> : hint ? <span className="field-hint">{hint}</span> : null}
    </label>
  );
}

/* ---------- Badge ---------- */
export function Badge({ tone = "grey", children }: { tone?: string; children: ReactNode }) {
  return <span className={`badge ${tone}`}>{children}</span>;
}

/* ---------- Avatar (photo link or initials; providers have no photo yet) ---------- */
export function Avatar({ name, url, size = 40, solid }: { name?: string | null; url?: string | null; size?: number; solid?: boolean }) {
  const [broken, setBroken] = useState(false);
  if (url && !broken) {
    return <img className="avatar" src={url} alt="" width={size} height={size} style={{ width: size, height: size }} onError={() => setBroken(true)} />;
  }
  return (
    <span className={`avatar ${solid ? "solid" : ""}`} style={{ width: size, height: size, fontSize: Math.round(size * 0.36) }} aria-hidden>
      {initials(name)}
    </span>
  );
}

/* ---------- Stars ---------- */
export function Stars({ value, size = 16 }: { value: number; size?: number }) {
  return (
    <span className="stars" aria-label={`${value} out of 5 stars`}>
      {[1, 2, 3, 4, 5].map((i) => (
        <Icon key={i} name="star" size={size} filled color={i <= Math.round(value) ? "#F59F00" : "#DDE3EE"} />
      ))}
    </span>
  );
}
export function StarInput({ value, onChange }: { value: number; onChange: (v: number) => void }) {
  return (
    <span className="stars input" role="radiogroup" aria-label="Rating">
      {[1, 2, 3, 4, 5].map((i) => (
        <button key={i} type="button" className={i <= value ? "on" : ""} onClick={() => onChange(i)} role="radio" aria-checked={i === value} aria-label={`${i} star${i > 1 ? "s" : ""}`}>
          <Icon name="star" size={34} filled color="currentColor" />
        </button>
      ))}
    </span>
  );
}
export const ratingWord = (v: number) => ["", "Poor", "Okay", "Good", "Very good", "Excellent"][v] ?? "";

/* ---------- Note ---------- */
export function Note({ tone = "info", icon = "info", children }: { tone?: string; icon?: IconName; children: ReactNode }) {
  return (
    <div className={`note ${tone}`}>
      <Icon name={icon} size={18} />
      <div>{children}</div>
    </div>
  );
}

/* ---------- Loading / empty / error ---------- */
export function Skeleton({ h = 120, w = "100%", r = 16 }: { h?: number; w?: number | string; r?: number }) {
  return <div className="skeleton" style={{ height: h, width: w, borderRadius: r }} />;
}
export function SkeletonList({ n = 4, h = 118 }: { n?: number; h?: number }) {
  return <div className="stack-sm">{Array.from({ length: n }, (_, i) => <Skeleton key={i} h={h} />)}</div>;
}
export function Empty({ icon = "search", title, children, action }: { icon?: IconName; title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className="empty">
      <div className="icon-circle"><Icon name={icon} /></div>
      <div className="h3">{title}</div>
      {children && <div className="small">{children}</div>}
      {action}
    </div>
  );
}
export function ErrorBox({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  return (
    <div className="note red" role="alert" style={{ alignItems: "center" }}>
      <Icon name="info" size={18} />
      <div className="grow">{errorMessage(error)}</div>
      {onRetry && <button className="btn btn-sm btn-secondary" onClick={onRetry}><Icon name="refresh" size={16} />Retry</button>}
    </div>
  );
}

/* ---------- Waking-up banner (cold start) ---------- */
export function WakingBanner() {
  const [slow, setSlow] = useState(false);
  useEffect(() => subscribeSlow(setSlow), []);
  if (!slow) return null;
  return (
    <div className="waking" role="status">
      <span className="spinner" />
      Waking up the server… the first load can take up to a minute.
    </div>
  );
}

/* ---------- Modal / bottom sheet ---------- */
export function Modal({ onClose, children, label, width }: { onClose: () => void; children: ReactNode; label: string; width?: number }) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const prev = document.activeElement as HTMLElement | null;
    const first = ref.current?.querySelector<HTMLElement>("input, textarea, button, select, a");
    first?.focus();
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    document.addEventListener("keydown", onKey);
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = overflow;
      prev?.focus?.();
    };
  }, [onClose]);
  return (
    <div className="scrim" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal" role="dialog" aria-modal="true" aria-label={label} ref={ref} style={width ? { width: `min(${width}px, 100%)` } : undefined}>
        {children}
      </div>
    </div>
  );
}
export function ModalHead({ title, onClose }: { title: string; onClose: () => void }) {
  return (
    <div className="modal-head">
      <div className="h2">{title}</div>
      <button className="icon-btn hide-mobile" onClick={onClose} aria-label="Close"><Icon name="close" size={20} color="#495572" /></button>
    </div>
  );
}

/* ---------- Toasts ---------- */
interface ToastMsg { id: number; text: string; error?: boolean; action?: { label: string; run: () => void } }
const ToastCtx = createContext<(t: Omit<ToastMsg, "id">) => void>(() => {});
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<ToastMsg | null>(null);
  const timer = useRef<number | undefined>(undefined);
  const show = useCallback((t: Omit<ToastMsg, "id">) => {
    window.clearTimeout(timer.current);
    setToast({ ...t, id: Date.now() });
    timer.current = window.setTimeout(() => setToast(null), 4500);
  }, []);
  return (
    <ToastCtx.Provider value={show}>
      {children}
      {toast && (
        <div className={`toast ${toast.error ? "error" : ""}`} role="status" key={toast.id}>
          <span className="ok"><Icon name={toast.error ? "close" : "check"} size={16} color="#fff" /></span>
          <span>{toast.text}</span>
          {toast.action && (
            <button className="link-btn" style={{ color: "#63E6BE" }} onClick={() => { toast.action!.run(); setToast(null); }}>
              {toast.action.label}
            </button>
          )}
        </div>
      )}
    </ToastCtx.Provider>
  );
}
export const useToast = () => useContext(ToastCtx);

/* ---------- Pill tabs ---------- */
export function PillTabs<T extends string>({
  tabs, value, onChange, fill,
}: { tabs: { value: T; label: string; count?: number }[]; value: T; onChange: (v: T) => void; fill?: boolean }) {
  return (
    <div className={`pill-tabs ${fill ? "fill" : ""}`} role="tablist">
      {tabs.map((t) => (
        <button key={t.value} role="tab" aria-selected={value === t.value} className={`pill-tab ${value === t.value ? "active" : ""}`} onClick={() => onChange(t.value)}>
          {t.label}
          {t.count != null && (value === t.value ? <span className="count">{t.count}</span> : <span className="muted">· {t.count}</span>)}
        </button>
      ))}
    </div>
  );
}

/* ---------- Level / XP ---------- */
import { levelProgress } from "../lib/format";
export function LevelBar({ totalXp, compact }: { totalXp: number; compact?: boolean }) {
  const { current, next, pct } = levelProgress(totalXp);
  return (
    <div className="stack-sm">
      <div className="row between">
        <Badge tone="indigo">Level {current.level} · {current.name}</Badge>
        <span className="xs strong body2">{next ? `${totalXp} / ${next.xp} XP to ${next.name}` : `${totalXp} XP · top level`}</span>
      </div>
      <div className="track"><div style={{ width: `${pct}%` }} /></div>
      {!compact && <span className="xs muted">Each finished gig and every good rating adds XP.</span>}
    </div>
  );
}
