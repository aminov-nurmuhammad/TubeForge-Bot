CREATE TABLE app_users (
    telegram_user_id BIGINT PRIMARY KEY,
    username VARCHAR(255),
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    language VARCHAR(8) NOT NULL DEFAULT 'EN',
    default_video_quality VARCHAR(16) NOT NULL DEFAULT '720',
    default_audio_format VARCHAR(16) NOT NULL DEFAULT 'MP3',
    send_as_document BOOLEAN NOT NULL DEFAULT FALSE,
    auto_compress BOOLEAN NOT NULL DEFAULT TRUE,
    terms_accepted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE media_requests (
    id VARCHAR(36) PRIMARY KEY,
    telegram_user_id BIGINT NOT NULL,
    chat_id BIGINT NOT NULL,
    preview_message_id BIGINT,
    source_url TEXT NOT NULL,
    source_type VARCHAR(24) NOT NULL,
    source_id VARCHAR(128),
    title TEXT,
    channel_name TEXT,
    duration_seconds BIGINT,
    thumbnail_url TEXT,
    metadata_json TEXT,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_media_request_user FOREIGN KEY (telegram_user_id) REFERENCES app_users(telegram_user_id)
);

CREATE INDEX idx_media_requests_user_created ON media_requests(telegram_user_id, created_at DESC);
CREATE INDEX idx_media_requests_source_id ON media_requests(source_id);

CREATE TABLE download_jobs (
    id VARCHAR(36) PRIMARY KEY,
    request_id VARCHAR(36) NOT NULL,
    telegram_user_id BIGINT NOT NULL,
    chat_id BIGINT NOT NULL,
    progress_message_id BIGINT,
    job_type VARCHAR(24) NOT NULL,
    format_code VARCHAR(64),
    status VARCHAR(24) NOT NULL,
    progress_percent INTEGER NOT NULL DEFAULT 0,
    result_file_name TEXT,
    result_size_bytes BIGINT,
    error_code VARCHAR(64),
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_download_job_request FOREIGN KEY (request_id) REFERENCES media_requests(id),
    CONSTRAINT fk_download_job_user FOREIGN KEY (telegram_user_id) REFERENCES app_users(telegram_user_id)
);

CREATE INDEX idx_jobs_user_created ON download_jobs(telegram_user_id, created_at DESC);
CREATE INDEX idx_jobs_status ON download_jobs(status);

CREATE TABLE user_sessions (
    telegram_user_id BIGINT PRIMARY KEY,
    state VARCHAR(32) NOT NULL,
    request_id VARCHAR(36),
    payload TEXT,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_user_session_user FOREIGN KEY (telegram_user_id) REFERENCES app_users(telegram_user_id)
);
