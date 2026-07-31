CREATE TABLE stickers (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    analysis_id UUID NOT NULL,
    board_id UUID NOT NULL,
    type VARCHAR(20) NOT NULL,
    title TEXT NOT NULL,
    viewed_at TIMESTAMPTZ,
    source_photo_id UUID,
    image_key TEXT,
    text_content TEXT,
    pos_x DOUBLE PRECISION NOT NULL,
    pos_y DOUBLE PRECISION NOT NULL,
    scale DOUBLE PRECISION NOT NULL DEFAULT 1,
    rotation DOUBLE PRECISION NOT NULL DEFAULT 0,
    z_index INTEGER NOT NULL DEFAULT 0,
    badge_offset_x DOUBLE PRECISION NOT NULL DEFAULT 0,
    badge_offset_y DOUBLE PRECISION NOT NULL DEFAULT 0,
    badge_rotation DOUBLE PRECISION NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT fk_stickers_analysis FOREIGN KEY (analysis_id) REFERENCES analysis (id),
    CONSTRAINT fk_stickers_board FOREIGN KEY (board_id) REFERENCES boards (id),
    CONSTRAINT fk_stickers_source_photo FOREIGN KEY (source_photo_id) REFERENCES photos (id),
    CONSTRAINT chk_stickers_content CHECK (
        (type = 'IMAGE' AND image_key IS NOT NULL)
        OR (type = 'TEXT' AND text_content IS NOT NULL)
    )
);

CREATE INDEX ix_sticker_board_z ON stickers (board_id, z_index);
CREATE INDEX ix_sticker_analysis ON stickers (analysis_id);
CREATE INDEX ix_sticker_source_photo ON stickers (source_photo_id);

CREATE TRIGGER stickers_set_updated_at
BEFORE UPDATE ON stickers
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE OR REPLACE FUNCTION enforce_analysis_sticker_limit()
RETURNS TRIGGER AS $$
BEGIN
    PERFORM pg_advisory_xact_lock(hashtextextended(NEW.analysis_id::text, 0));
    IF (SELECT count(*) FROM stickers WHERE analysis_id = NEW.analysis_id) >= 6 THEN
        RAISE EXCEPTION 'analysis sticker limit exceeded'
            USING ERRCODE = '23514', CONSTRAINT = 'chk_stickers_analysis_limit';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER stickers_enforce_analysis_limit
BEFORE INSERT ON stickers
FOR EACH ROW EXECUTE FUNCTION enforce_analysis_sticker_limit();

CREATE TABLE sticker_photos (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    sticker_id UUID NOT NULL,
    photo_id UUID NOT NULL,
    CONSTRAINT fk_sticker_photos_sticker FOREIGN KEY (sticker_id) REFERENCES stickers (id) ON DELETE CASCADE,
    CONSTRAINT fk_sticker_photos_photo FOREIGN KEY (photo_id) REFERENCES photos (id),
    CONSTRAINT uk_sticker_photo UNIQUE (sticker_id, photo_id)
);

CREATE INDEX ix_sticker_photo_photo ON sticker_photos (photo_id);

CREATE TABLE recap_comments (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    sticker_id UUID NOT NULL,
    content TEXT NOT NULL,
    is_float BOOLEAN NOT NULL DEFAULT false,
    pos_x DOUBLE PRECISION,
    pos_y DOUBLE PRECISION,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_recap_comments_sticker FOREIGN KEY (sticker_id) REFERENCES stickers (id) ON DELETE CASCADE,
    CONSTRAINT chk_recap_comment_float_position CHECK (
        NOT is_float OR (pos_x IS NOT NULL AND pos_y IS NOT NULL)
    )
);

CREATE INDEX ix_recap_sticker ON recap_comments (sticker_id);

CREATE TRIGGER recap_comments_set_updated_at
BEFORE UPDATE ON recap_comments
FOR EACH ROW EXECUTE FUNCTION set_updated_at();
