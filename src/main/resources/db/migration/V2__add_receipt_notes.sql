-- Add optional notes column to fee_receipts so receipts can store an optional note
ALTER TABLE fee_receipts
  ADD COLUMN notes TEXT;
