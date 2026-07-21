CREATE INDEX idx_media_requests_reusable
    ON media_requests (telegram_user_id, chat_id, status, expires_at DESC);

CREATE INDEX idx_jobs_duplicate_guard
    ON download_jobs (telegram_user_id, request_id, job_type, format_code, status, created_at DESC);
