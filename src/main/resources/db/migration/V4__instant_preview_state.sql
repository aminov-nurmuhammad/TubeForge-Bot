ALTER TABLE media_requests ADD COLUMN metadata_state VARCHAR(16) NOT NULL DEFAULT 'READY';
ALTER TABLE media_requests ADD COLUMN metadata_error_code VARCHAR(64);
ALTER TABLE media_requests ADD COLUMN metadata_error_message TEXT;

CREATE INDEX idx_media_requests_metadata_state
    ON media_requests (metadata_state, created_at DESC);
