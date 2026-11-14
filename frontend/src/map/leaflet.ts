import "leaflet/dist/leaflet.css";
import "leaflet.markercluster/dist/MarkerCluster.css";
import L from "leaflet";
import { artFor, iconForCategory } from "../lib/art";
import { iconSvg } from "../components/Icon";

// Tiles: MapTiler when a key is set (production), else Stadia "alidade_smooth",
// which works without a key on localhost. Never tile.openstreetmap.org (see guide §5).
const key = import.meta.env.VITE_MAP_TILE_KEY as string | undefined;
export const TILE = key
  ? {
      url: `https://api.maptiler.com/maps/streets-v2-light/256/{z}/{x}/{y}.png?key=${key}`,
      attribution:
        '&copy; <a href="https://www.maptiler.com/copyright/">MapTiler</a> &copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap contributors</a>',
    }
  : {
      url: "https://tiles.stadiamaps.com/tiles/alidade_smooth/{z}/{x}/{y}{r}.png",
      attribution:
        '&copy; <a href="https://stadiamaps.com/">Stadia Maps</a> &copy; <a href="https://openmaptiles.org/">OpenMapTiles</a> &copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap contributors</a>',
    };

export const INDIA_CENTER: [number, number] = [22.5, 79];
export const INDIA_ZOOM = 5;

/** The backend rejects anything outside India (lat 6–38, lng 68–98). */
export function clampToIndia(b: { minLat: number; minLng: number; maxLat: number; maxLng: number }) {
  return {
    minLat: Math.max(6, Math.min(38, b.minLat)),
    maxLat: Math.max(6, Math.min(38, b.maxLat)),
    minLng: Math.max(68, Math.min(98, b.minLng)),
    maxLng: Math.max(68, Math.min(98, b.maxLng)),
  };
}

const escapeHtml = (s: string) => s.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]!);

/** Photo pin from the design: indigo ring, illustration inside, small tail. Selected = bigger + dark ring. */
export function gigPinIcon(title: string, categoryName: string | undefined, selected = false) {
  const art = artFor(title, categoryName);
  const inner = art
    ? `<img src="${art}" alt="" />`
    : `<span class="inner">${iconSvg(iconForCategory(categoryName), selected ? 28 : 24, "#3B5BDB")}</span>`;
  const size = selected ? [68, 80] : [56, 66];
  return L.divIcon({
    className: "",
    html: `<div class="jfj-pin${selected ? " selected" : ""}" title="${escapeHtml(title)}"><div class="ring">${inner}</div><div class="tail"></div></div>`,
    iconSize: size as L.PointTuple,
    iconAnchor: [size[0] / 2, size[1] - 2],
  });
}

export function clusterIcon(count: number) {
  return L.divIcon({
    className: "",
    html: `<div class="jfj-cluster"><span>${count}</span></div>`,
    iconSize: [56, 56],
    iconAnchor: [28, 28],
  });
}

export const youAreHereIcon = L.divIcon({ className: "", html: '<div class="jfj-you"></div>', iconSize: [20, 20], iconAnchor: [10, 10] });

/** Draggable drop pin used by the location picker. */
export const dropPinIcon = L.divIcon({
  className: "",
  html: `<svg class="jfj-drop" width="44" height="64" viewBox="0 0 44 64"><ellipse cx="22" cy="60" rx="10" ry="3.5" fill="rgba(22,33,58,0.25)"/><path d="M22 1C33.6 1 43 10.4 43 22C43 36.5 22 57 22 57C22 57 1 36.5 1 22C1 10.4 10.4 1 22 1Z" fill="#3B5BDB" stroke="#fff" stroke-width="2"/><circle cx="22" cy="22" r="8" fill="#fff"/></svg>`,
  iconSize: [44, 64],
  iconAnchor: [22, 58],
});
