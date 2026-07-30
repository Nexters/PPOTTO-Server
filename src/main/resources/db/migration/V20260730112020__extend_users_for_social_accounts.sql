CREATE EXTENSION IF NOT EXISTS citext;

CREATE TYPE oauth_provider AS ENUM ('KAKAO', 'APPLE');

ALTER TABLE users
    ADD COLUMN provider oauth_provider NOT NULL,
    ADD COLUMN provider_user_id TEXT NOT NULL,
    ADD COLUMN provider_refresh_token TEXT,
    ADD COLUMN email CITEXT NOT NULL,
    ADD COLUMN deleted_at TIMESTAMPTZ;

CREATE UNIQUE INDEX uk_users_provider_uid
    ON users (provider, provider_user_id)
    WHERE deleted_at IS NULL;
