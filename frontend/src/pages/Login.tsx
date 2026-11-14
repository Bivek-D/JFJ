import { useState, type FormEvent } from "react";
import { Link, Navigate, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { ApiError, errorMessage } from "../api/client";
import { Field, Note, WakingBanner } from "../components/ui";
import { AuthVisual } from "./authVisual";

export default function Login() {
  const { login, status } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string; registered?: boolean } | null)?.from ?? "/";
  const registered = (location.state as { registered?: boolean } | null)?.registered;
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  if (status === "authed") return <Navigate to={from} replace />;

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError("");
    setFieldErrors({});
    if (!email.trim() || !password) {
      setError("Enter your email and password.");
      return;
    }
    setBusy(true);
    try {
      await login(email, password);
      navigate(from, { replace: true });
    } catch (err) {
      if (err instanceof ApiError && err.status === 403) setError(err.message || "This account is suspended. Contact support.");
      else if (err instanceof ApiError && err.status === 401) setError("That email and password don't match.");
      else {
        setError(errorMessage(err));
        if (err instanceof ApiError) setFieldErrors(err.fieldErrors);
      }
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
        <form className="card raised stack" style={{ padding: 36 }} onSubmit={submit} noValidate>
          <div className="hide-desktop"><span className="wordmark">JFJ</span></div>
          <div className="stack-sm">
            <h1 className="h1">Welcome back</h1>
            <p className="muted">Log in to find work or hire people near you.</p>
          </div>
          {registered && <Note tone="mint" icon="check">Account created. Log in to continue.</Note>}
          {error && <Note tone="red">{error}</Note>}
          <Field label="Email" error={fieldErrors.email}>
            <input className="input" type="email" autoComplete="email" inputMode="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="name@example.com" />
          </Field>
          <Field label="Password" error={fieldErrors.password}>
            <input className="input" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} />
          </Field>
          <button className="btn btn-primary btn-lg btn-block" disabled={busy}>{busy ? "Logging in…" : "Log in"}</button>
          <p className="center small body2">New to JFJ? <Link to="/register" className="strong">Create an account</Link></p>
        </form>
      </div>
    </div>
  );
}
