# Performance and capacity

## Fast paths

1. **Metadata hit:** a normalized URL is copied from PostgreSQL/H2 into a new user-owned request without running yt-dlp.
2. **Artifact hit:** Telegram receives an existing `file_id`; YouTube, local storage, FFmpeg and binary upload are skipped.
3. **Single flight:** concurrent cache misses share one inspection or one media build.
4. **Progressive MP4:** compatible combined video+audio is preferred before separate DASH streams where the requested quality allows it.
5. **Selective conversion:** compatible H.264/AAC media is remuxed with stream copy; only incompatible streams are transcoded.

## Capacity model

Cached delivery is mostly Telegram API and database latency, so one modest instance can serve large repeat traffic. Uncached media is bounded by CPU, YouTube bandwidth, Telegram upload bandwidth and the 50 MB hosted Bot API target. No application can make a new multi-megabyte upload complete in milliseconds.

Keep queues bounded. A large queue protects users from immediate rejection but does not create CPU or bandwidth. Scale concurrency only after measuring `tubeforge.cache.*`, active jobs, process duration, host load and rate-limit errors.

## Recommended profiles

| Host | Media jobs | Inspections | Queue |
|---|---:|---:|---:|
| Personal 2-core PC | 1 | 2 | 100-200 |
| 4-core server | 2 | 4 | 500 |
| 8-core server | 3-4 | 8 | 1000 |

These are starting points, not guarantees. Video transcoding, playlists and lossless audio can change resource usage dramatically.

## Horizontal scaling boundary

Telegram long polling allows one active consumer per bot token. TubeForge 3 is optimized as a production-grade modular monolith on one host. A future multi-node deployment should separate update ingestion from workers and add a distributed queue/lock; running several identical polling instances with the same token causes Telegram HTTP 409 and is not supported.
