import { useEffect, useState } from "react";
import * as ep from "../api/endpoints";
import { ApiError, errorMessage } from "../api/client";
import type { Contact } from "../api/types";
import { Icon } from "./Icon";
import { Avatar, Badge, Modal, ModalHead, Note, Skeleton } from "./ui";
import { longDate } from "../lib/format";

/** Contact screen: safety tips first, then the other side's name and phone (guide §7.2, §10). */
export function ContactModal({ assignmentId, onClose }: { assignmentId: string; onClose: () => void }) {
  const [c, setC] = useState<Contact | null>(null);
  const [error, setError] = useState<unknown>(null);
  useEffect(() => {
    ep.getContact(assignmentId).then(setC).catch(setError);
  }, [assignmentId]);
  const expired = error instanceof ApiError && (error.status === 403 || error.status === 404);
  return (
    <Modal onClose={onClose} label="Contact">
      <ModalHead title="Contact" onClose={onClose} />
      <Note tone="amber" icon="shield">
        Phone numbers aren't verified yet. Meet in a public place first if you're unsure, and never pay money to get or give work.
      </Note>
      {!c && !error && <Skeleton h={90} />}
      {expired && <Note tone="grey">Contact details are no longer available for this job.</Note>}
      {error && !expired ? <Note tone="red">{errorMessage(error)}</Note> : null}
      {c && (
        <div className="soft stack" style={{ padding: 16 }}>
          <div className="row">
            <Avatar name={c.name} size={48} solid={c.role?.toUpperCase().includes("PROVIDER")} />
            <div className="grow">
              <div className="h3">{c.name}</div>
              <div className="xs muted">{c.role ? c.role.charAt(0) + c.role.slice(1).toLowerCase() : ""}</div>
            </div>
            {!c.phoneVerified && <Badge tone="amber">Unverified</Badge>}
          </div>
          <div className="row">
            <Icon name="phone" color="#3B5BDB" />
            <span className="bold grow" style={{ fontSize: 18 }}>{c.phone}</span>
            <a className="btn btn-primary" href={`tel:+91${c.phone}`}><Icon name="phone" size={18} color="#fff" />Call</a>
          </div>
          {c.availableUntil && <span className="xs muted">Visible until {longDate(c.availableUntil)}</span>}
        </div>
      )}
    </Modal>
  );
}
