ALTER TABLE boards
ADD COLUMN name TEXT;

WITH numbered_boards AS (
    SELECT
        id,
        row_number() OVER (PARTITION BY user_id ORDER BY created_at, id) AS board_number
    FROM boards
)
UPDATE boards
SET name = 'Board ' || numbered_boards.board_number
FROM numbered_boards
WHERE boards.id = numbered_boards.id;

ALTER TABLE boards
ALTER COLUMN name SET NOT NULL,
ADD COLUMN deleted_at TIMESTAMPTZ,
ADD CONSTRAINT chk_boards_name_length CHECK (char_length(name) BETWEEN 1 AND 10);

DROP INDEX idx_boards_user_id;

CREATE INDEX ix_board_user_created ON boards (user_id, created_at);

CREATE TABLE drawings (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    board_id UUID NOT NULL,
    sticker_id UUID,
    scope VARCHAR(20) NOT NULL,
    stroke JSONB NOT NULL,
    color TEXT NOT NULL,
    stroke_width DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT chk_drawings_scope CHECK (scope IN ('STICKER', 'BOARD')),
    CONSTRAINT chk_drawings_scope_sticker CHECK ((scope = 'STICKER') = (sticker_id IS NOT NULL))
);

CREATE INDEX ix_drawing_board ON drawings (board_id);
CREATE INDEX ix_drawing_sticker ON drawings (sticker_id);

CREATE TRIGGER drawings_set_updated_at
BEFORE UPDATE ON drawings
FOR EACH ROW EXECUTE FUNCTION set_updated_at();
