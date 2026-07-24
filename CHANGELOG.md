# Changelog

## 5.0.0

- Rebuilt the preview as a compact, one-screen workflow: quick actions first, formats second and useful tools behind one predictable entry point.
- Video format buttons now carry the actual yt-dlp source format ID, so the highest available quality is visible and video-only streams are merged with audio instead of producing silent files.
- Adopted yt-dlp's resilient best-video fallback and removed the unconditional MP4 remux that could damage otherwise playable streams.
- Encoded format selectors safely for Telegram callback data; every generated quality button now reaches the intended router action.
- Removed persistent "completed" progress messages: the temporary progress card disappears after delivery, leaving only the result file or transcript.
- Converted callback failures to inline alerts, preventing duplicate error messages and keeping the chat readable.
- Validated videos even when users choose document delivery, preventing silent video files in that mode too.
- Added coverage for source-format selectors, 2160p visibility, callback encoding and document-delivery audio validation.

## 4.0.0

- Replaced blocking link inspection with an instant one-tap preview and background metadata hydration.
- Added explicit pending, ready and degraded metadata states; quick downloads survive YouTube inspection failures.
- Added refresh/retry UX for formats, subtitles, transcripts and other metadata-dependent tools.
- Made deterministic YouTube thumbnails available immediately and added direct best-thumbnail delivery.
- Removed the unconditional FFmpeg remux: Telegram-ready H.264/AAC MP4 now passes through unchanged after one probe.
- Renamed the default local feature to Transcript Studio and hid it when no subtitles exist; Ollama remains the optional real LLM provider.
- Improved local transcript ranking for short subtitle cues, duplicate suppression and non-empty fallbacks.
- Added an interactive admin control center for workload, cache counters, feature state and yt-dlp/FFmpeg health.
- Configured Telegram ID `1491734372` as the default owner while keeping `ADMIN_USER_IDS` overridable.
- Added callback contract, instant-preview ID extraction and media fast-path tests.

## 3.0.0

- Added a persistent global Telegram `file_id` cache: repeated media/format requests skip YouTube, FFmpeg and binary upload.
- Added single-flight coordination for identical inspections and downloads so concurrent users share one operation.
- Made metadata reuse global across users while preserving per-user request ownership.
- Added TubeForge AI Studio with free built-in smart summaries, timestamped chapters and study notes.
- Added optional Ollama integration with automatic fallback to the built-in local insight engine.
- Added persistent AI insight caching by video, mode, transcript language, output language and model.
- Rebuilt the inline interface around one-tap defaults, compact recommended qualities and a separate advanced-tools area.
- Prefer progressive MP4 formats with embedded audio when available to avoid unnecessary merge work.
- Added configurable bounded queues, cache/AI Micrometer metrics and cache statistics to the admin panel.
- Added database migrations and retention cleanup for media artifacts and AI results.

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
