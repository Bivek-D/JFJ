import { lazy, Suspense, useEffect, useMemo, useState } from "react";
import { Shell } from "../components/Shell";
import { Icon } from "../components/Icon";
import { GigCard } from "../components/GigCard";
import { Empty, ErrorBox, Modal, SkeletonList, Badge } from "../components/ui";
import * as ep from "../api/endpoints";
import type { Category, ExperienceLevel, GigSummary, PayType } from "../api/types";
import { cached } from "../lib/useAsync";
import { experienceName, todayIST } from "../lib/format";
import { useAuth } from "../auth/AuthContext";

const GigMap = lazy(() => import("../map/GigMap"));

/** radiusKm "any" = no distance limit (the list is then sorted newest first). */
export interface Filters extends ep.GigFilters { radiusKm: number | "any" }
export const EMPTY_FILTERS: Filters = { q: "", categoryId: "", minPay: "", maxPay: "", payType: "", experience: "", startFrom: "", radiusKm: 10 };

/** Number of active filters (shown as a dot / chips). */
export function activeFilterCount(f: Filters) {
  return ["categoryId", "minPay", "maxPay", "payType", "experience", "startFrom"].filter((k) => (f as unknown as Record<string, unknown>)[k] !== "").length;
}

function useIsMobile() {
  const [m, setM] = useState(() => window.matchMedia("(max-width: 899px)").matches);
  useEffect(() => {
    const mq = window.matchMedia("(max-width: 899px)");
    const on = () => setM(mq.matches);
    mq.addEventListener("change", on);
    return () => mq.removeEventListener("change", on);
  }, []);
  return m;
}

/** Slow connection or data saver → start on the list (the map bundle loads only when opened). */
function prefersList() {
  const c = (navigator as unknown as { connection?: { saveData?: boolean; effectiveType?: string } }).connection;
  return !!c && (c.saveData === true || /(^|-)2g$/.test(c.effectiveType ?? ""));
}

