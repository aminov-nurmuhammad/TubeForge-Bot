package uz.tubeforge.media;

import org.springframework.stereotype.Component;
import uz.tubeforge.domain.SourceType;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class YouTubeUrlParser {
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s<>]+", Pattern.CASE_INSENSITIVE);
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
            URI uri = new URI(candidate.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null || uri.getUserInfo() != null) return Optional.empty();
            if (!scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("http")) return Optional.empty();
            host = host.toLowerCase(Locale.ROOT);
            if (!HOSTS.contains(host)) return Optional.empty();

            String path = uri.getPath() == null ? "" : uri.getPath();
            String query = uri.getQuery() == null ? "" : uri.getQuery();
            SourceType type;
            if (path.startsWith("/playlist") || (!query.contains("v=") && query.contains("list="))) {
                type = SourceType.PLAYLIST;
            } else if (path.startsWith("/shorts/")) {
                type = SourceType.SHORT;
            } else {
                type = SourceType.VIDEO;
            }
            URI normalized = new URI("https", null, host, -1, path, query, null);
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
}
