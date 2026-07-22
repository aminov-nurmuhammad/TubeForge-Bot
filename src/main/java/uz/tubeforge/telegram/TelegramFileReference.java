package uz.tubeforge.telegram;

import uz.tubeforge.telegram.model.TgMediaFile;
import uz.tubeforge.telegram.model.TgMessage;

import java.util.List;
import java.util.Optional;

public record TelegramFileReference(
        DeliveryKind kind,
        String fileId,
        String fileUniqueId,
        long fileSize
) {
    public TelegramFileReference {
        if (kind == null) throw new IllegalArgumentException("Delivery kind is required");
        if (fileId == null || fileId.isBlank()) throw new IllegalArgumentException("Telegram file ID is required");
        fileUniqueId = fileUniqueId == null ? "" : fileUniqueId;
        fileSize = Math.max(0, fileSize);
    }

    public static Optional<TelegramFileReference> from(TgMessage message) {
        if (message == null) return Optional.empty();
        if (message.video() != null) return Optional.of(from(DeliveryKind.VIDEO, message.video()));
        if (message.audio() != null) return Optional.of(from(DeliveryKind.AUDIO, message.audio()));
        if (message.document() != null) return Optional.of(from(DeliveryKind.DOCUMENT, message.document()));
        List<TgMediaFile> photos = message.photo();
        if (photos != null && !photos.isEmpty()) return Optional.of(from(DeliveryKind.PHOTO, photos.get(photos.size() - 1)));
        return Optional.empty();
    }

    private static TelegramFileReference from(DeliveryKind kind, TgMediaFile file) {
        return new TelegramFileReference(kind, file.fileId(), file.fileUniqueId(),
                file.fileSize() == null ? 0 : file.fileSize());
    }
}
