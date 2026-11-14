import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { MobileTop, Shell, useModeSwitch } from "../components/Shell";
import { Icon } from "../components/Icon";
import { Avatar, Badge, Empty, LevelBar, Note, ReviewJobTag, Skeleton, Stars } from "../components/ui";
import { jobFor, myReviewJobs, type ReviewJob } from "../lib/reviewJobs";
import { GigThumb } from "../components/GigCard";
import * as ep from "../api/endpoints";
import type { OwnerGig, PendingReview, Review, WorkerProfile } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import { experienceName, missingLabel, ratingText, relativeTime, daysLeft } from "../lib/format";

function ReviewsCard({ userId, direction, title }: { userId: string; direction: Review["direction"]; title: string }) {
  const [items, setItems] = useState<Review[]>([]);
  const [page, setPage] = useState(0);
  const [more, setMore] = useState(false);
  const [loading, setLoading] = useState(true);
  const [jobs, setJobs] = useState<Map<string, ReviewJob> | null>(null);
  const needJobs = items.some((r) => !r.gig);
  useEffect(() => {
    if (!needJobs || jobs) return;
    let alive = true;
    myReviewJobs(direction).then((m) => alive && setJobs(m)).catch(() => {});
    return () => { alive = false; };
  }, [needJobs, direction, jobs]);
  useEffect(() => {
    setLoading(true);
    ep.userReviews(userId, page)
      .then((d) => {
        const mine = d.items.filter((r) => r.direction === direction);
        setItems((old) => (page === 0 ? mine : [...old, ...mine]));
        setMore(page + 1 < d.totalPages);
      })
      .catch(() => setMore(false))
      .finally(() => setLoading(false));
  }, [userId, direction, page]);
  return (
    <div className="card stack" style={{ padding: 0, gap: 0 }}>
      <div className="row between" style={{ padding: "18px 20px 10px" }}>
        <div className="h3">{title}</div>
      </div>
      {loading && page === 0 && <div style={{ padding: "0 20px 20px" }}><Skeleton h={80} /></div>}
      {!loading && items.length === 0 && <p className="small muted" style={{ padding: "0 20px 20px" }}>No reviews yet.</p>}
      {items.map((r, i) => {
        const job = jobFor(r, jobs);
        return (
          <div key={r.id} style={{ padding: "14px 20px", borderTop: i ? "1px solid var(--line)" : undefined }} className="row" >
            <Avatar name={r.reviewerName} size={40} />
            <div className="grow stack-sm" style={{ gap: 4, minWidth: 0 }}>
              <div className="row" style={{ gap: 8, flexWrap: "wrap" }}>
                <span className="strong small">{r.reviewerName}</span>
                <Stars value={r.rating} size={14} />
                <span className="xs muted">{relativeTime(r.createdAt)}</span>
              </div>
              {job && <ReviewJobTag title={job.title} to={direction === "WORKER_TO_PROVIDER" ? `/gigs/${job.id}` : `/gig/${job.id}`} />}
              {r.comment && <p className="small body2">{r.comment}</p>}
            </div>
          </div>
        );
      })}
      {more && (
        <div className="center" style={{ padding: "8px 20px 16px" }}>
          <button className="link-btn" disabled={loading} onClick={() => setPage((p) => p + 1)}>{loading ? "Loading…" : "Load more reviews"}</button>
        </div>
      )}
    </div>
  );
}

function ModeCard() {
  const { mode } = useAuth();
  const go = useModeSwitch();
  return (
    <div className="card stack" style={{ padding: 18 }}>
      <span className="small strong muted">You're using JFJ to</span>
      <div className="mode-switch green">
        <button className={mode === "WORKER" ? "on" : ""} onClick={() => mode !== "WORKER" && go("WORKER")}>Find work</button>
        <button className={mode === "PROVIDER" ? "on" : ""} onClick={() => mode !== "PROVIDER" && go("PROVIDER")}>Hire people</button>
      </div>
    </div>
  );
}

