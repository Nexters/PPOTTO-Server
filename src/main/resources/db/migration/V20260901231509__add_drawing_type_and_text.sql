ALTER TABLE drawings
ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT 'STROKE',
ADD COLUMN z_index INTEGER NOT NULL DEFAULT 0,
ADD COLUMN content TEXT,
ADD COLUMN font_size DOUBLE PRECISION,
ADD COLUMN pos_x DOUBLE PRECISION,
ADD COLUMN pos_y DOUBLE PRECISION,
ADD COLUMN max_width DOUBLE PRECISION,
ADD COLUMN rotation DOUBLE PRECISION NOT NULL DEFAULT 0,
ALTER COLUMN stroke DROP NOT NULL,
ALTER COLUMN stroke_width DROP NOT NULL;

UPDATE drawings
SET z_index = COALESCE((stroke ->> 'zIndex')::INTEGER, 0);

ALTER TABLE drawings
ADD CONSTRAINT chk_drawings_type CHECK (type IN ('STROKE', 'TEXT')),
ADD CONSTRAINT chk_drawings_stroke_shape
CHECK ((type = 'STROKE') = (stroke IS NOT NULL AND stroke_width IS NOT NULL)),
ADD CONSTRAINT chk_drawings_text_shape
CHECK ((type = 'TEXT') = (
    content IS NOT NULL
    AND font_size IS NOT NULL
    AND pos_x IS NOT NULL
    AND pos_y IS NOT NULL
    AND max_width IS NOT NULL
));
