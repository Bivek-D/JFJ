import { useCallback, useEffect, useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { MobileTop, Shell } from "../components/Shell";
import { Icon } from "../components/Icon";
import { Avatar, Badge, Empty, ErrorBox, Modal, ModalHead, Note, PillTabs, Skeleton, SkeletonList, Stars, useToast } from "../components/ui";
import { GigThumb } from "../components/GigCard";
import { ContactModal } from "../components/ContactModal";
import { ReviewModal } from "../components/ReviewModal";
import * as ep from "../api/endpoints";
import { ApiError, errorMessage } from "../api/client";
import type { Applicant, ApplicationStatus, GigDetail, PendingReview } from "../api/types";
import { daysLeft, experienceName, gigStatusBadge, longDate, payText, rupees, shortDate, relativeTime } from "../lib/format";

type Tab = "APPLIED" | "SHORTLISTED" | "HIRED" | "rate";
type Sort = "NEWEST" | "RATING" | "LEVEL";

function ApplicantCard({ a, busy, onAction, canHire }: { a: Applicant; busy: boolean; onAction: (kind: "shortlist" | "reject" | "hire", a: Applicant) => void; canHire: boolean }) {
  const w = a.worker;
  return (
    <div className="card stack" style={{ padding: 20 }}>
      <div className="row">
        <Avatar name={w.name} url={w.photoUrl} size={52} />
        <div className="grow">
          <Link to={`/workers/${w.id}`} className="h3" style={{ color: "var(--ink-900)" }}>{w.name}</Link>
          <div className="small body2 row" style={{ gap: 4, flexWrap: "wrap" }}>
            {w.rating?.count ? <><Stars value={w.rating.average ?? 0} size={13} /> {w.rating.average?.toFixed(1)} ({w.rating.count})</> : "No ratings yet"}
            {w.experienceLevel && <> · {experienceName[w.experienceLevel]}</>}
            {w.experienceYears != null && <> · {w.experienceYears} yrs</>}
            {w.city && <> · {w.city}</>}
          </div>
        </div>
        {w.level && <Badge tone="indigo">Level {w.level.level} · {w.level.name}</Badge>}
      </div>
      {a.note && <div className="soft small body2">“{a.note}”</div>}
      {(w.skills?.length > 0 || w.languages?.length > 0) && (
        <div className="row-wrap">
          {w.skills.slice(0, 5).map((s) => <span key={s.id} className="badge grey">{s.name}</span>)}
          {w.languages.slice(0, 3).map((l) => <span key={l} className="badge indigo">{l}</span>)}
        </div>
      )}
      <div className="row">
        <span className="xs muted grow">Applied {relativeTime(a.createdAt)}</span>
        {a.status !== "HIRED" && <button className="link-btn danger small" disabled={busy} onClick={() => onAction("reject", a)}>Reject</button>}
        {a.status === "APPLIED" && <button className="btn btn-secondary btn-sm" disabled={busy} onClick={() => onAction("shortlist", a)}>Shortlist</button>}
        {a.status === "SHORTLISTED" && <Badge tone="mint">Shortlisted ✓</Badge>}
        {a.status !== "HIRED" && canHire && <button className="btn btn-primary btn-sm" disabled={busy} onClick={() => onAction("hire", a)}>Hire</button>}
      </div>
    </div>
  );
}

export default function ManageGig() {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const toast = useToast();
  const [params, setParams] = useSearchParams();
  const [gig, setGig] = useState<GigDetail | null>(null);
  const [gigError, setGigError] = useState<unknown>(null);
  const [all, setAll] = useState<Applicant[] | null>(null);
  const [listError, setListError] = useState<unknown>(null);
  const [pending, setPending] = useState<PendingReview[]>([]);
  const [sort, setSort] = useState<Sort>("RATING");
  const [tab, setTabState] = useState<Tab>((params.get("tab") as Tab) || "APPLIED");
  const [busyId, setBusyId] = useState<string | null>(null);
  const [contactFor, setContactFor] = useState<string | null>(null);
  const [rateFor, setRateFor] = useState<PendingReview | null>(null);
  const [dialog, setDialog] = useState<"" | "start" | "complete" | "cancel">("");
  const [reason, setReason] = useState("");
  const [actionBusy, setActionBusy] = useState(false);
  const [actionError, setActionError] = useState("");

  const setTab = (t: Tab) => {
    setTabState(t);
    setParams(t === "APPLIED" ? {} : { tab: t }, { replace: true });
  };

  const loadGig = useCallback(() => {
    setGigError(null);
    ep.getGig(id).then(setGig).catch(setGigError);
  }, [id]);
  const loadApplicants = useCallback(() => {
    setListError(null);
    ep.gigApplicants(id, undefined, sort).then(setAll).catch(setListError);
  }, [id, sort]);
  const loadPending = useCallback(() => {
    ep.pendingReviews(id).then(setPending).catch(() => setPending([]));
  }, [id]);
  useEffect(loadGig, [loadGig]);
  useEffect(loadApplicants, [loadApplicants]);
  useEffect(() => {
    if (gig?.status === "COMPLETED") loadPending();
  }, [gig?.status, loadPending]);

  const by = (s: ApplicationStatus) => (all ?? []).filter((a) => a.status === s);
  const hired = by("HIRED");
  const openings = gig ? gig.openingsLeft : 0;

  async function act(kind: "shortlist" | "reject" | "hire", a: Applicant) {
    setBusyId(a.id);
    // optimistic update
    const prev = all;
    setAll((list) => (list ?? []).map((x) => (x.id === a.id ? { ...x, status: kind === "hire" ? "HIRED" : kind === "reject" ? "REJECTED" : "SHORTLISTED" } : x)));
    try {
      if (kind === "shortlist") await ep.shortlist(a.id);
      if (kind === "reject") await ep.reject(a.id);
      if (kind === "hire") {
        await ep.hire(a.id);
        const left = Math.max(0, openings - 1);
        toast({ text: `You hired ${a.worker.name}. ${left ? `${left} ${left === 1 ? "spot" : "spots"} left.` : "All spots are filled."}` });
        loadGig();
      } else toast({ text: kind === "shortlist" ? `${a.worker.name} shortlisted.` : `${a.worker.name} turned down.` });
      loadApplicants();
    } catch (e) {
      setAll(prev);
      // last slot just filled by someone else → backend says so; refresh the view
      toast({ text: errorMessage(e), error: true });
      if (e instanceof ApiError && (e.status === 400 || e.status === 409)) {
        loadGig();
        loadApplicants();
      }
    } finally {
      setBusyId(null);
    }
  }

  async function runDialog() {
    if (!gig) return;
    setActionBusy(true);
    setActionError("");
    try {
      if (dialog === "start") {
        await ep.startGig(gig.id);
        toast({ text: "Gig started. Complete it when the work is done." });
      } else if (dialog === "complete") {
        await ep.completeGig(gig.id);
        toast({ text: "Gig completed. Rate your workers." });
        setTab("rate");
      } else if (dialog === "cancel") {
        if (reason.trim().length < 5) {
          setActionError("Write a reason of at least 5 characters.");
          setActionBusy(false);
          return;
        }
        await ep.cancelGig(gig.id, reason.trim());
        toast({ text: "Gig cancelled." });
      }
      setDialog("");
      setReason("");
      loadGig();
      loadApplicants();
      if (dialog === "complete") loadPending();
    } catch (e) {
      setActionError(errorMessage(e));
    } finally {
      setActionBusy(false);
    }
  }

  if (gigError) return <Shell><MobileTop title="Manage gig" /><div className="container"><ErrorBox error={gigError} onRetry={loadGig} /></div></Shell>;
  if (!gig) return <Shell><MobileTop title="Manage gig" /><div className="container stack"><Skeleton h={130} /><SkeletonList n={2} h={200} /></div></Shell>;

  const status = gigStatusBadge[gig.status];
  const filled = gig.workersNeeded - gig.openingsLeft;
  const isOpen = gig.status === "OPEN";
  const canEdit = (gig.status === "OPEN" || gig.status === "DRAFT") && hired.length === 0;
  const tabs: { value: Tab; label: string; count?: number }[] = [
    { value: "APPLIED", label: "Applicants", count: by("APPLIED").length },
    { value: "SHORTLISTED", label: "Shortlisted", count: by("SHORTLISTED").length },
    { value: "HIRED", label: "Hired workers", count: hired.length },
  ];
  if (gig.status === "COMPLETED") tabs.push({ value: "rate", label: "Rate workers", count: pending.length });

  const actions = (
    <div className="row" style={{ gap: 10, flexWrap: "wrap" }}>
      {canEdit && <Link className="btn btn-secondary" to={`/gigs/${gig.id}/edit`}>Edit gig</Link>}
      {(isOpen || gig.status === "IN_PROGRESS") && <button className="btn btn-danger-ghost" onClick={() => setDialog("cancel")}>Cancel gig</button>}
      {isOpen && <button className="btn btn-primary" disabled={hired.length === 0} title={hired.length === 0 ? "Hire at least one worker first" : undefined} onClick={() => setDialog("start")}>Start gig</button>}
      {gig.status === "IN_PROGRESS" && <button className="btn btn-primary" onClick={() => setDialog("complete")}>Complete gig</button>}
    </div>
  );

  const listFor = (t: Tab) => {
    if (t === "rate") {
      return pending.length ? (
        <div className="stack-sm">
          {pending.map((p) => (
            <div key={p.assignmentId} className="card accent row" style={{ padding: 18 }}>
              <Avatar name={p.workerName} size={48} />
              <div className="grow">
                <div className="h3">How did {p.workerName} do?</div>
                <div className="small muted">{Math.max(0, daysLeft(p.reviewOpenUntil))} days left to rate</div>
              </div>
              <button className="btn btn-primary" onClick={() => setRateFor(p)}>Rate</button>
            </div>
          ))}
        </div>
      ) : (
        <Empty icon="star" title="All rated">Thanks! Your ratings help good workers get hired again.</Empty>
      );
    }
    if (all == null) return listError ? <ErrorBox error={listError} onRetry={loadApplicants} /> : <SkeletonList n={2} h={200} />;
    const list = by(t as ApplicationStatus);
    if (t === "HIRED") {
      return list.length ? (
        <div className="stack">
          {hired.length > 0 && !canEdit && gig.status === "OPEN" && (
            <Note tone="info" icon="shield">You've hired {hired.length}, so the gig can't be edited any more. Start it on the day the work begins.{gig.openingsLeft ? ` You can still hire ${gig.openingsLeft} more.` : ""}</Note>
          )}
          <div className="grid-2">
            {list.map((a) => (
              <div key={a.id} className="card mint stack" style={{ padding: 20 }}>
                <div className="row">
                  <Avatar name={a.worker.name} url={a.worker.photoUrl} size={52} />
                  <div className="grow">
                    <Link to={`/workers/${a.worker.id}`} className="h3" style={{ color: "var(--ink-900)" }}>{a.worker.name}</Link>
                    <div className="small body2">{a.worker.rating?.count ? `★ ${a.worker.rating.average?.toFixed(1)} (${a.worker.rating.count})` : "No ratings yet"}{a.worker.level ? ` · Level ${a.worker.level.level} · ${a.worker.level.name}` : ""}</div>
                  </div>
                  <Badge tone="mint">Hired</Badge>
                </div>
                {a.assignmentId && <button className="btn btn-primary" onClick={() => setContactFor(a.assignmentId!)}><Icon name="phone" size={18} color="#fff" />Contact {a.worker.name.split(" ")[0]}</button>}
              </div>
            ))}
          </div>
          <Note tone="amber" icon="shield">Phone numbers aren't verified yet. Meet in a public place first if you're unsure, and never ask a worker to pay anything.</Note>
        </div>
      ) : (
        <Empty icon="users" title="No one hired yet">Hire people from the Applicants or Shortlisted tab.</Empty>
      );
    }
    return list.length ? (
      <div className="grid-2">
        {list.map((a) => <ApplicantCard key={a.id} a={a} busy={busyId === a.id} onAction={act} canHire={isOpen && gig.openingsLeft > 0} />)}
      </div>
    ) : (
      <Empty icon="users" title={t === "APPLIED" ? "No new applicants" : "No one shortlisted"}>
        {t === "APPLIED" ? (isOpen ? "Workers nearby will see your gig and apply." : "This gig isn't taking applications.") : "Shortlist people you like before you hire."}
      </Empty>
    );
  };

  return (
    <Shell>
      <MobileTop title="Manage gig" />
      <div className="container stack-lg">
        <button className="btn btn-ghost hide-mobile" style={{ paddingLeft: 0, alignSelf: "flex-start" }} onClick={() => navigate("/gigs")}>
          <Icon name="chevronLeft" size={18} /> Back to My gigs
        </button>
        <div className="card row" style={{ gap: 20, flexWrap: "wrap" }}>
          <GigThumb title={gig.title} category={gig.category?.name} size={84} />
          <div className="grow stack-sm" style={{ minWidth: 220 }}>
            <Link to={`/gig/${gig.id}`} className="h2" style={{ color: "var(--ink-900)" }}>{gig.title}</Link>
            <span className="small muted">{[gig.locality, gig.city].filter(Boolean).join(", ")} · Starts {shortDate(gig.startDate)} · {gig.durationText} · {payText(gig.payAmount, gig.payType)}</span>
            <div className="row-wrap">
              <Badge tone={status.tone}>{status.label}</Badge>
              {gig.applyDeadline && isOpen && <Badge tone="amber">Applications close {longDate(gig.applyDeadline)}</Badge>}
            </div>
          </div>
          <div className="stack-sm" style={{ width: 210 }}>
            <div className="row between small"><span className="body2">Spots filled</span><span className="strong">{filled} of {gig.workersNeeded}</span></div>
            <div className="track mint"><div style={{ width: `${(filled / Math.max(1, gig.workersNeeded)) * 100}%` }} /></div>
          </div>
          <div className="hide-mobile">{actions}</div>
        </div>
        {gig.status === "CANCELLED" && <Note tone="red">This gig was cancelled.</Note>}
        <div className="row between" style={{ flexWrap: "wrap", gap: 12 }}>
          <PillTabs tabs={tabs} value={tab} onChange={setTab} />
          {(tab === "APPLIED" || tab === "SHORTLISTED") && (
            <label className="row small strong">
              Sort
              <select className="select" style={{ height: 40, width: 150 }} value={sort} onChange={(e) => setSort(e.target.value as Sort)}>
                <option value="RATING">Best rated</option>
                <option value="LEVEL">Highest level</option>
                <option value="NEWEST">Newest</option>
              </select>
            </label>
          )}
        </div>
        {listFor(tab)}
      </div>
      <div className="sticky-cta hide-desktop" style={{ bottom: "var(--bottom-nav-h)" }}>{actions}</div>

      {contactFor && <ContactModal assignmentId={contactFor} onClose={() => setContactFor(null)} />}
      {rateFor && (
        <ReviewModal
          assignmentId={rateFor.assignmentId}
          name={rateFor.workerName}
          subtitle={`Worked on ${gig.title}`}
          onClose={() => setRateFor(null)}
          onSaved={() => {
            setRateFor(null);
            toast({ text: `Rating saved. You can change it for 24 hours.` });
            loadPending();
          }}
        />
      )}
      {dialog && (
        <Modal onClose={() => { setDialog(""); setActionError(""); }} label="Confirm">
          <div className="modal-icon" style={{ background: dialog === "cancel" ? "var(--red-50)" : "var(--mint-50)" }}>
            <Icon name={dialog === "cancel" ? "close" : "check"} color={dialog === "cancel" ? "#E03131" : "#0B8A66"} />
          </div>
          <ModalHead
            title={dialog === "start" ? "Start this gig?" : dialog === "complete" ? "Mark this gig as complete?" : "Cancel this gig?"}
            onClose={() => setDialog("")}
          />
          {dialog === "start" && <p className="body2">Start it on the day the work begins. You've hired {hired.length} of {gig.workersNeeded}. Anyone you haven't hired can still be hired until you complete it.</p>}
          {dialog === "complete" && (
            <>
              <p className="body2">Your workers get XP and you can rate each other. Anyone still waiting to be hired is turned down automatically.</p>
              <div className="soft row between"><span className="small body2">{hired.length} {hired.length === 1 ? "worker" : "workers"} · {gig.durationText}</span><span className="strong">{gig.payType === "FIXED" ? `${rupees(gig.payAmount * hired.length)} to pay directly` : payText(gig.payAmount, gig.payType)}</span></div>
            </>
          )}
          {dialog === "cancel" && (
            <>
              <p className="body2">Hired workers will be told it's cancelled and will no longer see the address or your phone number. This can't be undone.</p>
              <label className="field">
                <span className="field-label">Reason (hired workers will see this)</span>
                <textarea className="textarea" maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} placeholder="The owner moved the work to next month." />
                <span className="xs muted" style={{ textAlign: "right" }}>{reason.length} / 500 · at least 5 characters</span>
              </label>
            </>
          )}
          {actionError && <Note tone="red">{actionError}</Note>}
          <div className="row" style={{ justifyContent: "flex-end" }}>
            <button className="btn btn-secondary" onClick={() => setDialog("")}>{dialog === "cancel" ? "Keep gig" : "Not yet"}</button>
            <button className={`btn ${dialog === "cancel" ? "btn-danger" : "btn-primary"}`} disabled={actionBusy} onClick={runDialog}>
              {actionBusy ? "Working…" : dialog === "start" ? "Start gig" : dialog === "complete" ? "Yes, complete gig" : "Cancel gig"}
            </button>
          </div>
        </Modal>
      )}
    </Shell>
  );
}
