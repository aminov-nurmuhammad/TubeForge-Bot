package uz.tubeforge.media;

import uz.tubeforge.domain.SourceType;

import java.util.Optional;

/** A normalized URL accepted by the shared yt-dlp media pipeline. */
public interface ParsedMediaUrl {
    String normalizedUrl();

    SourceType sourceType();

    default Optional<String> videoId() { return Optional.empty(); }

    default Optional<String> playlistId() { return Optional.empty(); }
}
