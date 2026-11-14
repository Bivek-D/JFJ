import { useEffect, useRef, useState } from "react";
import { MapContainer, Marker, TileLayer, useMap, useMapEvents } from "react-leaflet";
import type L from "leaflet";
import { TILE, INDIA_CENTER, INDIA_ZOOM, dropPinIcon } from "./leaflet";
import * as ep from "../api/endpoints";
import { ApiError } from "../api/client";
import type { Address } from "../api/types";
import { Icon } from "../components/Icon";

export interface PickedPoint { lat: number; lng: number }

function inIndia(lat: number, lng: number) {
  return lat >= 6 && lat <= 38 && lng >= 68 && lng <= 98;
}

function ClickToPlace({ onPick, locked }: { onPick: (p: PickedPoint) => void; locked: boolean }) {
  useMapEvents({ click: (e) => !locked && onPick({ lat: e.latlng.lat, lng: e.latlng.lng }) });
  return null;
}
function Fly({ to, v }: { to: PickedPoint | null; v: number }) {
  const map = useMap();
  useEffect(() => {
    if (v > 0 && to) map.flyTo([to.lat, to.lng], 17, { duration: 0.8 });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [v]);
  return null;
}
/** Open at the browser's location, quietly; never place the pin for the user. */
function OpenAtUser({ hasPin, fallback }: { hasPin: boolean; fallback: PickedPoint | null }) {
  const map = useMap();
  useEffect(() => {
    if (hasPin) return;
    if (fallback) map.setView([fallback.lat, fallback.lng], 13);
    navigator.geolocation?.getCurrentPosition(
      (p) => inIndia(p.coords.latitude, p.coords.longitude) && map.setView([p.coords.latitude, p.coords.longitude], 15),
      () => {},
      { timeout: 8000 },
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  return null;
}

/**
 * Pin-first location picker (guide §6): search with suggestions (debounced 300 ms,
 * 3+ chars, previous request aborted), tap/drag the pin → reverse geocode.
 * The pin always works on its own; search problems only hide or quiet the search box.
 */
export default function LocationPicker({
  pin, locked = false, fallbackCenter, onPick,
}: {
  pin: PickedPoint | null;
  locked?: boolean;
  fallbackCenter?: PickedPoint | null;
  onPick: (p: PickedPoint, address: Address | null, source: "search" | "pin") => void;
}) {
  const [q, setQ] = useState("");
  const [focus, setFocus] = useState(false);
  const [suggestions, setSuggestions] = useState<Address[]>([]);
  const [searchState, setSearchState] = useState<"ok" | "busy" | "limited" | "down" | "none">("ok");
  const [fly, setFly] = useState({ to: pin, v: 0 });
  const [reversing, setReversing] = useState(false);
  const reverseCtrl = useRef<AbortController | null>(null);

  // autocomplete (searchState is display-only, so it's deliberately not a dependency)
  const down = searchState === "down";
  useEffect(() => {
    if (down) return;
    const term = q.trim();
    if (term.length < 3) {
      setSuggestions([]);
      return;
    }
    const ctrl = new AbortController();
    const t = window.setTimeout(async () => {
      try {
        setSearchState("busy");
        const list = await ep.geoAutocomplete(term, ctrl.signal);
        setSuggestions(list ?? []);
        setSearchState(list?.length ? "ok" : "none");
      } catch (e) {
        if ((e as Error).name === "AbortError") return;
        if (e instanceof ApiError && e.status === 429) setSearchState("limited");
        else if (e instanceof ApiError && e.status === 503) setSearchState("down");
        else setSearchState("none");
        setSuggestions([]);
      }
    }, 300);
    return () => {
      window.clearTimeout(t);
      ctrl.abort();
    };
  }, [q, down]);

  async function placeAt(p: PickedPoint) {
    if (!inIndia(p.lat, p.lng)) return;
    onPick(p, null, "pin");
    reverseCtrl.current?.abort();
    const ctrl = new AbortController();
    reverseCtrl.current = ctrl;
    setReversing(true);
    try {
      const addr = await ep.geoReverse(p.lat, p.lng, ctrl.signal);
      onPick(p, addr, "pin");
    } catch {
      /* 404 is normal in small towns: the pin + typed address still work */
    } finally {
      if (!ctrl.signal.aborted) setReversing(false);
    }
  }

  function choose(a: Address) {
    const p = { lat: a.lat, lng: a.lng };
    onPick(p, a, "search");
    setFly({ to: p, v: fly.v + 1 });
    setQ(a.label);
    setSuggestions([]);
    setFocus(false);
  }

  return (
    <div className="picker-map">
      <MapContainer
        center={pin ? [pin.lat, pin.lng] : fallbackCenter ? [fallbackCenter.lat, fallbackCenter.lng] : INDIA_CENTER}
        zoom={pin ? 16 : fallbackCenter ? 13 : INDIA_ZOOM}
        style={{ height: "100%" }}
      >
        <TileLayer url={TILE.url} attribution={TILE.attribution} />
        <OpenAtUser hasPin={!!pin} fallback={fallbackCenter ?? null} />
        <ClickToPlace onPick={placeAt} locked={locked} />
        <Fly to={fly.to} v={fly.v} />
        {pin && (
          <Marker
            position={[pin.lat, pin.lng]}
            icon={dropPinIcon}
            draggable={!locked}
            eventHandlers={{
              dragend: (e) => {
                const ll = (e.target as L.Marker).getLatLng();
                placeAt({ lat: ll.lat, lng: ll.lng });
              },
            }}
          />
        )}
      </MapContainer>

      {searchState !== "down" && !locked && (
        <div className="suggestions">
          <div className={`search ${focus ? "focus" : ""}`}>
            <Icon name="search" size={20} color="#495572" />
            <input
              value={q}
              onChange={(e) => setQ(e.target.value)}
              onFocus={() => setFocus(true)}
              onBlur={() => window.setTimeout(() => setFocus(false), 150)}
              placeholder="Search street, area or landmark"
              aria-label="Search for an address"
            />
            {q && (
              <button className="icon-btn" style={{ background: "none", width: 28, height: 28 }} onClick={() => { setQ(""); setSuggestions([]); }} aria-label="Clear search">
                <Icon name="close" size={18} color="#8590A8" />
              </button>
            )}
          </div>
          {focus && suggestions.length > 0 && (
            <div role="listbox">
              {suggestions.map((s, i) => (
                <button key={`${s.lat},${s.lng},${i}`} className="suggestion" onMouseDown={(e) => e.preventDefault()} onClick={() => choose(s)}>
                  <Icon name="pin" size={20} color={i === 0 ? "#3B5BDB" : "#8590A8"} />
                  <span className="grow">
                    <span className="strong small" style={{ display: "block" }}>{s.addressLine || s.label}</span>
                    <span className="xs muted">{[s.locality, s.city, s.pincode].filter(Boolean).join(", ")}</span>
                  </span>
                </button>
              ))}
            </div>
          )}
          {focus && q.trim().length >= 3 && (
            <div className="xs" style={{ padding: "10px 14px 12px", borderTop: "1px solid var(--line)", color: searchState === "limited" ? "var(--amber-700)" : "var(--primary-500)", fontWeight: 500 }}>
              {searchState === "busy" && "Searching…"}
              {searchState === "limited" && "Lots of searches just now. Wait a moment, or drag the pin instead."}
              {(searchState === "none" || searchState === "ok") && (suggestions.length ? "Can't find it? Tap the map to drop the pin instead." : "No matches. Tap the map to drop the pin instead.")}
            </div>
          )}
        </div>
      )}

      {!pin && !locked && (
        <div className="map-pill" style={{ position: "absolute", left: "50%", bottom: 16, transform: "translateX(-50%)", zIndex: 500 }}>
          <Icon name="pin" size={18} color="#3B5BDB" /> Tap the map to drop the pin
        </div>
      )}
      {pin && reversing && (
        <div className="map-pill" style={{ position: "absolute", left: "50%", bottom: 16, transform: "translateX(-50%)", zIndex: 500 }}>
          <span className="spinner" style={{ borderTopColor: "var(--primary-500)", borderColor: "var(--primary-50)" }} /> Finding the address…
        </div>
      )}
    </div>
  );
}
