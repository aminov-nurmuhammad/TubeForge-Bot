# Deployment guide

## Zero-cost choices

The truly zero-cost option is an existing computer that can stay online while the bot is needed. Docker Compose needs no domain and receives Telegram updates through outbound long polling. A cloud provider's free tier may also work, but free-tier limits and availability can change.

## Docker Compose

```bash
cp .env.example .env
# Add TELEGRAM_BOT_TOKEN to .env
docker compose up --build -d
docker compose logs -f bot
```

The Compose stack includes PostgreSQL and persistent volumes. Restarting or rebuilding the bot does not erase user history.

## Upgrade

```bash
git pull
docker compose up --build -d
docker image prune
```

The application runs Flyway migrations automatically before Hibernate validates the schema.

## Back up

Create a PostgreSQL dump:

```bash
docker compose exec -T postgres pg_dump -U tubeforge tubeforge > tubeforge-backup.sql
```

Restore into a stopped/empty database as appropriate:

```bash
docker compose exec -T postgres psql -U tubeforge tubeforge < tubeforge-backup.sql
```

Temporary media does not normally need backup.

## Resource tuning

For a small machine, keep:

```dotenv
MAX_CONCURRENT_JOBS=1
MAX_CONCURRENT_INSPECTIONS=2
MAX_QUEUED_JOBS=200
MAX_PLAYLIST_ITEMS=10
MAX_VIDEO_DURATION_SECONDS=7200
```

FFmpeg conversion is CPU-intensive. Increasing concurrency is useful only when the host has enough CPU, memory, disk and outbound bandwidth.

For a larger single host, begin with one media process per two CPU cores, four to eight inspection workers and a bounded queue. The global `file_id` cache serves repeat traffic without consuming media-worker capacity. Watch `/actuator/metrics`, PostgreSQL connections, disk space and outbound bandwidth before raising concurrency.

## Optional local LLM

The default AI engine needs no extra process. To enable deeper Ollama output, run Ollama on the same host, pull a model, then set `AI_PROVIDER=ollama`, `OLLAMA_MODEL` and `OLLAMA_BASE_URL`. In Docker Desktop the host URL is commonly `http://host.docker.internal:11434`; on Linux Compose, explicitly expose or network the Ollama service. Failure automatically falls back to the built-in engine.

## Private mode

```dotenv
ACCESS_MODE=ALLOWLIST
ADMIN_USER_IDS=123456789
ALLOWED_USER_IDS=123456789,987654321
```

Restart after changes. `/id` reveals the numeric ID needed for configuration.

## Troubleshooting

| Symptom | Action |
|---|---|
| Bot does not reply | Check the token and `docker compose logs -f bot` |
| `409 Conflict` while polling | Stop the other instance using the same bot token or remove its webhook |
| Link inspection suddenly fails | Rebuild with a current stable yt-dlp |
| `YOUTUBE_AUTH_REQUIRED` | Export authorized cookies to a private Netscape `cookies.txt` and set `YOUTUBE_COOKIES_FILE` |
| `YOUTUBE_RATE_LIMITED` | Wait before retrying; avoid high concurrency; optionally configure authorized cookies |
| Video arrives without sound | Update to the current build; it verifies and normalizes video/audio streams before upload |
| Health reports FFmpeg/yt-dlp down | Rebuild the Docker image or install missing tools locally |
| Upload rejected | Lower quality, enable auto-compress, or reduce `TELEGRAM_MAX_UPLOAD_BYTES` |
| Disk usage grows | Reduce `MEDIA_CACHE_RETENTION` and playlist limits |
| Database connection fails | Wait for PostgreSQL health or verify Compose credentials |
