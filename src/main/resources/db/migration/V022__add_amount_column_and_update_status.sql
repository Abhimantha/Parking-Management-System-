-- Add amount column to summery_reservation table
ALTER TABLE summery_reservation ADD COLUMN amount DECIMAL(10,2);

-- Update existing status values from PENDING/RESERVED to COMPLETE/ACTIVE
-- This migration will update existing records to use the new status values
UPDATE summery_reservation SET status = 'COMPLETE' WHERE status = 'PENDING';
UPDATE summery_reservation SET status = 'ACTIVE' WHERE status = 'RESERVED';
