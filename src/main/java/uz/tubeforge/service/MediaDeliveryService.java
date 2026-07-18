package uz.tubeforge.service;

import org.springframework.stereotype.Service;
import uz.tubeforge.config.TelegramProperties;
import uz.tubeforge.domain.AppUser;
import uz.tubeforge.domain.JobType;
import uz.tubeforge.media.MediaFileTools;
import uz.tubeforge.media.MediaInfo;
import uz.tubeforge.media.MediaProcessingException;
import uz.tubeforge.telegram.TelegramApiClient;
import uz.tubeforge.util.Html;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class MediaDeliveryService {
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
        long limit = telegramProperties.maxUploadBytes();
        Path prepared = original;
        boolean mediaVideo = isVideo(type);
        boolean mediaAudio = isAudio(type);

        if (size(prepared) > limit && user.isAutoCompress() && mediaVideo) {
            prepared = fileTools.compressVideo(prepared, (long) (limit * 0.92));
        } else if (size(prepared) > limit && user.isAutoCompress() && mediaAudio) {
            prepared = fileTools.compressAudio(prepared, (long) (limit * 0.92));
        }

        List<Path> delivered = new ArrayList<>();
        if (size(prepared) <= limit) {
            sendOne(chatId, prepared, type, info, user, null);
            delivered.add(prepared);
            return new DeliveryResult(delivered, size(prepared));
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
            sendOne(chatId, part, type, info, user, "Part " + (i + 1) + "/" + parts.size());
            delivered.add(part);
        }
        long total = delivered.stream().mapToLong(this::size).sum();
        return new DeliveryResult(delivered, total);
    }

    private void sendOne(long chatId, Path file, JobType type, MediaInfo info, AppUser user, String part) {
        String caption = "✅ <b>TubeForge</b>\n" + Html.escape(info.title())
                + (part == null ? "" : "\n" + part);
        if (type == JobType.THUMBNAIL) {
            telegram.sendPhoto(chatId, file, caption);
        } else if (isVideo(type) && !user.isSendAsDocument()) {
            telegram.sendVideo(chatId, file, caption, true);
        } else if (isAudio(type)) {
            telegram.sendAudio(chatId, file, caption, info.title(), info.channel());
        } else {
            telegram.sendDocument(chatId, file, caption);
        }
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

    public record DeliveryResult(List<Path> files, long totalBytes) {}
}
