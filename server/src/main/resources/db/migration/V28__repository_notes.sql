-- Add notes column to repository table for admin remarks.
ALTER TABLE repository ADD COLUMN notes TEXT DEFAULT NULL;