function FiltersPanel({
  initial, categories, myExperience, onApply, onClose,
}: {
  initial: Filters; categories: Category[]; myExperience: ExperienceLevel | null;
  onApply: (f: Filters) => void; onClose: () => void;
}) {
  const [d, setD] = useState<Filters>(initial);
  const set = (patch: Partial<Filters>) => setD({ ...d, ...patch });
  const payTypes: (PayType | "")[] = ["", "DAILY", "FIXED", "HOURLY"];
  const payName = { "": "Any", DAILY: "Per day", FIXED: "Fixed", HOURLY: "Per hour" } as const;
  return (
    <Modal onClose={onClose} label="Filters" width={440}>
      <div className="row between">
        <div className="h2">Filters</div>
        <button className="link-btn" onClick={() => setD({ ...EMPTY_FILTERS, q: d.q })}>Clear all</button>
      </div>
      <div className="stack-sm">
        <div className="row between">
          <span className="strong small">Distance</span>
          <span className="strong small text-primary">{d.radiusKm === "any" ? "Anywhere" : `Within ${d.radiusKm} km`}</span>
        </div>
        <input
          type="range" min={1} max={50} step={1}
          value={d.radiusKm === "any" ? 50 : d.radiusKm}
          onChange={(e) => set({ radiusKm: Number(e.target.value) })}
          aria-label="Distance in km"
          style={{ accentColor: "var(--primary-500)", opacity: d.radiusKm === "any" ? 0.4 : 1 }}
        />
        <div className="row-wrap">
          {[5, 10, 25, 50].map((km) => (
            <button key={km} type="button" className={`chip ${d.radiusKm === km ? "active" : ""}`} onClick={() => set({ radiusKm: km })}>{km} km</button>
          ))}
          <button type="button" className={`chip ${d.radiusKm === "any" ? "active" : ""}`} onClick={() => set({ radiusKm: "any" })}>Anywhere</button>
        </div>
      </div>
      <div className="stack-sm">
        <span className="strong small">Category</span>
        <div className="row-wrap">
          <button className={`chip ${d.categoryId === "" ? "active" : ""}`} onClick={() => set({ categoryId: "" })}>All</button>
          {categories.map((c) => (
            <button key={c.id} className={`chip ${d.categoryId === c.id ? "active" : ""}`} onClick={() => set({ categoryId: c.id })}>{c.name}</button>
          ))}
        </div>
      </div>
      <div className="stack-sm">
        <span className="strong small">Pay</span>
        <div className="row-wrap">
          {payTypes.map((p) => (
            <button key={p || "any"} className={`chip ${d.payType === p ? "active" : ""}`} onClick={() => set({ payType: p })}>{payName[p]}</button>
          ))}
        </div>
        <div className="grid-2" style={{ gridTemplateColumns: "1fr 1fr" }}>
          <label className="field"><span className="field-label">Min ₹</span><input className="input" inputMode="numeric" value={d.minPay} onChange={(e) => set({ minPay: e.target.value === "" ? "" : Number(e.target.value.replace(/\D/g, "")) })} /></label>
          <label className="field"><span className="field-label">Max ₹</span><input className="input" inputMode="numeric" value={d.maxPay} onChange={(e) => set({ maxPay: e.target.value === "" ? "" : Number(e.target.value.replace(/\D/g, "")) })} /></label>
        </div>
      </div>
      <label className="field">
        <span className="field-label">Starting on or after</span>
        <input className="input" type="date" min={todayIST()} value={d.startFrom ?? ""} onChange={(e) => set({ startFrom: e.target.value })} />
      </label>
      <div className="soft row" style={{ padding: 14 }}>
        <div className="grow">
          <div className="strong small">Only gigs that fit my experience</div>
          <div className="xs muted">{myExperience ? `Hides gigs that need more than ${experienceName[myExperience]}.` : "Add your experience to your profile to use this."}</div>
        </div>
        <button
          type="button"
          className={`toggle ${d.experience ? "on" : ""}`}
          disabled={!myExperience}
          aria-pressed={!!d.experience}
          aria-label="Only gigs that fit my experience"
          onClick={() => set({ experience: d.experience ? "" : myExperience ?? "" })}
        />
      </div>
      <button className="btn btn-primary btn-lg btn-block" onClick={() => onApply(d)}>Show gigs</button>
    </Modal>
  );
}

