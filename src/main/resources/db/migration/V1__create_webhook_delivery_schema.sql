CREATE TABLE webhook_endpoints (
                                   id BIGSERIAL PRIMARY KEY,
                                   name VARCHAR(200) NOT NULL,
                                   url TEXT NOT NULL,
                                   created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE deliveries (
                            id BIGSERIAL PRIMARY KEY,
                            webhook_endpoint_id BIGINT NOT NULL
                                REFERENCES webhook_endpoints(id),

                            payload JSONB NOT NULL,

                            status VARCHAR(32) NOT NULL
                                CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),

                            created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                            updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_deliveries_status
    ON deliveries(status);