CREATE TABLE terms (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    code TEXT NOT NULL,
    version TEXT NOT NULL,
    is_required BOOLEAN NOT NULL DEFAULT true,
    content_url TEXT,
    effective_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_terms_code_version UNIQUE (code, version)
);

CREATE INDEX ix_terms_code_effective ON terms (code, effective_at);

CREATE TRIGGER terms_set_updated_at
BEFORE UPDATE ON terms
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE term_agreements (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    user_id UUID NOT NULL,
    term_id UUID NOT NULL,
    agreed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_term_agreements_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_term_agreements_term FOREIGN KEY (term_id) REFERENCES terms (id),
    CONSTRAINT uk_term_agreement UNIQUE (user_id, term_id)
);

CREATE INDEX ix_term_agreement_term ON term_agreements (term_id);
