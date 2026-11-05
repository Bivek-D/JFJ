-- Account tables for B1. Ratings, XP and level columns come in a later migration.
-- Email is stored lowercase and phone as 10 digits (normalized by the app), so UNIQUE catches duplicates.
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(100) NOT NULL,
    email           VARCHAR(254) NOT NULL UNIQUE,
    phone           VARCHAR(10)  NOT NULL UNIQUE,
    password_hash   VARCHAR(100) NOT NULL,
    date_of_birth   DATE         NOT NULL,
    gender          VARCHAR(20),
    address_line    VARCHAR(255) NOT NULL,
    city            VARCHAR(100) NOT NULL,
    state           VARCHAR(100) NOT NULL,
    pincode         VARCHAR(6)   NOT NULL,
    location        geometry(Point, 4326),
    role            VARCHAR(10)  NOT NULL DEFAULT 'USER'   CHECK (role IN ('USER', 'ADMIN')),
    status          VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED')),
    email_verified  BOOLEAN      NOT NULL DEFAULT FALSE,
    phone_verified  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_location ON users USING GIST (location);

-- Only a SHA-256 hash of each refresh token is stored; mode remembers the session's last mode (AUTH-4).
-- No ON DELETE CASCADE: users are soft-deleted (AUTH-9).
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users (id),
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    mode        VARCHAR(10) NOT NULL CHECK (mode IN ('WORKER', 'PROVIDER')),
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
