package uz.tubeforge.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import uz.tubeforge.config.FeatureProperties;
import uz.tubeforge.domain.*;
import uz.tubeforge.media.*;
import uz.tubeforge.repository.AppUserRepository;
import uz.tubeforge.repository.DownloadJobRepository;
import uz.tubeforge.telegram.model.*;
import uz.tubeforge.service.*;
import uz.tubeforge.util.Html;
import uz.tubeforge.util.HumanFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class TelegramUpdateRouter {
    private static final Logger log = LoggerFactory.getLogger(TelegramUpdateRouter.class);

    private final TelegramApiClient telegram;
    private final UserService users;
    private final AccessService access;
    private final SessionService sessions;
    private final MediaRequestService requests;
    private final MediaInspectionService inspection;
    private final MediaJobService jobs;
    private final YouTubeUrlParser urlParser;
    private final BotMessages messages;
    private final KeyboardFactory keyboards;
    private final FeatureProperties features;
    private final AppUserRepository userRepository;
    private final DownloadJobRepository jobRepository;
    private final TaskExecutor executor;

    public TelegramUpdateRouter(TelegramApiClient telegram, UserService users, AccessService access,
                                SessionService sessions, MediaRequestService requests,
                                MediaInspectionService inspection, MediaJobService jobs,
                                YouTubeUrlParser urlParser, BotMessages messages, KeyboardFactory keyboards,
                                FeatureProperties features, AppUserRepository userRepository,
                                DownloadJobRepository jobRepository,
                                @Qualifier("mediaInspectionExecutor") TaskExecutor executor) {
        this.telegram = telegram;
        this.users = users;
        this.access = access;
        this.sessions = sessions;
        this.requests = requests;
        this.inspection = inspection;
        this.jobs = jobs;
        this.urlParser = urlParser;
        this.messages = messages;
        this.keyboards = keyboards;
        this.features = features;
        this.userRepository = userRepository;
        this.jobRepository = jobRepository;
        this.executor = executor;
    }

    public void handle(TgUpdate update) {
        try {
            if (update.callbackQuery() != null) handleCallback(update.callbackQuery());
            else if (update.message() != null) handleMessage(update.message());
        } catch (Exception e) {
            log.error("Failed to route Telegram update {}", update.updateId(), e);
        }
    }

    private void handleMessage(TgMessage message) {
        if (message.from() == null || message.from().bot() || message.chat() == null) return;
        AppUser user = users.getOrCreate(message.from());
        String text = message.text() != null ? message.text().trim() : "";

        if (!access.isAllowed(user.getTelegramUserId())) {
            telegram.sendMessage(message.chat().id(), "🔒 <b>This bot is private.</b>\n\nYour Telegram ID: <code>"
                    + user.getTelegramUserId() + "</code>\nAsk the owner to add it to <code>ALLOWED_USER_IDS</code>.", null);
            return;
        }

        if (text.startsWith("/")) {
            handleCommand(message, user, text.split("\\s+", 2)[0].split("@", 2)[0].toLowerCase(Locale.ROOT));
            return;
        }

        Optional<uz.tubeforge.domain.UserSession> session = sessions.active(user.getTelegramUserId());
        if (session.isPresent() && session.get().getState() == SessionState.AWAITING_CLIP_RANGE) {
            handleClipRange(message, user, session.get(), text);
            return;
        }

        Optional<ParsedYouTubeUrl> url = urlParser.find(text);
        if (url.isPresent()) {
            if (!access.hasAcceptedTerms(user)) {
                sessions.awaitTerms(user.getTelegramUserId(), url.get().normalizedUrl());
                telegram.sendMessage(message.chat().id(), messages.terms(), keyboards.acceptTerms());
                return;
            }
            inspectLink(user, message.chat().id(), url.get());
            return;
        }

        telegram.sendMessage(message.chat().id(), "🔗 <b>Send me a YouTube link</b>\n\nI’ll detect it automatically and show every available tool. Use /help for details.", null);
    }

    private void handleCommand(TgMessage message, AppUser user, String command) {
        long chatId = message.chat().id();
        switch (command) {
            case "/start" -> {
                telegram.sendMessage(chatId, messages.welcome(user), null);
                if (!access.hasAcceptedTerms(user)) telegram.sendMessage(chatId, messages.terms(), keyboards.acceptTerms());
            }
            case "/help" -> telegram.sendMessage(chatId, messages.help(user.getLanguage()), null);
            case "/terms" -> telegram.sendMessage(chatId, messages.terms(), keyboards.acceptTerms());
            case "/privacy" -> telegram.sendMessage(chatId, messages.privacy(), null);
            case "/id" -> telegram.sendMessage(chatId, "Your Telegram ID: <code>" + user.getTelegramUserId() + "</code>", null);
            case "/settings" -> telegram.sendMessage(chatId, messages.settings(user), keyboards.settings(user));
            case "/history" -> showHistory(chatId, user);
            case "/jobs" -> showJobs(chatId, user);
            case "/admin" -> showAdmin(chatId, user);
            default -> telegram.sendMessage(chatId, "Unknown command. Use /help to see what TubeForge can do.", null);
        }
    }

    private void handleCallback(TgCallbackQuery callback) {
        if (callback.from() == null || callback.message() == null) return;
        AppUser user = users.getOrCreate(callback.from());
        if (!access.isAllowed(user.getTelegramUserId())) {
            telegram.answerCallback(callback.id(), "This bot is private.", true);
            return;
        }
        CallbackData data = CallbackData.parse(callback.data());
        telegram.answerCallback(callback.id(), null, false);
        try {
            routeCallback(callback, user, data);
        } catch (SecurityException e) {
            telegram.answerCallback(callback.id(), "This action belongs to another user.", true);
        } catch (MediaProcessingException e) {
            telegram.sendMessage(callback.message().chat().id(), "❌ <b>Could not start</b>\n\n" + Html.escape(e.getUserMessage()), null);
        } catch (IllegalArgumentException | IllegalStateException e) {
            telegram.sendMessage(callback.message().chat().id(), "⚠️ " + Html.escape(e.getMessage()), null);
        }
    }

    private void routeCallback(TgCallbackQuery callback, AppUser user, CallbackData data) {
        long chatId = callback.message().chat().id();
        long messageId = callback.message().messageId();
        switch (data.action()) {
            case "accept" -> acceptTerms(chatId, messageId, user);
            case "close" -> telegram.deleteMessage(chatId, messageId);
            case "back", "open" -> showPreview(chatId, messageId, user, data.arg(0));
            case "video" -> showVideoFormats(chatId, messageId, user, data.arg(0));
            case "audio" -> {
                ownRequest(data.arg(0), user);
                telegram.editMessage(chatId, messageId,
                        "🎵 <b>Choose an audio format</b>\n\nThe complete audio track will be extracted.",
                        keyboards.audioFormats(data.arg(0)));
            }
            case "audfmt" -> {
                ownRequest(data.arg(0), user);
                telegram.editMessage(chatId, messageId, "🎚 <b>Choose audio quality</b>\n\nHigher bitrate means a larger file.",
                        keyboards.audioQualities(data.arg(0), data.arg(1)));
            }
            case "thumb" -> {
                ownRequest(data.arg(0), user);
                telegram.editMessage(chatId, messageId, "🖼 <b>Thumbnail tools</b>\n\nChoose the best image or receive every available thumbnail.",
                        keyboards.thumbnailMenu(data.arg(0)));
            }
            case "subs" -> showSubtitleMenu(chatId, messageId, user, data.arg(0), "dls", "Subtitles");
            case "trans" -> showSubtitleMenu(chatId, messageId, user, data.arg(0), "dtr", "Transcript language");
            case "clip" -> {
                ownRequest(data.arg(0), user);
                telegram.editMessage(chatId, messageId, "✂️ <b>Create a precise clip</b>\n\nChoose the result type, then send a timestamp range.",
                        keyboards.clipMenu(data.arg(0)));
            }
            case "cliptype" -> {
                ownRequest(data.arg(0), user);
                sessions.awaitClipRange(user.getTelegramUserId(), data.arg(0), data.arg(1));
                telegram.editMessage(chatId, messageId,
                        "✂️ <b>Send the clip range</b>\n\nExample: <code>01:20-03:45</code>\nMaximum clip length: 30 minutes.",
                        keyboards.back(data.arg(0)));
            }
            case "ai" -> {
                ownRequest(data.arg(0), user);
                telegram.editMessage(chatId, messageId, messages.comingSoon(), keyboards.back(data.arg(0)));
            }
            case "info" -> showInfo(chatId, messageId, user, data.arg(0));
            case "dlv" -> jobs.queue(user.getTelegramUserId(), data.arg(0), JobType.VIDEO, data.arg(1), null);
            case "dla" -> jobs.queue(user.getTelegramUserId(), data.arg(0), JobType.AUDIO, data.arg(1) + ":" + data.arg(2), null);
            case "dlt" -> jobs.queue(user.getTelegramUserId(), data.arg(0), JobType.THUMBNAIL, "jpg", null);
            case "dlta" -> jobs.queue(user.getTelegramUserId(), data.arg(0), JobType.ALL_THUMBNAILS, "jpg", null);
            case "dls" -> jobs.queue(user.getTelegramUserId(), data.arg(0), JobType.SUBTITLES,
                    subtitleCode(data.arg(0), user, data.arg(1)), null);
            case "dtr" -> jobs.queue(user.getTelegramUserId(), data.arg(0), JobType.TRANSCRIPT,
                    subtitleCode(data.arg(0), user, data.arg(1)), null);
            case "pvideo" -> jobs.queue(user.getTelegramUserId(), data.arg(0), JobType.PLAYLIST_VIDEO,
                    user.getDefaultVideoQuality(), null);
            case "paudio" -> jobs.queue(user.getTelegramUserId(), data.arg(0), JobType.PLAYLIST_AUDIO,
                    user.getDefaultAudioFormat().toLowerCase(Locale.ROOT) + ":192", null);
            case "cancel" -> jobs.cancel(user.getTelegramUserId(), data.arg(0));
            case "settings" -> refreshSettings(chatId, messageId, user.getTelegramUserId());
            case "setlang" -> telegram.editMessage(chatId, messageId, "🌐 <b>Choose interface language</b>", keyboards.languageMenu());
            case "lang" -> { users.changeLanguage(user.getTelegramUserId(), Language.valueOf(data.arg(0))); refreshSettings(chatId, messageId, user.getTelegramUserId()); }
            case "setvq" -> telegram.editMessage(chatId, messageId, "🎥 <b>Default video quality</b>", keyboards.videoQualitySettings());
            case "vq" -> { users.changeVideoQuality(user.getTelegramUserId(), data.arg(0)); refreshSettings(chatId, messageId, user.getTelegramUserId()); }
            case "setaf" -> telegram.editMessage(chatId, messageId, "🎵 <b>Default audio format</b>", keyboards.audioFormatSettings());
            case "af" -> { users.changeAudioFormat(user.getTelegramUserId(), data.arg(0)); refreshSettings(chatId, messageId, user.getTelegramUserId()); }
            case "toggledoc" -> { users.toggleDocument(user.getTelegramUserId()); refreshSettings(chatId, messageId, user.getTelegramUserId()); }
            case "togglecmp" -> { users.toggleCompression(user.getTelegramUserId()); refreshSettings(chatId, messageId, user.getTelegramUserId()); }
            default -> telegram.sendMessage(chatId, "This button is no longer available. Send the link again.", null);
        }
    }

    private void inspectLink(AppUser user, long chatId, ParsedYouTubeUrl url) {
        Optional<MediaRequest> cached = requests.reusable(user.getTelegramUserId(), chatId, url);
        if (cached.isPresent()) {
            MediaRequest request = cached.orElseThrow();
            sendCachedPreview(chatId, request, requests.info(request));
            return;
        }
        MediaRequest request = requests.create(user.getTelegramUserId(), chatId, url);
        TgMessage progress = telegram.sendMessage(chatId, messages.inspecting(), null);
        requests.attachPreviewMessage(request.getId(), progress.messageId());
        executor.execute(() -> {
            try {
                MediaInfo info = inspection.inspect(url);
                requests.markReady(request.getId(), info);
                sendInspectionPreview(chatId, progress.messageId(), request.getId(), info);
            } catch (MediaProcessingException e) {
                requests.markFailed(request.getId());
                telegram.editMessage(chatId, progress.messageId(), "❌ <b>Could not inspect this link</b>\n\n"
                        + Html.escape(e.getUserMessage()) + "\n\n<code>" + e.getCode() + "</code>", null);
            } catch (Exception e) {
                requests.markFailed(request.getId());
                log.error("Link inspection failed", e);
                telegram.editMessage(chatId, progress.messageId(), "❌ <b>Could not inspect this link</b>\n\nAn unexpected server error occurred.", null);
            }
        });
    }

    private void sendCachedPreview(long chatId, MediaRequest request, MediaInfo info) {
        try {
            if (info.thumbnailUrl() != null && !info.thumbnailUrl().isBlank()) {
                TgMessage message = telegram.sendPhotoUrl(chatId, info.thumbnailUrl(), messages.preview(info),
                        keyboards.preview(request.getId(), info));
                requests.attachPreviewMessage(request.getId(), message.messageId());
                return;
            }
        } catch (TelegramApiException e) {
            log.debug("Telegram could not render cached thumbnail for {}: {}", request.getId(), e.getMessage());
        }
        TgMessage message = telegram.sendMessage(chatId, messages.preview(info), keyboards.preview(request.getId(), info));
        requests.attachPreviewMessage(request.getId(), message.messageId());
    }

    private void sendInspectionPreview(long chatId, long progressMessageId, String requestId, MediaInfo info) {
        if (info.thumbnailUrl() != null && !info.thumbnailUrl().isBlank()) {
            try {
                TgMessage preview = telegram.sendPhotoUrl(chatId, info.thumbnailUrl(), messages.preview(info),
                        keyboards.preview(requestId, info));
                requests.attachPreviewMessage(requestId, preview.messageId());
                try {
                    telegram.deleteMessage(chatId, progressMessageId);
                } catch (TelegramApiException e) {
                    log.debug("Could not remove inspection message {}: {}", progressMessageId, e.getMessage());
                }
                return;
            } catch (TelegramApiException e) {
                log.debug("Telegram could not render thumbnail for {}: {}", requestId, e.getMessage());
            }
        }
        telegram.editMessage(chatId, progressMessageId, messages.preview(info), keyboards.preview(requestId, info));
        requests.attachPreviewMessage(requestId, progressMessageId);
    }

    private void acceptTerms(long chatId, long messageId, AppUser user) {
        users.acceptTerms(user.getTelegramUserId());
        telegram.editMessage(chatId, messageId, "✅ <b>Terms accepted</b>\n\nTubeForge is ready. Send any YouTube link.", null);
        sessions.active(user.getTelegramUserId()).filter(s -> s.getState() == SessionState.AWAITING_TERMS).ifPresent(session -> {
            urlParser.parse(session.getPayload()).ifPresent(url -> inspectLink(users.require(user.getTelegramUserId()), chatId, url));
            sessions.clear(user.getTelegramUserId());
        });
    }

    private void handleClipRange(TgMessage message, AppUser user, uz.tubeforge.domain.UserSession session, String text) {
        try {
            ClipRange range = ClipRange.parse(text);
            MediaRequest request = requests.requireOwned(session.getRequestId(), user.getTelegramUserId());
            if (range.end().toSeconds() > request.getDurationSeconds()) {
                telegram.sendMessage(message.chat().id(), "⚠️ The end timestamp is beyond the video duration ("
                        + HumanFormat.duration(request.getDurationSeconds()) + ").", null);
                return;
            }
            boolean audio = "audio".equals(session.getPayload());
            String format = audio ? user.getDefaultAudioFormat().toLowerCase(Locale.ROOT) + ":192"
                    : user.getDefaultVideoQuality();
            jobs.queue(user.getTelegramUserId(), session.getRequestId(), audio ? JobType.CLIP_AUDIO : JobType.CLIP_VIDEO,
                    format, range);
            sessions.clear(user.getTelegramUserId());
        } catch (IllegalArgumentException e) {
            telegram.sendMessage(message.chat().id(), "⚠️ <b>Invalid range</b>\n\nUse this format: <code>01:20-03:45</code>\n"
                    + Html.escape(e.getMessage()), null);
        }
    }

    private void showPreview(long chatId, long messageId, AppUser user, String requestId) {
        MediaRequest request = ownRequest(requestId, user);
        MediaInfo info = requests.info(request);
        telegram.editMessage(chatId, messageId, messages.preview(info), keyboards.preview(requestId, info));
    }

    private void showVideoFormats(long chatId, long messageId, AppUser user, String requestId) {
        MediaRequest request = ownRequest(requestId, user);
        MediaInfo info = requests.info(request);
        telegram.editMessage(chatId, messageId, "🎥 <b>Choose video quality</b>\n\nSizes are estimates and may change when video and audio are merged.",
                keyboards.videoFormats(requestId, info));
    }

    private void showSubtitleMenu(long chatId, long messageId, AppUser user, String requestId,
                                  String action, String title) {
        MediaInfo info = requests.info(ownRequest(requestId, user));
        if (info.subtitles().isEmpty()) {
            telegram.editMessage(chatId, messageId, "📝 <b>No subtitles found</b>\n\nThis video does not expose official or automatic captions.",
                    keyboards.back(requestId));
            return;
        }
        telegram.editMessage(chatId, messageId, "📝 <b>" + title + "</b>\n\n🤖 marks automatically generated captions.",
                keyboards.subtitleMenu(requestId, info.subtitles(), action));
    }

    private void showInfo(long chatId, long messageId, AppUser user, String requestId) {
        MediaInfo info = requests.info(ownRequest(requestId, user));
        String description = info.description() == null ? "" : info.description().strip();
        if (description.length() > 1200) description = description.substring(0, 1200) + "…";
        String text = "ℹ️ <b>Media information</b>\n\n"
                + "🎬 <b>Title:</b> " + Html.escape(info.title()) + "\n"
                + "📺 <b>Channel:</b> " + Html.escape(info.channel()) + "\n"
                + "🆔 <b>ID:</b> <code>" + Html.escape(info.id()) + "</code>\n"
                + "⏱ <b>Duration:</b> " + HumanFormat.duration(info.durationSeconds()) + "\n"
                + "👁 <b>Views:</b> " + HumanFormat.number(info.viewCount()) + "\n"
                + "🎞 <b>Video qualities:</b> " + info.videoFormats().size() + "\n"
                + "📝 <b>Subtitle languages:</b> " + info.subtitles().size()
                + (description.isBlank() ? "" : "\n\n<b>Description</b>\n" + Html.escape(description));
        telegram.editMessage(chatId, messageId, text, keyboards.back(requestId));
    }

    private MediaRequest ownRequest(String id, AppUser user) {
        return requests.requireOwned(id, user.getTelegramUserId());
    }

    private String subtitleCode(String requestId, AppUser user, String indexText) {
        MediaInfo info = requests.info(ownRequest(requestId, user));
        int index;
        try { index = Integer.parseInt(indexText); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid subtitle selection"); }
        if (index < 0 || index >= Math.min(16, info.subtitles().size())) {
            throw new IllegalArgumentException("Subtitle selection expired");
        }
        String code = info.subtitles().get(index).code();
        if (code.length() > 32) throw new IllegalArgumentException("Unsupported subtitle language code");
        return code;
    }

    private void refreshSettings(long chatId, long messageId, long userId) {
        AppUser fresh = users.require(userId);
        telegram.editMessage(chatId, messageId, messages.settings(fresh), keyboards.settings(fresh));
    }

    private void showHistory(long chatId, AppUser user) {
        List<MediaRequest> history = requests.recent(user.getTelegramUserId(), 8).stream()
                .filter(request -> request.getStatus() == RequestStatus.READY).toList();
        if (history.isEmpty()) {
            telegram.sendMessage(chatId, "🕘 <b>No history yet</b>\n\nSend your first YouTube link.", null);
            return;
        }
        List<List<InlineButton>> rows = new ArrayList<>();
        for (MediaRequest request : history) {
            String title = request.getTitle() == null ? "Untitled" : request.getTitle();
            if (title.length() > 45) title = title.substring(0, 44) + "…";
            rows.add(List.of(InlineButton.callback("🎬 " + title, CallbackData.of("open", request.getId()))));
        }
        telegram.sendMessage(chatId, "🕘 <b>Recent media</b>\n\nChoose an item to reopen its tools.", InlineKeyboard.of(rows));
    }

    private void showJobs(long chatId, AppUser user) {
        List<DownloadJob> recent = jobs.recent(user.getTelegramUserId(), 10);
        if (recent.isEmpty()) {
            telegram.sendMessage(chatId, "📭 <b>No jobs yet</b>", null);
            return;
        }
        StringBuilder text = new StringBuilder("📋 <b>Recent jobs</b>\n\n");
        for (DownloadJob job : recent) {
            String icon = switch (job.getStatus()) {
                case COMPLETED -> "✅";
                case FAILED -> "❌";
                case CANCELLED -> "🚫";
                case QUEUED -> "⏳";
                default -> "⚙️";
            };
            text.append(icon).append(' ').append(job.getJobType()).append(" — ").append(job.getStatus());
            if (job.getStatus() == JobStatus.RUNNING) text.append(" ").append(job.getProgressPercent()).append('%');
            text.append('\n');
        }
        text.append("\nRemaining daily jobs: <b>").append(access.jobsRemaining(user.getTelegramUserId()) == Integer.MAX_VALUE
                ? "Unlimited" : access.jobsRemaining(user.getTelegramUserId())).append("</b>");
        telegram.sendMessage(chatId, text.toString(), null);
    }

    private void showAdmin(long chatId, AppUser user) {
        if (!access.isAdmin(user.getTelegramUserId())) {
            telegram.sendMessage(chatId, "🔒 This command is available only to configured administrators.", null);
            return;
        }
        telegram.sendMessage(chatId, "🛡 <b>TubeForge administration</b>\n\n"
                + "👥 Users: <b>" + userRepository.count() + "</b>\n"
                + "📦 Total jobs: <b>" + jobRepository.count() + "</b>\n"
                + "⚙️ Active jobs: <b>" + jobs.activeCount() + "</b>\n"
                + "🎥 Video: " + on(features.videoDownload()) + "\n"
                + "🎵 Audio: " + on(features.audioDownload()) + "\n"
                + "📝 Subtitles: " + on(features.subtitles()) + "\n"
                + "✂️ Clips: " + on(features.clips()) + "\n"
                + "📚 Playlists: " + on(features.playlists()), null);
    }

    private String on(boolean value) { return value ? "✅" : "❌"; }
}