export default function Explore() {
  const { mode } = useAuth();
  const mobile = useIsMobile();
  const [view, setView] = useState<"list" | "map">(() => (prefersList() ? "list" : "map"));
  const [categories, setCategories] = useState<Category[]>([]);
  const [myExperience, setMyExperience] = useState<ExperienceLevel | null>(null);
  const [filters, setFilters] = useState<Filters>(EMPTY_FILTERS);
  const [search, setSearch] = useState("");
  const [showFilters, setShowFilters] = useState(false);
  const [coords, setCoords] = useState<{ lat: number; lng: number } | null>(null);
  const [geo, setGeo] = useState<"idle" | "asking" | "ok" | "denied">("idle");

  const [items, setItems] = useState<GigSummary[]>([]);
  const [page, setPage] = useState(0);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [reload, setReload] = useState(0);

  const mapVisible = !mobile || view === "map";

  useEffect(() => {
    cached("categories", ep.getCategories).then(setCategories).catch(() => {});
    if (mode === "WORKER") ep.getMyWorkerProfile().then((p) => setMyExperience(p.experienceLevel)).catch(() => {});
  }, [mode]);

  // Geolocation is asked only when a map opens (or the user taps "Near me").
  function askLocation() {
    if (!navigator.geolocation) return setGeo("denied");
    setGeo("asking");
    navigator.geolocation.getCurrentPosition(
      (p) => {
        setCoords({ lat: p.coords.latitude, lng: p.coords.longitude });
        setGeo("ok");
      },
      () => setGeo("denied"),
      { timeout: 8000, maximumAge: 300_000 },
    );
  }
  useEffect(() => {
    if (mapVisible && geo === "idle") askLocation();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mapVisible]);

  const listFilters = useMemo(() => ({ ...filters, q: search }), [filters, search]);
  const key = JSON.stringify(listFilters) + JSON.stringify(coords) + reload;

  // reset paging when filters change
  useEffect(() => {
    setPage(0);
  }, [key]);

  useEffect(() => {
    if (geo === "asking") return;
    const ctrl = new AbortController();
    setLoading(true);
    setError(null);
    const near = coords && filters.radiusKm !== "any";
    ep.searchGigs(
      { ...listFilters, lat: near ? coords.lat : undefined, lng: near ? coords.lng : undefined, radiusKm: near ? (filters.radiusKm as number) : undefined, page, size: 20 },
      ctrl.signal,
    )
      .then((d) => {
        setItems((old) => (page === 0 ? d.items : [...old, ...d.items]));
        setTotal(d.totalItems);
        setTotalPages(d.totalPages);
      })
      .catch((e) => (e as Error).name !== "AbortError" && setError(e))
      .finally(() => !ctrl.signal.aborted && setLoading(false));
    return () => ctrl.abort();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, page, geo]);

  const nFilters = activeFilterCount(filters);
  const catName = filters.categoryId !== "" ? categories.find((c) => c.id === filters.categoryId)?.name : null;

  const searchBar = (
    <form className="search-bar" onSubmit={(e) => { e.preventDefault(); setSearch((e.currentTarget.elements.namedItem("q") as HTMLInputElement).value); }} role="search">
      <Icon name="search" color="#16213A" />
      <input name="q" defaultValue={search} placeholder="Search gigs, skills or area" aria-label="Search gigs" onBlur={(e) => setSearch(e.target.value)} />
      <button type="button" className="filter-btn" onClick={() => setShowFilters(true)} aria-label={`Filters${nFilters ? `, ${nFilters} active` : ""}`}>
        <Icon name="filter" />
        {nFilters > 0 && <span className="dot" />}
      </button>
    </form>
  );

  const chips = (
    <div className="row-wrap">
      {catName && <button className="chip active" onClick={() => setFilters({ ...filters, categoryId: "" })}>{catName} <Icon name="close" size={14} color="#fff" /></button>}
      {filters.payType && <button className="chip active" onClick={() => setFilters({ ...filters, payType: "" })}>{{ DAILY: "Per day", FIXED: "Fixed", HOURLY: "Per hour" }[filters.payType]} <Icon name="close" size={14} color="#fff" /></button>}
      {(filters.minPay !== "" || filters.maxPay !== "") && <button className="chip active" onClick={() => setFilters({ ...filters, minPay: "", maxPay: "" })}>₹{filters.minPay || 0}–{filters.maxPay || "any"} <Icon name="close" size={14} color="#fff" /></button>}
      {filters.startFrom && <button className="chip active" onClick={() => setFilters({ ...filters, startFrom: "" })}>From {filters.startFrom} <Icon name="close" size={14} color="#fff" /></button>}
      {filters.experience && <button className="chip active" onClick={() => setFilters({ ...filters, experience: "" })}>Fits my experience <Icon name="close" size={14} color="#fff" /></button>}
      {coords ? (
        <button className="chip" onClick={() => setShowFilters(true)}>
          <Icon name="pin" size={16} /> {filters.radiusKm === "any" ? "Anywhere" : `Within ${filters.radiusKm} km`}
        </button>
      ) : (
        <button className="chip" onClick={askLocation}><Icon name="locate" size={16} /> Near me</button>
      )}
    </div>
  );

  const list = (
    <div className="stack">
      <div className="row between">
        <span className="strong small">{coords && filters.radiusKm !== "any" ? "Nearest first" : "Newest first"}</span>
        <span className="xs muted">Pins show approximate areas</span>
      </div>
      {geo === "denied" && !coords && <span className="xs muted">Location is off, so gigs are sorted by newest. Turn it on to see what's near you.</span>}
      {error ? <ErrorBox error={error} onRetry={() => setReload((r) => r + 1)} /> : null}
      {loading && page === 0 ? (
        <SkeletonList n={5} />
      ) : items.length === 0 && !error ? (
        <Empty
          icon="search"
          title="No gigs here yet"
          action={
            <div className="row-wrap" style={{ justifyContent: "center" }}>
              {coords && filters.radiusKm !== "any" && <button className="btn btn-primary" onClick={() => setFilters({ ...filters, radiusKm: "any" })}>Search anywhere</button>}
              {(nFilters > 0 || search) && <button className="btn btn-secondary" onClick={() => { setFilters({ ...EMPTY_FILTERS, radiusKm: filters.radiusKm }); setSearch(""); }}>Clear filters</button>}
            </div>
          }
        >
          {coords && filters.radiusKm !== "any" ? `Nothing within ${filters.radiusKm} km. Try a bigger distance or search anywhere.` : "Try different words or fewer filters."}
        </Empty>
      ) : (
        <div className="stack-sm">
          {items.map((g) => <GigCard key={g.id} gig={g} />)}
        </div>
      )}
      {page + 1 < totalPages && !error && (
        <button className="btn btn-secondary" disabled={loading} onClick={() => setPage((p) => p + 1)}>{loading ? "Loading…" : "Show more gigs"}</button>
      )}
    </div>
  );

  const mapEl = (
    <Suspense fallback={<div className="skeleton" style={{ position: "absolute", inset: 0, borderRadius: 0 }} />}>
      {geo !== "asking" ? (
        <GigMap
          filters={listFilters}
          categories={categories}
          start={coords ? [coords.lat, coords.lng] : null}
          radiusKm={filters.radiusKm}
          onAnywhere={() => setFilters({ ...filters, radiusKm: "any" })}
        />
      ) : (
        <div className="skeleton" style={{ position: "absolute", inset: 0, borderRadius: 0 }} />
      )}
    </Suspense>
  );

  const toggle = (
    <div className="view-toggle" role="group" aria-label="View">
      <button className={view === "list" ? "on" : ""} onClick={() => setView("list")}><Icon name="list" size={20} color={view === "list" ? "#fff" : "#495572"} />List</button>
      <button className={view === "map" ? "on" : ""} onClick={() => setView("map")}><Icon name="map" size={20} color={view === "map" ? "#fff" : "#495572"} />Map</button>
    </div>
  );

  return (
    <Shell>
      {mobile ? (
        view === "map" ? (
          <div className="explore-map">
            {mapEl}
            <div className="mobile-map-top">{searchBar}<div style={{ overflowX: "auto" }}>{chips}</div></div>
            <div className="mobile-toggle">{toggle}</div>
          </div>
        ) : (
          <div className="explore-list">
            <div className="stack-sm">
              <h1 className="h2">Gigs near you</h1>
              <span className="small muted">{loading && page === 0 ? "Looking for gigs…" : `${total} open ${total === 1 ? "gig" : "gigs"}`}</span>
            </div>
            {searchBar}
            {chips}
            {list}
            <div className="mobile-toggle">{toggle}</div>
          </div>
        )
      ) : (
        <div className="explore">
          <aside className="explore-list">
            <div className="stack-sm">
              <h1 className="h1">Gigs near you</h1>
              <span className="small muted">
                {loading && page === 0 ? "Looking for gigs…" : `${total} open ${total === 1 ? "gig" : "gigs"}${coords && filters.radiusKm !== "any" ? ` within ${filters.radiusKm} km` : ""}`}
                {mode === "PROVIDER" && <> · <Badge tone="amber">You're in hiring mode</Badge></>}
              </span>
            </div>
            {searchBar}
            {chips}
            {list}
          </aside>
          <section className="explore-map">{mapEl}</section>
        </div>
      )}
      {showFilters && (
        <FiltersPanel
          initial={filters}
          categories={categories}
          myExperience={myExperience}
          onClose={() => setShowFilters(false)}
          onApply={(f) => { setFilters(f); setShowFilters(false); }}
        />
      )}
    </Shell>
  );
}
