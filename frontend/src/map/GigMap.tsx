import { useEffect, useMemo, useRef, useState } from "react";
import { Circle, MapContainer, Marker, TileLayer, useMap, useMapEvents } from "react-leaflet";
import MarkerClusterGroup from "react-leaflet-cluster";
import L from "leaflet";
import { Link } from "react-router-dom";
import { TILE, INDIA_CENTER, INDIA_ZOOM, clampToIndia, gigPinIcon, clusterIcon, youAreHereIcon } from "./leaflet";
import * as ep from "../api/endpoints";
import type { Category, MapPin } from "../api/types";
import { Icon } from "../components/Icon";
import { artFor, iconForCategory } from "../lib/art";
import { payText } from "../lib/format";
import { errorMessage } from "../api/client";

type Bounds = { minLat: number; minLng: number; maxLat: number; maxLng: number };
const ALL_INDIA: Bounds = { minLat: 6, minLng: 68, maxLat: 38, maxLng: 98 };

/** Reports the visible area ~400 ms after the map stops moving (only once the map has a real size). */
function AreaWatcher({ onArea }: { onArea: (b: Bounds) => void }) {
  const map = useMap();
  const timer = useRef<number | undefined>(undefined);
  const report = () => {
    window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => {
      const size = map.getSize();
      if (size.x === 0 || size.y === 0) return;
      const b = map.getBounds();
      onArea(clampToIndia({ minLat: b.getSouth(), minLng: b.getWest(), maxLat: b.getNorth(), maxLng: b.getEast() }));
    }, 400);
  };
  useMapEvents({ moveend: report });
  useEffect(() => {
    report();
    return () => window.clearTimeout(timer.current);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  return null;
}

/** Leaflet measures its box once; re-measure when the container changes size (e.g. it mounted hidden). */
function SizeWatcher() {
  const map = useMap();
  useEffect(() => {
    const el = map.getContainer();
    const ro = new ResizeObserver(() => {
      map.invalidateSize();
      map.fire("moveend");
    });
    ro.observe(el);
    return () => ro.disconnect();
  }, [map]);
  return null;
}

function Recenter({ center, zoom, version }: { center: [number, number] | null; zoom: number; version: number }) {
  const map = useMap();
  useEffect(() => {
    if (center) map.setView(center, zoom);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [version]);
  return null;
}

function FitTo({ points, version }: { points: [number, number][]; version: number }) {
  const map = useMap();
  useEffect(() => {
    if (!version || !points.length) return;
    // no animation: an animated zoom can stall (e.g. background tab) and never fire moveend
    map.invalidateSize();
    if (points.length === 1) map.setView(points[0], 13, { animate: false });
    else map.fitBounds(L.latLngBounds(points), { padding: [70, 70], maxZoom: 13, animate: false });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [version]);
  return null;
}

function Controls({ onLocate }: { onLocate: () => void }) {
  const map = useMap();
  return (
    <div style={{ position: "absolute", right: 16, top: 16, zIndex: 500, display: "flex", flexDirection: "column", gap: 10 }} className="hide-mobile">
      <div style={{ background: "#fff", borderRadius: 12, boxShadow: "var(--shadow-float)", overflow: "hidden", display: "flex", flexDirection: "column" }}>
        <button className="icon-btn" style={{ background: "#fff", borderRadius: 0 }} onClick={() => map.zoomIn()} aria-label="Zoom in"><Icon name="plus" size={20} /></button>
        <div style={{ height: 1, background: "var(--line)" }} />
        <button className="icon-btn" style={{ background: "#fff", borderRadius: 0 }} onClick={() => map.zoomOut()} aria-label="Zoom out"><Icon name="minus" size={20} /></button>
      </div>
      <button className="icon-btn white" style={{ borderRadius: 12 }} onClick={onLocate} aria-label="Go to my location"><Icon name="locate" size={20} /></button>
    </div>
  );
}

/** Great-circle distance in km. */
export function distanceKm(a: [number, number], b: [number, number]) {
  const r = Math.PI / 180;
  const dLat = (b[0] - a[0]) * r;
  const dLng = (b[1] - a[1]) * r;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(a[0] * r) * Math.cos(b[0] * r) * Math.sin(dLng / 2) ** 2;
  return 2 * 6371 * Math.asin(Math.sqrt(h));
}

/** Nearest pins to a point (simple equirectangular distance is plenty for ordering). */
export function nearestPins(pins: MapPin[], from: [number, number], n: number) {
  const [lat0, lng0] = from;
  const k = Math.cos((lat0 * Math.PI) / 180);
  return [...pins]
    .sort((a, b) => ((a.lat - lat0) ** 2 + ((a.lng - lng0) * k) ** 2) - ((b.lat - lat0) ** 2 + ((b.lng - lng0) * k) ** 2))
    .slice(0, n);
}

export default function GigMap({
  filters, categories, start, radiusKm = "any", onNeedPlace, onAnywhere,
}: {
  filters: ep.GigFilters;
  categories: Category[];
  /** where to open: the user's location if we have it, else the whole of India */
  start: [number, number] | null;
  /** distance filter around the user's location; "any" = no limit */
  radiusKm?: number | "any";
  onNeedPlace?: () => void;
  onAnywhere?: () => void;
}) {
  const [area, setArea] = useState<Bounds | null>(null);
  const [pins, setPins] = useState<MapPin[]>([]);
  const [elsewhere, setElsewhere] = useState<MapPin[] | null>(null);
  const [truncated, setTruncated] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>("");
  const [selected, setSelected] = useState<MapPin | null>(null);
  const [you, setYou] = useState<[number, number] | null>(start);
  const [recenter, setRecenter] = useState({ center: start, v: 0 });
  const [fit, setFit] = useState<{ points: [number, number][]; v: number }>({ points: [], v: 0 });
  const autoFitted = useRef(false);
  const catName = useMemo(() => new Map(categories.map((c) => [c.id, c.name])), [categories]);
  const filterKey = JSON.stringify(filters);
  const startKey = start ? `${start[0]},${start[1]}` : "";
  /** active distance limit (only when we know where the user is) */
  const radius = you && typeof radiusKm === "number" ? radiusKm : null;

  // the location can arrive after the map mounted
  useEffect(() => {
    if (start) setYou(start);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [startKey]);

  // new filters → allow one more automatic "zoom to the gigs"
  useEffect(() => {
    autoFitted.current = false;
    setElsewhere(null);
  }, [filterKey]);

  // distance filter on: frame the circle around the user
  const youKey = you ? `${you[0]},${you[1]}` : "";
  const hadRadius = useRef(false);
  useEffect(() => {
    if (radius === null) {
      // limit just removed ("Anywhere"): look across India, then zoom to wherever the gigs are
      if (hadRadius.current) {
        hadRadius.current = false;
        autoFitted.current = true; // this effect does the zoom, not the area fetch
        const ctrl = new AbortController();
        ep.mapGigs(ALL_INDIA, filters, ctrl.signal)
          .then((d) => {
            const pts = (d.items ?? []).map((p) => [p.lat, p.lng] as [number, number]);
            if (pts.length) setFit((f) => ({ points: pts, v: f.v + 1 }));
          })
          .catch(() => {});
        return () => ctrl.abort();
      }
      return;
    }
    hadRadius.current = true;
    if (!you) return;
    const b = L.latLng(you[0], you[1]).toBounds(radius * 2000);
    setFit((f) => ({ points: [[b.getSouth(), b.getWest()], [b.getNorth(), b.getEast()]], v: f.v + 1 }));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [youKey, radius]);

  const shown = useMemo(
    () => (you && radius !== null ? pins.filter((p) => distanceKm(you, [p.lat, p.lng]) <= radius) : pins),
    [pins, you, radius],
  );

  // fetch pins for the visible area (stale requests are aborted)
  useEffect(() => {
    if (!area || area.minLat >= area.maxLat || area.minLng >= area.maxLng) return;
    const ctrl = new AbortController();
    setLoading(true);
    setError("");
    ep.mapGigs(area, filters, ctrl.signal)
      .then(async (d) => {
        const items = d.items ?? [];
        setPins(items);
        setTruncated(!!d.truncated);
        const inRange = you && radius !== null ? items.filter((p) => distanceKm(you, [p.lat, p.lng]) <= radius) : items;
        if (inRange.length) {
          setElsewhere(null);
          // Opened without a distance limit: zoom to where the gigs are, once.
          if (radius === null && !autoFitted.current && !d.truncated) {
            autoFitted.current = true;
            setFit((f) => ({ points: items.map((p) => [p.lat, p.lng] as [number, number]), v: f.v + 1 }));
          }
          return;
        }
        // Nothing in view: look across India so we can point the user to the gigs that do exist.
        const all = await ep.mapGigs(ALL_INDIA, filters, ctrl.signal);
        const list = all.items ?? [];
        setElsewhere(list);
        // with a distance limit the circle stays framed; the pill offers "Search anywhere"
        if (list.length && !autoFitted.current && radius === null) {
          autoFitted.current = true;
          const from: [number, number] = you ?? [(area.minLat + area.maxLat) / 2, (area.minLng + area.maxLng) / 2];
          const near = nearestPins(list, from, 8).map((p) => [p.lat, p.lng] as [number, number]);
          setFit((f) => ({ points: you ? [...near, you] : near, v: f.v + 1 }));
        }
      })
      .catch((e) => {
        if ((e as Error).name !== "AbortError") setError(errorMessage(e));
      })
      .finally(() => !ctrl.signal.aborted && setLoading(false));
    return () => ctrl.abort();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [area, filterKey, radius]);

  const locate = () => {
    if (!navigator.geolocation) return;
    navigator.geolocation.getCurrentPosition(
      (p) => {
        const c: [number, number] = [p.coords.latitude, p.coords.longitude];
        setYou(c);
        setRecenter((r) => ({ center: c, v: r.v + 1 }));
      },
      () => onNeedPlace?.(),
      { timeout: 8000 },
    );
  };

  const showAll = () => {
    const list = elsewhere ?? [];
    if (!list.length) return;
    const from: [number, number] = you ?? INDIA_CENTER;
    setFit((f) => ({ points: nearestPins(list, from, 8).map((p) => [p.lat, p.lng] as [number, number]), v: f.v + 1 }));
  };

  const selCat = selected ? catName.get(selected.categoryId) : undefined;
  const selArt = selected ? artFor(selected.title, selCat) : null;
  const emptyHere = !loading && !error && shown.length === 0 && elsewhere !== null;

  return (
    <div style={{ position: "absolute", inset: 0 }}>
      <MapContainer
        center={start ?? INDIA_CENTER}
        zoom={start ? 13 : INDIA_ZOOM}
        zoomControl={false}
        style={{ height: "100%", width: "100%" }}
        attributionControl
      >
        <TileLayer url={TILE.url} attribution={TILE.attribution} />
        <SizeWatcher />
        <AreaWatcher onArea={setArea} />
        <Recenter center={recenter.center} zoom={14} version={recenter.v} />
        <FitTo points={fit.points} version={fit.v} />
        <Controls onLocate={locate} />
        {you && radius !== null && (
          <Circle center={you} radius={radius * 1000} interactive={false}
            pathOptions={{ color: "#3B5BDB", weight: 1.5, fillColor: "#3B5BDB", fillOpacity: 0.06, dashArray: "6 6" }} />
        )}
        {you && <Marker position={you} icon={youAreHereIcon} interactive={false} />}
        <MarkerClusterGroup
          chunkedLoading
          showCoverageOnHover={false}
          maxClusterRadius={40}
          disableClusteringAtZoom={9}
          iconCreateFunction={(c: { getChildCount: () => number }) => clusterIcon(c.getChildCount())}
        >
          {shown.map((p) => (
            <Marker
              key={p.id}
              position={[p.lat, p.lng]}
              icon={gigPinIcon(p.title, catName.get(p.categoryId), selected?.id === p.id)}
              zIndexOffset={selected?.id === p.id ? 1000 : 0}
              eventHandlers={{ click: () => setSelected(p) }}
              keyboard
              title={p.title}
            />
          ))}
        </MarkerClusterGroup>
      </MapContainer>

      <div className="map-overlay-top">
        {truncated && (
          <div className="map-pill dark" role="status">
            <Icon name="search" size={18} color="#fff" /> Too many gigs here. Zoom in to see them all.
          </div>
        )}
        {error && <div className="map-pill" role="alert" style={{ color: "var(--red-700)" }}>{error}</div>}
        {loading && !truncated && <div className="map-pill"><span className="spinner" style={{ borderTopColor: "var(--primary-500)", borderColor: "var(--primary-50)" }} /> Loading gigs</div>}
        {emptyHere && radius !== null ? (
          <button className="map-pill" onClick={() => onAnywhere?.()}>
            <Icon name="map" size={18} color="#3B5BDB" /> No gigs within {radius} km · Search anywhere
          </button>
        ) : emptyHere && (elsewhere!.length ? (
          <button className="map-pill" onClick={showAll}>
            <Icon name="map" size={18} color="#3B5BDB" /> No gigs in this area · Show {elsewhere!.length === 1 ? "the nearest gig" : "the nearest gigs"}
          </button>
        ) : (
          <div className="map-pill" role="status"><Icon name="search" size={18} /> No open gigs match yet. Try fewer filters.</div>
        ))}
      </div>

      {selected ? (
        <div className="map-preview" role="dialog" aria-label={selected.title}>
          {selArt ? <img className="gig-thumb" src={selArt} alt="" /> : <div className="gig-thumb"><Icon name={iconForCategory(selCat)} size={36} /></div>}
          <div className="gig-info">
            <span className="gig-cat">{selCat ?? "Gig"}</span>
            <span className="gig-title">{selected.title}</span>
            <div className="gig-bottom">
              <span className="gig-pay">{payText(selected.payAmount, selected.payType)}</span>
              <Link className="btn btn-primary btn-sm" to={`/gig/${selected.id}`}>View gig</Link>
            </div>
          </div>
          <button className="icon-btn" style={{ position: "absolute", top: -12, right: -12, width: 32, height: 32, background: "#fff", boxShadow: "var(--shadow-float)" }} onClick={() => setSelected(null)} aria-label="Close preview">
            <Icon name="close" size={16} />
          </button>
        </div>
      ) : (
        <div className="map-note hide-mobile">
          <Icon name="shield" size={18} color="#495572" /> Pins are approximate. You get the exact address once you're hired.
        </div>
      )}
    </div>
  );
}
