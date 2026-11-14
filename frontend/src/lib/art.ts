import type { IconName } from "../components/Icon";

// The API has no gig photos yet, so cards and map pins use our illustrations,
// picked from the gig title first and the category second. When nothing fits,
// the pin falls back to the category icon (the "no photo" pin from the design).

const ART_BY_KEYWORD: [RegExp, string][] = [
  [/paint|putty|whitewash/i, "painting"],
  [/plumb|pipe|tap|leak|sink|bathroom/i, "plumbing"],
  [/electric|wiring|light|fan|switch|ac\b/i, "electric"],
  [/clean|sweep|mop|wash|dust/i, "cleaning"],
  [/garden|plant|lawn|tree/i, "gardening"],
  [/shift|move|moving|load|pack|lift|carry/i, "moving"],
  [/tutor|teach|class|maths|english|homework/i, "tutor"],
  [/brick|mason|construct|tile|tiling|carpent|repair|weld|cement/i, "construction"],
];

const ART_BY_CATEGORY: Record<string, string> = {
  "Cleaning": "cleaning",
  "Construction & Repair": "construction",
  "Moving & Loading": "moving",
  "Teaching & Tutoring": "tutor",
};

export function artFor(title?: string, categoryName?: string): string | null {
  const t = title ?? "";
  for (const [re, art] of ART_BY_KEYWORD) if (re.test(t)) return `/art/${art}.png`;
  if (categoryName && ART_BY_CATEGORY[categoryName]) return `/art/${ART_BY_CATEGORY[categoryName]}.png`;
  return null;
}

const ICON_BY_CATEGORY: Record<string, IconName> = {
  "Household": "home",
  "Cooking & Catering": "cook",
  "Delivery & Driving": "truck",
  "Construction & Repair": "hammer",
  "Cleaning": "sparkles",
  "Moving & Loading": "truck",
  "Events": "calendar",
  "Care (child/elderly)": "heart",
  "Teaching & Tutoring": "book",
  "Shop & Retail": "store",
  "Office & Data": "list",
  "Other": "briefcase",
};
export const iconForCategory = (name?: string): IconName => (name && ICON_BY_CATEGORY[name]) || "briefcase";
