import { lazy, Suspense, useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { MobileTop, Shell, useModeSwitch } from "../components/Shell";
import { Icon, type IconName } from "../components/Icon";
import { Avatar, Badge, ErrorBox, LevelBar, Modal, ModalHead, Note, ReviewJobTag, Skeleton, Stars, useToast } from "../components/ui";
import { GigThumb } from "../components/GigCard";
import { artFor, iconForCategory } from "../lib/art";
import * as ep from "../api/endpoints";
import { errorMessage } from "../api/client";
import type { GigDetail, Review, WorkerProfile } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import { useAsync } from "../lib/useAsync";
import { experienceName, levelLabel, longDate, payLabel, rupees, shortDate, relativeTime, ratingText, missingLabel } from "../lib/format";

const GigAreaMap = lazy(() => import("../map/GigAreaMap"));

type ApplyState =
  | { kind: "can" }
  | { kind: "applied" }
  | { kind: "mine" }
  | { kind: "wrongMode" }
  | { kind: "closed"; why: string }
  | { kind: "incomplete"; missing: string[] };

/** Which Apply button to show (guide: login, profile incomplete, own gig, no openings, already applied). */
export function applyState(gig: GigDetail, opts: { mode: string; appliedIds: Set<string>; profile: WorkerProfile | null }): ApplyState {
  if (gig.mine) return { kind: "mine" };
  if (opts.appliedIds.has(gig.id)) return { kind: "applied" };
  if (gig.status !== "OPEN") return { kind: "closed", why: "This gig isn't taking applications." };
  if (gig.openingsLeft <= 0) return { kind: "closed", why: "All spots are filled." };
  if (gig.applyDeadline && new Date(gig.applyDeadline) < new Date()) return { kind: "closed", why: "The last day to apply has passed." };
  if (opts.mode !== "WORKER") return { kind: "wrongMode" };
  if (opts.profile && !opts.profile.profileComplete) return { kind: "incomplete", missing: opts.profile.missing ?? [] };
  return { kind: "can" };
}

function GigHero({ title, category }: { title: string; category?: string }) {
  const art = artFor(title, category);
  return (
    <div style={{ borderRadius: 20, overflow: "hidden", height: 300, background: "var(--primary-50)", display: "grid", placeItems: "center" }} className="gig-hero">
      {art ? <img src={art} alt="" style={{ width: "100%", height: "100%", objectFit: "cover" }} /> : <Icon name={iconForCategory(category)} size={96} color="#3B5BDB" />}
    </div>
  );
}

function Fact({ icon, label, value }: { icon: IconName; label: string; value: string }) {
  return (
    <div className="fact">
      <span className="ic"><Icon name={icon} size={20} /></span>
      <span className="xs muted">{label}</span>
      <span className="strong">{value}</span>
    </div>
  );
}

function ProviderSheet({ gig, onClose }: { gig: GigDetail; onClose: () => void }) {
  const [reviews, setReviews] = useState<Review[]>([]);
  const [page, setPage] = useState(0);
  const [more, setMore] = useState(false);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    setLoading(true);
    ep.userReviews(gig.provider.id, page)
      .then((d) => {
        // the endpoint returns both directions: keep what workers said about this provider
        const mine = d.items.filter((r) => r.direction === "WORKER_TO_PROVIDER");
        setReviews((old) => (page === 0 ? mine : [...old, ...mine]));
        setMore(page + 1 < d.totalPages);
      })
      .catch(() => setMore(false))
      .finally(() => setLoading(false));
  }, [gig.provider.id, page]);
  const p = gig.provider;
  return (
    <Modal onClose={onClose} label="About this provider">
      <ModalHead title="About this provider" onClose={onClose} />
      <div className="row">
        <Avatar name={p.name} size={52} solid />
        <div className="grow">
          <div className="h3">{p.name}</div>
          <div className="small body2">{ratingText(p.rating)} as a provider</div>
        </div>
      </div>
      {p.level && <LevelBar totalXp={p.level.totalXp} compact />}
      <div className="h3">What workers say</div>
      {loading && page === 0 ? <Skeleton h={70} /> : reviews.length === 0 ? <p className="small muted">No reviews from workers yet.</p> : null}
      {reviews.map((r) => (
        <div key={r.id} className="soft stack-sm">
          <div className="row between"><span className="strong small">{r.reviewerName}</span><Stars value={r.rating} size={14} /></div>
          {r.gig && <ReviewJobTag title={r.gig.title} />}
          {r.comment && <p className="small body2">{r.comment}</p>}
          <span className="xs muted">{relativeTime(r.createdAt)}</span>
        </div>
      ))}
      {more && <button className="btn btn-secondary" disabled={loading} onClick={() => setPage((x) => x + 1)}>{loading ? "Loading…" : "Load more"}</button>}
      <p className="xs muted center">You'll see the provider's phone number only after you're hired.</p>
    </Modal>
  );
}

