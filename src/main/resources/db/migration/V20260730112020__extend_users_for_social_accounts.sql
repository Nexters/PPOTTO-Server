CREATE EXTENSION IF NOT EXISTS citext;

CREATE TYPE oauth_provider AS ENUM ('KAKAO', 'APPLE');

ALTER TABLE users
    ADD COLUMN provider oauth_provider,
    ADD COLUMN provider_user_id TEXT,
    ADD COLUMN provider_refresh_token TEXT,
    ADD COLUMN email CITEXT,
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_users_social_identity_complete
        CHECK (
            provider IS NOT NULL
            AND provider_user_id IS NOT NULL
            AND email IS NOT NULL
        ) NOT VALID;

CREATE UNIQUE INDEX uk_users_provider_uid
    ON users (provider, provider_user_id)
    WHERE deleted_at IS NULL;
