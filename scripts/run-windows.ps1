$ErrorActionPreference = "Stop"

if (-not (Test-Path ".env")) {
    Write-Host "Create .env from .env.example and add your Telegram bot token first." -ForegroundColor Yellow
    exit 1
}

docker compose up --build
