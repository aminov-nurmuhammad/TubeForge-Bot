package uz.tubeforge.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.PageRequest;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.tubeforge.config.MediaProperties;
import uz.tubeforge.config.TelegramProperties;
import uz.tubeforge.domain.*;
import uz.tubeforge.media.*;
import uz.tubeforge.repository.DownloadJobRepository;
import uz.tubeforge.telegram.BotMessages;
import uz.tubeforge.telegram.KeyboardFactory;
import uz.tubeforge.telegram.TelegramApiClient;
import uz.tubeforge.telegram.TelegramApiException;
import uz.tubeforge.util.SrtTextExtractor;
import uz.tubeforge.util.FilenameSanitizer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
    private final TaskExecutor executor;
    private final Clock clock;
    private final Set<String> cancellationRequested = ConcurrentHashMap.newKeySet();
    private final Map<String, Instant> lastProgressUpdate = new ConcurrentHashMap<>();

    public MediaJobService(DownloadJobRepository jobs, MediaRequestService requests, UserService users,
                           AccessService access, YtDlpCommandFactory commands, ManagedProcessRunner runner,
                           StorageService storage, MediaDeliveryService delivery, TelegramApiClient telegram,
                           BotMessages messages, KeyboardFactory keyboards, MediaProperties properties,
                           TelegramProperties telegramProperties,
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
        this.executor = executor;
        this.clock = clock;
    }

    public DownloadJob queue(long userId, String requestId, JobType type, String formatCode, ClipRange range) {
        if (!access.canCreateJob(userId)) {
            throw new MediaProcessingException("DAILY_LIMIT", "You have reached your processing limit for the last 24 hours.");
        }
        MediaRequest request = requests.requireOwned(requestId, userId);
        String storedFormat = encodeFormat(formatCode, range);
        if (storedFormat.length() > 64) throw new IllegalArgumentException("Format selection is too long");
        DownloadJob job = DownloadJob.queued(request, type, storedFormat, clock.instant());
        jobs.save(job);

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
        executor.execute(() -> execute(job.getId()));
        return job;
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
            executor.execute(() -> execute(job.getId()));
        }
    }

    private void execute(String jobId) {
        DownloadJob job = jobs.findById(jobId).orElse(null);
        if (job == null || cancellationRequested.contains(jobId) || job.getStatus() == JobStatus.CANCELLED) return;
        try {
            job.start(clock.instant());
            jobs.save(job);
            safeProgress(job, 1, "Starting media tools");

            MediaRequest request = requests.requireOwned(job.getRequestId(), job.getTelegramUserId());
            MediaInfo info = requests.info(request);
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

            DeliverySummary delivered = prepareAndDeliver(job, directory, info, users.require(job.getTelegramUserId()));
            job.complete(delivered.name(), delivered.totalBytes(), clock.instant());
            jobs.save(job);
            safeEdit(job, "✅ <b>Completed</b>\n\nYour file has been delivered successfully.", null);
        } catch (MediaProcessingException e) {
            fail(jobId, e.getCode(), e.getUserMessage());
        } catch (TelegramApiException e) {
            fail(jobId, "TELEGRAM_UPLOAD_FAILED", "Telegram could not accept the generated file. " + safeError(e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected media job failure {}", jobId, e);
            fail(jobId, "INTERNAL_ERROR", "An unexpected error occurred while processing this media.");
        } finally {
            lastProgressUpdate.remove(jobId);
            cancellationRequested.remove(jobId);
        }
    }

    private DeliverySummary prepareAndDeliver(DownloadJob job, Path directory, MediaInfo info, AppUser user) {
        List<Path> outputs = storage.resultFiles(directory);
        if (outputs.isEmpty()) throw new MediaProcessingException("OUTPUT_MISSING", "No output file was created.");
        if (job.getJobType() == JobType.ALL_THUMBNAILS) {
            List<Path> images = outputs.stream().filter(this::isImage).toList();
            Path zip = storage.zip(directory, images, "TubeForge-thumbnails.zip");
            var result = delivery.deliver(job.getChatId(), zip, JobType.ALL_THUMBNAILS, info, user);
            return new DeliverySummary(zip.getFileName().toString(), result.totalBytes());
        }
        if (job.getJobType() == JobType.TRANSCRIPT) {
            Path subtitle = outputs.stream().filter(path -> extension(path).equals("srt")).findFirst()
                    .orElseThrow(() -> new MediaProcessingException("NO_SUBTITLES", "No subtitles were available for this language."));
            try {
                String text = SrtTextExtractor.extract(Files.readString(subtitle));
                String language = FilenameSanitizer.sanitize(job.getFormatCode().split("@")[0], "unknown");
                Path transcript = storage.writeText(directory, "TubeForge-transcript-" + language + ".txt", text);
                var result = delivery.deliver(job.getChatId(), transcript, JobType.TRANSCRIPT, info, user);
                return new DeliverySummary(transcript.getFileName().toString(), result.totalBytes());
            } catch (IOException e) {
                throw new MediaProcessingException("TRANSCRIPT_FAILED", "The transcript could not be created.", e);
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
            return new DeliverySummary(delivered + " playlist items", total);
        }
        Path output = selectOutput(outputs, job.getJobType());
        var result = delivery.deliver(job.getChatId(), output, job.getJobType(), info, user);
        return new DeliverySummary(output.getFileName().toString(), result.totalBytes());
    }

    private Path selectOutput(List<Path> outputs, JobType type) {
        return outputs.stream().filter(path -> switch (type) {
                    case THUMBNAIL -> isImage(path);
                    case SUBTITLES -> extension(path).equals("srt");
                    case AUDIO, CLIP_AUDIO -> isAudio(path);
                    case VIDEO, CLIP_VIDEO -> isVideo(path);
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
        if (lower.contains("private video")) return new MediaProcessingException("PRIVATE_VIDEO", "This video is private.");
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
        };
    }

    private boolean telegramConfigured() {
        return telegramProperties.configured();
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

    private record JobSpec(String format, ClipRange range) {}
    private record DeliverySummary(String name, long totalBytes) {}
}
