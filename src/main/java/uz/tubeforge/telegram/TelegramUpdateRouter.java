package uz.tubeforge.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import uz.tubeforge.config.AiProperties;
import uz.tubeforge.config.FeatureProperties;
import uz.tubeforge.domain.*;
import uz.tubeforge.health.MediaToolsHealthIndicator;
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
import java.util.Set;

@Component
public class TelegramUpdateRouter {
    private static final Logger log = LoggerFactory.getLogger(TelegramUpdateRouter.class);
    private static final Set<String> SUPPORTED_CALLBACK_ACTIONS = Set.of(
            "accept", "close", "back", "open", "video", "allv", "audio", "alla", "audfmt",
            "thumb", "subs", "trans", "clip", "cliptype", "tools", "ai", "aisel", "info",
            "dlv", "dla", "qv", "qa", "dlt", "dlta", "dls", "dtr", "ais", "aic", "ain",
            "pvideo", "paudio", "cancel", "settings", "setlang", "lang", "setvq", "vq",
            "setaf", "af", "toggledoc", "togglecmp", "meta", "metaretry",
            "adm", "admqueue", "admcache", "admhealth"
    );

    private final TelegramApiClient telegram;
    private final UserService users;
    private final AccessService access;
    private final SessionService sessions;
    private final MediaRequestService requests;
    private final MediaInspectionCoordinator inspection;
    private final MediaJobService jobs;
    private final MediaUrlParser urlParser;
    private final BotMessages messages;
    private final KeyboardFactory keyboards;
    private final FeatureProperties features;
    private final AppUserRepository userRepository;
    private final DownloadJobRepository jobRepository;
    private final ArtifactCacheService artifactCache;
    private final AiInsightCacheService insightCache;
    private final PerformanceMetrics metrics;
    private final AiProperties aiProperties;
    private final MediaToolsHealthIndicator mediaToolsHealth;
    private final TaskExecutor executor;

