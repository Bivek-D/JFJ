import { Circle, MapContainer, Marker, TileLayer } from "react-leaflet";
import { TILE, gigPinIcon } from "./leaflet";
import type { GigDetail } from "../api/types";

/** Gig detail map: a ~500 m circle for everyone, the exact pin only for the owner and hired workers. */
export default function GigAreaMap({ gig, height = 220 }: { gig: GigDetail; height?: number }) {
  const exact = gig.exact && gig.exact.lat != null ? gig.exact : null;
  const lat = exact ? exact.lat : gig.lat;
  const lng = exact ? exact.lng : gig.lng;
  return (
    <div style={{ height, borderRadius: 14, overflow: "hidden", border: "1px solid var(--line)" }}>
      <MapContainer center={[lat, lng]} zoom={exact ? 16 : 14} scrollWheelZoom={false} style={{ height: "100%" }}>
        <TileLayer url={TILE.url} attribution={TILE.attribution} />
        {exact ? (
          <Marker position={[lat, lng]} icon={gigPinIcon(gig.title, gig.category?.name)} />
        ) : (
          <>
            <Circle center={[lat, lng]} radius={500} pathOptions={{ color: "#3B5BDB", weight: 2, fillColor: "#3B5BDB", fillOpacity: 0.15 }} />
            <Marker position={[lat, lng]} icon={gigPinIcon(gig.title, gig.category?.name)} interactive={false} />
          </>
        )}
      </MapContainer>
    </div>
  );
}
