-- V34: Add binary storage support to client_images
-- Adds file_name for all storage types and data BYTEA for DATABASE storage

ALTER TABLE client_images ADD COLUMN IF NOT EXISTS file_name VARCHAR(255);
ALTER TABLE client_images ADD COLUMN IF NOT EXISTS data BYTEA;
