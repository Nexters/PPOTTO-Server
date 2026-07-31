ALTER TABLE stickers ADD COLUMN summary TEXT;

UPDATE stickers s
SET summary = left(promoted.content, 100)
FROM (
    SELECT DISTINCT ON (sticker_id) id, sticker_id, content
    FROM recap_comments
    WHERE is_float IS FALSE
    ORDER BY sticker_id, id
) promoted
WHERE promoted.sticker_id = s.id;

DELETE FROM recap_comments
WHERE id IN (
    SELECT DISTINCT ON (sticker_id) id
    FROM recap_comments
    WHERE is_float IS FALSE
    ORDER BY sticker_id, id
);

UPDATE stickers SET summary = title WHERE summary IS NULL;

ALTER TABLE stickers ALTER COLUMN summary SET NOT NULL;

ALTER TABLE recap_comments DROP CONSTRAINT chk_recap_comment_float_position;

ALTER TABLE recap_comments DROP COLUMN is_float;

ALTER TABLE recap_comments
    ADD CONSTRAINT chk_recap_comment_position CHECK ((pos_x IS NULL) = (pos_y IS NULL));
