package uz.tubeforge.media;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import uz.tubeforge.config.MediaProperties;

import java.nio.file.Path;
import java.util.Locale;

@Service
public class MediaInspectionService {
    private final YtDlpCommandFactory commands;
    private final ManagedProcessRunner processRunner;
    private final MediaMetadataParser parser;
    private final MediaProperties properties;
    private final ObjectMapper objectMapper;

    public MediaInspectionService(YtDlpCommandFactory commands, ManagedProcessRunner processRunner,
                                  MediaMetadataParser parser, MediaProperties properties,
                                  ObjectMapper objectMapper) {
        this.commands = commands;
        this.processRunner = processRunner;
        this.parser = parser;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public MediaInfo inspect(ParsedMediaUrl url) {
        ProcessResult result = processRunner.capture(commands.inspect(url), Path.of("."), properties.metadataTimeout());
        if (result.timedOut()) {
            throw new MediaProcessingException("METADATA_TIMEOUT", timeoutMessage(url));
        }
        if (!result.successful()) throw classify(result.output(), url);
        try {
            JsonNode json = objectMapper.readTree(findJson(result.output()));
            MediaInfo info = parser.parse(json, url.sourceType());
            if (info.sourceType() != uz.tubeforge.domain.SourceType.PLAYLIST
                    && info.durationSeconds() > properties.maxVideoDurationSeconds()) {
                throw new MediaProcessingException("VIDEO_TOO_LONG", "This video is longer than the configured limit.");
            }
            return info;
        } catch (MediaProcessingException e) {
            throw e;
        } catch (Exception e) {
            throw new MediaProcessingException("INVALID_METADATA", "The video information could not be understood.", e);
        }
    }

    private String findJson(String output) {
        int start = output.indexOf('{');
        int end = output.lastIndexOf('}');
        if (start < 0 || end < start) throw new MediaProcessingException("NO_METADATA", "No video information was returned.");
        return output.substring(start, end + 1);
    }

    private MediaProcessingException classify(String output, ParsedMediaUrl url) {
        String text = output == null ? "" : output.toLowerCase(Locale.ROOT);
        if (text.contains("private video")) return new MediaProcessingException("PRIVATE_VIDEO", "This video is private.");
        if (text.contains("members-only") || text.contains("members only")) {
            return new MediaProcessingException("MEMBERS_ONLY", "This is members-only content and cannot be processed.");
        }
        if (text.contains("video unavailable") || text.contains("not available")) {
            return new MediaProcessingException("UNAVAILABLE", "This video is unavailable or restricted in the server's region.");
        }
        if (text.contains("sign in to confirm your age") || text.contains("age-restricted")) {
            return new MediaProcessingException("AGE_RESTRICTED", "This video requires age verification and cannot be processed.");
        }
        if (text.contains("sign in to confirm you’re not a bot")
                || text.contains("sign in to confirm you're not a bot")
                || text.contains("use --cookies-from-browser")
                || text.contains("use --cookies for the authentication")) {
            return new MediaProcessingException("YOUTUBE_AUTH_REQUIRED",
                    "YouTube asked this server to verify itself. Configure YOUTUBE_COOKIES_FILE with an exported cookies.txt file or try again later.");
        }
        if (text.contains("http error 429") || text.contains("too many requests")) {
            return new MediaProcessingException("YOUTUBE_RATE_LIMITED",
                    "YouTube temporarily rate-limited this server. Wait a few minutes or configure YOUTUBE_COOKIES_FILE.");
        }
        if (text.contains("no supported javascript runtime") || text.contains("javascript runtime") && text.contains("missing")) {
            return new MediaProcessingException("JS_RUNTIME_MISSING",
                    "YouTube requires a JavaScript runtime. Install Deno and restart TubeForge.");
        }
        if (text.contains("ffmpeg not found") || text.contains("ffprobe not found")) {
            return new MediaProcessingException("FFMPEG_MISSING",
                    "FFmpeg could not be found. Check FFMPEG_PATH and FFPROBE_PATH.");
        }
        if (text.contains("copyright")) return new MediaProcessingException("COPYRIGHT_BLOCK", "This video is blocked by YouTube.");
        return new MediaProcessingException("INSPECTION_FAILED", genericMessage(output, url));
    }

    private String timeoutMessage(ParsedMediaUrl url) {
        return url.sourceType() == uz.tubeforge.domain.SourceType.INSTAGRAM_REEL
                ? "Instagram took too long to return this Reel's information."
                : "YouTube took too long to return this video's information.";
    }

    private String genericMessage(String output, ParsedMediaUrl url) {
        String lower = output == null ? "" : output.toLowerCase(Locale.ROOT);
        boolean instagram = url.sourceType() == uz.tubeforge.domain.SourceType.INSTAGRAM_REEL;
        if (instagram && (lower.contains("checkpoint") || lower.contains("login required")
                || lower.contains("please log in") || lower.contains("log in to see"))) {
            return "Instagram requires a public session for this Reel. Private or login-only Reels are not supported.";
        }
        if (instagram && lower.contains("private")) {
            return "This Instagram Reel is private or restricted.";
        }
        if (instagram && (lower.contains("http error 429") || lower.contains("too many requests"))) {
            return "Instagram temporarily rate-limited this server. Wait a few minutes and try again.";
        }
        return instagram
                ? "This Instagram Reel could not be processed right now. It may be private, removed or rate-limited."
                : "This YouTube link could not be processed right now.";
    }
}
