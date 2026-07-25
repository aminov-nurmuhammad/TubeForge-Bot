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

/** Parses only public Instagram Reel URLs; login/private links are left to yt-dlp to reject safely. */
@Component
public class InstagramUrlParser {
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?:https?://)?(?:www\\.|m\\.)?instagram\\.com/(?:reel|reels)/[^\\s<>/?#]+(?:/)?(?:\\?[^\\s<>]*)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{3,128}$");
    private static final Set<String> HOSTS = Set.of("instagram.com", "www.instagram.com", "m.instagram.com");

    public Optional<ParsedInstagramUrl> find(String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        Matcher matcher = URL_PATTERN.matcher(text);
        while (matcher.find()) {
            Optional<ParsedInstagramUrl> parsed = parse(stripTrailingPunctuation(matcher.group()));
            if (parsed.isPresent()) return parsed;
        }
        return Optional.empty();
    }

    public Optional<ParsedInstagramUrl> parse(String candidate) {
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

            String[] segments = (uri.getRawPath() == null ? "" : uri.getRawPath()).split("/");
            if (segments.length < 3) return Optional.empty();
            String kind = segments[1].toLowerCase(Locale.ROOT);
            if (!kind.equals("reel") && !kind.equals("reels")) return Optional.empty();
            String reelId = segments[2];
            if (!ID_PATTERN.matcher(reelId).matches()) return Optional.empty();

            URI normalized = new URI("https", null, "www.instagram.com", -1,
                    "/reel/" + reelId + "/", null, null);
            return Optional.of(new ParsedInstagramUrl(normalized.toASCIIString(), SourceType.INSTAGRAM_REEL, reelId));
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
