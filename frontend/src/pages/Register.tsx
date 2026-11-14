import { useState, type FormEvent } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import * as ep from "../api/endpoints";
import { ApiError, errorMessage } from "../api/client";
import { Field, Note, WakingBanner } from "../components/ui";
import { AuthVisual } from "./authVisual";

const GENDERS = ["Female", "Male", "Other", "Prefer not to say"];

/** Client checks mirror the backend rules (guide §9); backend fieldErrors still win. */
export function validateRegister(f: Record<string, string>) {
  const e: Record<string, string> = {};
  if (!f.name.trim()) e.name = "Enter your full name.";
  if (!/^\S+@\S+\.\S+$/.test(f.email.trim())) e.email = "Enter a valid email address.";
  if (!/^[6-9]\d{9}$/.test(f.phone.replace(/\D/g, ""))) e.phone = "Enter 10 digits starting with 6, 7, 8 or 9.";
  if (f.password.length < 8 || f.password.length > 72 || !/[A-Za-z]/.test(f.password) || !/\d/.test(f.password))
    e.password = "Use 8 to 72 characters, with a letter and a number.";
  if (!f.dateOfBirth) e.dateOfBirth = "Enter your date of birth.";
  else {
    const dob = new Date(f.dateOfBirth);
    const adult = new Date(dob.getFullYear() + 18, dob.getMonth(), dob.getDate());
    if (adult > new Date()) e.dateOfBirth = "You must be 18 or older to use JFJ.";
  }
  if (!f.addressLine.trim()) e.addressLine = "Enter your address.";
  if (!f.city.trim()) e.city = "Enter your city.";
  if (!f.state.trim()) e.state = "Enter your state.";
  if (!/^\d{6}$/.test(f.pincode.trim())) e.pincode = "Enter a 6-digit pincode.";
  return e;
}

export default function Register() {
  const { status, login } = useAuth();
  const navigate = useNavigate();
  const [f, setF] = useState({ name: "", email: "", phone: "", password: "", dateOfBirth: "", gender: "", addressLine: "", city: "", state: "", pincode: "" });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState("");
  const [busy, setBusy] = useState(false);
  if (status === "authed") return <Navigate to="/" replace />;

  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value });

  async function submit(e: FormEvent) {
    e.preventDefault();
    setFormError("");
    const errs = validateRegister(f);
    setErrors(errs);
    if (Object.keys(errs).length) return;
    setBusy(true);
    try {
      await ep.register({
        name: f.name.trim(), email: f.email.trim().toLowerCase(), phone: f.phone.replace(/\D/g, ""), password: f.password,
        dateOfBirth: f.dateOfBirth, gender: f.gender || undefined, addressLine: f.addressLine.trim(), city: f.city.trim(),
        state: f.state.trim(), pincode: f.pincode.trim(),
      });
      try {
        await login(f.email, f.password);
        navigate("/profile/edit", { replace: true, state: { welcome: true } });
      } catch {
        navigate("/login", { replace: true, state: { registered: true } });
      }
    } catch (err) {
      if (err instanceof ApiError) {
        setErrors(err.fieldErrors);
        if (err.status === 409) setFormError(err.message || "That email or phone number is already registered.");
        else if (!Object.keys(err.fieldErrors).length) setFormError(errorMessage(err));
      } else setFormError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div>
      <WakingBanner />
      <div style={{ padding: "18px 40px 0" }} className="hide-mobile"><span className="wordmark">JFJ</span></div>
      <div className="auth-shell">
        <AuthVisual />
        <form className="card raised stack" style={{ padding: 32 }} onSubmit={submit} noValidate>
          <div className="hide-desktop"><span className="wordmark">JFJ</span></div>
          <div className="stack-sm">
            <h1 className="h1">Create your account</h1>
            <p className="muted">One account works for both finding work and hiring.</p>
          </div>
          {formError && <Note tone="red">{formError}</Note>}
          <Field label="Full name" error={errors.name}><input className="input" autoComplete="name" value={f.name} onChange={set("name")} /></Field>
          <Field label="Email" error={errors.email}><input className="input" type="email" autoComplete="email" value={f.email} onChange={set("email")} /></Field>
          <Field label="Mobile number (10 digits)" error={errors.phone}>
            <div className="input-wrap"><span className="lead">+91</span><input className="input" inputMode="numeric" autoComplete="tel-national" maxLength={11} value={f.phone} onChange={set("phone")} placeholder="98765 43210" /></div>
          </Field>
          <Field label="Password" error={errors.password} hint="At least 8 characters, with a letter and a number.">
            <input className="input" type="password" autoComplete="new-password" value={f.password} onChange={set("password")} />
          </Field>
          <Field label="Date of birth" error={errors.dateOfBirth} hint="You must be 18 or older to use JFJ.">
            <input className="input" type="date" value={f.dateOfBirth} onChange={set("dateOfBirth")} max={new Date().toISOString().slice(0, 10)} />
          </Field>
          <div className="field">
            <span className="field-label">Gender (optional)</span>
            <div className="row-wrap">
              {GENDERS.map((g) => (
                <button type="button" key={g} className={`chip ${f.gender === g ? "active" : ""}`} onClick={() => setF({ ...f, gender: f.gender === g ? "" : g })}>{g}</button>
              ))}
            </div>
          </div>
          <Field label="Address line" error={errors.addressLine}><input className="input" autoComplete="street-address" value={f.addressLine} onChange={set("addressLine")} /></Field>
          <div className="grid-2" style={{ gridTemplateColumns: "1fr 1fr 0.8fr" }}>
            <Field label="City" error={errors.city}><input className="input" autoComplete="address-level2" value={f.city} onChange={set("city")} /></Field>
            <Field label="State" error={errors.state}><input className="input" autoComplete="address-level1" value={f.state} onChange={set("state")} /></Field>
            <Field label="Pincode" error={errors.pincode}><input className="input" inputMode="numeric" maxLength={6} autoComplete="postal-code" value={f.pincode} onChange={set("pincode")} /></Field>
          </div>
          <button className="btn btn-primary btn-lg btn-block" disabled={busy}>{busy ? "Creating account…" : "Create account"}</button>
          <p className="center small body2">Already have an account? <Link to="/login" className="strong">Log in</Link></p>
        </form>
      </div>
    </div>
  );
}
