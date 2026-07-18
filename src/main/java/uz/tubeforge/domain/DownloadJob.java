package uz.tubeforge.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "download_jobs")
public class DownloadJob {
    @Id
    private String id;

    @Column(name = "request_id", nullable = false)
    private String requestId;

    @Column(name = "telegram_user_id", nullable = false)
    private Long telegramUserId;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "progress_message_id")
    private Long progressMessageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false)
    private JobType jobType;

    @Column(name = "format_code")
    private String formatCode;

    @Enumerated(EnumType.STRING)
    private JobStatus status;

    @Column(name = "progress_percent")
    private int progressPercent;

    @Column(name = "result_file_name", columnDefinition = "TEXT")
    private String resultFileName;

    @Column(name = "result_size_bytes")
    private Long resultSizeBytes;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected DownloadJob() {
    }

    public static DownloadJob queued(MediaRequest request, JobType type, String format, Instant now) {
        var job = new DownloadJob();
        job.id = UUID.randomUUID().toString();
        job.requestId = request.getId();
        job.telegramUserId = request.getTelegramUserId();
        job.chatId = request.getChatId();
        job.jobType = type;
        job.formatCode = format;
        job.status = JobStatus.QUEUED;
        job.createdAt = now;
        return job;
    }

    public void start(Instant now) { status = JobStatus.RUNNING; startedAt = now; }
    public void delivering() { status = JobStatus.DELIVERING; }
    public void progress(int value) { progressPercent = Math.max(progressPercent, Math.min(100, value)); }
    public void complete(String fileName, long size, Instant now) {
        status = JobStatus.COMPLETED;
        progressPercent = 100;
        resultFileName = fileName;
        resultSizeBytes = size;
        completedAt = now;
    }
    public void fail(String code, String message, Instant now) {
        status = JobStatus.FAILED;
        errorCode = code;
        errorMessage = message;
        completedAt = now;
    }
    public void cancel(Instant now) { status = JobStatus.CANCELLED; completedAt = now; }
    public void requeue() {
        status = JobStatus.QUEUED;
        progressPercent = 0;
        startedAt = null;
        completedAt = null;
        errorCode = null;
        errorMessage = null;
    }
    public void setProgressMessageId(long id) { progressMessageId = id; }

    public String getId() { return id; }
    public String getRequestId() { return requestId; }
    public Long getTelegramUserId() { return telegramUserId; }
    public Long getChatId() { return chatId; }
    public Long getProgressMessageId() { return progressMessageId; }
    public JobType getJobType() { return jobType; }
    public String getFormatCode() { return formatCode; }
    public JobStatus getStatus() { return status; }
    public int getProgressPercent() { return progressPercent; }
    public String getResultFileName() { return resultFileName; }
    public Long getResultSizeBytes() { return resultSizeBytes; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
