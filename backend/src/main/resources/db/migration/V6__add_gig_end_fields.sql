-- B4-lite: how a gig ended (PRD data model). Added now, with the feature that fills them.
ALTER TABLE gigs ADD COLUMN cancel_reason VARCHAR(500);
ALTER TABLE gigs ADD COLUMN completed_at  TIMESTAMPTZ;
