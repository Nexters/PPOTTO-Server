CREATE TABLE user_device_tokens (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    user_id UUID NOT NULL,
    device_id TEXT NOT NULL,
    platform VARCHAR(20) NOT NULL,
    fcm_token TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uk_user_device_tokens_user_device ON user_device_tokens (user_id, device_id);
CREATE INDEX idx_user_device_tokens_fcm_token ON user_device_tokens (fcm_token);

CREATE TRIGGER user_device_tokens_set_updated_at
BEFORE UPDATE ON user_device_tokens
FOR EACH ROW EXECUTE FUNCTION set_updated_at();
