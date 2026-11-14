import { useEffect, useRef, useState, type ReactNode } from "react";
import { Link, NavLink, Navigate, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { Icon, type IconName } from "./Icon";
import { Avatar, Badge, WakingBanner, useToast } from "./ui";
import { levelProgress } from "../lib/format";
import { errorMessage } from "../api/client";
import type { Mode } from "../api/types";

interface NavItem { to: string; label: string; icon: IconName; end?: boolean }

export function navItems(mode: Mode): NavItem[] {
  return mode === "PROVIDER"
    ? [
        { to: "/gigs", label: "My gigs", icon: "briefcase" },
        { to: "/post", label: "Post", icon: "plus" },
        { to: "/", label: "Explore", icon: "map", end: true },
        { to: "/profile", label: "Profile", icon: "user" },
      ]
    : [
        { to: "/", label: "Explore", icon: "map", end: true },
        { to: "/jobs", label: "My jobs", icon: "briefcase" },
        { to: "/profile", label: "Profile", icon: "user" },
      ];
}

/** Switch mode and land on that mode's home. */
export function useModeSwitch() {
  const { switchMode } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  return async (mode: Mode, to?: string) => {
    try {
      await switchMode(mode);
      navigate(to ?? (mode === "PROVIDER" ? "/gigs" : "/"));
      toast({ text: mode === "PROVIDER" ? "You're now hiring people." : "You're now finding work." });
    } catch (e) {
      toast({ text: errorMessage(e), error: true });
    }
  };
}

function AccountMenu({ onClose }: { onClose: () => void }) {
  const { me, mode, logout } = useAuth();
  const go = useModeSwitch();
  const navigate = useNavigate();
  if (!me) return null;
  const xp = me.level?.totalXp ?? 0;
  const { current, next, pct } = levelProgress(xp);
  return (
    <div className="menu" role="menu">
      <div className="row" style={{ padding: 18 }}>
        <Avatar name={me.name} size={44} />
        <div className="grow">
          <div className="strong">{me.name}</div>
          <div className="small muted" style={{ overflow: "hidden", textOverflow: "ellipsis" }}>{me.email}</div>
        </div>
      </div>
      <div className="stack-sm" style={{ padding: "0 18px 16px" }}>
        <div className="row between">
          <Badge tone="indigo">Level {current.level} · {current.name}</Badge>
          <span className="xs strong body2">{next ? `${xp} / ${next.xp} XP` : `${xp} XP`}</span>
        </div>
        <div className="track" style={{ height: 6 }}><div style={{ width: `${pct}%` }} /></div>
      </div>
      <div className="stack-sm" style={{ padding: "14px 18px", background: "var(--bg)" }}>
        <span className="xs strong muted">You're using JFJ to</span>
        <div className="mode-switch">
          <button className={mode === "WORKER" ? "on" : ""} onClick={() => { onClose(); if (mode !== "WORKER") go("WORKER"); }}>Find work</button>
          <button className={mode === "PROVIDER" ? "on" : ""} onClick={() => { onClose(); if (mode !== "PROVIDER") go("PROVIDER"); }}>Hire people</button>
        </div>
      </div>
      <div style={{ padding: "6px 8px 8px" }}>
        <button className="menu-link" onClick={() => { onClose(); navigate("/profile/edit"); }}>
          <Icon name="user" size={20} color="#495572" /> Edit my worker profile
        </button>
        <button className="menu-link" onClick={() => { onClose(); navigate("/profile"); }}>
          <Icon name="star" size={20} filled color="#F59F00" /> My ratings and reviews
        </button>
        <button className="menu-link" style={{ color: "var(--red-700)", fontWeight: 600 }} onClick={async () => { onClose(); await logout(); navigate("/login"); }}>
          <Icon name="logout" size={20} color="#C92A2A" /> Log out
        </button>
      </div>
    </div>
  );
}

function Header() {
  const { me, mode } = useAuth();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const go = useModeSwitch();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  useEffect(() => setOpen(false), [pathname]);
  useEffect(() => {
    if (!open) return;
    const close = (e: MouseEvent) => !ref.current?.contains(e.target as Node) && setOpen(false);
    document.addEventListener("mousedown", close);
    return () => document.removeEventListener("mousedown", close);
  }, [open]);

  const items = mode === "PROVIDER"
    ? [{ to: "/gigs", label: "My gigs" }, { to: "/", label: "Explore gigs", end: true }, { to: "/profile", label: "My profile" }]
    : [{ to: "/", label: "Explore gigs", end: true }, { to: "/jobs", label: "My jobs" }, { to: "/profile", label: "My profile" }];

  return (
    <header className="header">
      <Link to={mode === "PROVIDER" ? "/gigs" : "/"} className="wordmark" aria-label="JFJ home">JFJ</Link>
      <nav className="nav-pill" aria-label="Main">
        {items.map((i) => (
          <NavLink key={i.to} to={i.to} end={i.end} className={({ isActive }) => (isActive ? "active" : "")}>
            {i.label}
          </NavLink>
        ))}
      </nav>
      <div className="header-right">
        <div ref={ref} style={{ position: "relative" }}>
          <button className="avatar-btn" onClick={() => setOpen((o) => !o)} aria-haspopup="menu" aria-expanded={open} aria-label="Account menu">
            <Avatar name={me?.name} size={40} />
          </button>
          {open && <AccountMenu onClose={() => setOpen(false)} />}
        </div>
        <button
          className="btn btn-primary btn-sm"
          style={{ padding: "10px 16px 10px 18px" }}
          onClick={() => (mode === "PROVIDER" ? navigate("/post") : go("PROVIDER", "/post"))}
        >
          Post a gig <Icon name="arrowRight" size={18} color="#fff" />
        </button>
      </div>
    </header>
  );
}

function BottomNav() {
  const { mode } = useAuth();
  const items = navItems(mode);
  return (
    <nav className="bottom-nav" style={{ gridTemplateColumns: `repeat(${items.length}, 1fr)` }} aria-label="Main">
      {items.map((i) => (
        <NavLink key={i.to} to={i.to} end={i.end} className={({ isActive }) => (isActive ? "active" : "")}>
          {({ isActive }) => (
            <>
              <Icon name={i.icon} color={isActive ? "#3B5BDB" : "#8590A8"} />
              {i.label}
            </>
          )}
        </NavLink>
      ))}
    </nav>
  );
}

/** Mobile top bar with a back button, for inner pages. */
export function MobileTop({ title, back = true, right }: { title: string; back?: boolean; right?: ReactNode }) {
  const navigate = useNavigate();
  return (
    <div className="mobile-top">
      {back && (
        <button className="icon-btn" style={{ background: "none", width: 32 }} onClick={() => navigate(-1)} aria-label="Back">
          <Icon name="chevronLeft" />
        </button>
      )}
      <div className="h3 grow">{title}</div>
      {right}
    </div>
  );
}

export function Shell({ children, bottomNav = true }: { children: ReactNode; bottomNav?: boolean }) {
  return (
    <div className={`page ${bottomNav ? "has-bottom-nav" : ""}`}>
      <WakingBanner />
      <Header />
      {children}
      {bottomNav && <BottomNav />}
    </div>
  );
}

export function FullScreenLoader() {
  return (
    <div style={{ minHeight: "100vh", display: "grid", placeItems: "center", padding: 40 }}>
      <div className="stack center" style={{ alignItems: "center", maxWidth: 320 }}>
        <div className="wordmark" style={{ fontSize: 48 }}>JFJ</div>
        <span className="spinner lg" />
        <WakingBanner />
      </div>
    </div>
  );
}

/** Route guards: by login state and, optionally, by mode. */
export function RequireAuth({ children, mode }: { children: ReactNode; mode?: Mode }) {
  const { status, mode: current } = useAuth();
  const { pathname } = useLocation();
  if (status === "checking") return <FullScreenLoader />;
  if (status === "anon") return <Navigate to="/login" replace state={{ from: pathname }} />;
  if (mode && current !== mode) return <ModeGate need={mode} />;
  return <>{children}</>;
}

function ModeGate({ need }: { need: Mode }) {
  const go = useModeSwitch();
  const { pathname } = useLocation();
  return (
    <Shell>
      <div className="container narrow">
        <div className="card stack" style={{ alignItems: "flex-start" }}>
          <div className="h2">{need === "PROVIDER" ? "This page is for hiring" : "This page is for finding work"}</div>
          <p className="body2">
            You're in {need === "PROVIDER" ? "Find work" : "Hire people"} mode. Switch to see this page.
          </p>
          <button className="btn btn-primary" onClick={() => go(need, pathname)}>
            Switch to {need === "PROVIDER" ? "Hire people" : "Find work"}
          </button>
        </div>
      </div>
    </Shell>
  );
}
