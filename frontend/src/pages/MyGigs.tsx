import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { MobileTop, Shell } from "../components/Shell";
import { Icon } from "../components/Icon";
import { Badge, Empty, ErrorBox, Modal, ModalHead, PillTabs, SkeletonList, useToast } from "../components/ui";
import { GigThumb } from "../components/GigCard";
import * as ep from "../api/endpoints";
import { errorMessage } from "../api/client";
import type { GigStatus, OwnerGig } from "../api/types";
import { useAsync } from "../lib/useAsync";
import { gigStatusBadge, payText, ratingText, shortDate } from "../lib/format";
import { useAuth } from "../auth/AuthContext";

const TABS: { value: GigStatus; label: string }[] = [
  { value: "OPEN", label: "Open" },
  { value: "IN_PROGRESS", label: "In progress" },
  { value: "DRAFT", label: "Drafts" },
  { value: "COMPLETED", label: "Completed" },
  { value: "CANCELLED", label: "Cancelled" },
  { value: "EXPIRED", label: "Expired" },
];

function GigRow({ g, onPublish, onDelete }: { g: OwnerGig; onPublish: (g: OwnerGig) => void; onDelete: (g: OwnerGig) => void }) {
  const navigate = useNavigate();
  const [counts, setCounts] = useState<{ applied: number; hired: number } | null>(null);
  useEffect(() => {
    if (g.status !== "OPEN" && g.status !== "IN_PROGRESS") return;
    ep.gigApplicants(g.id)
      .then((list) => setCounts({ applied: list.filter((a) => a.status === "APPLIED" || a.status === "SHORTLISTED").length, hired: list.filter((a) => a.status === "HIRED").length }))
      .catch(() => {});
  }, [g.id, g.status]);
  const badge = gigStatusBadge[g.status];
  return (
    <div className="card pad-sm row" style={{ gap: 16, flexWrap: "wrap" }}>
      <GigThumb title={g.title} category={g.category?.name} size={72} />
      <div className="grow stack-sm" style={{ gap: 4, minWidth: 180 }}>
        <Link to={`/gigs/${g.id}`} className="h3" style={{ color: "var(--ink-900)" }}>{g.title}</Link>
        <span className="small muted">{[g.locality, g.city].filter(Boolean).join(", ")} · Starts {shortDate(g.startDate)} · {payText(g.payAmount, g.payType)}</span>
        <div><Badge tone={badge.tone}>{badge.label}</Badge></div>
      </div>
      {counts && (
        <div className="row" style={{ gap: 24 }}>
          <div className="stack-sm" style={{ gap: 0 }}><span className="bold" style={{ fontSize: 20 }}>{counts.applied}</span><span className="xs muted">waiting</span></div>
          <div className="stack-sm" style={{ gap: 0 }}><span className="bold" style={{ fontSize: 20 }}>{counts.hired} / {g.workersNeeded}</span><span className="xs muted">hired</span></div>
        </div>
      )}
      <div className="row" style={{ gap: 8 }}>
        {g.status === "DRAFT" ? (
          <>
            <button className="btn btn-secondary btn-sm" onClick={() => onDelete(g)} aria-label={`Delete draft ${g.title}`}>Delete</button>
            <button className="btn btn-secondary btn-sm" onClick={() => navigate(`/gigs/${g.id}/edit`)}>Edit</button>
            <button className="btn btn-primary btn-sm" onClick={() => onPublish(g)}>Publish</button>
          </>
        ) : (
          <Link className={`btn btn-sm ${g.status === "OPEN" && counts?.applied ? "btn-primary" : "btn-soft"}`} to={`/gigs/${g.id}`}>
            {g.status === "OPEN" ? "Review applicants" : "Manage"}
          </Link>
        )}
      </div>
    </div>
  );
}

