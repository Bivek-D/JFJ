import { useParams } from "react-router-dom";
import { MobileTop, Shell } from "../components/Shell";
import { Avatar, Badge, ErrorBox, LevelBar, ReviewJobTag, Skeleton, Stars } from "../components/ui";
import * as ep from "../api/endpoints";
import { useAsync } from "../lib/useAsync";
import { experienceName, ratingText, relativeTime } from "../lib/format";
import { useAuth } from "../auth/AuthContext";
import { jobFor, myReviewJobs } from "../lib/reviewJobs";

/** The limited view providers see (no contact details). */
export default function PublicWorker() {
  const { id = "" } = useParams();
  const { me } = useAuth();
  const self = me?.id === id;
  const w = useAsync(() => ep.getPublicWorker(id), [id]);
  const reviews = useAsync(() => ep.userReviews(id), [id]);
  // only your own reviews can be matched to jobs (the API doesn't send the job)
  const jobs = useAsync(() => (self ? myReviewJobs("PROVIDER_TO_WORKER") : Promise.resolve(null)), [self]);
  const p = w.data;
  const list = (reviews.data?.items ?? []).filter((r) => r.direction === "PROVIDER_TO_WORKER");
  return (
    <Shell>
      <MobileTop title={p?.name ?? "Worker"} />
      <div className="container narrow stack">
        {w.error ? <ErrorBox error={w.error} onRetry={w.reload} /> : !p ? <Skeleton h={260} /> : (
          <>
            <div className="card stack">
              <div className="row" style={{ gap: 16 }}>
                <Avatar name={p.name} url={p.photoUrl} size={76} />
                <div className="grow">
                  <h1 className="h2">{p.name}</h1>
                  <div className="small body2">{ratingText(p.rating)} · {p.completedGigs} gigs done{p.city ? ` · ${p.city}` : ""}</div>
                </div>
              </div>
              {p.level && <LevelBar totalXp={p.level.totalXp} compact />}
              {p.description && <p className="body2">{p.description}</p>}
              <div className="row-wrap">
                {p.experienceLevel && <Badge tone="indigo">{experienceName[p.experienceLevel]}{p.experienceYears ? ` · ${p.experienceYears} yrs` : ""}</Badge>}
                {p.languages.map((l) => <Badge key={l} tone="grey">{l}</Badge>)}
              </div>
              {p.skills.length > 0 && <div className="row-wrap">{p.skills.map((s) => <span className="chip" key={s.id}>{s.name}</span>)}</div>}
            </div>
            <div className="card stack">
              <div className="h3">What providers say</div>
              {reviews.loading ? <Skeleton h={60} /> : list.length === 0 ? <p className="small muted">No reviews yet.</p> : list.map((r) => {
                const job = jobFor(r, jobs.data);
                return (
                <div key={r.id} className="soft stack-sm">
                  <div className="row between"><span className="strong small">{r.reviewerName}</span><Stars value={r.rating} size={14} /></div>
                  {job && <ReviewJobTag title={job.title} to={`/gig/${job.id}`} />}
                  {r.comment && <p className="small body2">{r.comment}</p>}
                  <span className="xs muted">{relativeTime(r.createdAt)}</span>
                </div>
                );
              })}
            </div>
          </>
        )}
      </div>
    </Shell>
  );
}
