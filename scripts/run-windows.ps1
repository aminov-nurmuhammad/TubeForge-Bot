param(
    [switch]$Docker,
    [switch]$SkipChecks
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $ProjectRoot

if (-not (Test-Path ".env")) {
    Copy-Item ".env.example" ".env"
    Write-Host "Created .env. Add TELEGRAM_BOT_TOKEN, then run this script again." -ForegroundColor Yellow
    exit 1
}

Get-Content ".env" | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
        $parts = $line.Split("=", 2)
        $name = $parts[0].Trim()
        $value = $parts[1].Trim().Trim('"').Trim("'")
        if ($name) { Set-Item -Path "Env:$name" -Value $value }
    }
}

if (-not $env:TELEGRAM_BOT_TOKEN -or $env:TELEGRAM_BOT_TOKEN -like "*replace_with*") {
    Write-Host "Set TELEGRAM_BOT_TOKEN in .env before starting TubeForge." -ForegroundColor Red
    exit 1
}

if ($Docker) {
    docker compose up --build
    exit $LASTEXITCODE
}

$defaultTools = @{
    YT_DLP_PATH = "C:\TubeForgeTools\yt-dlp.exe"
    FFMPEG_PATH = "C:\TubeForgeTools\ffmpeg\bin\ffmpeg.exe"
    FFPROBE_PATH = "C:\TubeForgeTools\ffmpeg\bin\ffprobe.exe"
}
$commandNames = @{
    YT_DLP_PATH = "yt-dlp"
    FFMPEG_PATH = "ffmpeg"
    FFPROBE_PATH = "ffprobe"
}

foreach ($entry in $defaultTools.GetEnumerator()) {
    $current = [Environment]::GetEnvironmentVariable($entry.Key, "Process")
    $currentExists = $current -and ((Test-Path $current) -or (Get-Command $current -ErrorAction SilentlyContinue))
    if (-not $currentExists -and (Test-Path $entry.Value)) {
        Set-Item -Path "Env:$($entry.Key)" -Value $entry.Value
    } elseif (-not $current) {
        Set-Item -Path "Env:$($entry.Key)" -Value $commandNames[$entry.Key]
    }
}

if (-not $SkipChecks) {
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
        throw "Java 17+ was not found in PATH."
    }

    foreach ($name in @("YT_DLP_PATH", "FFMPEG_PATH", "FFPROBE_PATH")) {
        $value = [Environment]::GetEnvironmentVariable($name, "Process")
        $exists = (Test-Path $value) -or (Get-Command $value -ErrorAction SilentlyContinue)
        if (-not $exists) { throw "$name does not point to an executable: $value" }
    }

    Write-Host "TubeForge preflight passed." -ForegroundColor Green
    & $env:YT_DLP_PATH --version
    & $env:FFMPEG_PATH -version 2>&1 | Select-Object -First 1
}

& ".\mvnw.cmd" spring-boot:run
exit $LASTEXITCODE
