package uz.tubeforge.service;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.tubeforge.config.TelegramProperties;
import uz.tubeforge.domain.AppUser;
import uz.tubeforge.domain.JobType;
import uz.tubeforge.media.MediaFileTools;
import uz.tubeforge.media.MediaInfo;
import uz.tubeforge.media.MediaProcessingException;
import uz.tubeforge.telegram.TelegramApiClient;
import uz.tubeforge.telegram.TelegramApiException;
import uz.tubeforge.telegram.TelegramFileReference;
import uz.tubeforge.telegram.model.InlineKeyboard;
import uz.tubeforge.telegram.model.TgMessage;
import uz.tubeforge.util.Html;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class MediaDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(MediaDeliveryService.class);

    private final TelegramProperties telegramProperties;
    private final TelegramApiClient telegram;
    private final MediaFileTools fileTools;

    public MediaDeliveryService(TelegramProperties telegramProperties, TelegramApiClient telegram,
                                MediaFileTools fileTools) {
        this.telegramProperties = telegramProperties;
        this.telegram = telegram;
        this.fileTools = fileTools;
    }

    public DeliveryResult deliver(long chatId, Path original, JobType type, MediaInfo info, AppUser user) {
        return deliver(chatId, original, type, info, user, null);
    }

    public DeliveryResult deliver(long chatId, Path original, JobType type, MediaInfo info, AppUser user,
                                  InlineKeyboard keyboard) {
        long limit = telegramProperties.maxUploadBytes();
        Path prepared = original;
        boolean mediaVideo = isVideo(type);
        boolean mediaAudio = isAudio(type);
        boolean instantReel = mediaVideo
                && info.sourceType() == uz.tubeforge.domain.SourceType.INSTAGRAM_REEL;

        // Validate and normalize every video, including document uploads. This prevents a
        // video-only source from ever being delivered as a silent file when the user chose
        // "send as document" in settings.
        if (mediaVideo) {
            prepared = fileTools.prepareTelegramVideo(prepared);
        }

        if (size(prepared) > limit && (user.isAutoCompress() || instantReel) && mediaVideo) {
            prepared = fileTools.compressVideo(prepared, (long) (limit * 0.92));
        } else if (size(prepared) > limit && user.isAutoCompress() && mediaAudio) {
            prepared = fileTools.compressAudio(prepared, (long) (limit * 0.92));
        }

        List<Path> delivered = new ArrayList<>();
        List<TelegramFileReference> references = new ArrayList<>();
        if (size(prepared) <= limit) {
            TelegramFileReference.from(sendOne(chatId, prepared, type, info, user, null, keyboard)).ifPresent(references::add);
            delivered.add(prepared);
            return new DeliveryResult(delivered, size(prepared), references);
        }

        if (!mediaVideo && !mediaAudio) {
            throw new MediaProcessingException("FILE_TOO_LARGE",
                    "The generated file is larger than this bot's Telegram upload limit.");
        }

        List<Path> parts = fileTools.split(prepared, limit);
        if (parts.isEmpty()) {
            throw new MediaProcessingException("FILE_TOO_LARGE", "The media is too large to deliver through Telegram.");
        }
        for (int i = 0; i < parts.size(); i++) {
            Path part = parts.get(i);
            if (size(part) > limit && mediaVideo) part = fileTools.compressVideo(part, (long) (limit * 0.90));
            if (size(part) > limit && mediaAudio) part = fileTools.compressAudio(part, (long) (limit * 0.90));
            if (size(part) > limit) throw new MediaProcessingException("PART_TOO_LARGE", "A media part remains too large to upload.");
            TelegramFileReference.from(sendOne(chatId, part, type, info, user,
                    "Part " + (i + 1) + "/" + parts.size(), i == 0 ? keyboard : null)).ifPresent(references::add);
            delivered.add(part);
        }
        long total = delivered.stream().mapToLong(this::size).sum();
        return new DeliveryResult(delivered, total, references);
    }

    private TgMessage sendOne(long chatId, Path file, JobType type, MediaInfo info, AppUser user, String part,
                              InlineKeyboard keyboard) {
        String caption = caption(info) + (part == null ? "" : "\n" + part);
        if (type == JobType.THUMBNAIL) {
            try {
                return sendPhoto(chatId, file, caption, keyboard);
            } catch (TelegramApiException e) {
                log.debug("Telegram rejected inline photo {}: {}", file.getFileName(), e.getMessage());
                return sendDocument(chatId, file, caption, keyboard);
            }
        } else if (isVideo(type) && (!user.isSendAsDocument()
                || info.sourceType() == uz.tubeforge.domain.SourceType.INSTAGRAM_REEL)) {
            try {
                return sendVideo(chatId, file, caption, keyboard);
            } catch (TelegramApiException e) {
                log.debug("Telegram rejected inline video {}: {}", file.getFileName(), e.getMessage());
                return sendDocument(chatId, file,
                        caption + "\n\n⚠️ Telegram could not display this file as an inline video.", keyboard);
            }
        } else if (isAudio(type) && isTelegramAudio(file)) {
            try {
                return sendAudio(chatId, file, caption, info.title(), info.channel(), keyboard);
            } catch (TelegramApiException e) {
                log.debug("Telegram rejected inline audio {}: {}", file.getFileName(), e.getMessage());
                return sendDocument(chatId, file, caption, keyboard);
            }
        } else {
            return sendDocument(chatId, file, caption, keyboard);
        }
    }

    private TgMessage sendVideo(long chatId, Path file, String caption, InlineKeyboard keyboard) {
        return keyboard == null ? telegram.sendVideo(chatId, file, caption, true)
                : telegram.sendVideo(chatId, file, caption, true, keyboard);
    }

    private TgMessage sendPhoto(long chatId, Path file, String caption, InlineKeyboard keyboard) {
        return keyboard == null ? telegram.sendPhoto(chatId, file, caption)
                : telegram.sendPhoto(chatId, file, caption, keyboard);
    }

    private TgMessage sendAudio(long chatId, Path file, String caption, String title, String performer,
                                InlineKeyboard keyboard) {
        return keyboard == null ? telegram.sendAudio(chatId, file, caption, title, performer)
                : telegram.sendAudio(chatId, file, caption, title, performer, keyboard);
    }

    private TgMessage sendDocument(long chatId, Path file, String caption, InlineKeyboard keyboard) {
        return keyboard == null ? telegram.sendDocument(chatId, file, caption)
                : telegram.sendDocument(chatId, file, caption, keyboard);
    }

    private String caption(MediaInfo info) {
        if (info.sourceType() == uz.tubeforge.domain.SourceType.INSTAGRAM_REEL) {
            String title = info.title() == null ? "" : info.title().strip();
            return "📸 <b>Instagram Reel</b>" + (title.isBlank() || "Instagram Reel".equals(title)
                    ? "" : "\n" + Html.escape(title));
        }
        return "✅ <b>TubeForge</b>\n" + Html.escape(info.title());
    }

    private boolean isVideo(JobType type) {
        return type == JobType.VIDEO || type == JobType.CLIP_VIDEO || type == JobType.PLAYLIST_VIDEO;
    }

    private boolean isAudio(JobType type) {
        return type == JobType.AUDIO || type == JobType.CLIP_AUDIO || type == JobType.PLAYLIST_AUDIO;
    }

    private long size(Path path) {
        try { return Files.size(path); } catch (IOException e) { return 0; }
    }

    private boolean isTelegramAudio(Path path) {
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".mp3") || name.endsWith(".m4a");
    }

    public record DeliveryResult(List<Path> files, long totalBytes, List<TelegramFileReference> references) {
        public DeliveryResult {
            files = List.copyOf(files);
            references = List.copyOf(references);
        }

        public java.util.Optional<TelegramFileReference> singleReference() {
            return references.size() == 1 ? java.util.Optional.of(references.get(0)) : java.util.Optional.empty();
        }
    }
}
