#!/usr/bin/env bash
set -euo pipefail

missing=0
for command in java ffmpeg ffprobe yt-dlp; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "Missing required command: $command"
    missing=1
  fi
done

if [[ -z "${TELEGRAM_BOT_TOKEN:-}" ]]; then
  echo "TELEGRAM_BOT_TOKEN is not set. Copy .env.example to .env and add the BotFather token."
  missing=1
fi

if [[ "$missing" -ne 0 ]]; then
  exit 1
fi

echo "TubeForge preflight passed."
java -version
ffmpeg -version | head -n 1
yt-dlp --version
