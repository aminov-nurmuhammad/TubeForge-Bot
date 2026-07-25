# Testing

Run the complete test and packaging pipeline:

```bash
./mvnw clean verify
```

The suite covers:

- strict YouTube and Instagram Reel URL recognition and lookalike-host rejection;
- clip range parsing and duration limits;
- Telegram callback encoding and byte limits;
- subtitle-to-transcript conversion;
- metadata parsing for formats and subtitle precedence;
- compact one-tap keyboards and callback size limits;
- reusable Telegram `file_id` extraction;
- local AI summaries and timestamped chapters;
- full Spring context startup with Flyway and H2.

The JaCoCo report is created under `target/site/jacoco/index.html`.

## Manual bot smoke test

1. Send `/start` and accept terms.
2. Send one short public video you are authorized to process.
3. Test 360p/720p video, MP3 audio, best thumbnail, one subtitle and transcript.
4. Create a 10-second video and audio clip.
5. Cancel one running job.
6. Reopen the item through `/history`.
7. Change `/settings`, restart, and confirm preferences persist.
8. Test a private/deleted link and confirm the error is understandable.
9. Verify `/actuator/health` and `/admin`.
10. Request the same quality twice and confirm the second job reports an instant cache hit.
11. Run every Transcript Studio mode for one subtitle language.

Do not place real bot tokens or copyrighted test media in the repository.
