INSERT INTO sticker_views (sticker_id, user_id, viewed_at)
SELECT stickers.id, boards.user_id, stickers.viewed_at
FROM stickers
JOIN boards ON boards.id = stickers.board_id
WHERE stickers.viewed_at IS NOT NULL
ON CONFLICT (sticker_id, user_id) DO NOTHING;

ALTER TABLE stickers DROP COLUMN viewed_at;
