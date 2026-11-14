import { useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { MobileTop, Shell } from "../components/Shell";
import { Icon } from "../components/Icon";
import { Avatar, ErrorBox, Field, Note, Skeleton, useToast } from "../components/ui";
import * as ep from "../api/endpoints";
import { ApiError, errorMessage } from "../api/client";
import type { ExperienceLevel, Skill, WorkerProfile } from "../api/types";
import { cached } from "../lib/useAsync";
import { missingLabel } from "../lib/format";
import { useAuth } from "../auth/AuthContext";

const SUGGESTED_LANGS = ["Hindi", "English", "Marathi", "Bengali", "Tamil", "Telugu", "Kannada", "Gujarati", "Assamese"];
const LEVELS: ExperienceLevel[] = ["BEGINNER", "INTERMEDIATE", "EXPERIENCED"];

export default function WorkerProfileEdit() {
  const navigate = useNavigate();
  const toast = useToast();
  const { mode, switchMode } = useAuth();
  const welcome = (useLocation().state as { welcome?: boolean } | null)?.welcome;
  const [profile, setProfile] = useState<WorkerProfile | null>(null);
  const [skills, setSkills] = useState<Skill[]>([]);
  const [loadError, setLoadError] = useState<unknown>(null);
  const [f, setF] = useState({ photoUrl: "", description: "", languages: [] as string[], experienceYears: "", experienceLevel: "" as ExperienceLevel | "", skillIds: [] as number[] });
  const [newLang, setNewLang] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  const load = () => {
    setLoadError(null);
    Promise.all([ep.getMyWorkerProfile(), cached("skills", ep.getSkills)])
      .then(([p, s]) => {
        setProfile(p);
        setSkills(s);
        setF({
          photoUrl: p.photoUrl ?? "", description: p.description ?? "", languages: p.languages ?? [],
          experienceYears: p.experienceYears != null ? String(p.experienceYears) : "", experienceLevel: p.experienceLevel ?? "",
          skillIds: (p.skills ?? []).map((x) => x.id),
        });
      })
      .catch(setLoadError);
  };
  useEffect(load, []);

  const toggleSkill = (id: number) =>
    setF((x) => ({ ...x, skillIds: x.skillIds.includes(id) ? x.skillIds.filter((i) => i !== id) : x.skillIds.length >= 10 ? x.skillIds : [...x.skillIds, id] }));
  const addLang = (l: string) => {
    const v = l.trim();
    if (!v || f.languages.length >= 5 || f.languages.some((x) => x.toLowerCase() === v.toLowerCase())) return;
    setF({ ...f, languages: [...f.languages, v] });
    setNewLang("");
  };

  function validate() {
    const e: Record<string, string> = {};
    if (f.photoUrl.trim() && !/^https:\/\/\S+$/i.test(f.photoUrl.trim())) e.photoUrl = "Use a link that starts with https://";
    if (f.description.length > 1000) e.description = "Keep it under 1000 characters.";
    const y = Number(f.experienceYears);
    if (f.experienceYears === "" || !Number.isInteger(y) || y < 0 || y > 60) e.experienceYears = "Enter a number from 0 to 60.";
    if (!f.experienceLevel) e.experienceLevel = "Choose your experience level.";
    return e;
  }

  async function save() {
    const e = validate();
    setErrors(e);
    if (Object.keys(e).length) return;
    setBusy(true);
    try {
      // Saving a worker profile only works in Worker mode.
      if (mode !== "WORKER") await switchMode("WORKER");
      // PUT replaces everything, so we send every field.
      await ep.saveWorkerProfile({
        photoUrl: f.photoUrl.trim() || null, description: f.description.trim() || null, languages: f.languages,
        experienceYears: Number(f.experienceYears), experienceLevel: f.experienceLevel as ExperienceLevel,
      });
      const p = await ep.saveWorkerSkills(f.skillIds);
      setProfile(p);
      toast({ text: p.profileComplete ? "Profile saved. You can apply for gigs." : "Profile saved." });
      if (p.profileComplete) navigate("/");
    } catch (err) {
      if (err instanceof ApiError) setErrors(err.fieldErrors);
      toast({ text: errorMessage(err), error: true });
    } finally {
      setBusy(false);
    }
  }

  if (loadError) return <Shell><MobileTop title="Your worker profile" /><div className="container"><ErrorBox error={loadError} onRetry={load} /></div></Shell>;

  // live checklist (what the backend asks for: a photo, a skill and a language)
  const checks = [
    { label: "At least one skill", ok: f.skillIds.length > 0 },
    { label: "At least one language", ok: f.languages.length > 0 },
    { label: "A profile photo link", ok: /^https:\/\/\S+$/i.test(f.photoUrl.trim()) },
  ];
  const doneCount = checks.filter((c) => c.ok).length;
  const left = checks.length - doneCount;
  const serverMissing = profile && !profile.profileComplete ? profile.missing : [];

  const checklist = (
    <div className={`card stack ${left ? "amber" : "mint"}`} style={{ padding: 22, background: left ? "#fff" : undefined }}>
      <div className="h3">{left ? "Finish your profile to apply" : "Your profile is ready"}</div>
      <p className="small body2">You need a photo, a skill and a language before you can apply for gigs.</p>
      <div className={`track ${left ? "amber" : "mint"}`}><div style={{ width: `${(doneCount / checks.length) * 100}%` }} /></div>
      {checks.map((c) => (
        <div className="row small" key={c.label}>
          {c.ok ? (
            <span style={{ width: 22, height: 22, borderRadius: "50%", background: "var(--mint-500)", display: "grid", placeItems: "center" }}><Icon name="check" size={14} color="#fff" /></span>
          ) : (
            <span style={{ width: 22, height: 22, borderRadius: "50%", border: "2px solid var(--amber-500)" }} />
          )}
          <span className={c.ok ? "body2" : "strong grow"}>{c.label}</span>
          {!c.ok && <span className="xs strong" style={{ color: "var(--amber-700)" }}>Missing</span>}
        </div>
      ))}
      {serverMissing.length > 0 && <span className="xs muted">Saved profile still missing: {serverMissing.map(missingLabel).join(", ").toLowerCase()}.</span>}
    </div>
  );

  return (
    <Shell bottomNav={false}>
      <MobileTop title="Your worker profile" />
      <div className="container">
        <div className="cols">
          <div className="card stack-lg">
            <div className="stack-sm">
              <h1 className="h1 hide-mobile">Your worker profile</h1>
              <p className="body2">Providers see this when you apply. A complete profile gets hired faster.</p>
            </div>
            {welcome && <Note tone="mint" icon="check">Welcome to JFJ! Add a few details so you can start applying.</Note>}
            <div className="hide-desktop">{checklist}</div>
            {!profile ? <Skeleton h={400} /> : (
              <>
                <div className="row" style={{ alignItems: "flex-start", gap: 16 }}>
                  <Avatar name={profile.name} url={/^https:\/\//.test(f.photoUrl) ? f.photoUrl : null} size={76} />
                  <Field label="Photo link (must start with https://)" error={errors.photoUrl} hint="Paste a link to a clear photo of your face. Photo upload is coming soon." className="grow">
                    <input className="input" inputMode="url" value={f.photoUrl} onChange={(e) => setF({ ...f, photoUrl: e.target.value })} placeholder="https://" />
                  </Field>
                </div>
                <Field label="About you" error={errors.description} hint={`${f.description.length} / 1000`}>
                  <textarea className="textarea" maxLength={1000} value={f.description} onChange={(e) => setF({ ...f, description: e.target.value })} placeholder="What work do you do, and what are you good at?" />
                </Field>
                <div className="field">
                  <span className="field-label">Languages you speak (up to 5)</span>
                  <div className="row-wrap">
                    {f.languages.map((l) => (
                      <button key={l} type="button" className="chip active" onClick={() => setF({ ...f, languages: f.languages.filter((x) => x !== l) })} aria-label={`Remove ${l}`}>
                        {l} <Icon name="close" size={14} color="#fff" />
                      </button>
                    ))}
                    {f.languages.length < 5 && SUGGESTED_LANGS.filter((s) => !f.languages.includes(s)).slice(0, 4).map((s) => (
                      <button key={s} type="button" className="chip" onClick={() => addLang(s)}>+ {s}</button>
                    ))}
                  </div>
                  {f.languages.length < 5 && (
                    <div className="row" style={{ maxWidth: 360 }}>
                      <input className="input" value={newLang} onChange={(e) => setNewLang(e.target.value)} onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); addLang(newLang); } }} placeholder="Another language" aria-label="Add a language" />
                      <button type="button" className="btn btn-soft" onClick={() => addLang(newLang)}>Add</button>
                    </div>
                  )}
                  {errors.languages && <span className="field-error">{errors.languages}</span>}
                </div>
                <div className="row" style={{ alignItems: "flex-start", gap: 16, flexWrap: "wrap" }}>
                  <Field label="Years of experience" error={errors.experienceYears} className="" >
                    <input className="input" style={{ width: 160 }} inputMode="numeric" value={f.experienceYears} onChange={(e) => setF({ ...f, experienceYears: e.target.value.replace(/\D/g, "").slice(0, 2) })} />
                  </Field>
                  <div className="field grow" style={{ minWidth: 260 }}>
                    <span className="field-label">Experience level</span>
                    <div className="options">
                      {LEVELS.map((l) => (
                        <button key={l} type="button" className={`option center ${f.experienceLevel === l ? "selected" : ""}`} onClick={() => setF({ ...f, experienceLevel: l })}>
                          {l.charAt(0) + l.slice(1).toLowerCase()}
                        </button>
                      ))}
                    </div>
                    {errors.experienceLevel && <span className="field-error">{errors.experienceLevel}</span>}
                  </div>
                </div>
                <div className="field">
                  <div className="row between"><span className="field-label">Your skills</span><span className="small strong text-primary">{f.skillIds.length} of 10 chosen</span></div>
                  <div className="row-wrap">
                    {skills.map((s) => {
                      const on = f.skillIds.includes(s.id);
                      return (
                        <button key={s.id} type="button" className={`chip ${on ? "active" : ""}`} aria-pressed={on} onClick={() => toggleSkill(s.id)} disabled={!on && f.skillIds.length >= 10}>
                          {s.name}{on && <Icon name="check" size={14} color="#fff" />}
                        </button>
                      );
                    })}
                  </div>
                </div>
                <div className="row" style={{ justifyContent: "flex-end" }}>
                  <button className="btn btn-secondary hide-mobile" onClick={() => navigate(`/workers/${profile.userId}`)}>See my public profile</button>
                  <button className="btn btn-primary btn-lg grow-mobile" onClick={save} disabled={busy}>{busy ? "Saving…" : "Save profile"}</button>
                </div>
              </>
            )}
          </div>
          <div className="stack hide-mobile" style={{ position: "sticky", top: 88 }}>{checklist}</div>
        </div>
      </div>
    </Shell>
  );
}
