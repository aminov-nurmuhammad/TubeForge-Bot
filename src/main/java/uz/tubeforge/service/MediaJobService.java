package uz.tubeforge.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.PageRequest;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.tubeforge.config.MediaProperties;
import uz.tubeforge.config.TelegramProperties;
import uz.tubeforge.config.FeatureProperties;
import uz.tubeforge.domain.*;
import uz.tubeforge.media.*;
import uz.tubeforge.repository.DownloadJobRepository;
import uz.tubeforge.telegram.BotMessages;
import uz.tubeforge.telegram.KeyboardFactory;
import uz.tubeforge.telegram.TelegramApiClient;
import uz.tubeforge.telegram.TelegramApiException;
import uz.tubeforge.util.SrtTextExtractor;
import uz.tubeforge.util.FilenameSanitizer;
import uz.tubeforge.ai.AiInsightResult;
import uz.tubeforge.ai.AiStudioService;
import uz.tubeforge.ai.InsightType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MediaJobService {
    private static final Logger log = LoggerFactory.getLogger(MediaJobService.class);
    private static final Pattern PROGRESS = Pattern.compile("download:\\s*([0-9.]+)%?\\|([^|]*)\\|([^|]*)");

    private final DownloadJobRepository jobs;
    private final MediaRequestService requests;
    private final UserService users;
    private final AccessService access;
    private final YtDlpCommandFactory commands;
    private final ManagedProcessRunner runner;
    private final StorageService storage;
    private final MediaDeliveryService delivery;
    private final TelegramApiClient telegram;
    private final BotMessages messages;
    private final KeyboardFactory keyboards;
    private final MediaProperties properties;
    private final TelegramProperties telegramProperties;
    private final FeatureProperties featureProperties;
    private final ArtifactCacheService artifactCache;
    private final PerformanceMetrics metrics;
    private final AiStudioService aiStudio;
    private final AiInsightCacheService insightCache;
    private final TaskExecutor executor;
    private final Clock clock;
    private final Set<String> cancellationRequested = ConcurrentHashMap.newKeySet();
    private final Map<String, Instant> lastProgressUpdate = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<MediaArtifact>> inFlightArtifacts = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<AiInsight>> inFlightInsights = new ConcurrentHashMap<>();

    public MediaJobService(DownloadJobRepository jobs, MediaRequestService requests, UserService users,
                           AccessService access, YtDlpCommandFactory commands, ManagedProcessRunner runner,
                           StorageService storage, MediaDeliveryService delivery, TelegramApiClient telegram,
                           BotMessages messages, KeyboardFactory keyboards, MediaProperties properties,
                           TelegramProperties telegramProperties, FeatureProperties featureProperties,
                           ArtifactCacheService artifactCache, PerformanceMetrics metrics,
                           AiStudioService aiStudio, AiInsightCacheService insightCache,
                           @Qualifier("mediaJobExecutor") TaskExecutor executor, Clock clock) {
        this.jobs = jobs;
        this.requests = requests;
        this.users = users;
        this.access = access;
        this.commands = commands;
        this.runner = runner;
        this.storage = storage;
        this.delivery = delivery;
        this.telegram = telegram;
        this.messages = messages;
        this.keyboards = keyboards;
        this.properties = properties;
        this.telegramProperties = telegramProperties;
        this.featureProperties = featureProperties;
        this.artifactCache = artifactCache;
        this.metrics = metrics;
        this.aiStudio = aiStudio;
        this.insightCache = insightCache;
        this.executor = executor;
        this.clock = clock;
    }

    public DownloadJob queue(long userId, String requestId, JobType type, String formatCode, ClipRange range) {
        ensureEnabled(type);
        String storedFormat = encodeFormat(formatCode, range);
        jobs.findFirstByTelegramUserIdAndRequestIdAndJobTypeAndFormatCodeAndStatusInOrderByCreatedAtDesc(
                userId, requestId, type, storedFormat,
                List.of(JobStatus.QUEUED, JobStatus.RUNNING, JobStatus.DELIVERING))
                .ifPresent(existing -> {
                    throw new MediaProcessingException("JOB_ALREADY_ACTIVE",
                            "This exact job is already queued or running. Use /jobs to check it.");
                });
        MediaRequest request = requests.requireOwned(requestId, userId);
        if (request.getSourceType() == SourceType.INSTAGRAM_REEL && !featureProperties.instagramReels()) {
            throw new MediaProcessingException("FEATURE_DISABLED", "Instagram Reels are currently disabled by the bot owner.");
        }
        AppUser user = users.require(userId);
        String artifactKey = artifactCache.key(request, type, storedFormat, user);
        boolean instantAvailable = artifactCache.cacheable(type) && artifactCache.isAvailable(artifactKey);
        if (!instantAvailable && !access.canCreateJob(userId)) {
            throw new MediaProcessingException("DAILY_LIMIT", "You have reached your processing limit for the last 24 hours.");
        }
        if (storedFormat.length() > 64) throw new IllegalArgumentException("Format selection is too long");
        DownloadJob job = DownloadJob.queued(request, type, storedFormat, clock.instant());
        jobs.save(job);
        if (instantAvailable) {
            Optional<MediaArtifact> artifact = artifactCache.find(artifactKey);
            if (artifact.isPresent() && deliverImmediately(job, artifact.orElseThrow(), requests.info(request))) {
                return job;
            }
        }

        final uz.tubeforge.telegram.model.TgMessage progress;
        try {
            progress = telegram.sendMessage(request.getChatId(), messages.processing(label(type), 0, "Queued"),
                    keyboards.cancelJob(job.getId()));
        } catch (RuntimeException e) {
            job.fail("TELEGRAM_MESSAGE_FAILED", "Could not create the progress message", clock.instant());
            jobs.save(job);
            throw e;
        }
        job.setProgressMessageId(progress.messageId());
        jobs.save(job);
        try {
            executor.execute(() -> execute(job.getId()));
        } catch (TaskRejectedException e) {
            job.fail("QUEUE_FULL", "The processing queue is full", clock.instant());
            jobs.save(job);
            safeEdit(job, "⚠️ <b>Server is busy</b>\n\nThe processing queue is full. Please try again shortly.", null);
            throw new MediaProcessingException("QUEUE_FULL", "The processing queue is full. Please try again shortly.", e);
        }
        return job;
    }

    private boolean deliverImmediately(DownloadJob job, MediaArtifact artifact, MediaInfo info) {
        try {
            job.start(clock.instant());
            jobs.save(job);
            artifactCache.deliver(artifact, job.getChatId(), info);
            job.complete(artifact.getResultFileName() == null ? "cached media" : artifact.getResultFileName(),
                    artifact.getSizeBytes() == null ? 0 : artifact.getSizeBytes(), clock.instant());
            jobs.save(job);
            return true;
        } catch (TelegramApiException e) {
            log.info("Cached Telegram file {} became invalid; rebuilding it", artifact.getCacheKey());
            artifactCache.invalidate(artifact.getCacheKey());
            job.requeue();
            jobs.save(job);
            return false;
        }
    }

    public boolean cancel(long userId, String jobId) {
        DownloadJob job = jobs.findById(jobId).orElseThrow(() -> new IllegalArgumentException("Job was not found"));
        if (!job.getTelegramUserId().equals(userId) && !access.isAdmin(userId)) throw new SecurityException("Not your job");
        if (Set.of(JobStatus.COMPLETED, JobStatus.FAILED, JobStatus.CANCELLED).contains(job.getStatus())) return false;
        cancellationRequested.add(jobId);
        runner.cancel(jobId);
        job.cancel(clock.instant());
        jobs.save(job);
        safeEdit(job, "❌ <b>Job cancelled</b>\n\nNo file was delivered.", null);
        return true;
    }

    @Transactional(readOnly = true)
    public List<DownloadJob> recent(long userId, int limit) {
        return jobs.findByTelegramUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public long activeCount() {
        return jobs.countByStatusIn(List.of(JobStatus.QUEUED, JobStatus.RUNNING, JobStatus.DELIVERING));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverUnfinishedJobs() {
        if (!telegramConfigured()) return;
        List<DownloadJob> unfinished = jobs.findByStatusIn(List.of(JobStatus.QUEUED, JobStatus.RUNNING, JobStatus.DELIVERING));
        for (DownloadJob job : unfinished) {
            job.requeue();
            jobs.save(job);
            try {
                executor.execute(() -> execute(job.getId()));
            } catch (TaskRejectedException e) {
                log.warn("Recovery queue is full; job {} remains queued", job.getId());
                break;
            }
        }
    }

    private void execute(String jobId) {
        DownloadJob job = jobs.findById(jobId).orElse(null);
        if (job == null || cancellationRequested.contains(jobId) || job.getStatus() == JobStatus.CANCELLED) return;
        String flightKey = null;
        CompletableFuture<MediaArtifact> ownedFlight = null;
        String insightFlightKey = null;
        CompletableFuture<AiInsight> ownedInsightFlight = null;
        try {
            job.start(clock.instant());
            jobs.save(job);
            safeProgress(job, 1, "Checking the instant cache");

            MediaRequest request = requests.requireOwned(job.getRequestId(), job.getTelegramUserId());
            MediaInfo info = requests.info(request);
            AppUser user = users.require(job.getTelegramUserId());

            InsightType insightType = insightType(job.getJobType());
            if (insightType != null) {
                insightFlightKey = insightCache.key(request, job, user, insightType);
                Optional<AiInsight> cachedInsight = insightCache.find(insightFlightKey);
                if (cachedInsight.isPresent()) {
                    deliverInsight(job, cachedInsight.orElseThrow().getContent(),
                            cachedInsight.orElseThrow().getProvider(), true);
                    return;
                }
                CompletableFuture<AiInsight> candidate = new CompletableFuture<>();
                CompletableFuture<AiInsight> existing = inFlightInsights.putIfAbsent(insightFlightKey, candidate);
                if (existing != null) {
                    metrics.coalescedJob();
                    safeProgress(job, 3, "Reusing identical AI work already in progress");
                    AiInsight shared = existing.get(Math.max(1, properties.processTimeout().toSeconds()), TimeUnit.SECONDS);
                    deliverInsight(job, shared.getContent(), shared.getProvider(), true);
                    return;
                }
                ownedInsightFlight = candidate;
            }

            if (artifactCache.cacheable(job.getJobType())) {
                flightKey = artifactCache.key(request, job, user);
                Optional<MediaArtifact> cached = artifactCache.find(flightKey);
                if (cached.isPresent() && deliverCached(job, cached.orElseThrow(), info)) return;

                CompletableFuture<MediaArtifact> candidate = new CompletableFuture<>();
                CompletableFuture<MediaArtifact> existing = inFlightArtifacts.putIfAbsent(flightKey, candidate);
                if (existing != null) {
                    metrics.coalescedJob();
                    safeProgress(job, 3, "Reusing an identical job already in progress");
                    MediaArtifact shared = existing.get(Math.max(1, properties.processTimeout().toSeconds()), TimeUnit.SECONDS);
                    if (shared != null && deliverCached(job, shared, info)) return;
                } else {
                    ownedFlight = candidate;
                }
            }

            safeProgress(job, 4, "Starting media tools");
            JobSpec spec = decodeFormat(job.getFormatCode());
            Path directory = storage.jobDirectory(jobId);
            List<String> command = commands.download(job.getJobType(), spec.format(), request.getSourceUrl(),
                    directory, spec.range(), properties.maxPlaylistItems());
            ProcessResult result = runner.run(jobId, command, directory, properties.processTimeout(),
                    line -> handleProgress(jobId, line));

            if (cancellationRequested.remove(jobId) || jobStatus(jobId) == JobStatus.CANCELLED) return;
            if (result.timedOut()) throw new MediaProcessingException("PROCESS_TIMEOUT", "Processing exceeded the configured time limit.");
            if (!result.successful()) throw classifyFailure(result.output());

            job = jobs.findById(jobId).orElseThrow();
            job.delivering();
            job.progress(100);
            jobs.save(job);
            safeEdit(job, "📤 <b>Uploading your result…</b>\n\nProcessing is complete.", null);

            DeliverySummary delivered = prepareAndDeliver(job, directory, info, user);
            if (ownedInsightFlight != null && delivered.aiInsight() != null) {
                ownedInsightFlight.complete(delivered.aiInsight());
            }
            if (delivered.aiInsight() != null) return;
            if (ownedFlight != null && flightKey != null && delivered.cacheDelivery() != null) {
                Optional<MediaArtifact> stored = artifactCache.store(flightKey, request, job, delivered.cacheDelivery());
                ownedFlight.complete(stored.orElse(null));
            } else if (ownedFlight != null) {
                ownedFlight.complete(null);
            }
            job.complete(delivered.name(), delivered.totalBytes(), clock.instant());
            jobs.save(job);
            safeDelete(job);
        } catch (MediaProcessingException e) {
            completeFlightExceptionally(ownedFlight, e);
            completeInsightFlightExceptionally(ownedInsightFlight, e);
            fail(jobId, e.getCode(), e.getUserMessage());
        } catch (TelegramApiException e) {
            completeFlightExceptionally(ownedFlight, e);
            completeInsightFlightExceptionally(ownedInsightFlight, e);
            fail(jobId, "TELEGRAM_UPLOAD_FAILED", "Telegram could not accept the generated file. " + safeError(e.getMessage()));
        } catch (Exception e) {
            completeFlightExceptionally(ownedFlight, e);
            completeInsightFlightExceptionally(ownedInsightFlight, e);
            log.error("Unexpected media job failure {}", jobId, e);
            fail(jobId, "INTERNAL_ERROR", "An unexpected error occurred while processing this media.");
        } finally {
            if (ownedFlight != null && !ownedFlight.isDone()) {
                ownedFlight.completeExceptionally(new MediaProcessingException("JOB_INTERRUPTED",
                        "The shared media job ended before a reusable result was created."));
            }
            if (ownedInsightFlight != null && !ownedInsightFlight.isDone()) {
                ownedInsightFlight.completeExceptionally(new MediaProcessingException("AI_JOB_INTERRUPTED",
                        "The shared AI job ended before a reusable result was created."));
            }
            if (flightKey != null && ownedFlight != null) inFlightArtifacts.remove(flightKey, ownedFlight);
            if (insightFlightKey != null && ownedInsightFlight != null) {
                inFlightInsights.remove(insightFlightKey, ownedInsightFlight);
            }
            lastProgressUpdate.remove(jobId);
            cancellationRequested.remove(jobId);
        }
    }

    private boolean deliverCached(DownloadJob job, MediaArtifact artifact, MediaInfo info) {
        try {
            job.delivering();
            job.progress(100);
            jobs.save(job);
            safeEdit(job, "⚡ <b>Instant cache hit</b>\n\nSending the already prepared Telegram file…", null);
            artifactCache.deliver(artifact, job.getChatId(), info);
            job.complete(artifact.getResultFileName() == null ? "cached media" : artifact.getResultFileName(),
                    artifact.getSizeBytes() == null ? 0 : artifact.getSizeBytes(), clock.instant());
            jobs.save(job);
            safeDelete(job);
            return true;
        } catch (TelegramApiException e) {
            log.info("Cached Telegram file {} became invalid; rebuilding it", artifact.getCacheKey());
            artifactCache.invalidate(artifact.getCacheKey());
            job.start(clock.instant());
            jobs.save(job);
            return false;
        }
    }

    private void completeFlightExceptionally(CompletableFuture<MediaArtifact> future, Throwable error) {
        if (future != null && !future.isDone()) future.completeExceptionally(error);
    }

    private void completeInsightFlightExceptionally(CompletableFuture<AiInsight> future, Throwable error) {
        if (future != null && !future.isDone()) future.completeExceptionally(error);
    }

    private DeliverySummary prepareAndDeliver(DownloadJob job, Path directory, MediaInfo info, AppUser user) {
        List<Path> outputs = storage.resultFiles(directory);
        if (outputs.isEmpty()) throw new MediaProcessingException("OUTPUT_MISSING", "No output file was created.");
        if (job.getJobType() == JobType.ALL_THUMBNAILS) {
            List<Path> images = outputs.stream().filter(this::isImage).toList();
            if (images.isEmpty()) {
                throw new MediaProcessingException("THUMBNAIL_MISSING", "YouTube did not return any downloadable thumbnails.");
            }
            Path zip = storage.zip(directory, images, "TubeForge-thumbnails.zip");
            var result = delivery.deliver(job.getChatId(), zip, JobType.ALL_THUMBNAILS, info, user);
            return new DeliverySummary(zip.getFileName().toString(), result.totalBytes(), null, null);
        }
        if (job.getJobType() == JobType.TRANSCRIPT) {
            Path subtitle = outputs.stream().filter(path -> extension(path).equals("srt")).findFirst()
                    .orElseThrow(() -> new MediaProcessingException("NO_SUBTITLES", "No subtitles were available for this language."));
            try {
                String text = SrtTextExtractor.extract(Files.readString(subtitle));
                String language = FilenameSanitizer.sanitize(job.getFormatCode().split("@")[0], "unknown");
                Path transcript = storage.writeText(directory, "TubeForge-transcript-" + language + ".txt", text);
                var result = delivery.deliver(job.getChatId(), transcript, JobType.TRANSCRIPT, info, user);
                return new DeliverySummary(transcript.getFileName().toString(), result.totalBytes(), result, null);
            } catch (IOException e) {
                throw new MediaProcessingException("TRANSCRIPT_FAILED", "The transcript could not be created.", e);
            }
        }
        InsightType insightType = insightType(job.getJobType());
        if (insightType != null) {
            Path subtitle = outputs.stream().filter(path -> extension(path).equals("srt")).findFirst()
                    .orElseThrow(() -> new MediaProcessingException("NO_SUBTITLES",
                            "Transcript Studio needs subtitles, but none were available for this language."));
            try {
                String srt = Files.readString(subtitle);
                AiInsightResult result = aiStudio.generate(insightType, srt, info, user.getLanguage());
                MediaRequest request = requests.requireOwned(job.getRequestId(), job.getTelegramUserId());
                String key = insightCache.key(request, job, user, insightType);
                AiInsight stored = insightCache.store(key, request, job, user, insightType, result);
                deliverInsight(job, result.content(), result.provider(), false);
                return new DeliverySummary("AI " + insightType.name().toLowerCase(Locale.ROOT),
                        result.content().length(), null, stored);
            } catch (IOException e) {
                throw new MediaProcessingException("AI_TRANSCRIPT_FAILED",
                        "Transcript Studio could not read the subtitle transcript.", e);
            }
        }
        if (job.getJobType() == JobType.PLAYLIST_AUDIO || job.getJobType() == JobType.PLAYLIST_VIDEO) {
            long total = 0;
            int delivered = 0;
            for (Path output : outputs) {
                if (!matchesMediaType(output, job.getJobType())) continue;
                total += delivery.deliver(job.getChatId(), output, job.getJobType(), info, user).totalBytes();
                delivered++;
            }
            if (delivered == 0) throw new MediaProcessingException("OUTPUT_MISSING", "No playlist items were created.");
            return new DeliverySummary(delivered + " playlist items", total, null, null);
        }
        Path output = selectOutput(outputs, job.getJobType());
        var result = delivery.deliver(job.getChatId(), output, job.getJobType(), info, user);
        return new DeliverySummary(output.getFileName().toString(), result.totalBytes(), result, null);
    }

    private Path selectOutput(List<Path> outputs, JobType type) {
        return outputs.stream().filter(path -> switch (type) {
                    case THUMBNAIL -> isImage(path);
                    case SUBTITLES -> extension(path).equals("srt");
                    case AUDIO, CLIP_AUDIO -> isAudio(path);
                    case VIDEO, CLIP_VIDEO -> isVideo(path);
                    case AI_SUMMARY, AI_CHAPTERS, AI_STUDY_NOTES -> extension(path).equals("srt");
                    default -> true;
                }).findFirst()
                .orElseThrow(() -> new MediaProcessingException("OUTPUT_MISSING", "The requested output format was not created."));
    }

    private void handleProgress(String jobId, String line) {
        Matcher matcher = PROGRESS.matcher(line);
        if (!matcher.find()) return;
        int percent;
        try { percent = (int) Math.floor(Double.parseDouble(matcher.group(1))); }
        catch (NumberFormatException ignored) { return; }
        Instant now = clock.instant();
        Instant previous = lastProgressUpdate.get(jobId);
        if (percent < 100 && previous != null && Duration.between(previous, now).compareTo(properties.progressUpdateInterval()) < 0) return;
        lastProgressUpdate.put(jobId, now);
        DownloadJob job = jobs.findById(jobId).orElse(null);
        if (job == null || job.getStatus() == JobStatus.CANCELLED) return;
        job.progress(percent);
        jobs.save(job);
        String speed = matcher.group(2).strip();
        String eta = matcher.group(3).strip();
        safeProgress(job, percent, (speed.isBlank() ? "" : "Speed: " + speed) + (eta.isBlank() ? "" : " • ETA: " + eta));
    }

    private void safeProgress(DownloadJob job, int percent, String detail) {
        safeEdit(job, messages.processing(label(job.getJobType()), percent, detail), keyboards.cancelJob(job.getId()));
    }

    private void safeEdit(DownloadJob job, String text, uz.tubeforge.telegram.model.InlineKeyboard keyboard) {
        if (job.getProgressMessageId() == null) return;
        try {
            telegram.editMessage(job.getChatId(), job.getProgressMessageId(), text, keyboard);
        } catch (TelegramApiException e) {
            if (e.getMessage() == null || !e.getMessage().contains("message is not modified")) {
                log.debug("Could not update progress message for {}: {}", job.getId(), e.getMessage());
            }
        }
    }

    private void safeDelete(DownloadJob job) {
        if (job.getProgressMessageId() == null) return;
        try {
            telegram.deleteMessage(job.getChatId(), job.getProgressMessageId());
        } catch (TelegramApiException e) {
            // A user may have deleted the progress card, or Telegram may have expired it.
            log.debug("Could not remove progress message for {}: {}", job.getId(), e.getMessage());
        }
    }

    private void fail(String jobId, String code, String userMessage) {
        DownloadJob job = jobs.findById(jobId).orElse(null);
        if (job == null || job.getStatus() == JobStatus.CANCELLED) return;
        job.fail(code, safeError(userMessage), clock.instant());
        jobs.save(job);
        safeEdit(job, "❌ <b>Processing failed</b>\n\n" + uz.tubeforge.util.Html.escape(userMessage)
                + "\n\n<code>" + code + "</code>", null);
    }

    private MediaProcessingException classifyFailure(String output) {
        String lower = output == null ? "" : output.toLowerCase(Locale.ROOT);
        if (lower.contains("requested format is not available")) {
            return new MediaProcessingException("FORMAT_UNAVAILABLE", "That format is no longer available. Please inspect the link again.");
        }
        if (lower.contains("no subtitles") || lower.contains("there are no subtitles")) {
            return new MediaProcessingException("NO_SUBTITLES", "No subtitles are available in the selected language.");
        }
        if (lower.contains("instagram") && (lower.contains("login required")
                || lower.contains("checkpoint") || lower.contains("please log in")
                || lower.contains("log in to see"))) {
            return new MediaProcessingException("INSTAGRAM_AUTH_REQUIRED",
                    "Instagram requires a public session for this Reel. Private or login-only Reels are not supported.");
        }
        if (lower.contains("instagram") && (lower.contains("http error 429") || lower.contains("too many requests"))) {
            return new MediaProcessingException("INSTAGRAM_RATE_LIMITED",
                    "Instagram temporarily rate-limited this server. Wait a few minutes and try again.");
        }
        if (lower.contains("instagram") && lower.contains("private")) {
            return new MediaProcessingException("INSTAGRAM_PRIVATE",
                    "This Instagram Reel is private or restricted.");
        }
        if (lower.contains("private video")) return new MediaProcessingException("PRIVATE_VIDEO", "This video is private.");
        if (lower.contains("sign in to confirm you’re not a bot")
                || lower.contains("sign in to confirm you're not a bot")
                || lower.contains("use --cookies for the authentication")) {
            return new MediaProcessingException("YOUTUBE_AUTH_REQUIRED",
                    "YouTube asked this server to verify itself. Configure YOUTUBE_COOKIES_FILE or try again later.");
        }
        if (lower.contains("http error 429") || lower.contains("too many requests")) {
            return new MediaProcessingException("YOUTUBE_RATE_LIMITED",
                    "YouTube temporarily rate-limited this server. Wait a few minutes and try again.");
        }
        if (lower.contains("ffmpeg not found") || lower.contains("ffprobe not found")) {
            return new MediaProcessingException("FFMPEG_MISSING",
                    "FFmpeg could not be found. Check FFMPEG_PATH and FFPROBE_PATH.");
        }
        if (lower.contains("postprocessing") && lower.contains("error")) {
            return new MediaProcessingException("POSTPROCESSING_FAILED",
                    "The media downloaded, but FFmpeg could not create the requested output format.");
        }
        if (lower.contains("video unavailable") || lower.contains("not available")) {
            return new MediaProcessingException("UNAVAILABLE", "The video became unavailable or is restricted in the server's region.");
        }
        if (lower.contains("disk") && lower.contains("space")) {
            return new MediaProcessingException("DISK_FULL", "The server does not have enough free storage.");
        }
        return new MediaProcessingException("DOWNLOAD_FAILED", "The media tool could not complete this request. Try another format or try again later.");
    }

    private String encodeFormat(String format, ClipRange range) {
        String base = format == null ? "" : format;
        if (range == null) return base;
        return base + "@" + range.startFormatted() + "-" + range.endFormatted();
    }

    private JobSpec decodeFormat(String stored) {
        if (stored == null) return new JobSpec("", null);
        int at = stored.indexOf('@');
        if (at < 0) return new JobSpec(stored, null);
        return new JobSpec(stored.substring(0, at), ClipRange.parse(stored.substring(at + 1)));
    }

    private JobStatus jobStatus(String id) {
        return jobs.findById(id).map(DownloadJob::getStatus).orElse(JobStatus.FAILED);
    }

    private String label(JobType type) {
        return switch (type) {
            case VIDEO -> "Downloading video";
            case AUDIO -> "Extracting audio";
            case THUMBNAIL, ALL_THUMBNAILS -> "Preparing thumbnails";
            case SUBTITLES -> "Preparing subtitles";
            case TRANSCRIPT -> "Creating transcript";
            case CLIP_VIDEO, CLIP_AUDIO -> "Creating clip";
            case PLAYLIST_VIDEO, PLAYLIST_AUDIO -> "Processing playlist";
            case AI_SUMMARY -> "Creating transcript summary";
            case AI_CHAPTERS -> "Finding chapters and key moments";
            case AI_STUDY_NOTES -> "Creating study notes";
        };
    }

    private boolean telegramConfigured() {
        return telegramProperties.configured();
    }

    private void ensureEnabled(JobType type) {
        boolean enabled = switch (type) {
            case VIDEO -> featureProperties.videoDownload();
            case AUDIO -> featureProperties.audioDownload();
            case THUMBNAIL, ALL_THUMBNAILS -> featureProperties.thumbnails();
            case SUBTITLES -> featureProperties.subtitles();
            case TRANSCRIPT -> featureProperties.transcripts();
            case CLIP_VIDEO, CLIP_AUDIO -> featureProperties.clips();
            case PLAYLIST_VIDEO, PLAYLIST_AUDIO -> featureProperties.playlists();
            case AI_SUMMARY, AI_CHAPTERS, AI_STUDY_NOTES -> featureProperties.aiStudio();
        };
        if (!enabled) {
            throw new MediaProcessingException("FEATURE_DISABLED", "This tool is currently disabled by the bot owner.");
        }
    }

    private boolean matchesMediaType(Path path, JobType type) {
        return type == JobType.PLAYLIST_AUDIO ? isAudio(path) : isVideo(path);
    }

    private boolean isVideo(Path path) { return Set.of("mp4", "mkv", "webm", "mov").contains(extension(path)); }
    private boolean isAudio(Path path) { return Set.of("mp3", "m4a", "ogg", "opus", "wav", "flac", "aac").contains(extension(path)); }
    private boolean isImage(Path path) { return Set.of("jpg", "jpeg", "png", "webp").contains(extension(path)); }
    private String extension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
    private String safeError(String value) {
        if (value == null || value.isBlank()) return "Unknown error";
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }

    private InsightType insightType(JobType type) {
        return switch (type) {
            case AI_SUMMARY -> InsightType.SUMMARY;
            case AI_CHAPTERS -> InsightType.CHAPTERS;
            case AI_STUDY_NOTES -> InsightType.STUDY_NOTES;
            default -> null;
        };
    }

    private void deliverInsight(DownloadJob job, String content, String provider, boolean cached) {
        job.delivering();
        job.progress(100);
        jobs.save(job);
        String text = "🧠 <b>TubeForge Transcript Studio</b>\n"
                + (cached ? "⚡ Instant insight cache\n" : "")
                + "<i>Engine: " + uz.tubeforge.util.Html.escape(provider) + "</i>\n\n"
                + uz.tubeforge.util.Html.escape(content);
        telegram.sendLongMessage(job.getChatId(), text);
        job.complete("AI insight", content.length(), clock.instant());
        jobs.save(job);
        safeDelete(job);
    }

    private record JobSpec(String format, ClipRange range) {}
    private record DeliverySummary(String name, long totalBytes, MediaDeliveryService.DeliveryResult cacheDelivery,
                                   AiInsight aiInsight) {}
}
