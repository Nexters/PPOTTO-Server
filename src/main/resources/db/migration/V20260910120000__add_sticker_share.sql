ALTER TABLE stickers
    ADD COLUMN share_token TEXT,
    ADD COLUMN share_photos BOOLEAN NOT NULL DEFAULT false;

CREATE UNIQUE INDEX uk_stickers_share_token ON stickers (share_token);
