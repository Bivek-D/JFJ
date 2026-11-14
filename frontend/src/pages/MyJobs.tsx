import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { MobileTop, Shell } from "../components/Shell";
import { Icon } from "../components/Icon";
import { Badge, Empty, ErrorBox, LevelBar, Modal, ModalHead, Note, PillTabs, SkeletonList, useToast } from "../components/ui";
import { GigThumb } from "../components/GigCard";
import { ContactModal } from "../components/ContactModal";
import { ReviewModal } from "../components/ReviewModal";
import * as ep from "../api/endpoints";
import { errorMessage } from "../api/client";
import type { Application, Assignment } from "../api/types";
import { useAsync } from "../lib/useAsync";
import { useAuth } from "../auth/AuthContext";
import { applicationBadge, daysLeft, payText, relativeTime, shortDate } from "../lib/format";

type Tab = "all" | "shortlisted" | "hired" | "done";

function ApplicationRow({ a, onWithdraw }: { a: Application; onWithdraw: (a: Application) => void }) {
  const b = applicationBadge[a.status];
  const canWithdraw = a.status === "APPLIED" || a.status === "SHORTLISTED";
  return (
    <div className="card pad-sm row" style={{ alignItems: "center", opacity: a.status === "REJECTED" || a.status === "WITHDRAWN" ? 0.7 : 1 }}>
      <GigThumb title={a.gig.title} size={64} />
      <div className="grow stack-sm" style={{ gap: 3 }}>
        <Link to={`/gig/${a.gig.id}`} className="strong" style={{ color: "var(--ink-900)" }}>{a.gig.title}</Link>
        <span className="small muted">{payText(a.gig.payAmount, a.gig.payType)} · {shortDate(a.gig.startDate)} · {a.gig.locality || a.gig.city}</span>
        <span className="xs muted">{a.status === "SHORTLISTED" ? "Shortlisted" : "Applied"} {relativeTime(a.createdAt)}</span>
      </div>
      <div className="stack-sm" style={{ alignItems: "flex-end" }}>
        <Badge tone={b.tone}>{b.label}</Badge>
        {canWithdraw && <button className="link-btn small" onClick={() => onWithdraw(a)}>Withdraw</button>}
        {a.status === "REJECTED" && <Link to="/" className="small strong">Find similar</Link>}
      </div>
    </div>
  );
}

function HiredCard({ j, onContact }: { j: Assignment; onContact: () => void }) {
  return (
    <div className="card mint stack">
      <div className="row between">
        <Badge tone="mint">You're hired</Badge>
        <span className="small strong" style={{ color: "var(--amber-700)" }}>
          {j.gig.status === "IN_PROGRESS" ? "In progress" : `Starts ${shortDate(j.gig.startDate)}`}
        </span>
      </div>
      <div className="row">
        <GigThumb title={j.gig.title} size={52} />
        <div className="grow">
          <div className="h3">{j.gig.title}</div>
          <div className="small muted">{payText(j.gig.payAmount, j.gig.payType)} · {j.gig.locality || j.gig.city}</div>
        </div>
      </div>
      <div className="row">
        <button className="btn btn-primary grow" onClick={onContact}><Icon name="phone" size={18} color="#fff" />Contact provider</button>
        <Link className="btn btn-secondary grow" to={`/gig/${j.gig.id}`}>Address and details</Link>
      </div>
    </div>
  );
}

function DoneCard({ j, onRate }: { j: Assignment; onRate: () => void }) {
  const removed = j.status === "REMOVED";
  const canRate = !removed && !j.reviewedByMe && j.reviewOpenUntil && daysLeft(j.reviewOpenUntil) >= 0;
  return (
    <div className="card pad-sm row" style={{ alignItems: "center" }}>
      <GigThumb title={j.gig.title} size={64} />
      <div className="grow stack-sm" style={{ gap: 3 }}>
        <span className="strong">{j.gig.title}</span>
        <span className="small muted">{payText(j.gig.payAmount, j.gig.payType)} · {j.endedAt ? `Ended ${relativeTime(j.endedAt)}` : shortDate(j.gig.startDate)}</span>
        {removed && <span className="xs" style={{ color: "var(--red-700)" }}>{j.cancelReason || j.removedReason || "This job was cancelled."}</span>}
      </div>
      <div className="stack-sm" style={{ alignItems: "flex-end" }}>
        <Badge tone={removed ? "red" : "indigo"}>{removed ? "Cancelled" : "Completed"}</Badge>
        {canRate && <button className="btn btn-soft btn-sm" onClick={onRate}>Rate provider</button>}
        {j.reviewedByMe && <span className="xs text-mint strong">You rated this</span>}
      </div>
    </div>
  );
}

