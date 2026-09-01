-- Replace the original two-role model with ROOT / SCHOOL_ADMIN / SCHOOL_USER.
ALTER TABLE users DROP CONSTRAINT IF EXISTS school_user_requires_school;
UPDATE users SET role = 'SCHOOL_ADMIN' WHERE role = 'ADMIN';
UPDATE users SET role = 'SCHOOL_USER' WHERE role = 'SCHOOL';
ALTER TABLE users ADD CONSTRAINT user_school_role_check
  CHECK ((role = 'ROOT' AND school_id IS NULL) OR (role IN ('SCHOOL_ADMIN','SCHOOL_USER') AND school_id IS NOT NULL));

-- Fee amounts are configured per school, class, fee type and academic year.
CREATE TABLE class_fee_structures (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  school_id BIGINT NOT NULL REFERENCES schools(id),
  class_id BIGINT NOT NULL REFERENCES classes(id),
  fee_type_id BIGINT NOT NULL REFERENCES fee_types(id),
  academic_year VARCHAR(9) NOT NULL,
  amount NUMERIC(12,2) NOT NULL CHECK(amount >= 0),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(school_id, class_id, fee_type_id, academic_year)
);
CREATE INDEX idx_class_fee_structure_lookup
  ON class_fee_structures(school_id, class_id, academic_year, active);

-- Receipt numbers are unique within a school, not globally.
ALTER TABLE fee_receipts DROP CONSTRAINT IF EXISTS fee_receipts_receipt_number_key;
ALTER TABLE fee_receipts ADD CONSTRAINT uq_fee_receipts_school_number UNIQUE(school_id, receipt_number);
