-- Seed default fee types for default school (id 1)
-- Insert if not exists to be safe on re-run
INSERT INTO fee_types (school_id, code, display_name, active)
SELECT 1, 'TUITION', 'Tuition Fee', true
WHERE NOT EXISTS (SELECT 1 FROM fee_types WHERE school_id=1 AND code='TUITION');

INSERT INTO fee_types (school_id, code, display_name, active)
SELECT 1, 'VAN', 'Van Fee', true
WHERE NOT EXISTS (SELECT 1 FROM fee_types WHERE school_id=1 AND code='VAN');