    public TelegramUpdateRouter(TelegramApiClient telegram, UserService users, AccessService access,
                                SessionService sessions, MediaRequestService requests,
                                MediaInspectionCoordinator inspection, MediaJobService jobs,
                                MediaUrlParser urlParser, BotMessages messages, KeyboardFactory keyboards,
                                FeatureProperties features, AppUserRepository userRepository,
                                DownloadJobRepository jobRepository, ArtifactCacheService artifactCache,
                                AiInsightCacheService insightCache, PerformanceMetrics metrics,
                                AiProperties aiProperties, MediaToolsHealthIndicator mediaToolsHealth,
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
        this.artifactCache = artifactCache;
        this.insightCache = insightCache;
        this.metrics = metrics;
        this.aiProperties = aiProperties;
        this.mediaToolsHealth = mediaToolsHealth;
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

    static boolean supportsCallbackAction(String action) {
        return SUPPORTED_CALLBACK_ACTIONS.contains(action);
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

        Optional<ParsedMediaUrl> url = urlParser.find(text);
        if (url.isPresent()) {
            if (url.get().sourceType() == SourceType.INSTAGRAM_REEL && !features.instagramReels()) {
                telegram.sendMessage(message.chat().id(), "Instagram Reels are temporarily disabled by the bot owner.", null);
                return;
            }
            if (!access.hasAcceptedTerms(user)) {
                sessions.awaitTerms(user.getTelegramUserId(), url.get().normalizedUrl());
                telegram.sendMessage(message.chat().id(), messages.terms(), keyboards.acceptTerms());
                return;
            }
            if (!access.canInspectLink(user.getTelegramUserId())) {
                telegram.sendMessage(message.chat().id(),
                        "⏱ <b>Too many links</b>\n\nPlease wait a minute before sending more. "
                                + "Your active downloads keep running.", null);
                return;
            }
            inspectLink(user, message.chat().id(), url.get());
            return;
        }

        telegram.sendMessage(message.chat().id(), "🔗 <b>Send a YouTube or Instagram Reel link</b>\n\nI’ll detect it automatically and show the fastest available actions. Use /help for details.", null);
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
        try {
            routeCallback(callback, user, data);
            safeAnswerCallback(callback.id());
        } catch (SecurityException e) {
            answerCallbackError(callback.id(), "🔒 This action belongs to another user.");
        } catch (MediaProcessingException e) {
            answerCallbackError(callback.id(), "❌ " + e.getUserMessage());
        } catch (IllegalArgumentException | IllegalStateException e) {
            answerCallbackError(callback.id(), "⚠️ " + (e.getMessage() == null ? "This action is no longer available." : e.getMessage()));
        } catch (TelegramApiException e) {
            answerCallbackError(callback.id(), "⚠️ Telegram could not update this message. Try again.");
            log.debug("Telegram callback action failed {}: {}", data.action(), e.getMessage());
        } catch (RuntimeException e) {
            answerCallbackError(callback.id(), "⚠️ This action failed. Try again.");
            log.error("Unexpected callback action failure {}", data.action(), e);
        }
    }

    private void routeCallback(TgCallbackQuery callback, AppUser user, CallbackData data) {
        long chatId = callback.message().chat().id();
        long messageId = callback.message().messageId();
        boolean captionMessage = callback.message().caption() != null;
        switch (data.action()) {
            case "accept" -> acceptTerms(chatId, messageId, user);
            case "close" -> telegram.deleteMessage(chatId, messageId);
            case "back", "open" -> showPreview(chatId, messageId, captionMessage, user, data.arg(0));
            case "video" -> showVideoFormats(chatId, messageId, captionMessage, user, data.arg(0));
            case "allv" -> {
                MediaRequest request = ownRequest(data.arg(0), user);
                MediaInfo info = metadataInfo(chatId, messageId, captionMessage, request);
                if (info == null) return;
                editInteractive(chatId, messageId, captionMessage,
                        "🎛 <b>All available video formats</b>\n\nChoose the exact source quality.",
                        keyboards.videoFormats(data.arg(0), info, true));
            }
            case "audio" -> {
                ownRequest(data.arg(0), user);
                editInteractive(chatId, messageId, captionMessage,
                        "🎵 <b>Choose an audio format</b>\n\nThe complete audio track will be extracted.",
                        keyboards.audioFormats(data.arg(0)));
            }
            case "alla" -> {
                ownRequest(data.arg(0), user);
                editInteractive(chatId, messageId, captionMessage,
                        "🎵 <b>All audio formats</b>\n\nFor most users MP3 or M4A is the fastest choice.",
                        keyboards.allAudioFormats(data.arg(0)));
            }
            case "audfmt" -> {
                ownRequest(data.arg(0), user);
                String format = data.arg(1).toLowerCase(Locale.ROOT);
                if (format.equals("wav") || format.equals("flac")) {
                    jobs.queue(user.getTelegramUserId(), data.arg(0), JobType.AUDIO, format + ":192", null);
                } else {
                    editInteractive(chatId, messageId, captionMessage,
                            "🎚 <b>Choose audio quality</b>\n\nHigher bitrate means a larger file.",
                            keyboards.audioQualities(data.arg(0), format));
                }
            }
            case "thumb" -> {
                MediaRequest request = ownRequest(data.arg(0), user);
                editInteractive(chatId, messageId, captionMessage,
                        "🖼 <b>Cover image</b>\n\nGet the best image available for this media.",
                        keyboards.thumbnailMenu(data.arg(0),
                                request.getSourceType() != SourceType.INSTAGRAM_REEL));
            }
            case "subs" -> showSubtitleMenu(chatId, messageId, captionMessage, user, data.arg(0), "dls", "Subtitles");
            case "trans" -> showSubtitleMenu(chatId, messageId, captionMessage, user, data.arg(0), "dtr", "Transcript language");
            case "clip" -> {
                MediaRequest request = ownRequest(data.arg(0), user);
                if (metadataInfo(chatId, messageId, captionMessage, request) == null) return;
                editInteractive(chatId, messageId, captionMessage,
                        "✂️ <b>Create a precise clip</b>\n\nChoose the result type, then send a timestamp range.",
                        keyboards.clipMenu(data.arg(0)));
            }
            case "cliptype" -> {
                ownRequest(data.arg(0), user);
                sessions.awaitClipRange(user.getTelegramUserId(), data.arg(0), data.arg(1));
                editInteractive(chatId, messageId, captionMessage,
                        "✂️ <b>Send the clip range</b>\n\nExample: <code>01:20-03:45</code>\nMaximum clip length: 30 minutes.",
                        keyboards.back(data.arg(0)));
            }
            case "tools" -> {
                MediaRequest request = ownRequest(data.arg(0), user);
                MediaInfo info = metadataInfo(chatId, messageId, captionMessage, request);
                if (info == null) return;
                editInteractive(chatId, messageId, captionMessage,
                        "🧰 <b>TubeForge tools</b>\n\nThumbnails, subtitles, transcripts and precise clips.",
                        keyboards.toolsMenu(data.arg(0), info));
            }
            case "ai" -> {
                MediaRequest request = ownRequest(data.arg(0), user);
                MediaInfo info = metadataInfo(chatId, messageId, captionMessage, request);
                if (info == null) return;
                if (info.subtitles().isEmpty()) {
                    editInteractive(chatId, messageId, captionMessage,
                            "📝 <b>Transcript Studio needs subtitles</b>\n\nThis video does not expose official or automatic captions.",
                            keyboards.back(data.arg(0)));
                    return;
                }
                editInteractive(chatId, messageId, captionMessage, messages.aiStudio(), keyboards.aiStudio(data.arg(0)));
            }
            case "aisel" -> {
                String action = switch (data.arg(1)) {
                    case "chapters" -> "aic";
                    case "notes" -> "ain";
                    default -> "ais";
                };
                showSubtitleMenu(chatId, messageId, captionMessage, user, data.arg(0), action,
                        "Choose the AI transcript language");
            }
            case "info" -> showInfo(chatId, messageId, captionMessage, user, data.arg(0));
            case "dlv" -> jobs.queue(user.getTelegramUserId(), data.arg(0), JobType.VIDEO, data.arg(1), null);
            case "dla" -> jobs.queue(user.getTelegramUserId(), data.arg(0), JobType.AUDIO, data.arg(1) + ":" + data.arg(2), null);
            case "qv" -> jobs.queue(user.getTelegramUserId(), data.arg(0), JobType.VIDEO, data.arg(1), null);
            case "qa" -> jobs.queue(user.getTelegramUserId(), data.arg(0), JobType.AUDIO, data.arg(1) + ":192", null);
            case "dlt" -> sendBestThumbnail(user, data.arg(0));
            case "dlta" -> jobs.queue(user.getTelegramUserId(), data.arg(0), JobType.ALL_THUMBNAILS, "jpg", null);
            case "dls" -> jobs.queue(user.getTelegramUserId(), data.arg(0), JobType.SUBTITLES,
                    subtitleCode(data.arg(0), user, data.arg(1)), null);
            case "dtr" -> jobs.queue(user.getTelegramUserId(), data.arg(0), JobType.TRANSCRIPT,
                    subtitleCode(data.arg(0), user, data.arg(1)), null);
            case "ais" -> jobs.queue(user.getTelegramUserId(), data.arg(0), JobType.AI_SUMMARY,
                    subtitleCode(data.arg(0), user, data.arg(1)), null);
            case "aic" -> jobs.queue(user.getTelegramUserId(), data.arg(0), JobType.AI_CHAPTERS,
                    subtitleCode(data.arg(0), user, data.arg(1)), null);
            case "ain" -> jobs.queue(user.getTelegramUserId(), data.arg(0), JobType.AI_STUDY_NOTES,
                    subtitleCode(data.arg(0), user, data.arg(1)), null);
            case "pvideo" -> jobs.queue(user.getTelegramUserId(), data.arg(0), JobType.PLAYLIST_VIDEO,
                    user.getDefaultVideoQuality(), null);
            case "paudio" -> jobs.queue(user.getTelegramUserId(), data.arg(0), JobType.PLAYLIST_AUDIO,
                    user.getDefaultAudioFormat().toLowerCase(Locale.ROOT) + ":192", null);
            case "cancel" -> jobs.cancel(user.getTelegramUserId(), data.arg(0));
            case "settings" -> refreshSettings(chatId, messageId, captionMessage, user.getTelegramUserId());
            case "setlang" -> editInteractive(chatId, messageId, captionMessage, "🌐 <b>Choose interface language</b>", keyboards.languageMenu());
            case "lang" -> { users.changeLanguage(user.getTelegramUserId(), Language.valueOf(data.arg(0))); refreshSettings(chatId, messageId, captionMessage, user.getTelegramUserId()); }
            case "setvq" -> editInteractive(chatId, messageId, captionMessage, "🎥 <b>Default video quality</b>", keyboards.videoQualitySettings());
            case "vq" -> { users.changeVideoQuality(user.getTelegramUserId(), data.arg(0)); refreshSettings(chatId, messageId, captionMessage, user.getTelegramUserId()); }
            case "setaf" -> editInteractive(chatId, messageId, captionMessage, "🎵 <b>Default audio format</b>", keyboards.audioFormatSettings());
            case "af" -> { users.changeAudioFormat(user.getTelegramUserId(), data.arg(0)); refreshSettings(chatId, messageId, captionMessage, user.getTelegramUserId()); }
            case "toggledoc" -> { users.toggleDocument(user.getTelegramUserId()); refreshSettings(chatId, messageId, captionMessage, user.getTelegramUserId()); }
            case "togglecmp" -> { users.toggleCompression(user.getTelegramUserId()); refreshSettings(chatId, messageId, captionMessage, user.getTelegramUserId()); }
            case "meta" -> showMetadataStatus(chatId, messageId, captionMessage, user, data.arg(0));
            case "metaretry" -> retryMetadata(chatId, messageId, captionMessage, user, data.arg(0));
            case "adm" -> editAdmin(chatId, messageId, captionMessage, user, adminOverview());
            case "admqueue" -> editAdmin(chatId, messageId, captionMessage, user, adminWorkload());
            case "admcache" -> editAdmin(chatId, messageId, captionMessage, user, adminCache());
            case "admhealth" -> showAdminHealth(chatId, messageId, captionMessage, user);
            default -> telegram.sendMessage(chatId, "This button is no longer available. Send the link again.", null);
        }
    }

    private void inspectLink(AppUser user, long chatId, ParsedMediaUrl url) {
        if (url.sourceType() == SourceType.INSTAGRAM_REEL) {
            startInstantReel(user, chatId, url);
            return;
        }
        Optional<MediaRequest> cached = requests.reusable(user.getTelegramUserId(), chatId, url);
        if (cached.isPresent()) {
            MediaRequest request = cached.orElseThrow();
            sendCachedPreview(chatId, request, requests.info(request), user);
            return;
        }
        MediaRequest request = requests.createInstant(user.getTelegramUserId(), chatId, url);
        MediaInfo instantInfo = requests.info(request);
        TgMessage preview;
        if (instantInfo.thumbnailUrl() == null || instantInfo.thumbnailUrl().isBlank()) {
            preview = telegram.sendMessage(chatId, messages.preview(instantInfo, MetadataState.PENDING),
                    keyboards.preview(request.getId(), instantInfo, user, MetadataState.PENDING));
        } else {
            try {
                preview = telegram.sendPhotoUrl(chatId, instantInfo.thumbnailUrl(),
                        messages.preview(instantInfo, MetadataState.PENDING),
                        keyboards.preview(request.getId(), instantInfo, user, MetadataState.PENDING));
            } catch (TelegramApiException e) {
                preview = telegram.sendMessage(chatId, messages.preview(instantInfo, MetadataState.PENDING),
                        keyboards.preview(request.getId(), instantInfo, user, MetadataState.PENDING));
            }
        }
        requests.attachPreviewMessage(request.getId(), preview.messageId());
        scheduleMetadata(request.getId(), url, user, chatId, preview.messageId(), preview.caption() != null);
    }

    private void startInstantReel(AppUser user, long chatId, ParsedMediaUrl url) {
        MediaRequest request = requests.createInstant(user.getTelegramUserId(), chatId, url);
        try {
            jobs.queue(user.getTelegramUserId(), request.getId(), JobType.VIDEO, "best", null);
        } catch (MediaProcessingException e) {
            telegram.sendMessage(chatId, "⚠️ <b>Could not start this Reel</b>\n\n"
                    + Html.escape(e.getUserMessage()), keyboards.reelRetry(request.getId(), request.getSourceUrl()));
        }
    }

    private void sendCachedPreview(long chatId, MediaRequest request, MediaInfo info, AppUser user) {
        try {
            if (info.thumbnailUrl() != null && !info.thumbnailUrl().isBlank()) {
                TgMessage message = telegram.sendPhotoUrl(chatId, info.thumbnailUrl(), messages.preview(info),
                        keyboards.preview(request.getId(), info, user));
                requests.attachPreviewMessage(request.getId(), message.messageId());
                return;
            }
        } catch (TelegramApiException e) {
            log.debug("Telegram could not render cached thumbnail for {}: {}", request.getId(), e.getMessage());
        }
        TgMessage message = telegram.sendMessage(chatId, messages.preview(info), keyboards.preview(request.getId(), info, user));
        requests.attachPreviewMessage(request.getId(), message.messageId());
    }

    private void acceptTerms(long chatId, long messageId, AppUser user) {
        users.acceptTerms(user.getTelegramUserId());
        telegram.editMessage(chatId, messageId, "✅ <b>Terms accepted</b>\n\nTubeForge is ready. Send a YouTube or Instagram Reel link.", null);
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

    private void showPreview(long chatId, long messageId, boolean captionMessage, AppUser user, String requestId) {
        MediaRequest request = ownRequest(requestId, user);
        MediaInfo info = requests.info(request);
        editInteractive(chatId, messageId, captionMessage, messages.preview(info, request.getMetadataState()),
                keyboards.preview(requestId, info, user, request.getMetadataState()));
    }

    private void showVideoFormats(long chatId, long messageId, boolean captionMessage, AppUser user, String requestId) {
        MediaRequest request = ownRequest(requestId, user);
        MediaInfo info = metadataInfo(chatId, messageId, captionMessage, request);
        if (info == null) return;
        editInteractive(chatId, messageId, captionMessage,
                "🎥 <b>Choose video quality</b>\n\nSizes are estimates and may change when video and audio are merged.",
                keyboards.videoFormats(requestId, info));
    }

    private void showSubtitleMenu(long chatId, long messageId, boolean captionMessage, AppUser user, String requestId,
                                  String action, String title) {
        MediaRequest request = ownRequest(requestId, user);
        MediaInfo info = metadataInfo(chatId, messageId, captionMessage, request);
        if (info == null) return;
        if (info.subtitles().isEmpty()) {
            editInteractive(chatId, messageId, captionMessage,
                    "📝 <b>No subtitles found</b>\n\nThis video does not expose official or automatic captions.",
                    keyboards.back(requestId));
            return;
        }
        editInteractive(chatId, messageId, captionMessage,
                "📝 <b>" + title + "</b>\n\n🤖 marks automatically generated captions.",
                keyboards.subtitleMenu(requestId, info.subtitles(), action));
    }

    private void showInfo(long chatId, long messageId, boolean captionMessage, AppUser user, String requestId) {
        MediaRequest request = ownRequest(requestId, user);
        MediaInfo info = metadataInfo(chatId, messageId, captionMessage, request);
        if (info == null) return;
        String description = info.description() == null ? "" : info.description().strip();
        int descriptionLimit = captionMessage ? 450 : 1200;
        if (description.length() > descriptionLimit) description = description.substring(0, descriptionLimit) + "…";
        String text = "ℹ️ <b>Media information</b>\n\n"
                + "🎬 <b>Title:</b> " + Html.escape(info.title()) + "\n"
                + "📺 <b>Channel:</b> " + Html.escape(info.channel()) + "\n"
                + "🆔 <b>ID:</b> <code>" + Html.escape(info.id()) + "</code>\n"
                + "⏱ <b>Duration:</b> " + HumanFormat.duration(info.durationSeconds()) + "\n"
                + "👁 <b>Views:</b> " + HumanFormat.number(info.viewCount()) + "\n"
                + "🎞 <b>Video qualities:</b> " + info.videoFormats().size() + "\n"
                + "📝 <b>Subtitle languages:</b> " + info.subtitles().size()
                + (description.isBlank() ? "" : "\n\n<b>Description</b>\n" + Html.escape(description));
        editInteractive(chatId, messageId, captionMessage, text, keyboards.back(requestId));
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

    private void refreshSettings(long chatId, long messageId, boolean captionMessage, long userId) {
        AppUser fresh = users.require(userId);
        editInteractive(chatId, messageId, captionMessage, messages.settings(fresh), keyboards.settings(fresh));
    }

    private void showHistory(long chatId, AppUser user) {
        List<MediaRequest> history = requests.recent(user.getTelegramUserId(), 8).stream()
                .filter(requests::isUsable).toList();
        if (history.isEmpty()) {
            telegram.sendMessage(chatId, "🕘 <b>No history yet</b>\n\nSend your first YouTube or Instagram Reel link.", null);
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
        telegram.sendMessage(chatId, adminOverview(), keyboards.admin());
    }

    private void scheduleMetadata(String requestId, ParsedMediaUrl url, AppUser user, long chatId,
                                  long messageId, boolean captionMessage) {
        try {
            executor.execute(() -> {
                try {
                    MediaInfo info = inspection.inspect(url);
                    MediaRequest ready = requests.markReady(requestId, info);
                    editInteractive(chatId, messageId, captionMessage, messages.preview(info),
                            keyboards.preview(requestId, info, user, ready.getMetadataState()));
                } catch (MediaProcessingException e) {
                    MediaRequest degraded = requests.markMetadataDegraded(requestId, e.getCode(), e.getUserMessage());
                    MediaInfo info = requests.info(degraded);
                    editInteractive(chatId, messageId, captionMessage,
                            messages.preview(info, MetadataState.DEGRADED),
                            keyboards.preview(requestId, info, user, MetadataState.DEGRADED));
                } catch (TelegramApiException e) {
                    log.debug("Metadata was hydrated but preview {} could not be updated: {}", requestId, e.getMessage());
                } catch (Exception e) {
                    log.error("Background metadata hydration failed for {}", requestId, e);
                    MediaRequest degraded = requests.markMetadataDegraded(requestId, "INSPECTION_FAILED",
                            "The advanced analysis failed unexpectedly");
                    MediaInfo info = requests.info(degraded);
                    editInteractive(chatId, messageId, captionMessage,
                            messages.preview(info, MetadataState.DEGRADED),
                            keyboards.preview(requestId, info, user, MetadataState.DEGRADED));
                }
            });
        } catch (TaskRejectedException e) {
            MediaRequest degraded = requests.markMetadataDegraded(requestId, "INSPECTION_QUEUE_FULL",
                    "The advanced-analysis queue is full");
            MediaInfo info = requests.info(degraded);
            editInteractive(chatId, messageId, captionMessage, messages.preview(info, MetadataState.DEGRADED),
                    keyboards.preview(requestId, info, user, MetadataState.DEGRADED));
        }
    }

    private MediaInfo metadataInfo(long chatId, long messageId, boolean captionMessage, MediaRequest request) {
        if (request.getMetadataState() == MetadataState.READY) return requests.info(request);
        showMetadataState(chatId, messageId, captionMessage, request);
        return null;
    }

    private void showMetadataStatus(long chatId, long messageId, boolean captionMessage,
                                    AppUser user, String requestId) {
        MediaRequest request = ownRequest(requestId, user);
        if (request.getMetadataState() == MetadataState.READY) {
            showPreview(chatId, messageId, captionMessage, user, requestId);
            return;
        }
        showMetadataState(chatId, messageId, captionMessage, request);
    }

    private void showMetadataState(long chatId, long messageId, boolean captionMessage, MediaRequest request) {
        String text;
        if (request.getMetadataState() == MetadataState.DEGRADED) {
            text = "⚠️ <b>Advanced analysis is unavailable</b>\n\n"
                    + Html.escape(request.getMetadataErrorMessage())
                    + "\n\nQuick video, audio and thumbnail downloads still work.\n"
                    + "<code>" + Html.escape(request.getMetadataErrorCode()) + "</code>";
        } else {
            text = "⏳ <b>Advanced tools are still loading</b>\n\n"
                    + "Quick video and audio can start now. Formats, subtitles and Transcript Studio will appear automatically.";
        }
        editInteractive(chatId, messageId, captionMessage, text,
                keyboards.metadataStatus(request.getId(), request.getMetadataState()));
    }

    private void retryMetadata(long chatId, long messageId, boolean captionMessage,
                               AppUser user, String requestId) {
        MediaRequest request = ownRequest(requestId, user);
        ParsedMediaUrl url = urlParser.parse(request.getSourceUrl())
                .orElseThrow(() -> new IllegalStateException("The stored media link is invalid"));
        requests.markMetadataPending(requestId);
        MediaInfo info = requests.info(request);
        editInteractive(chatId, messageId, captionMessage, messages.preview(info, MetadataState.PENDING),
                keyboards.preview(requestId, info, user, MetadataState.PENDING));
        scheduleMetadata(requestId, url, user, chatId, messageId, captionMessage);
    }

    private void sendBestThumbnail(AppUser user, String requestId) {
        MediaRequest request = ownRequest(requestId, user);
        MediaInfo info = requests.info(request);
        if (info.thumbnailUrl() == null || info.thumbnailUrl().isBlank()) {
            jobs.queue(user.getTelegramUserId(), requestId, JobType.THUMBNAIL, "jpg", null);
            return;
        }
        try {
            telegram.sendPhotoUrl(request.getChatId(), info.thumbnailUrl(),
                    "🖼 <b>" + Html.escape(info.title()) + "</b>\n\nBest available cover image.", null);
        } catch (TelegramApiException e) {
            jobs.queue(user.getTelegramUserId(), requestId, JobType.THUMBNAIL, "jpg", null);
        }
    }

    private void editAdmin(long chatId, long messageId, boolean captionMessage, AppUser user, String text) {
        requireAdmin(user);
        editInteractive(chatId, messageId, captionMessage, text, keyboards.admin());
    }

    private String adminOverview() {
        return "🛡 <b>TubeForge Control Center</b>\n\n"
                + "👥 Users: <b>" + userRepository.count() + "</b>\n"
                + "📦 Total jobs: <b>" + jobRepository.count() + "</b>\n"
                + "⚙️ Active jobs: <b>" + jobs.activeCount() + "</b>\n"
                + "🔎 Active inspections: <b>" + inspection.activeInspections() + "</b>\n\n"
                + "🎥 Video: " + on(features.videoDownload()) + "\n"
                + "🎵 Audio: " + on(features.audioDownload()) + "\n"
                + "📝 Subtitles: " + on(features.subtitles()) + "\n"
                + "✂️ Clips: " + on(features.clips()) + "\n"
                + "📚 Playlists: " + on(features.playlists()) + "\n"
                + "📸 Instagram Reels: " + on(features.instagramReels()) + "\n"
                + "🧠 Transcript Studio: " + on(features.aiStudio()) + "\n"
                + "🤖 Engine: <code>" + Html.escape(aiProperties.ollama()
                ? "Ollama / " + aiProperties.model() : "local extractive") + "</code>";
    }

    private String adminWorkload() {
        long queued = jobRepository.countByStatusIn(List.of(JobStatus.QUEUED));
        long running = jobRepository.countByStatusIn(List.of(JobStatus.RUNNING));
        long delivering = jobRepository.countByStatusIn(List.of(JobStatus.DELIVERING));
        long failed = jobRepository.countByStatusIn(List.of(JobStatus.FAILED));
        return "⚙️ <b>Workload</b>\n\n"
                + "⏳ Queued: <b>" + queued + "</b>\n"
                + "🛠 Running: <b>" + running + "</b>\n"
                + "📤 Delivering: <b>" + delivering + "</b>\n"
                + "❌ Failed (all time): <b>" + failed + "</b>\n"
                + "🔎 Metadata workers active: <b>" + inspection.activeInspections() + "</b>\n"
                + "🧯 Metadata retry cooldowns: <b>" + inspection.coolingDownLinks() + "</b>\n"
                + "📸 Reel download cooldowns: <b>" + jobs.coolingDownMedia() + "</b>";
    }

    private String adminCache() {
        PerformanceMetrics.Snapshot value = metrics.snapshot();
        return "⚡ <b>Cache & performance</b>\n\n"
                + "📁 Reusable Telegram files: <b>" + artifactCache.entries() + "</b>\n"
                + "🚀 Artifact deliveries: <b>" + artifactCache.hits() + "</b>\n"
                + "🧠 Cached transcript insights: <b>" + insightCache.entries() + "</b>\n\n"
                + "Metadata hits / misses: <b>" + value.metadataHits() + " / " + value.metadataMisses() + "</b>\n"
                + "Artifact hits / misses: <b>" + value.artifactHits() + " / " + value.artifactMisses() + "</b>\n"
                + "Coalesced duplicate work: <b>" + value.coalescedJobs() + "</b>\n"
                + "Telegram updates / rejected: <b>" + value.dispatchedUpdates() + " / "
                + value.rejectedUpdates() + "</b>\n"
                + "Telegram API retries: <b>" + value.telegramRetries() + "</b>\n"
                + "Inspection cooldown hits: <b>" + value.inspectionCooldownHits() + "</b>\n"
                + "Rate-limited link floods: <b>" + value.rateLimitedLinks() + "</b>\n"
                + "Instant Reels requested / delivered: <b>" + value.instantReelRequests() + " / "
                + value.instantReelDeliveries() + "</b>\n"
                + "Instant Reel cache / failures: <b>" + value.instantReelCacheDeliveries() + " / "
                + value.instantReelFailures() + "</b>\n"
                + "Average Reel delivery: <b>" + value.instantReelAverageMillis() + " ms</b>\n"
                + "Local / Ollama analysis: <b>" + value.aiLocal() + " / " + value.aiOllama() + "</b>";
    }

    private void showAdminHealth(long chatId, long messageId, boolean captionMessage, AppUser user) {
        requireAdmin(user);
        editInteractive(chatId, messageId, captionMessage,
                "🩺 <b>Checking yt-dlp, FFmpeg and FFprobe…</b>", keyboards.admin());
        try {
            executor.execute(() -> {
                var health = mediaToolsHealth.health();
                StringBuilder text = new StringBuilder("🩺 <b>Media tool health</b>\n\n")
                        .append("Overall: <b>").append(Html.escape(health.getStatus().getCode())).append("</b>\n");
                health.getDetails().forEach((key, value) -> text.append("• ")
                        .append(Html.escape(key)).append(": <code>")
                        .append(Html.escape(String.valueOf(value))).append("</code>\n"));
                editInteractive(chatId, messageId, captionMessage, text.toString(), keyboards.admin());
            });
        } catch (TaskRejectedException e) {
            editInteractive(chatId, messageId, captionMessage,
                    "⚠️ <b>Health check queue is busy</b>\n\nTry again shortly.", keyboards.admin());
        }
    }

    private void requireAdmin(AppUser user) {
        if (!access.isAdmin(user.getTelegramUserId())) {
            throw new SecurityException("Administrator access required");
        }
    }

    private String on(boolean value) { return value ? "✅" : "❌"; }

    private void editInteractive(long chatId, long messageId, boolean captionMessage, String text,
                                 InlineKeyboard keyboard) {
        if (captionMessage) {
            telegram.editCaption(chatId, messageId, text, keyboard);
        } else {
            telegram.editMessage(chatId, messageId, text, keyboard);
        }
    }

    private void safeAnswerCallback(String callbackId) {
        try {
            telegram.answerCallback(callbackId, null, false);
        } catch (TelegramApiException e) {
            log.debug("Could not acknowledge callback {}: {}", callbackId, e.getMessage());
        }
    }

    private void answerCallbackError(String callbackId, String text) {
        String message = text == null || text.isBlank() ? "This action could not be completed." : text;
        if (message.length() > 190) message = message.substring(0, 187) + "…";
        try {
            telegram.answerCallback(callbackId, message, true);
        } catch (TelegramApiException e) {
            log.debug("Could not show callback error {}: {}", callbackId, e.getMessage());
        }
    }
}
