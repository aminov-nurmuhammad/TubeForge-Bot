ALTER TABLE media_requests ADD COLUMN metadata_inspected_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE media_requests ADD COLUMN source_url_hash VARCHAR(64);
UPDATE media_requests SET metadata_inspected_at = created_at WHERE metadata_inspected_at IS NULL;
ALTER TABLE media_requests ALTER COLUMN metadata_inspected_at SET NOT NULL;
CREATE INDEX idx_media_requests_url_hash ON media_requests (source_url_hash, status, metadata_inspected_at DESC);

CREATE TABLE media_artifacts (
    cache_key VARCHAR(64) PRIMARY KEY,
    source_id VARCHAR(256) NOT NULL,
    job_type VARCHAR(32) NOT NULL,
    format_code VARCHAR(64),
    delivery_kind VARCHAR(16) NOT NULL,
    telegram_file_id TEXT NOT NULL,
    telegram_file_unique_id TEXT,
    result_file_name TEXT,
    size_bytes BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_used_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    hit_count BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_media_artifacts_source ON media_artifacts (source_id, job_type, format_code);
CREATE INDEX idx_media_artifacts_expiry ON media_artifacts (expires_at);

CREATE TABLE ai_insights (
    cache_key VARCHAR(64) PRIMARY KEY,
    source_id VARCHAR(256) NOT NULL,
    insight_type VARCHAR(32) NOT NULL,
    transcript_language VARCHAR(32) NOT NULL,
    output_language VARCHAR(8) NOT NULL,
    content TEXT NOT NULL,
    provider VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_used_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    hit_count BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_ai_insights_source ON ai_insights (source_id, insight_type, transcript_language, output_language);
CREATE INDEX idx_ai_insights_expiry ON ai_insights (expires_at);