function ProviderStats() {
  const [counts, setCounts] = useState<Record<string, number | null>>({ all: null, OPEN: null, IN_PROGRESS: null, COMPLETED: null });
  const [pending, setPending] = useState<{ gig: OwnerGig; list: PendingReview[] }[]>([]);
  useEffect(() => {
    // counts come from totalItems of size=1 pages (guide §7.1)
    (["all", "OPEN", "IN_PROGRESS", "COMPLETED"] as const).forEach((s) =>
      ep.myGigs(s === "all" ? undefined : s, 0, 1).then((p) => setCounts((c) => ({ ...c, [s]: p.totalItems }))).catch(() => {}),
    );
    ep.myGigs("COMPLETED", 0, 20)
      .then(async (p) => {
        const res = await Promise.all(p.items.map(async (g) => ({ gig: g, list: await ep.pendingReviews(g.id).catch(() => []) })));
        setPending(res.filter((r) => r.list.length));
      })
      .catch(() => {});
  }, []);
  const stat = (n: number | null, label: string) => (
    <div className="soft stack-sm" style={{ gap: 2, padding: 14 }}>
      <span className="bold" style={{ fontSize: 22 }}>{n ?? "–"}</span>
      <span className="xs muted">{label}</span>
    </div>
  );
  return (
    <>
      <div className="grid-4" style={{ gridTemplateColumns: "repeat(2, minmax(0,1fr))" }}>
        {stat(counts.all, "Gigs posted")}
        {stat(counts.OPEN, "Open now")}
        {stat(counts.IN_PROGRESS, "In progress")}
        {stat(counts.COMPLETED, "Completed")}
      </div>
      {pending.map(({ gig, list }) => (
        <div key={gig.id} className="card amber row" style={{ padding: 16 }}>
          <GigThumb title={gig.title} category={gig.category?.name} size={52} />
          <div className="grow">
            <div className="strong" style={{ color: "var(--amber-ink)" }}>{list.length} {list.length === 1 ? "worker" : "workers"} waiting for your rating</div>
            <div className="small" style={{ color: "var(--amber-700)" }}>{gig.title} · {Math.max(0, daysLeft(list[0].reviewOpenUntil))} days left to rate</div>
          </div>
          <Link className="btn btn-primary btn-sm" to={`/gigs/${gig.id}?tab=rate`}>Rate now</Link>
        </div>
      ))}
    </>
  );
}

export default function Profile() {
  const { me, mode, logout } = useAuth();
  const navigate = useNavigate();
  const [wp, setWp] = useState<WorkerProfile | null>(null);
  useEffect(() => {
    ep.getMyWorkerProfile().then(setWp).catch(() => {});
  }, []);
  if (!me) return null;
  const provider = mode === "PROVIDER";

  const idCard = (
    <div className="card stack" style={{ padding: 24 }}>
      <div className="row" style={{ gap: 16 }}>
        <Avatar name={me.name} url={provider ? null : wp?.photoUrl} size={72} solid={provider} />
        <div className="grow">
          <h1 className="h2">{me.name}</h1>
          <div className="small muted" style={{ wordBreak: "break-all" }}>{me.email} · {me.phone}</div>
        </div>
      </div>
      <div className="soft stack-sm" style={{ padding: 14 }}>
        <div className="row between small"><span className="body2">As a {provider ? "provider" : "worker"}</span><span className="strong">{ratingText(provider ? me.providerRating : me.workerRating)}</span></div>
        <div className="row between small"><span className="body2">As a {provider ? "worker" : "provider"}</span><span className="muted">{ratingText(provider ? me.workerRating : me.providerRating)}</span></div>
      </div>
      {me.level && <LevelBar totalXp={me.level.totalXp} />}
      {provider && <span className="xs muted">Editing your account details is coming soon.</span>}
    </div>
  );

  const workerSide = (
    <>
      {wp && !wp.profileComplete && (
        <Note tone="amber" icon="info">
          Finish your worker profile to apply for gigs. Missing: {wp.missing.map(missingLabel).join(", ").toLowerCase()}.{" "}
          <Link to="/profile/edit" className="strong">Finish now</Link>
        </Note>
      )}
      <div className="card stack">
        <div className="row between">
          <div className="h3">Your worker profile</div>
          <Link to="/profile/edit" className="btn btn-soft btn-sm"><Icon name="edit" size={16} />Edit</Link>
        </div>
        {!wp ? <Skeleton h={90} /> : (
          <>
            {wp.description ? <p className="body2">{wp.description}</p> : <p className="small muted">No description yet.</p>}
            <div className="row-wrap">
              {wp.experienceLevel && <Badge tone="indigo">{experienceName[wp.experienceLevel]}{wp.experienceYears ? ` · ${wp.experienceYears} yrs` : ""}</Badge>}
              {wp.languages.map((l) => <Badge key={l} tone="grey">{l}</Badge>)}
            </div>
            {wp.skills.length > 0 && <div className="row-wrap">{wp.skills.map((s) => <span key={s.id} className="chip">{s.name}</span>)}</div>}
            <Link to={`/workers/${me.id}`} className="small strong">See how providers see you</Link>
          </>
        )}
      </div>
    </>
  );

  return (
    <Shell>
      <MobileTop title="Profile" back={false} />
      <div className="container">
        <div className="cols" style={{ gridTemplateColumns: "400px minmax(0,1fr)" }}>
          <div className="stack">
            {idCard}
            <ModeCard />
            <button className="btn btn-danger-ghost" onClick={async () => { await logout(); navigate("/login"); }}>
              <Icon name="logout" size={18} color="#C92A2A" />Log out
            </button>
          </div>
          <div className="stack">
            {provider ? <ProviderStats /> : workerSide}
            <ReviewsCard
              userId={me.id}
              direction={provider ? "WORKER_TO_PROVIDER" : "PROVIDER_TO_WORKER"}
              title={provider ? "What workers say" : "What providers say"}
            />
            {!provider && !wp?.skills.length && wp && (
              <Empty icon="briefcase" title="Get hired faster" action={<Link to="/profile/edit" className="btn btn-primary">Add your skills</Link>}>
                Providers pick people whose skills match the gig.
              </Empty>
            )}
          </div>
        </div>
      </div>
    </Shell>
  );
}
