# Security policy

## Secrets

Never commit `.env`, Telegram bot tokens, database passwords, cookies, browser profiles, or user media. TubeForge intentionally does not support cookies or commands supplied by Telegram users.

If a token is exposed, revoke it immediately through `@BotFather`, create a new token, update the deployment, and remove the exposed value from Git history.

## Supported content

TubeForge does not attempt to bypass private, paid, members-only, DRM-protected, age-gated, or otherwise access-controlled media. URLs are restricted to recognized YouTube domains and external tools receive arguments through `ProcessBuilder`, not through a shell.

## Reporting

For a private deployment, report security problems directly to the repository owner. Do not include real bot tokens, private links, or personal media in bug reports.
