CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TRIGGER users_set_updated_at
BEFORE UPDATE ON users
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE boards (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_boards_user_id ON boards (user_id);

CREATE TRIGGER boards_set_updated_at
BEFORE UPDATE ON boards
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE images (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    board_id UUID NOT NULL,
    upload_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    upload_session_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_images_board_id ON images (board_id);
CREATE INDEX idx_images_upload_session_id ON images (upload_session_id);
