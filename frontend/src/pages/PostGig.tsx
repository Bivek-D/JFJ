import { lazy, Suspense, useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Shell } from "../components/Shell";
import { Icon } from "../components/Icon";
import { ErrorBox, Field, Note, Skeleton, useToast } from "../components/ui";
import { GigThumb } from "../components/GigCard";
import * as ep from "../api/endpoints";
import { ApiError, errorMessage } from "../api/client";
import type { Address, Category, ExperienceLevel, GigRequest, OwnerGig, PayType, Skill } from "../api/types";
import { cached } from "../lib/useAsync";
import { experienceName, LEVELS, payTypeName, rupees, shortDate, todayIST } from "../lib/format";
import type { PickedPoint } from "../map/LocationPicker";

const LocationPicker = lazy(() => import("../map/LocationPicker"));

const STEPS = ["Basics", "Pay and workers", "Schedule", "Location", "Requirements", "Review and publish"] as const;
const DURATIONS = ["1 day", "2 days", "3 days", "1 week", "2 weeks"];

export interface GigForm {
  title: string; description: string; categoryId: number | ""; payType: PayType; payAmount: string; workersNeeded: number;
  startDate: string; durationText: string; applyDeadline: string; minExperience: ExperienceLevel | ""; minLevel: number | "";
  skillIds: number[]; lat: number | null; lng: number | null; addressLine: string; locality: string; city: string;
}
const EMPTY: GigForm = {
  title: "", description: "", categoryId: "", payType: "DAILY", payAmount: "", workersNeeded: 1, startDate: "", durationText: "",
  applyDeadline: "", minExperience: "", minLevel: "", skillIds: [], lat: null, lng: null, addressLine: "", locality: "", city: "",
};

/** Per-step checks that mirror the backend rules (guide §9). */
export function validateStep(step: number, f: GigForm, locationConfirmed: boolean): Record<string, string> {
  const e: Record<string, string> = {};
  if (step === 0) {
    if (!f.title.trim()) e.title = "Give your gig a title.";
    else if (f.title.length > 100) e.title = "Keep the title under 100 characters.";
    if (f.categoryId === "") e.categoryId = "Pick a category.";
    if (!f.description.trim()) e.description = "Describe the work.";
    else if (f.description.length > 2000) e.description = "Keep it under 2000 characters.";
  }
  if (step === 1) {
    const amt = Number(f.payAmount);
    if (!f.payAmount || !(amt > 0)) e.payAmount = "Enter the pay amount.";
    else if (!/^\d+(\.\d{1,2})?$/.test(f.payAmount)) e.payAmount = "Use at most 2 decimals.";
    if (!(f.workersNeeded >= 1 && f.workersNeeded <= 50)) e.workersNeeded = "Between 1 and 50 workers.";
  }
  if (step === 2) {
    const today = todayIST();
    if (!f.startDate) e.startDate = "Pick a start date.";
    else if (f.startDate < today) e.startDate = "The start date can't be in the past.";
    if (!f.durationText.trim()) e.durationText = "How long will it take? For example, 2 days.";
    if (f.applyDeadline && f.startDate && f.applyDeadline > f.startDate) e.applyDeadline = "The last day to apply must be on or before the start date.";
  }
  if (step === 3) {
    if (f.lat == null || f.lng == null) e.location = "Drop the pin on the exact spot.";
    else if (!locationConfirmed) e.location = "Press Confirm location to continue.";
    if (!f.addressLine.trim()) e.addressLine = "Enter the house, flat or building.";
    if (!f.locality.trim()) e.locality = "Enter the locality or area.";
    if (!f.city.trim()) e.city = "Enter the city.";
  }
  return e;
}

export function toRequest(f: GigForm): GigRequest {
  return {
    title: f.title.trim(), description: f.description.trim(), categoryId: Number(f.categoryId), payType: f.payType,
    payAmount: Number(f.payAmount), workersNeeded: f.workersNeeded, startDate: f.startDate, durationText: f.durationText.trim(),
    applyDeadline: f.applyDeadline || null, minExperience: f.minExperience || null, minLevel: f.minLevel === "" ? null : f.minLevel,
    skillIds: f.skillIds, lat: Number((f.lat ?? 0).toFixed(6)), lng: Number((f.lng ?? 0).toFixed(6)),
    addressLine: f.addressLine.trim(), locality: f.locality.trim(), city: f.city.trim(),
  };
}

