-- Convert audit_logs JSONB columns to text to match current code saving string values
-- This migration changes column types using a safe cast
ALTER TABLE audit_logs ALTER COLUMN new_value TYPE text USING new_value::text;
ALTER TABLE audit_logs ALTER COLUMN old_value TYPE text USING old_value::text;
