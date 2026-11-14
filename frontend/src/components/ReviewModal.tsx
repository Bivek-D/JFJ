import { useState } from "react";
import * as ep from "../api/endpoints";
import { errorMessage } from "../api/client";
import type { Review } from "../api/types";
import { Avatar, Modal, ModalHead, Note, StarInput, ratingWord } from "./ui";

/** Post (or edit, within 24 h) a review for an assignment. Direction is set by the backend. */
export function ReviewModal({
  assignmentId, name, subtitle, existing, onClose, onSaved,
}: {
  assignmentId: string; name: string; subtitle?: string; existing?: Review | null;
  onClose: () => void; onSaved: (r: Review) => void;
}) {
  const [rating, setRating] = useState(existing?.rating ?? 0);
  const [comment, setComment] = useState(existing?.comment ?? "");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function save() {
    if (!rating) return setError("Choose a star rating.");
    setBusy(true);
    setError("");
    try {
      const r = existing ? await ep.editReview(existing.id, rating, comment) : await ep.postReview(assignmentId, rating, comment);
      onSaved(r);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  const first = name.split(" ")[0];
  return (
    <Modal onClose={onClose} label={`Rate ${name}`}>
      <ModalHead title={existing ? `Edit your rating for ${first}` : `How was it with ${first}?`} onClose={onClose} />
      <div className="row">
        <Avatar name={name} size={48} />
        <div className="grow">
          <div className="strong">{name}</div>
          {subtitle && <div className="small muted">{subtitle}</div>}
        </div>
      </div>
      <div className="row" style={{ gap: 14 }}>
        <StarInput value={rating} onChange={setRating} />
        <span className="strong" style={{ color: "var(--amber-700)" }}>{ratingWord(rating)}</span>
      </div>
      <label className="field">
        <span className="field-label">Comment (optional)</span>
        <textarea className="textarea" maxLength={500} value={comment} onChange={(e) => setComment(e.target.value)} placeholder="What went well? What could be better?" />
        <span className="xs muted" style={{ textAlign: "right" }}>{comment.length} / 500</span>
      </label>
      {error && <Note tone="red">{error}</Note>}
      <p className="xs muted">Your review shows on {first}'s profile. You can edit it for 24 hours.</p>
      <div className="row" style={{ justifyContent: "flex-end" }}>
        <button className="btn btn-secondary hide-mobile" onClick={onClose}>Later</button>
        <button className="btn btn-primary btn-lg grow-mobile" onClick={save} disabled={busy}>{busy ? "Saving…" : existing ? "Save changes" : "Post review"}</button>
      </div>
    </Modal>
  );
}
