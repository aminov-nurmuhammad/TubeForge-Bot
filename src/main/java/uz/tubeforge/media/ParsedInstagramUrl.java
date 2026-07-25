package uz.tubeforge.media;

import uz.tubeforge.domain.SourceType;

/** Canonical public Instagram Reel URL. Authentication is intentionally not bypassed here. */
public record ParsedInstagramUrl(String normalizedUrl, SourceType sourceType, String reelId)
        implements ParsedMediaUrl {

    public ParsedInstagramUrl {
        if (sourceType != SourceType.INSTAGRAM_REEL) {
            throw new IllegalArgumentException("Instagram URLs must use INSTAGRAM_REEL source type");
        }
        if (reelId == null || reelId.isBlank()) throw new IllegalArgumentException("Reel id is required");
    }

    @Override
    public java.util.Optional<String> videoId() {
        return java.util.Optional.of(reelId);
    }
}
