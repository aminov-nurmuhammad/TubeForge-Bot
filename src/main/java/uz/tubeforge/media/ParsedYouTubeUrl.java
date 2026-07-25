package uz.tubeforge.media;

import uz.tubeforge.domain.SourceType;

import java.net.URI;
import java.util.Arrays;
import java.util.Optional;

public record ParsedYouTubeUrl(String normalizedUrl, SourceType sourceType) implements ParsedMediaUrl {
    public Optional<String> videoId() {
        if (sourceType == SourceType.PLAYLIST) return Optional.empty();
        URI uri = URI.create(normalizedUrl);
        if (uri.getPath() != null && (uri.getPath().startsWith("/shorts/") || uri.getPath().startsWith("/live/"))) {
            String prefix = uri.getPath().startsWith("/shorts/") ? "/shorts/" : "/live/";
            return Arrays.stream(uri.getPath().substring(prefix.length()).split("/"))
                    .filter(value -> !value.isBlank()).findFirst();
        }
        return queryValue(uri.getRawQuery(), "v");
    }

    public Optional<String> playlistId() {
        return queryValue(URI.create(normalizedUrl).getRawQuery(), "list");
    }

    private Optional<String> queryValue(String query, String key) {
        if (query == null || query.isBlank()) return Optional.empty();
        return Arrays.stream(query.split("&"))
                .map(part -> part.split("=", 2))
                .filter(parts -> parts.length == 2 && parts[0].equalsIgnoreCase(key))
                .map(parts -> parts[1])
                .filter(value -> !value.isBlank())
                .findFirst();
    }
}
