import { describe, expect, it } from "vitest";
import { validateRegister } from "../pages/Register";
import { validateStep, toRequest, type GigForm } from "../pages/PostGig";
import { applyState } from "../pages/GigDetail";
import { activeFilterCount, EMPTY_FILTERS } from "../pages/Explore";
import { levelProgress, payText, missingLabel } from "../lib/format";
import { artFor } from "../lib/art";
import { clampToIndia } from "../map/leaflet";
import { nearestPins } from "../map/GigMap";
import { jobFor } from "../lib/reviewJobs";
import type { GigDetail, Review } from "../api/types";

const goodReg = {
  name: "Arjun Patil", email: "arjun@test.example", phone: "9876543210", password: "secret123", dateOfBirth: "1998-05-12",
  gender: "", addressLine: "B-12 Shanti Nagar", city: "Pune", state: "Maharashtra", pincode: "411038",
};

describe("register rules", () => {
  it("accepts a valid form", () => expect(validateRegister(goodReg)).toEqual({}));
  it("rejects phones not starting 6-9 and short pincodes", () => {
    const e = validateRegister({ ...goodReg, phone: "5876543210", pincode: "4110" });
    expect(e.phone).toBeTruthy();
    expect(e.pincode).toBeTruthy();
  });
  it("needs a letter and a digit in the password", () => {
    expect(validateRegister({ ...goodReg, password: "abcdefgh" }).password).toBeTruthy();
    expect(validateRegister({ ...goodReg, password: "12345678" }).password).toBeTruthy();
  });
  it("blocks under-18s", () => {
    const y = new Date().getFullYear() - 17;
    expect(validateRegister({ ...goodReg, dateOfBirth: `${y}-01-01` }).dateOfBirth).toBeTruthy();
  });
});

const form: GigForm = {
  title: "Paint a flat", description: "Two rooms", categoryId: 1, payType: "DAILY", payAmount: "1800", workersNeeded: 2,
  startDate: "2099-01-10", durationText: "2 days", applyDeadline: "", minExperience: "", minLevel: "", skillIds: [14],
  lat: 18.5074123456, lng: 73.8077654321, addressLine: "Flat 302", locality: "Kothrud", city: "Pune",
};

describe("post gig steps", () => {
  it("requires a confirmed pin", () => {
    expect(validateStep(3, form, false).location).toMatch(/Confirm/);
    expect(validateStep(3, form, true)).toEqual({});
    expect(validateStep(3, { ...form, lat: null, lng: null }, false).location).toMatch(/pin/);
  });
  it("checks the apply deadline is not after the start date", () => {
    expect(validateStep(2, { ...form, applyDeadline: "2099-02-01" }, true).applyDeadline).toBeTruthy();
  });
  it("rejects past start dates and >2 decimals", () => {
    expect(validateStep(2, { ...form, startDate: "2000-01-01" }, true).startDate).toBeTruthy();
    expect(validateStep(1, { ...form, payAmount: "10.555" }, true).payAmount).toBeTruthy();
  });
  it("rounds coordinates to 6 decimals and sends named lat/lng", () => {
    const r = toRequest(form);
    expect(r.lat).toBe(18.507412);
    expect(r.lng).toBe(73.807765);
    expect(r.applyDeadline).toBeNull();
    expect(r.categoryId).toBe(1);
  });
});

const gig = { id: "g1", mine: false, status: "OPEN", openingsLeft: 2, applyDeadline: null } as unknown as GigDetail;
const base = { mode: "WORKER", appliedIds: new Set<string>(), profile: null };

describe("apply button state", () => {
  it("covers the matrix", () => {
    expect(applyState(gig, base).kind).toBe("can");
    expect(applyState({ ...gig, mine: true }, base).kind).toBe("mine");
    expect(applyState(gig, { ...base, appliedIds: new Set(["g1"]) }).kind).toBe("applied");
    expect(applyState({ ...gig, openingsLeft: 0 }, base).kind).toBe("closed");
    expect(applyState(gig, { ...base, mode: "PROVIDER" }).kind).toBe("wrongMode");
    expect(applyState(gig, { ...base, profile: { profileComplete: false, missing: ["photoUrl"] } as never }).kind).toBe("incomplete");
  });
});

describe("misc", () => {
  it("counts active filters", () => {
    expect(activeFilterCount(EMPTY_FILTERS)).toBe(0);
    expect(activeFilterCount({ ...EMPTY_FILTERS, categoryId: 3, payType: "DAILY" })).toBe(2);
  });
  it("computes level progress with backend thresholds", () => {
    expect(levelProgress(140)).toMatchObject({ current: { name: "Trusted" }, next: { name: "Skilled", xp: 200 }, pct: 57 });
    expect(levelProgress(950).next).toBeNull();
  });
  it("formats pay and missing labels", () => {
    expect(payText(1800, "DAILY")).toBe("₹1,800 / day");
    expect(missingLabel("photoUrl")).toMatch(/photo/i);
  });
  it("picks art from title first, then category", () => {
    expect(artFor("Paint a 2BHK flat", "Household")).toBe("/art/painting.png");
    expect(artFor("Help needed", "Cleaning")).toBe("/art/cleaning.png");
    expect(artFor("Help at a wedding", "Events")).toBeNull();
  });
  it("finds the nearest gigs for the empty-map zoom", () => {
    const pins = [
      { id: "far", lat: 25.57, lng: 91.88 },
      { id: "near", lat: 26.18, lng: 91.75 },
      { id: "mid", lat: 26.4, lng: 90.27 },
    ] as never;
    expect(nearestPins(pins, [26.17, 91.76], 2).map((p) => p.id)).toEqual(["near", "far"]);
  });
  it("names the job a review is about", () => {
    const r = { assignmentId: "a1" } as never as Review;
    const lookup = new Map([["a1", { id: "g9", title: "Wedding helpers needed" }]]);
    expect(jobFor(r, lookup)?.title).toBe("Wedding helpers needed");
    expect(jobFor(r, null)).toBeNull();
    expect(jobFor({ ...r, gig: { id: "g1", title: "From API" } }, lookup)?.title).toBe("From API");
  });
  it("never sends a box outside India", () => {
    expect(clampToIndia({ minLat: -10, minLng: 50, maxLat: 60, maxLng: 120 })).toEqual({ minLat: 6, minLng: 68, maxLat: 38, maxLng: 98 });
  });
});
