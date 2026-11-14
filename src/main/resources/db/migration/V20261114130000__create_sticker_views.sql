CREATE TABLE sticker_views (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    sticker_id UUID NOT NULL,
    user_id UUID NOT NULL,
    viewed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_sticker_views_sticker
        FOREIGN KEY (sticker_id) REFERENCES stickers (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uk_sticker_views_sticker_user
    ON sticker_views (sticker_id, user_id);

CREATE INDEX ix_sticker_views_user_sticker
    ON sticker_views (user_id, sticker_id);
