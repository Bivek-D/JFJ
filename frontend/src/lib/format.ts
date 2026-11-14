import type { ExperienceLevel, GigStatus, PayType, ApplicationStatus } from "../api/types";

const inr = new Intl.NumberFormat("en-IN", { maximumFractionDigits: 2 });
export const rupees = (n: number | null | undefined) => (n == null ? "" : `₹${inr.format(n)}`);

export function payLabel(type: PayType) {
  return type === "DAILY" ? "/ day" : type === "HOURLY" ? "/ hour" : "fixed";
}
export const payText = (amount: number, type: PayType) => `${rupees(amount)} ${payLabel(type)}`;
export const payTypeName: Record<PayType, string> = { DAILY: "Per day", FIXED: "Fixed price", HOURLY: "Per hour" };

/** Dates are shown in India time (the backend's business timezone). */
const TZ = "Asia/Kolkata";
export function shortDate(s?: string | null) {
  if (!s) return "";
  const d = new Date(s.length === 10 ? `${s}T00:00:00+05:30` : s);
  return d.toLocaleDateString("en-IN", { weekday: "short", day: "numeric", month: "short", timeZone: TZ });
}
export function longDate(s?: string | null) {
  if (!s) return "";
  const d = new Date(s.length === 10 ? `${s}T00:00:00+05:30` : s);
  return d.toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric", timeZone: TZ });
}
export function relativeTime(s?: string | null) {
  if (!s) return "";
  const diff = Date.now() - new Date(s).getTime();
  const m = Math.round(diff / 60000);
  if (m < 1) return "just now";
  if (m < 60) return `${m} min ago`;
  const h = Math.round(m / 60);
  if (h < 24) return `${h} hour${h === 1 ? "" : "s"} ago`;
  const d = Math.round(h / 24);
  if (d < 7) return `${d} day${d === 1 ? "" : "s"} ago`;
  return longDate(s);
}
/** Hours left until an ISO timestamp (negative when past). */
export function hoursLeft(s?: string | null) {
  if (!s) return -1;
  return Math.floor((new Date(s).getTime() - Date.now()) / 3_600_000);
}
export function daysLeft(s?: string | null) {
  if (!s) return -1;
  return Math.ceil((new Date(s).getTime() - Date.now()) / 86_400_000);
}
/** Today in India as YYYY-MM-DD. */
export function todayIST() {
  return new Date().toLocaleDateString("en-CA", { timeZone: TZ });
}

export const experienceName: Record<ExperienceLevel, string> = {
  BEGINNER: "Beginner", INTERMEDIATE: "Intermediate", EXPERIENCED: "Experienced",
};

export const LEVELS = [
  { level: 1, name: "Newcomer", xp: 0 },
  { level: 2, name: "Trusted", xp: 60 },
  { level: 3, name: "Skilled", xp: 200 },
  { level: 4, name: "Pro", xp: 450 },
  { level: 5, name: "Expert", xp: 900 },
];
export function levelProgress(totalXp: number) {
  const current = [...LEVELS].reverse().find((l) => l.xp <= totalXp) ?? LEVELS[0];
  const next = LEVELS.find((l) => l.xp > totalXp) ?? null;
  const pct = next ? Math.round(((totalXp - current.xp) / (next.xp - current.xp)) * 100) : 100;
  return { current, next, pct };
}
export const levelLabel = (level?: { level: number; name: string } | null) =>
  level ? `Level ${level.level} · ${level.name}` : "Level 1 · Newcomer";

export function ratingText(r?: { average: number | null; count: number } | null) {
  if (!r || !r.count || r.average == null) return "No ratings yet";
  return `${r.average.toFixed(1)} ★ (${r.count})`;
}

export const gigStatusBadge: Record<GigStatus, { label: string; tone: string }> = {
  DRAFT: { label: "Draft", tone: "grey" },
  OPEN: { label: "Open", tone: "mint" },
  IN_PROGRESS: { label: "In progress", tone: "amber" },
  COMPLETED: { label: "Completed", tone: "indigo" },
  CANCELLED: { label: "Cancelled", tone: "red" },
  EXPIRED: { label: "Expired", tone: "grey" },
};
export const applicationBadge: Record<ApplicationStatus, { label: string; tone: string }> = {
  APPLIED: { label: "Applied", tone: "grey" },
  SHORTLISTED: { label: "Shortlisted", tone: "amber" },
  HIRED: { label: "Hired", tone: "mint" },
  REJECTED: { label: "Not selected", tone: "grey" },
  WITHDRAWN: { label: "Withdrawn", tone: "grey" },
};

export function initials(name?: string | null) {
  if (!name) return "?";
  const parts = name.trim().split(/\s+/);
  return ((parts[0]?.[0] ?? "") + (parts.length > 1 ? parts[parts.length - 1][0] : "")).toUpperCase();
}

/** Missing-field codes from the backend → friendly checklist labels. */
export function missingLabel(code: string) {
  const c = code.toLowerCase();
  if (c.includes("photo")) return "A profile photo link";
  if (c.includes("skill")) return "At least one skill";
  if (c.includes("lang")) return "At least one language";
  if (c.includes("desc")) return "A short description";
  if (c.includes("exp")) return "Your experience";
  return code;
}
