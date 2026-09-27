ALTER TABLE deliveries
    DROP CONSTRAINT  deliveries_status_check;

ALTER TABLE deliveries
    ADD CONSTRAINT deliveries_status_check
    CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED'));

ALTER TABLE deliveries
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN lease_until TIMESTAMPTZ;

CREATE INDEX idx_deliveries_ready
    ON deliveries (next_attempt_at, id)
    WHERE status = 'PENDING';