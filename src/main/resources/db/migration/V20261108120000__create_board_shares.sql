CREATE TABLE board_shares (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    board_id UUID NOT NULL,
    sharer_user_id UUID NOT NULL,
    share_token TEXT NOT NULL,
    scope VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_board_shares_board FOREIGN KEY (board_id) REFERENCES boards (id) ON DELETE CASCADE,
    CONSTRAINT chk_board_shares_scope CHECK (scope IN ('BOARD', 'RECAP', 'PHOTOS'))
);

CREATE UNIQUE INDEX uk_board_shares_share_token ON board_shares (share_token);
CREATE UNIQUE INDEX uk_board_shares_board_sharer ON board_shares (board_id, sharer_user_id);
CREATE INDEX ix_board_shares_sharer ON board_shares (sharer_user_id);

CREATE TRIGGER board_shares_set_updated_at
BEFORE UPDATE ON board_shares
FOR EACH ROW EXECUTE FUNCTION set_updated_at();
