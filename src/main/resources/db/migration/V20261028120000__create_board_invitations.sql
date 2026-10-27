CREATE TABLE board_invitations (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    board_id UUID NOT NULL,
    inviter_user_id UUID NOT NULL,
    code VARCHAR(6) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    preview_image_key TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_board_invitations_board FOREIGN KEY (board_id) REFERENCES boards (id) ON DELETE CASCADE,
    CONSTRAINT chk_board_invitations_code CHECK (code ~ '^[A-HJ-NP-Z2-9]{6}$')
);

CREATE UNIQUE INDEX uk_board_invitations_code ON board_invitations (code);
CREATE UNIQUE INDEX uk_board_invitations_board_inviter ON board_invitations (board_id, inviter_user_id);
CREATE INDEX ix_board_invitations_inviter ON board_invitations (inviter_user_id);

CREATE TRIGGER board_invitations_set_updated_at
BEFORE UPDATE ON board_invitations
FOR EACH ROW EXECUTE FUNCTION set_updated_at();
