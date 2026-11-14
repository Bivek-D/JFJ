import { Link } from "react-router-dom";
import type { GigSummary } from "../api/types";
import { artFor, iconForCategory } from "../lib/art";
import { payText, shortDate } from "../lib/format";
import { Icon } from "./Icon";
import { Badge } from "./ui";

export function GigThumb({ title, category, size = 92 }: { title: string; category?: string; size?: number }) {
  const art = artFor(title, category);
  return art ? (
    <img className="gig-thumb" src={art} alt="" style={{ width: size, height: size }} loading="lazy" />
  ) : (
    <div className="gig-thumb" style={{ width: size, height: size }}>
      <Icon name={iconForCategory(category)} size={Math.round(size * 0.38)} />
    </div>
  );
}

export function GigCard({ gig }: { gig: GigSummary }) {
  const place = [gig.locality, gig.city].filter(Boolean).join(", ");
  const dist = gig.distanceKm != null ? ` · ${gig.distanceKm < 1 ? gig.distanceKm.toFixed(1) : Math.round(gig.distanceKm * 10) / 10} km` : "";
  return (
    <Link to={`/gig/${gig.id}`} className="gig-card">
      <GigThumb title={gig.title} category={gig.category?.name} />
      <div className="gig-info">
        <span className="gig-cat">{gig.category?.name}{dist}</span>
        <span className="gig-title">{gig.title}</span>
        <span className="gig-meta">{place}{gig.startDate ? ` · Starts ${shortDate(gig.startDate)}` : ""}</span>
        <div className="gig-bottom">
          <span className="gig-pay">{payText(gig.payAmount, gig.payType)}</span>
          <Badge tone="mint">{gig.workersNeeded} {gig.workersNeeded === 1 ? "worker" : "workers"}</Badge>
        </div>
      </div>
    </Link>
  );
}
