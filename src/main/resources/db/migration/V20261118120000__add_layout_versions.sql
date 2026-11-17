ALTER TABLE stickers
ADD COLUMN layout_version BIGINT NOT NULL DEFAULT 0,
ADD CONSTRAINT ck_stickers_layout_version_nonnegative CHECK (layout_version >= 0);

ALTER TABLE drawings
ADD COLUMN layout_version BIGINT NOT NULL DEFAULT 0,
ADD CONSTRAINT ck_drawings_layout_version_nonnegative CHECK (layout_version >= 0);
