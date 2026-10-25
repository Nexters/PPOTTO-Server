CREATE TABLE board_members (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    board_id UUID NOT NULL,
    user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_board_members_board FOREIGN KEY (board_id) REFERENCES boards (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uk_board_members_board_user ON board_members (board_id, user_id);
CREATE INDEX ix_board_members_user ON board_members (user_id);