export default function MyGigs() {
  const { me } = useAuth();
  const toast = useToast();
  const [tab, setTab] = useState<GigStatus>("OPEN");
  const [counts, setCounts] = useState<Partial<Record<GigStatus, number>>>({});
  const [page, setPage] = useState(0);
  const list = useAsync(() => ep.myGigs(tab, page, 20), [tab, page]);
  const [items, setItems] = useState<OwnerGig[]>([]);
  const [deleting, setDeleting] = useState<OwnerGig | null>(null);

  const loadCounts = () =>
    TABS.forEach((t) => ep.myGigs(t.value, 0, 1).then((p) => setCounts((c) => ({ ...c, [t.value]: p.totalItems }))).catch(() => {}));
  useEffect(loadCounts, []);
  useEffect(() => {
    if (list.data) setItems((old) => (page === 0 ? list.data!.items : [...old, ...list.data!.items]));
  }, [list.data, page]);
  useEffect(() => setPage(0), [tab]);

  async function publish(g: OwnerGig) {
    try {
      await ep.publishGig(g.id);
      toast({ text: `"${g.title}" is live.` });
      list.reload();
      loadCounts();
    } catch (e) {
      toast({ text: errorMessage(e), error: true });
    }
  }
  async function remove() {
    if (!deleting) return;
    try {
      await ep.deleteGig(deleting.id);
      toast({ text: "Draft deleted." });
      setDeleting(null);
      list.reload();
      loadCounts();
    } catch (e) {
      toast({ text: errorMessage(e), error: true });
    }
  }

  return (
    <Shell>
      <MobileTop title="My gigs" back={false} right={<Link className="btn btn-primary btn-sm" to="/post"><Icon name="plus" size={18} color="#fff" />Post</Link>} />
      <div className="container stack-lg">
        <div className="row between hide-mobile">
          <div className="stack-sm">
            <h1 className="h1">My gigs</h1>
            <p className="muted">Manage the gigs you've posted and hire the right people.</p>
          </div>
        </div>
        <div className="grid-4" style={{ gridTemplateColumns: "repeat(3, minmax(0,1fr))" }}>
          <div className="card pad-sm stack-sm" style={{ gap: 2 }}><span className="small muted">Open gigs</span><span className="bold" style={{ fontSize: 26 }}>{counts.OPEN ?? "–"}</span></div>
          <div className="card pad-sm stack-sm" style={{ gap: 2 }}><span className="small muted">In progress</span><span className="bold" style={{ fontSize: 26 }}>{counts.IN_PROGRESS ?? "–"}</span></div>
          <div className="card pad-sm stack-sm" style={{ gap: 2 }}><span className="small muted">Your rating</span><span className="bold" style={{ fontSize: 22 }}>{ratingText(me?.providerRating)}</span></div>
        </div>
        <PillTabs tabs={TABS.map((t) => ({ ...t, count: counts[t.value] }))} value={tab} onChange={setTab} />
        {list.error ? <ErrorBox error={list.error} onRetry={list.reload} /> : list.loading && page === 0 ? <SkeletonList n={3} h={104} /> : items.length === 0 ? (
          <Empty icon="briefcase" title={tab === "OPEN" ? "No open gigs" : `No ${TABS.find((t) => t.value === tab)!.label.toLowerCase()} gigs`} action={tab === "OPEN" || tab === "DRAFT" ? <Link className="btn btn-primary" to="/post">Post a gig</Link> : undefined}>
            {tab === "OPEN" ? "Post a gig and workers nearby can apply." : "Gigs show up here as they move along."}
          </Empty>
        ) : (
          <div className="stack-sm">
            {items.map((g) => <GigRow key={g.id} g={g} onPublish={publish} onDelete={setDeleting} />)}
            {list.data && page + 1 < list.data.totalPages && <button className="btn btn-secondary" disabled={list.loading} onClick={() => setPage((p) => p + 1)}>Show more</button>}
          </div>
        )}
      </div>
      {deleting && (
        <Modal onClose={() => setDeleting(null)} label="Delete draft">
          <ModalHead title="Delete this draft?" onClose={() => setDeleting(null)} />
          <p className="body2">"{deleting.title}" will be deleted. This can't be undone.</p>
          <div className="row" style={{ justifyContent: "flex-end" }}>
            <button className="btn btn-secondary" onClick={() => setDeleting(null)}>Keep it</button>
            <button className="btn btn-danger" onClick={remove}>Delete draft</button>
          </div>
        </Modal>
      )}
    </Shell>
  );
}
