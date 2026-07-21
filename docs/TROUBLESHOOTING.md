# Troubleshooting

## Bot does not answer

1. Confirm `TELEGRAM_BOT_TOKEN` is present in `.env` and is not the example value.
2. Stop every other process using the same token. Telegram permits only one long-polling consumer; a second one returns HTTP 409.
3. Open `http://localhost:8080/actuator/health` and inspect the application log.

## `PROCESS_START_FAILED`

The configured executable cannot be started. On the Windows layout used by TubeForge, `.env` may contain:

```dotenv
YT_DLP_PATH=C:/TubeForgeTools/yt-dlp.exe
FFMPEG_PATH=C:/TubeForgeTools/ffmpeg/bin/ffmpeg.exe
FFPROBE_PATH=C:/TubeForgeTools/ffmpeg/bin/ffprobe.exe
```

The supplied `scripts/run-windows.ps1` detects these paths automatically and prints versions before starting Spring Boot.

## `YOUTUBE_AUTH_REQUIRED`

YouTube asked the host to confirm it is not automated traffic. TubeForge does not decrypt browser databases or bypass account controls. For your own authorized content, export a Netscape-format cookie file and set its private local path with `YOUTUBE_COOKIES_FILE`. Never commit or share that file.

## `YOUTUBE_RATE_LIMITED`

Stop repeated retries and wait. Keep `MAX_CONCURRENT_INSPECTIONS` and `YT_DLP_CONCURRENT_FRAGMENTS` conservative. A cookies file can help only when YouTube expects an authenticated session; it cannot guarantee that an IP address will not be limited.

## Video playback or sound

Version 2 verifies both streams before upload. Inline video is remuxed or transcoded to an MP4 containing H.264 video and AAC audio. If Telegram still rejects inline playback, TubeForge sends the valid file as a document and records the original API error.

## Audio output

- MP3 and M4A are sent with Telegram's audio player.
- WAV, FLAC and OGG are sent as documents because Telegram's inline audio endpoint does not reliably accept those containers.
- WAV and FLAC are lossless, so the bitrate selection step is skipped.

## Preview image

TubeForge first asks Telegram to fetch the YouTube thumbnail URL. If Telegram cannot fetch it, the bot retains the complete text preview and every action button. A downloaded thumbnail job is uploaded locally and falls back to a document if Telegram rejects it as a photo.

## Development build

```bash
./mvnw --batch-mode clean verify
```

Tests use an isolated in-memory H2 database, disabled Telegram polling and temporary storage under `target/`.
