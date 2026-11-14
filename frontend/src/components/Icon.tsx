// Line icons from the JFJ design system (24px grid, 2px stroke).
const PATHS = {
  search: "M18 11A7 7 0 1 1 4 11A7 7 0 1 1 18 11Z M21 21L16 16",
  filter: "M4 6H20 M7 12H17 M10 18H14",
  map: "M3 6L9 3L15 6L21 3V18L15 21L9 18L3 21Z M9 3V18 M15 6V21",
  list: "M9 6H21 M9 12H21 M9 18H21 M3 6H5 M3 12H5 M3 18H5",
  bell: "M6 9A6 6 0 0 1 18 9C18 15 20.5 17 20.5 17H3.5C3.5 17 6 15 6 9Z M10 21A2 2 0 0 0 14 21",
  pin: "M12 21C12 21 19 14.5 19 9.5A7 7 0 0 0 5 9.5C5 14.5 12 21 12 21Z M14.5 9.5A2.5 2.5 0 1 1 9.5 9.5A2.5 2.5 0 1 1 14.5 9.5Z",
  home: "M3 10L12 3L21 10V20H3Z M9 20V13H15V20",
  briefcase: "M3 7H21V20H3Z M8 7V5A2 2 0 0 1 10 3H14A2 2 0 0 1 16 5V7 M3 13H21",
  plus: "M12 5V19 M5 12H19",
  minus: "M5 12H19",
  user: "M20 21V19A4 4 0 0 0 16 15H8A4 4 0 0 0 4 19V21 M16 7A4 4 0 1 1 8 7A4 4 0 1 1 16 7Z",
  locate: "M12 2V5 M12 19V22 M2 12H5 M19 12H22 M19 12A7 7 0 1 1 5 12A7 7 0 1 1 19 12Z M14 12A2 2 0 1 1 10 12A2 2 0 1 1 14 12Z",
  chevronLeft: "M15 18L9 12L15 6",
  chevronRight: "M9 18L15 12L9 6",
  chevronDown: "M6 9L12 15L18 9",
  close: "M18 6L6 18 M6 6L18 18",
  clock: "M21 12A9 9 0 1 1 3 12A9 9 0 1 1 21 12Z M12 7V12L15 14",
  calendar: "M3 5H21V21H3Z M3 10H21 M8 3V7 M16 3V7",
  users: "M16 21V19A4 4 0 0 0 12 15H6A4 4 0 0 0 2 19V21 M13 7A4 4 0 1 1 5 7A4 4 0 1 1 13 7Z M22 21V19A4 4 0 0 0 19 15.1 M16 3.1A4 4 0 0 1 16 10.9",
  heart: "M12 20L4.5 12.8C2.5 10.8 2.5 7.5 4.5 5.5C6.5 3.5 9.8 3.5 11.8 5.5L12 5.7L12.2 5.5C14.2 3.5 17.5 3.5 19.5 5.5C21.5 7.5 21.5 10.8 19.5 12.8Z",
  check: "M20 6L9 17L4 12",
  shield: "M12 22C12 22 20 18 20 12V5L12 2L4 5V12C4 18 12 22 12 22Z M9 12L11 14L15 10",
  paint: "M4 3H18V9H4Z M18 6H20V11H11V14 M10 14H12V21H10Z",
  wrench: "M14.7 6.3A4 4 0 0 0 9.4 11.6L3 18L6 21L12.4 14.6A4 4 0 0 0 17.7 9.3L15 12L12 9Z",
  zap: "M13 2L3 14H12L11 22L21 10H12Z",
  leaf: "M11 20A7 7 0 0 1 4 13C4 6 11 4 20 4C20 13 18 20 11 20Z M4 21C6 16 9 13 13 11",
  truck: "M1 4H15V16H1Z M15 8H19L23 12V16H15Z M8 18.5A2.5 2.5 0 1 1 3 18.5A2.5 2.5 0 1 1 8 18.5Z M21 18.5A2.5 2.5 0 1 1 16 18.5A2.5 2.5 0 1 1 21 18.5Z",
  sparkles: "M12 3L13.9 8.1L19 10L13.9 11.9L12 17L10.1 11.9L5 10L10.1 8.1Z M19 15L19.8 17.2L22 18L19.8 18.8L19 21L18.2 18.8L16 18L18.2 17.2Z",
  hammer: "M14 10L4 20L2 18L12 8 M10 6L14 2L22 10L18 14Z",
  phone: "M22 16.9V20A2 2 0 0 1 19.8 22A19.8 19.8 0 0 1 2 4.2A2 2 0 0 1 4 2H7.1A2 2 0 0 1 9.1 3.7L9.8 7.1A2 2 0 0 1 9.2 9L7.6 10.6A16 16 0 0 0 13.4 16.4L15 14.8A2 2 0 0 1 16.9 14.2L20.3 14.9A2 2 0 0 1 22 16.9Z",
  rupee: "M6 4H18 M6 9H18 M15 21L7 13H9.5C12.5 13 14.5 11 14.5 8.5C14.5 6 12.5 4 9.5 4",
  arrowRight: "M5 12H19 M13 6L19 12L13 18",
  logout: "M9 21H5A2 2 0 0 1 3 19V5A2 2 0 0 1 5 3H9 M16 17L21 12L16 7 M21 12H9",
  edit: "M4 20H8L19 9L15 5L4 16Z M13 7L17 11",
  info: "M21 12A9 9 0 1 1 3 12A9 9 0 1 1 21 12Z M12 11V16 M12 8H12.01",
  refresh: "M20 11A8 8 0 1 0 18 17 M20 4V11H13",
  book: "M4 4H10A3 3 0 0 1 13 7V20A2 2 0 0 0 11 18H4Z M20 4H16A3 3 0 0 0 13 7V20A2 2 0 0 1 15 18H20Z",
  store: "M3 9L5 4H19L21 9 M3 9H21V11A3 3 0 0 1 15 11A3 3 0 0 1 9 11A3 3 0 0 1 3 11Z M5 12V20H19V12",
  cook: "M6 13H18V20H6Z M4 13H20 M8 13C8 9 9.5 7 12 7C14.5 7 16 9 16 13 M12 4V7",
  star: "M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26Z",
} as const;

export type IconName = keyof typeof PATHS;

export function Icon({
  name,
  size = 24,
  color = "currentColor",
  filled = false,
  strokeWidth = 2,
  className,
  title,
}: {
  name: IconName;
  size?: number;
  color?: string;
  filled?: boolean;
  strokeWidth?: number;
  className?: string;
  title?: string;
}) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill={filled ? color : "none"}
      stroke={filled ? "none" : color}
      strokeWidth={strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      role={title ? "img" : undefined}
      aria-hidden={title ? undefined : true}
      aria-label={title}
    >
      <path d={PATHS[name]} />
    </svg>
  );
}

/** Raw SVG markup, for Leaflet divIcons (outside React). */
export function iconSvg(name: IconName, size = 24, color = "#3B5BDB") {
  return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="${color}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="${PATHS[name]}"/></svg>`;
}