function ApplyModal({ gig, onClose, onDone }: { gig: GigDetail; onClose: () => void; onDone: () => void }) {
  const { me } = useAuth();
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function send() {
    setBusy(true);
    setError("");
    try {
      await ep.applyToGig(gig.id, note);
      onDone();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <Modal onClose={onClose} label="Apply for this gig">
      <ModalHead title="Apply for this gig" onClose={onClose} />
      <div className="soft row">
        <GigThumb title={gig.title} category={gig.category?.name} size={52} />
        <div className="grow">
          <div className="strong">{gig.title}</div>
          <div className="small muted">{rupees(gig.payAmount)} {payLabel(gig.payType)} · Starts {shortDate(gig.startDate)} · {gig.provider.name}</div>
        </div>
      </div>
      <div className="row">
        <Avatar name={me?.name} size={44} />
        <div className="grow">
          <div className="strong small">{me?.name} · {levelLabel(me?.level)}</div>
          <div className="xs muted">{gig.provider.name} will see your rating, level, skills and experience.</div>
        </div>
      </div>
      <label className="field">
        <span className="field-label">Note to {gig.provider.name.split(" ")[0]} (optional)</span>
        <textarea className="textarea" maxLength={500} value={note} onChange={(e) => setNote(e.target.value)} placeholder="Say why you're a good fit and when you can start." />
        <span className="xs muted" style={{ textAlign: "right" }}>{note.length} / 500</span>
      </label>
      {error && <Note tone="red">{error} {/profile/i.test(error) && <Link to="/profile/edit">Finish my profile</Link>}</Note>}
      <div className="row" style={{ justifyContent: "flex-end" }}>
        <button className="btn btn-secondary hide-mobile" onClick={onClose}>Cancel</button>
        <button className="btn btn-primary btn-lg grow-mobile" onClick={send} disabled={busy}>{busy ? "Sending…" : "Send application"}</button>
      </div>
    </Modal>
  );
}

export default function GigDetailPage() {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const { mode } = useAuth();
  const toast = useToast();
  const switchTo = useModeSwitch();
  const { data: gig, error, loading, reload } = useAsync(() => ep.getGig(id), [id]);
  const [profile, setProfile] = useState<WorkerProfile | null>(null);
  const [appliedIds, setAppliedIds] = useState<Set<string>>(new Set());
  const [showApply, setShowApply] = useState(false);
  const [showProvider, setShowProvider] = useState(false);

  useEffect(() => {
    if (mode !== "WORKER") return;
    ep.getMyWorkerProfile().then(setProfile).catch(() => {});
    ep.myApplications()
      .then((p) => setAppliedIds(new Set(p.items.filter((a) => a.status !== "WITHDRAWN").map((a) => a.gig.id))))
      .catch(() => {});
  }, [mode, id]);

  if (loading || error || !gig) {
    return (
      <Shell>
        <MobileTop title="Gig" />
        <div className="container stack">
          {error ? <ErrorBox error={error} onRetry={reload} /> : <><Skeleton h={300} /><Skeleton h={160} /></>}
        </div>
      </Shell>
    );
  }

  const st = applyState(gig, { mode, appliedIds, profile });
  const place = [gig.locality, gig.city].filter(Boolean).join(", ");

  const cta = (() => {
    switch (st.kind) {
      case "can": return <button className="btn btn-primary btn-lg btn-block" onClick={() => setShowApply(true)}>Apply now</button>;
      case "applied": return <button className="btn btn-soft btn-lg btn-block" onClick={() => navigate("/jobs")}><Icon name="check" size={18} /> Applied · see My jobs</button>;
      case "mine": return <Link className="btn btn-primary btn-lg btn-block" to={`/gigs/${gig.id}`}>Manage this gig</Link>;
      case "wrongMode": return <button className="btn btn-primary btn-lg btn-block" onClick={() => switchTo("WORKER", `/gig/${gig.id}`)}>Switch to Find work to apply</button>;
      case "closed": return <button className="btn btn-secondary btn-lg btn-block" disabled>{st.why}</button>;
      case "incomplete": return <Link className="btn btn-primary btn-lg btn-block" to="/profile/edit">Finish your profile to apply</Link>;
    }
  })();
  const ctaNote =
    st.kind === "incomplete" ? (
      <Note tone="amber">Before you can apply, add: {st.missing.map(missingLabel).join(", ").toLowerCase() || "the missing details"}.</Note>
    ) : st.kind === "can" ? (
      <span className="small text-mint strong">Your profile is complete, so you can apply.</span>
    ) : null;

  const providerCard = (
    <button className="card stack" style={{ textAlign: "left", cursor: "pointer", padding: 20, width: "100%" }} onClick={() => setShowProvider(true)} aria-label={`About ${gig.provider.name}`}>
      <span className="small muted">Posted by</span>
      <div className="row">
        <Avatar name={gig.provider.name} size={52} solid />
        <div className="grow">
          <div className="h3">{gig.provider.name}</div>
          <div className="small body2 row" style={{ gap: 4 }}>
            {gig.provider.rating?.count ? <><Icon name="star" filled color="#F59F00" size={15} />{gig.provider.rating.average?.toFixed(1)} · {gig.provider.rating.count} reviews</> : "No ratings yet"}
          </div>
        </div>
        <Icon name="chevronRight" color="#8590A8" />
      </div>
      {gig.provider.level && <div><Badge tone="indigo">{levelLabel(gig.provider.level)}</Badge></div>}
    </button>
  );

  return (
    <Shell bottomNav={false}>
      <MobileTop title={gig.title} />
      <div className="container">
        <button className="btn btn-ghost hide-mobile" style={{ paddingLeft: 0, marginBottom: 12 }} onClick={() => navigate(-1)}>
          <Icon name="chevronLeft" size={18} /> Back
        </button>
        <div className="cols">
          <div className="stack-lg">
            <GigHero title={gig.title} category={gig.category?.name} />
            <div className="stack-sm">
              <div className="row-wrap">
                <Badge tone="indigo">{gig.category?.name}</Badge>
                <Badge tone="mint">{gig.openingsLeft} of {gig.workersNeeded} spots left</Badge>
                <Badge tone="amber">Starts {shortDate(gig.startDate)}</Badge>
                {gig.status !== "OPEN" && <Badge tone="grey">{gig.status.replace("_", " ").toLowerCase()}</Badge>}
              </div>
              <h1 className="h1" style={{ fontSize: 32 }}>{gig.title}</h1>
              <p className="muted">{place} · Posted {relativeTime(gig.createdAt)}</p>
            </div>
            <div className="grid-4">
              <Fact icon="rupee" label="Pay" value={`${rupees(gig.payAmount)} ${payLabel(gig.payType)}`} />
              <Fact icon="calendar" label="Duration" value={gig.durationText || "—"} />
              <Fact icon="users" label="Workers needed" value={`${gig.workersNeeded} ${gig.workersNeeded === 1 ? "person" : "people"}`} />
              <Fact icon="shield" label="Experience" value={[gig.minExperience ? experienceName[gig.minExperience] + "+" : "Any", gig.minLevel ? `Level ${gig.minLevel}+` : ""].filter(Boolean).join(", ")} />
            </div>
            <div className="card stack">
              <div className="h3">About this gig</div>
              <p className="body2" style={{ whiteSpace: "pre-wrap", lineHeight: 1.6 }}>{gig.description}</p>
              {gig.skills?.length > 0 && (
                <>
                  <div className="strong small">Skills needed</div>
                  <div className="row-wrap">{gig.skills.map((s) => <span className="chip" key={s.id}>{s.name}</span>)}</div>
                </>
              )}
              {gig.applyDeadline && <p className="small muted">Apply by {longDate(gig.applyDeadline)}</p>}
            </div>
            <div className="card stack">
              <div className="h3">Where you'll work</div>
              <Suspense fallback={<Skeleton h={220} />}>
                <GigAreaMap gig={gig} />
              </Suspense>
              {gig.exact ? (
                <Note tone="mint" icon="pin">
                  <div className="strong">{gig.exact.addressLine}</div>
                  {gig.exact.providerPhone && <div>Provider phone: <a href={`tel:+91${gig.exact.providerPhone}`}>{gig.exact.providerPhone}</a></div>}
                </Note>
              ) : (
                <div className="row small body2"><Icon name="shield" size={18} color="#495572" /> This is an approximate area in {gig.locality || gig.city}. You'll see the exact address once you're hired.</div>
              )}
            </div>
            <div className="hide-desktop">{providerCard}</div>
          </div>
          <div className="stack hide-mobile" style={{ position: "sticky", top: 88 }}>
            <div className="card raised stack">
              <div className="row" style={{ alignItems: "baseline", gap: 6 }}>
                <span className="bold" style={{ fontSize: 32 }}>{rupees(gig.payAmount)}</span>
                <span className="muted">{payLabel(gig.payType)}</span>
              </div>
              <div className="stack-sm">
                <div className="row between small"><span className="body2">Spots filled</span><span className="strong">{gig.workersNeeded - gig.openingsLeft} of {gig.workersNeeded}</span></div>
                <div className="track mint"><div style={{ width: `${((gig.workersNeeded - gig.openingsLeft) / Math.max(1, gig.workersNeeded)) * 100}%` }} /></div>
              </div>
              {cta}
              {ctaNote}
            </div>
            {providerCard}
          </div>
        </div>
      </div>
      <div className="sticky-cta hide-desktop">
        <div className="stack-sm" style={{ gap: 0, minWidth: 110 }}>
          <span className="bold" style={{ fontSize: 18 }}>{rupees(gig.payAmount)} {payLabel(gig.payType)}</span>
          <span className="xs text-mint strong">{gig.openingsLeft} of {gig.workersNeeded} spots left</span>
        </div>
        <div className="grow">{cta}</div>
      </div>
      {st.kind === "incomplete" && <div className="container hide-desktop" style={{ paddingTop: 0 }}>{ctaNote}</div>}
      {showApply && (
        <ApplyModal
          gig={gig}
          onClose={() => setShowApply(false)}
          onDone={() => {
            setShowApply(false);
            setAppliedIds(new Set([...appliedIds, gig.id]));
            toast({ text: "Application sent. Track it in My jobs." });
          }}
        />
      )}
      {showProvider && <ProviderSheet gig={gig} onClose={() => setShowProvider(false)} />}
    </Shell>
  );
}
