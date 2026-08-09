ALTER TABLE stickers
    ADD COLUMN main_color VARCHAR(7) NOT NULL DEFAULT '#222222';

ALTER TABLE stickers
    ADD CONSTRAINT chk_sticker_main_color CHECK (main_color ~ '^#[0-9A-Fa-f]{6}$');