export default function MyJobs() {
  const { me } = useAuth();
  const toast = useToast();
  const apps = useAsync(() => ep.myApplications(), []);
  const jobs = useAsync(() => ep.myAssignments(), []);
  const [tab, setTab] = useState<Tab>("all");
  const [contactFor, setContactFor] = useState<string | null>(null);
  const [rateFor, setRateForRaw] = useState<Assignment | null>(null);
  const [rateName, setRateName] = useState("your provider");
  // The assignment has no provider name; the contact screen does (open for 7 days after the job).
  const setRateFor = (j: Assignment | null) => {
    setRateForRaw(j);
    setRateName("your provider");
    if (j) ep.getContact(j.id).then((c) => setRateName(c.name)).catch(() => {});
  };
  const [withdrawing, setWithdrawing] = useState<Application | null>(null);
  const [busy, setBusy] = useState(false);

  const applications = useMemo(() => (apps.data?.items ?? []).filter((a) => a.status !== "HIRED" && a.status !== "WITHDRAWN"), [apps.data]);
  const active = (jobs.data?.items ?? []).filter((j) => j.status === "ACTIVE");
  const done = (jobs.data?.items ?? []).filter((j) => j.status !== "ACTIVE");
  const shortlisted = applications.filter((a) => a.status === "SHORTLISTED");
  const toRate = done.filter((j) => j.status === "COMPLETED" && !j.reviewedByMe && j.reviewOpenUntil && daysLeft(j.reviewOpenUntil) >= 0);

  async function withdraw() {
    if (!withdrawing) return;
    setBusy(true);
    try {
      await ep.withdrawApplication(withdrawing.id);
      toast({ text: "Application withdrawn." });
      setWithdrawing(null);
      apps.reload();
    } catch (e) {
      toast({ text: errorMessage(e), error: true });
    } finally {
      setBusy(false);
    }
  }

  const loading = apps.loading || jobs.loading;
  const tabs = [
    { value: "all" as Tab, label: "All", count: applications.length },
    { value: "shortlisted" as Tab, label: "Shortlisted", count: shortlisted.length },
    { value: "hired" as Tab, label: "Hired", count: active.length },
    { value: "done" as Tab, label: "Done", count: done.length },
  ];

  const hiredList = active.map((j) => <HiredCard key={j.id} j={j} onContact={() => setContactFor(j.id)} />);
  const body = (() => {
    if (loading) return <SkeletonList n={4} h={96} />;
    if (apps.error || jobs.error) return <ErrorBox error={apps.error || jobs.error} onRetry={() => { apps.reload(); jobs.reload(); }} />;
    if (tab === "hired") return active.length ? <div className="stack">{hiredList}</div> : <Empty icon="briefcase" title="No active jobs yet" action={<Link className="btn btn-primary" to="/">Find gigs</Link>}>When a provider hires you, the job and their contact show up here.</Empty>;
    if (tab === "done") return done.length ? <div className="stack-sm">{done.map((j) => <DoneCard key={j.id} j={j} onRate={() => setRateFor(j)} />)}</div> : <Empty icon="check" title="Nothing finished yet">Completed jobs and ratings show up here.</Empty>;
    const list = tab === "shortlisted" ? shortlisted : applications;
    return (
      <div className="stack">
        {tab === "all" && active.length > 0 && <div className="stack hide-desktop">{hiredList}</div>}
        {list.length ? (
          <div className="stack-sm">
            <div className="h3">Your applications</div>
            {list.map((a) => <ApplicationRow key={a.id} a={a} onWithdraw={setWithdrawing} />)}
          </div>
        ) : (
          <Empty icon="search" title={tab === "shortlisted" ? "No shortlists yet" : "Start applying"} action={<Link className="btn btn-primary" to="/">Explore gigs</Link>}>
            {tab === "shortlisted" ? "Providers shortlist people they like before hiring." : "Gigs you apply for show up here so you can track them."}
          </Empty>
        )}
      </div>
    );
  })();

  return (
    <Shell>
      <MobileTop title="My jobs" back={false} />
      <div className="container stack-lg">
        <div className="stack-sm hide-mobile">
          <h1 className="h1">My jobs</h1>
          <p className="muted">Track your applications and the work you've been hired for.</p>
        </div>
        {toRate.length > 0 && (
          <Note tone="amber" icon="star">
            {toRate.length === 1 ? `Rate how it went on "${toRate[0].gig.title}".` : `You have ${toRate.length} jobs to rate.`}{" "}
            <button className="link-btn" onClick={() => setRateFor(toRate[0])}>Rate now</button>
          </Note>
        )}
        <PillTabs tabs={tabs} value={tab} onChange={setTab} />
        <div className="cols">
          <div>{body}</div>
          <div className="stack hide-mobile">
            {tab !== "hired" && hiredList}
            {me?.level && (
              <div className="card stack">
                <div className="h3">Your level</div>
                <LevelBar totalXp={me.level.totalXp} />
              </div>
            )}
          </div>
        </div>
      </div>
      {contactFor && <ContactModal assignmentId={contactFor} onClose={() => setContactFor(null)} />}
      {rateFor && (
        <ReviewModal
          assignmentId={rateFor.id}
          name={rateName}
          subtitle={`${rateFor.gig.title}${rateFor.endedAt ? ` · completed ${relativeTime(rateFor.endedAt)}` : ""}`}
          onClose={() => setRateFor(null)}
          onSaved={() => {
            setRateFor(null);
            toast({ text: "Thanks! Your review is posted." });
            jobs.reload();
          }}
        />
      )}
      {withdrawing && (
        <Modal onClose={() => setWithdrawing(null)} label="Withdraw application">
          <ModalHead title="Withdraw your application?" onClose={() => setWithdrawing(null)} />
          <p className="body2">You'll be removed from the applicants for "{withdrawing.gig.title}". You can apply again later if spots are still open.</p>
          <div className="row" style={{ justifyContent: "flex-end" }}>
            <button className="btn btn-secondary" onClick={() => setWithdrawing(null)}>Keep it</button>
            <button className="btn btn-danger" onClick={withdraw} disabled={busy}>{busy ? "Withdrawing…" : "Withdraw"}</button>
          </div>
        </Modal>
      )}
    </Shell>
  );
}
