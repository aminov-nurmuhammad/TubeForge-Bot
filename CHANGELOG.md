# Changelog

## 2.0.0

- Rebuilt yt-dlp command generation with explicit FFmpeg discovery, retries, fragment concurrency, proxy and cookies-file support.
- Fixed silent video output by requiring an audio stream and normalizing playable videos to H.264/AAC MP4 with fast-start metadata.
- Fixed MP3/M4A extraction and lossless WAV/FLAC flows; unsupported Telegram audio containers now use document delivery.
- Added inline-media upload fallbacks so Telegram photo, video or audio validation errors do not discard a valid result.
- Added thumbnail-backed previews with caption-aware inline menus and text fallback.
- Added canonical handling for scheme-less, short, mobile, embedded, Shorts, playlist and timestamped YouTube links.
- Added separate metadata and download executors, duplicate active-job prevention, bounded-queue feedback and polling backoff.
- Added metadata reuse for repeated links and more accurate merged video-size estimates.
- Removed committed runtime secrets, restored zero-configuration H2, added `.env` import and optional PostgreSQL settings.
- Rebuilt the Windows launcher with automatic detection for the `C:\TubeForgeTools` layout.
- Repaired Maven Wrapper permissions, isolated the test profile, upgraded CI actions and current build plugins.

## 1.0.0

- Initial TubeForge bot implementation.
