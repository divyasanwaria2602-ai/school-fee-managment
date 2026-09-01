-- Permanent repair for databases where audit_logs is missing even though
-- earlier Flyway migrations were already recorded as applied.
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    school_id BIGINT REFERENCES schools(id),
    user_id BIGINT REFERENCES users(id),
    action VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT NOT NULL,
    old_value TEXT,
    new_value TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE audit_logs
    ALTER COLUMN old_value TYPE text USING old_value::text;

ALTER TABLE audit_logs
    ALTER COLUMN new_value TYPE text USING new_value::text;

CREATE INDEX IF NOT EXISTS idx_audit_logs_school_created
    ON audit_logs (school_id, created_at);
