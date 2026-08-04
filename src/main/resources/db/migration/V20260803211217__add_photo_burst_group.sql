ALTER TABLE photos
    ADD COLUMN burst_group_id UUID NULL,
    ADD COLUMN is_representative BOOLEAN NOT NULL DEFAULT true;

CREATE INDEX idx_photos_burst_group_id ON photos (burst_group_id);

CREATE UNIQUE INDEX uk_photos_burst_representative
    ON photos (burst_group_id)
    WHERE is_representative = true;