function fromOwnerGig(g: OwnerGig): GigForm {
  return {
    title: g.title, description: g.description, categoryId: g.category?.id ?? "", payType: g.payType, payAmount: String(g.payAmount),
    workersNeeded: g.workersNeeded, startDate: g.startDate, durationText: g.durationText, applyDeadline: g.applyDeadline ?? "",
    minExperience: g.minExperience ?? "", minLevel: g.minLevel ?? "", skillIds: (g.skills ?? []).map((s) => s.id),
    lat: g.lat, lng: g.lng, addressLine: g.addressLine ?? "", locality: g.locality ?? "", city: g.city ?? "",
  };
}

export default function PostGig() {
  const { id } = useParams();
  const editing = !!id;
  const navigate = useNavigate();
  const toast = useToast();
  const [step, setStep] = useState(0);
  const [f, setF] = useState<GigForm>(EMPTY);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [categories, setCategories] = useState<Category[]>([]);
  const [skills, setSkills] = useState<Skill[]>([]);
  const [existing, setExisting] = useState<OwnerGig | null>(null);
  const [loadError, setLoadError] = useState<unknown>(null);
  const [confirmed, setConfirmed] = useState(false);
  const [touched, setTouched] = useState<Record<string, boolean>>({});
  const [suggested, setSuggested] = useState<Address | null>(null);
  const [busy, setBusy] = useState<"" | "draft" | "publish">("");
  const [formError, setFormError] = useState("");

  useEffect(() => {
    cached("categories", ep.getCategories).then(setCategories).catch(setLoadError);
    cached("skills", ep.getSkills).then(setSkills).catch(() => {});
  }, []);

  // Edit: the owner view (with locationEditsLeft) comes from /gigs/mine.
  useEffect(() => {
    if (!id) return;
    (async () => {
      try {
        for (let page = 0; page < 10; page++) {
          const p = await ep.myGigs(undefined, page, 50);
          const g = p.items.find((x) => x.id === id);
          if (g) {
            setExisting(g);
            setF(fromOwnerGig(g));
            setConfirmed(true);
            return;
          }
          if (page + 1 >= p.totalPages) break;
        }
        setLoadError(new ApiError(404, "We couldn't find that gig in your gigs."));
      } catch (e) {
        setLoadError(e);
      }
    })();
  }, [id]);

  const set = (patch: Partial<GigForm>) => setF((x) => ({ ...x, ...patch }));
  const locked = editing && existing ? existing.locationEditsLeft <= 0 : false;
  const editBlocked = editing && existing && existing.status !== "DRAFT" && existing.status !== "OPEN";

  function onPick(p: PickedPoint, addr: Address | null) {
    if (locked) return;
    set({ lat: p.lat, lng: p.lng });
    setConfirmed(false);
    if (!addr) return;
    // never overwrite what the user typed by hand without asking
    const next = { addressLine: addr.addressLine || addr.label || "", locality: addr.locality || "", city: addr.city || "" };
    const conflicts = (Object.keys(next) as (keyof typeof next)[]).some((k) => touched[k] && f[k] && next[k] && f[k] !== next[k]);
    if (conflicts) setSuggested(addr);
    else {
      setF((x) => ({
        ...x,
        addressLine: touched.addressLine && x.addressLine ? x.addressLine : next.addressLine || x.addressLine,
        locality: touched.locality && x.locality ? x.locality : next.locality || x.locality,
        city: touched.city && x.city ? x.city : next.city || x.city,
      }));
      setSuggested(null);
    }
  }

  function next() {
    const e = validateStep(step, f, confirmed);
    setErrors(e);
    if (Object.keys(e).length) return;
    setStep((s) => Math.min(STEPS.length - 1, s + 1));
    window.scrollTo(0, 0);
  }
  function back() {
    setErrors({});
    if (step === 0) navigate(-1);
    else setStep((s) => s - 1);
  }

  async function save(publish: boolean) {
    for (let s = 0; s < 4; s++) {
      const e = validateStep(s, f, confirmed);
      if (Object.keys(e).length) {
        setErrors(e);
        setStep(s);
        return;
      }
    }
    setBusy(publish ? "publish" : "draft");
    setFormError("");
    try {
      const body = toRequest(f);
      let g = editing && existing ? await ep.updateGig(existing.id, body) : await ep.createGig(body);
      if (publish && g.status === "DRAFT") {
        try {
          g = await ep.publishGig(g.id);
        } catch (err) {
          setFormError(`Saved as a draft, but publishing failed: ${errorMessage(err)}`);
          setExisting(g);
          return;
        }
      }
      toast({ text: publish ? "Your gig is live. Workers nearby can see it now." : editing ? "Changes saved." : "Draft saved." });
      navigate(`/gigs/${g.id}`);
    } catch (err) {
      if (err instanceof ApiError && Object.keys(err.fieldErrors).length) {
        setErrors(err.fieldErrors);
        setFormError(err.message || "Some details need fixing.");
      } else setFormError(errorMessage(err));
    } finally {
      setBusy("");
    }
  }

  const catName = categories.find((c) => c.id === f.categoryId)?.name;
  const total = useMemo(() => {
    const amt = Number(f.payAmount) || 0;
    const days = Number((f.durationText.match(/\d+/) ?? ["1"])[0]) * (/week/i.test(f.durationText) ? 7 : 1);
    return f.payType === "DAILY" ? amt * f.workersNeeded * days : f.payType === "FIXED" ? amt * f.workersNeeded : null;
  }, [f.payAmount, f.workersNeeded, f.durationText, f.payType]);

  if (loadError) return <Shell bottomNav={false}><div className="container"><ErrorBox error={loadError} /></div></Shell>;
  if (editing && !existing) return <Shell bottomNav={false}><div className="container"><Skeleton h={500} /></div></Shell>;

  const heading: Record<number, [string, string]> = {
    0: ["What do you need done?", "Give your gig a clear title and pick a category. Good details get better applicants."],
    1: ["Pay and workers", "Choose how you'll pay and how many people you need. Workers see the pay before they apply."],
    2: ["When should it happen?", "All dates are in India Standard Time (IST)."],
    3: ["Where is the work?", "Search for the place or drop the pin on the exact spot. Workers only see an approximate area until you hire them."],
    4: ["Who are you looking for?", "Set the skills and experience you need. Stricter rules mean fewer applicants."],
    5: ["Review and publish", "Check everything once. You can edit the gig until you hire someone."],
  };

  const stepBody = (() => {
    switch (step) {
      case 0:
        return (
          <>
            <Field label="Gig title" error={errors.title} hint={`${f.title.length} / 100`}>
              <input className="input" maxLength={100} value={f.title} onChange={(e) => set({ title: e.target.value })} placeholder="Paint a 2BHK flat" />
            </Field>
            <div className="field">
              <span className="field-label">Category</span>
              <div className="row-wrap">
                {categories.map((c) => (
                  <button key={c.id} type="button" className={`chip ${f.categoryId === c.id ? "active" : ""}`} onClick={() => set({ categoryId: c.id })}>{c.name}</button>
                ))}
              </div>
              {errors.categoryId && <span className="field-error">{errors.categoryId}</span>}
            </div>
            <Field label="Describe the work" error={errors.description} hint="Mention the size of the job, what you'll provide, and any timing needs.">
              <textarea className="textarea" maxLength={2000} value={f.description} onChange={(e) => set({ description: e.target.value })} placeholder="We need 2 painters for a 2BHK flat. Paint and ladders are provided." />
            </Field>
          </>
        );
      case 1:
        return (
          <>
            <div className="field">
              <span className="field-label">Pay type</span>
              <div className="options">
                {(["DAILY", "FIXED", "HOURLY"] as PayType[]).map((p) => (
                  <button key={p} type="button" className={`option ${f.payType === p ? "selected" : ""}`} onClick={() => set({ payType: p })}>
                    {payTypeName[p]}
                    <span className="sub">{p === "DAILY" ? "Best for work over several days" : p === "FIXED" ? "One price for the whole job" : "For short or uncertain tasks"}</span>
                  </button>
                ))}
              </div>
            </div>
            <div className="grid-2">
              <Field label={`Pay per worker${f.payType === "DAILY" ? ", per day" : f.payType === "HOURLY" ? ", per hour" : ""}`} error={errors.payAmount}>
                <div className="input-wrap"><span className="lead">₹</span><input className="input" inputMode="decimal" value={f.payAmount} onChange={(e) => set({ payAmount: e.target.value.replace(/[^\d.]/g, "") })} placeholder="1800" /></div>
              </Field>
              <div className="field">
                <span className="field-label">Workers needed</span>
                <div className="row between" style={{ height: 48, border: "1px solid var(--line-strong)", borderRadius: 10, padding: "0 6px" }}>
                  <button type="button" className="icon-btn" style={{ width: 36, height: 36, borderRadius: 8 }} onClick={() => set({ workersNeeded: Math.max(1, f.workersNeeded - 1) })} aria-label="Fewer workers"><Icon name="minus" size={20} /></button>
                  <span className="strong">{f.workersNeeded} {f.workersNeeded === 1 ? "worker" : "workers"}</span>
                  <button type="button" className="icon-btn" style={{ width: 36, height: 36, borderRadius: 8, background: "var(--primary-50)" }} onClick={() => set({ workersNeeded: Math.min(50, f.workersNeeded + 1) })} aria-label="More workers"><Icon name="plus" size={20} color="#3B5BDB" /></button>
                </div>
                {errors.workersNeeded && <span className="field-error">{errors.workersNeeded}</span>}
              </div>
            </div>
            {total != null && total > 0 && (
              <div className="soft row between" style={{ padding: "16px 18px" }}>
                <span className="small strong body2">Estimated total · {f.workersNeeded} {f.workersNeeded === 1 ? "worker" : "workers"}{f.payType === "DAILY" && f.durationText ? ` × ${f.durationText}` : ""}</span>
                <span className="bold" style={{ fontSize: 20 }}>{rupees(total)}</span>
              </div>
            )}
          </>
        );
      case 2:
        return (
          <>
            <div className="grid-2">
              <Field label="Start date" error={errors.startDate}>
                <input className="input" type="date" min={todayIST()} value={f.startDate} onChange={(e) => set({ startDate: e.target.value })} />
              </Field>
              <Field label="Last day to apply (optional)" error={errors.applyDeadline} hint="On or before the start date.">
                <input className="input" type="date" min={todayIST()} max={f.startDate || undefined} value={f.applyDeadline} onChange={(e) => set({ applyDeadline: e.target.value })} />
              </Field>
            </div>
            <Field label="How long will it take?" error={errors.durationText}>
              <input className="input" value={f.durationText} onChange={(e) => set({ durationText: e.target.value })} placeholder="2 days" />
            </Field>
            <div className="row-wrap" style={{ marginTop: -8 }}>
              {DURATIONS.map((d) => <button key={d} type="button" className={`chip ${f.durationText === d ? "active" : ""}`} onClick={() => set({ durationText: d })}>{d}</button>)}
            </div>
            <Note tone="grey" icon="clock">Your gig closes on its own after the last day to apply, or when all spots are filled.</Note>
          </>
        );
      case 3:
        return (
          <>
            {editing && existing && (
              locked ? <Note tone="amber" icon="pin">This gig's location has been moved 3 times, so the pin is locked now.</Note>
                : <Note tone="info" icon="pin">You can move this gig's location {existing.locationEditsLeft} more {existing.locationEditsLeft === 1 ? "time" : "times"}.</Note>
            )}
            <Suspense fallback={<Skeleton h={430} />}>
              <LocationPicker pin={f.lat != null && f.lng != null ? { lat: f.lat, lng: f.lng } : null} locked={locked} onPick={onPick} />
            </Suspense>
            {errors.location && <span className="field-error">{errors.location}</span>}
            {suggested && (
              <Note tone="info" icon="pin">
                We found "{suggested.addressLine || suggested.label}". You've typed your own address, so we kept it.{" "}
                <button className="link-btn" onClick={() => { set({ addressLine: suggested.addressLine || suggested.label, locality: suggested.locality, city: suggested.city }); setSuggested(null); }}>Use the suggested address</button>
              </Note>
            )}
            <div className="grid-2">
              <Field label="Locality" error={errors.locality}><input className="input" value={f.locality} onChange={(e) => { set({ locality: e.target.value }); setTouched({ ...touched, locality: true }); }} /></Field>
              <Field label="City" error={errors.city}><input className="input" value={f.city} onChange={(e) => { set({ city: e.target.value }); setTouched({ ...touched, city: true }); }} /></Field>
            </div>
            <Field label="Address line (house, flat or building)" error={errors.addressLine} hint="Only hired workers see this.">
              <input className="input" value={f.addressLine} onChange={(e) => { set({ addressLine: e.target.value }); setTouched({ ...touched, addressLine: true }); }} />
            </Field>
            <Note tone="amber" icon="pin">In smaller towns, search may not find every street. The pin is what counts, so place it carefully.</Note>
            {f.lat != null && !locked && (
              confirmed ? (
                <Note tone="mint" icon="check">Location confirmed. Moving the pin will ask you to confirm again.</Note>
              ) : (
                <button type="button" className="btn btn-soft btn-lg" onClick={() => { setConfirmed(true); setErrors({}); }}>
                  <Icon name="check" size={18} />Confirm location
                </button>
              )
            )}
          </>
        );
      case 4:
        return (
          <>
            <div className="field">
              <div className="row between"><span className="field-label">Skills needed (optional)</span><span className="xs muted">{f.skillIds.length} of 10</span></div>
              <div className="row-wrap">
                {skills.map((s) => {
                  const on = f.skillIds.includes(s.id);
                  return (
                    <button key={s.id} type="button" className={`chip ${on ? "active" : ""}`} aria-pressed={on} disabled={!on && f.skillIds.length >= 10}
                      onClick={() => set({ skillIds: on ? f.skillIds.filter((x) => x !== s.id) : [...f.skillIds, s.id] })}>
                      {s.name}{on && <Icon name="check" size={14} color="#fff" />}
                    </button>
                  );
                })}
              </div>
            </div>
            <div className="field">
              <span className="field-label">Minimum experience</span>
              <div className="options">
                {(["", "BEGINNER", "INTERMEDIATE", "EXPERIENCED"] as const).map((x) => (
                  <button key={x || "any"} type="button" className={`option center ${f.minExperience === x ? "selected" : ""}`} onClick={() => set({ minExperience: x })}>
                    {x ? experienceName[x] : "Any"}
                  </button>
                ))}
              </div>
            </div>
            <div className="field">
              <span className="field-label">Minimum level on JFJ</span>
              <div className="options">
                <button type="button" className={`option center ${f.minLevel === "" ? "selected" : ""}`} onClick={() => set({ minLevel: "" })}>Any</button>
                {LEVELS.slice(1, 4).map((l) => (
                  <button key={l.level} type="button" className={`option center ${f.minLevel === l.level ? "selected" : ""}`} onClick={() => set({ minLevel: l.level })}>
                    {l.name}+<span className="sub">Level {l.level}</span>
                  </button>
                ))}
              </div>
            </div>
          </>
        );
      default: {
        const row = (label: string, value: string, to: number) => (
          <div className="row" style={{ padding: "14px 16px", borderTop: to ? "1px solid var(--line)" : undefined }}>
            <div className="grow"><div className="xs muted">{label}</div><div className="strong">{value}</div></div>
            <button className="link-btn" onClick={() => setStep(to)}>Edit</button>
          </div>
        );
        return (
          <div className="grid-2" style={{ gridTemplateColumns: "minmax(0,1fr) 300px", alignItems: "start" }}>
            <div className="card" style={{ padding: 0 }}>
              {row("Basics", `${f.title} · ${catName ?? ""}`, 0)}
              {row("Pay and workers", `${rupees(Number(f.payAmount))} ${payTypeName[f.payType].toLowerCase()} · ${f.workersNeeded} ${f.workersNeeded === 1 ? "worker" : "workers"}`, 1)}
              {row("Schedule", `${shortDate(f.startDate)} · ${f.durationText}${f.applyDeadline ? ` · apply by ${shortDate(f.applyDeadline)}` : ""}`, 2)}
              {row("Location", [f.addressLine, f.locality, f.city].filter(Boolean).join(", "), 3)}
              {row("Requirements", [f.skillIds.length ? `${f.skillIds.length} skills` : "No skills set", f.minExperience ? experienceName[f.minExperience] + "+" : "Any experience", f.minLevel ? `Level ${f.minLevel}+` : ""].filter(Boolean).join(" · "), 4)}
            </div>
            <div className="soft stack-sm" style={{ padding: 16 }}>
              <span className="xs strong body2">How workers will see it</span>
              <div className="gig-card" style={{ cursor: "default" }}>
                <GigThumb title={f.title} category={catName} size={72} />
                <div className="gig-info">
                  <span className="gig-cat">{catName} · near you</span>
                  <span className="gig-title">{f.title}</span>
                  <span className="gig-pay">{rupees(Number(f.payAmount))} {f.payType === "DAILY" ? "/ day" : f.payType === "HOURLY" ? "/ hour" : "fixed"}</span>
                </div>
              </div>
              <span className="xs muted">Workers see an approximate area on the map. Only people you hire see the exact address.</span>
            </div>
          </div>
        );
      }
    }
  })();

  const footer = (
    <div className="row" style={{ paddingTop: 8 }}>
      <button className="btn btn-secondary" onClick={back}>{step === 0 ? "Cancel" : "Back"}</button>
      <div className="grow" />
      {step === STEPS.length - 1 ? (
        <>
          {(!editing || existing?.status === "DRAFT") && <button className="btn btn-soft" disabled={!!busy} onClick={() => save(false)}>{busy === "draft" ? "Saving…" : "Save draft"}</button>}
          <button className="btn btn-primary" disabled={!!busy} onClick={() => save(!editing || existing?.status === "DRAFT")}>
            {busy === "publish" ? "Publishing…" : editing && existing?.status !== "DRAFT" ? "Save changes" : "Publish gig"}
          </button>
        </>
      ) : (
        <button className="btn btn-primary" onClick={next}>Continue</button>
      )}
    </div>
  );

  return (
    <Shell bottomNav={false}>
      <div className="mobile-top" style={{ flexDirection: "column", alignItems: "stretch", padding: 0, gap: 0 }}>
        <div className="row" style={{ padding: "10px 16px 10px 8px" }}>
          <button className="icon-btn" style={{ background: "none" }} onClick={back} aria-label="Back"><Icon name="chevronLeft" /></button>
          <div className="grow">
            <div className="h3">{heading[step][0]}</div>
            <div className="xs muted">Step {step + 1} of {STEPS.length} · {STEPS[step]}</div>
          </div>
        </div>
        <div className="progress-strip"><div style={{ width: `${((step + 1) / STEPS.length) * 100}%` }} /></div>
      </div>
      <div className="container" style={{ maxWidth: 1200 }}>
        <div className="cols" style={{ gridTemplateColumns: "290px minmax(0,1fr)" }}>
          <nav className="card stack hide-mobile" style={{ padding: 20, gap: 4, position: "sticky", top: 88 }} aria-label="Steps">
            <div className="h2" style={{ fontSize: 20 }}>{editing ? "Edit gig" : "Post a gig"}</div>
            <div className="small muted" style={{ marginBottom: 12 }}>Step {step + 1} of {STEPS.length}</div>
            {STEPS.map((s, i) => (
              <button key={s} className={`stepper-row ${i === step ? "current" : i < step ? "done" : ""}`} onClick={() => i < step && setStep(i)} disabled={i > step} aria-current={i === step ? "step" : undefined}>
                <span className="step-dot">{i < step ? <Icon name="check" size={16} color="#fff" /> : i + 1}</span>
                {s}
              </button>
            ))}
          </nav>
          <div className="card stack-lg" style={{ padding: 28 }}>
            <div className="stack-sm hide-mobile">
              <h1 className="h1" style={{ fontSize: 26 }}>{heading[step][0]}</h1>
              <p className="body2">{heading[step][1]}</p>
            </div>
            <p className="body2 hide-desktop">{heading[step][1]}</p>
            {editBlocked && <Note tone="amber">This gig can't be edited any more because it has started or ended.</Note>}
            {stepBody}
            {formError && <Note tone="red">{formError}</Note>}
            {footer}
          </div>
        </div>
      </div>
    </Shell>
  );
}
