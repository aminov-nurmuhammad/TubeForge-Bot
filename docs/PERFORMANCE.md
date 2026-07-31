# Performance and capacity

## Fast paths

1. **Direct Reel delivery:** URL parsing and one database insert immediately queue the original video; no card, inspection or second user action is on the critical path.
2. **Instant YouTube shell:** one database insert makes one-tap actions available without waiting for yt-dlp.
3. **Metadata hit:** a normalized URL is copied from PostgreSQL/H2 into a new user-owned request without running yt-dlp.
4. **Artifact hit:** Telegram receives an existing `file_id`; source download, local storage, FFmpeg and binary upload are skipped.
5. **Single flight:** concurrent cache misses share one inspection or one media build.
6. **Original combined MP4:** Instagram's combined MP4 is preferred before any merge.
7. **Zero conversion:** H.264/AAC MP4 passes one FFprobe call and is uploaded unchanged; only incompatible media invokes FFmpeg.
8. **Ordered concurrent updates:** one chat keeps strict update order, while independent chats use separate bounded workers.
9. **Failure cooldown:** repeated requests for the same recently rejected URL reuse the actionable failure instead of hammering the source.
10. **Smart format fallback:** exact YouTube quality actions prefer Telegram-ready streams and keep the requested height if a source format ID expires.

## Capacity model

Cached delivery is mostly Telegram API and database latency, so one modest instance can serve large repeat traffic. Uncached media is bounded by CPU, YouTube bandwidth, Telegram upload bandwidth and the 50 MB hosted Bot API target. No application can make a new multi-megabyte upload complete in milliseconds.

Keep queues bounded. A large queue protects users from immediate rejection but does not create CPU or bandwidth. Scale concurrency only after measuring `tubeforge.cache.*`, `tubeforge.telegram.*`, active jobs, process duration, host load and rate-limit errors.

## Recommended profiles

| Host | Media jobs | Inspections | Queue |
|---|---:|---:|---:|
| Personal 2-core PC | 1 | 2 | 100-200 |
| 4-core server | 2 | 4 | 500 |
| 8-core server | 3-4 | 8 | 1000 |

These are starting points, not guarantees. Video transcoding, playlists and lossless audio can change resource usage dramatically.

## Horizontal scaling boundary

Telegram long polling allows one active consumer per bot token. TubeForge 8 is optimized as a production-grade modular monolith on one host. A future multi-node deployment should separate update ingestion from workers and add a distributed queue/lock; running several identical polling instances with the same token causes Telegram HTTP 409 and is not supported.
