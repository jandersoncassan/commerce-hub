CREATE TABLE idempotency_keys (
    idempotency_key UUID PRIMARY KEY,
    http_method VARCHAR(10) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_id UUID,
    response_status SMALLINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_idempotency_keys_expires_at ON idempotency_keys (expires_at);
