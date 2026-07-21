package uz.tubeforge.media;

import org.springframework.stereotype.Component;
import uz.tubeforge.domain.SourceType;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class YouTubeUrlParser {
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?:https?://)?(?:www\\.|m\\.|music\\.)?(?:youtube\\.com|youtu\\.be|youtube-nocookie\\.com)/[^\\s<>]+",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> HOSTS = Set.of(
            "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com",
            "youtu.be", "www.youtu.be", "youtube-nocookie.com", "www.youtube-nocookie.com"
    );

    public Optional<ParsedYouTubeUrl> find(String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        Matcher matcher = URL_PATTERN.matcher(text);
        while (matcher.find()) {
            String candidate = stripTrailingPunctuation(matcher.group());
            Optional<ParsedYouTubeUrl> parsed = parse(candidate);
            if (parsed.isPresent()) return parsed;
        }
        return Optional.empty();
    }

    public Optional<ParsedYouTubeUrl> parse(String candidate) {
        try {
            String input = stripTrailingPunctuation(candidate == null ? "" : candidate.trim());
            if (!input.contains("://")) input = "https://" + input;
            URI uri = new URI(input);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null || uri.getUserInfo() != null) return Optional.empty();
            if (!scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("http")) return Optional.empty();
            host = host.toLowerCase(Locale.ROOT);
            if (!HOSTS.contains(host)) return Optional.empty();

            String path = uri.getRawPath() == null ? "" : uri.getRawPath();
            String query = cleanQuery(uri.getRawQuery());
            String lowerQuery = query.toLowerCase(Locale.ROOT);
            SourceType type;
            if (path.startsWith("/playlist") || (!hasParameter(lowerQuery, "v") && hasParameter(lowerQuery, "list"))) {
                type = SourceType.PLAYLIST;
            } else if (path.startsWith("/shorts/")) {
                type = SourceType.SHORT;
            } else {
                type = SourceType.VIDEO;
            }

            String normalizedPath = path.isBlank() ? "/" : path;
            String normalizedQuery = query;
            if (host.endsWith("youtu.be")) {
                String videoId = firstPathSegment(path);
                if (videoId.isBlank()) return Optional.empty();
                normalizedPath = "/watch";
                normalizedQuery = "v=" + videoId + (query.isBlank() ? "" : "&" + query);
                type = SourceType.VIDEO;
            } else if (host.contains("youtube-nocookie.com") && path.startsWith("/embed/")) {
                String videoId = firstPathSegment(path.substring("/embed".length()));
                if (videoId.isBlank()) return Optional.empty();
                normalizedPath = "/watch";
                normalizedQuery = "v=" + videoId + (query.isBlank() ? "" : "&" + query);
                type = SourceType.VIDEO;
            } else if (path.startsWith("/embed/")) {
                String videoId = firstPathSegment(path.substring("/embed".length()));
                if (videoId.isBlank()) return Optional.empty();
                normalizedPath = "/watch";
                normalizedQuery = "v=" + videoId + (query.isBlank() ? "" : "&" + query);
                type = SourceType.VIDEO;
            }

            URI normalized = new URI("https", null, "www.youtube.com", -1,
                    normalizedPath, normalizedQuery.isBlank() ? null : normalizedQuery, null);
            return Optional.of(new ParsedYouTubeUrl(normalized.toASCIIString(), type));
        } catch (URISyntaxException ignored) {
            return Optional.empty();
        }
    }

    private String stripTrailingPunctuation(String value) {
        int end = value.length();
        while (end > 0 && ".,;:!?)]}'\"".indexOf(value.charAt(end - 1)) >= 0) end--;
        return value.substring(0, end);
    }

    private String cleanQuery(String query) {
        if (query == null || query.isBlank()) return "";
        return Arrays.stream(query.split("&"))
                .filter(part -> !part.isBlank())
                .filter(part -> {
                    String key = part.split("=", 2)[0].toLowerCase(Locale.ROOT);
                    return !key.equals("si") && !key.equals("feature") && !key.equals("pp")
                            && !key.startsWith("utm_");
                })
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    private boolean hasParameter(String query, String key) {
        if (query.isBlank()) return false;
        return Arrays.stream(query.split("&"))
                .map(part -> part.split("=", 2)[0])
                .anyMatch(key::equals);
    }

    private String firstPathSegment(String path) {
        return Arrays.stream(path.split("/"))
                .filter(segment -> !segment.isBlank())
                .findFirst()
                .orElse("");
    }
}
