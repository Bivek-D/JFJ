-- B4-lite: applying and hiring (PRD 5.5, 4.4).
-- An application is a worker asking for a gig; an assignment is a hire. Kept apart because an
-- application can end many ways (rejected, withdrawn), while only a hire carries work state.

CREATE TABLE applications (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    gig_id      UUID         NOT NULL REFERENCES gigs (id),
    worker_id   UUID         NOT NULL REFERENCES users (id),
    note        VARCHAR(500),
    status      VARCHAR(12)  NOT NULL DEFAULT 'APPLIED'
                CHECK (status IN ('APPLIED', 'SHORTLISTED', 'HIRED', 'REJECTED', 'WITHDRAWN')),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- APP-1: one application per worker per gig, even if two requests race.
    UNIQUE (gig_id, worker_id)
);

-- "My applications" (worker). Lookups by gig use the UNIQUE index above (it starts with gig_id).
CREATE INDEX idx_applications_worker_status ON applications (worker_id, status);

CREATE TABLE assignments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    gig_id          UUID         NOT NULL REFERENCES gigs (id),
    worker_id       UUID         NOT NULL REFERENCES users (id),
    application_id  UUID         NOT NULL UNIQUE REFERENCES applications (id),
    status          VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'COMPLETED', 'REMOVED')),
    removed_reason  VARCHAR(500),
    -- Set when the assignment becomes COMPLETED or REMOVED (contact and review windows count from it).
    ended_at        TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (gig_id, worker_id)
);

CREATE INDEX idx_assignments_worker_status ON assignments (worker_id, status);
