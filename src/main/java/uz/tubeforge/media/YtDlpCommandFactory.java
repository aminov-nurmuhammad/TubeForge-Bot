package uz.tubeforge.media;

import org.springframework.stereotype.Component;
import uz.tubeforge.config.MediaProperties;
import uz.tubeforge.domain.JobType;
import uz.tubeforge.domain.SourceType;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class YtDlpCommandFactory {
    private final MediaProperties properties;

    public YtDlpCommandFactory(MediaProperties properties) {
        this.properties = properties;
    }

    public List<String> inspect(ParsedMediaUrl url) {
        List<String> command = base(false);
        command.addAll(List.of("--dump-single-json", "--skip-download", "--no-warnings", "--no-progress"));
        if (url.sourceType() == uz.tubeforge.domain.SourceType.PLAYLIST) {
            command.addAll(List.of("--yes-playlist", "--flat-playlist", "--playlist-end",
                    Integer.toString(properties.maxPlaylistItems())));
        } else {
            command.add("--no-playlist");
        }
        command.add(url.normalizedUrl());
        return command;
    }

    public List<String> download(JobType type, String formatCode, String url, Path outputDirectory,
                                 ClipRange clipRange, int maxPlaylistItems) {
        return download(type, formatCode, url, outputDirectory, clipRange, maxPlaylistItems, SourceType.VIDEO);
    }

    public List<String> download(JobType type, String formatCode, String url, Path outputDirectory,
                                 ClipRange clipRange, int maxPlaylistItems, SourceType sourceType) {
        List<String> command = base();
        command.addAll(List.of("--newline", "--restrict-filenames", "--trim-filenames", "180", "--no-mtime",
                "--progress-template", "download:%(progress._percent_str)s|%(progress._speed_str)s|%(progress._eta_str)s",
                "-o", outputDirectory.resolve("%(title).120B-%(id)s.%(ext)s").toString()));

        switch (type) {
            case VIDEO -> addVideo(command, formatCode, sourceType);
            case AUDIO -> addAudio(command, formatCode);
            case THUMBNAIL -> command.addAll(List.of("--skip-download", "--write-thumbnail", "--convert-thumbnails", "jpg"));
            case ALL_THUMBNAILS -> command.addAll(List.of("--skip-download", "--write-all-thumbnails", "--convert-thumbnails", "jpg"));
            case SUBTITLES, TRANSCRIPT, AI_SUMMARY, AI_CHAPTERS, AI_STUDY_NOTES -> addSubtitles(command, formatCode);
            case CLIP_VIDEO -> {
                addVideo(command, formatCode == null || formatCode.isBlank() ? "720" : formatCode, sourceType);
                addClip(command, clipRange);
            }
            case CLIP_AUDIO -> {
                addAudio(command, formatCode == null || formatCode.isBlank() ? "mp3:192" : formatCode);
                addClip(command, clipRange);
            }
            case PLAYLIST_VIDEO -> {
                command.addAll(List.of("--yes-playlist", "--playlist-end", Integer.toString(maxPlaylistItems)));
                addVideo(command, formatCode, sourceType);
            }
            case PLAYLIST_AUDIO -> {
                command.addAll(List.of("--yes-playlist", "--playlist-end", Integer.toString(maxPlaylistItems)));
                addAudio(command, formatCode);
            }
        }
        if (type != JobType.PLAYLIST_AUDIO && type != JobType.PLAYLIST_VIDEO) command.add("--no-playlist");
        command.add(url);
        return command;
    }

    private List<String> base() {
        return base(true);
    }

    private List<String> base(boolean download) {
        int retries = download ? properties.extractorRetries() : Math.min(2, properties.extractorRetries());
        List<String> command = new ArrayList<>(List.of(
                properties.ytDlpPath(),
                "--ignore-config",
                "--ffmpeg-location", properties.ffmpegPath(),
                "--socket-timeout", Long.toString(Math.max(1, properties.socketTimeout().toSeconds())),
                "--retries", Integer.toString(retries),
                "--fragment-retries", Integer.toString(retries),
                "--extractor-retries", Integer.toString(retries),
                "--no-colors"
        ));
        if (download) {
            command.addAll(List.of("--concurrent-fragments", Integer.toString(properties.concurrentFragments())));
        }
        if (properties.hasCookiesFile()) {
            command.addAll(List.of("--cookies", properties.cookiesFile().toAbsolutePath().normalize().toString()));
        }
        if (properties.hasProxy()) {
            command.addAll(List.of("--proxy", properties.proxyUrl()));
        }
        return command;
    }

    private void addVideo(List<String> command, String quality, SourceType sourceType) {
        String requestedQuality = quality == null ? "" : quality.replace('~', ':');
        String selector;
        if ("best".equalsIgnoreCase(requestedQuality)) {
            // Public Reels normally expose their original H.264/AAC MP4 as one combined
            // stream. Prefer it before DASH-style merging: one HTTP transfer and no merge
            // is both the fastest path and the closest representation of the source file.
            selector = sourceType == SourceType.INSTAGRAM_REEL
                    ? "best[ext=mp4][vcodec!=none][acodec!=none]/best[vcodec!=none][acodec!=none]/bv*+ba"
                    : "bv*+ba/b";
        } else if ("small".equalsIgnoreCase(requestedQuality)) {
            selector = "worstvideo+worstaudio/worst";
        } else if (requestedQuality.startsWith("format:")) {
            selector = exactFormatSelector(requestedQuality);
        } else {
            int height = parseHeight(requestedQuality.replace("height:", ""));
            selector = "bv*[height<=" + height + "]+ba/b[height<=" + height + "]/best[height<=" + height + "]";
        }
        command.addAll(List.of("-f", selector, "--merge-output-format", "mp4"));
    }

    private String exactFormatSelector(String value) {
        String[] parts = value.split(":", 4);
        if (parts.length < 2 || !parts[1].matches("[A-Za-z0-9._+,-]+")) return "bv*+ba/b";
        String formatId = parts[1];
        int height = parts.length == 4 ? parseHeight(parts[3]) : 4320;
        String qualityFallback = "bv*[height<=" + height + "]+ba/b[height<=" + height
                + "]/best[height<=" + height + "]";
        return parts.length >= 3 && "combined".equals(parts[2])
                ? formatId + "/" + qualityFallback
                : formatId + "+ba/" + qualityFallback;
    }

    private void addAudio(List<String> command, String code) {
        String[] parts = (code == null ? "mp3:192" : code).split(":", 2);
        String requested = parts[0].toLowerCase(Locale.ROOT);
        String format = switch (requested) {
            case "m4a", "wav", "ogg", "flac" -> requested;
            default -> "mp3";
        };
        int quality = parseAudioQuality(parts.length > 1 ? parts[1] : "192");
        command.addAll(List.of("-f", "bestaudio[ext=m4a]/bestaudio/best", "-x", "--audio-format", format,
                "--audio-quality", quality + "K", "--embed-metadata"));
    }

    private void addSubtitles(List<String> command, String languageCode) {
        String language = languageCode == null || languageCode.isBlank() ? "en" : languageCode;
        command.addAll(List.of("--skip-download", "--write-subs", "--write-auto-subs",
                "--sub-langs", language, "--sub-format", "srt/best", "--convert-subs", "srt"));
    }

    private void addClip(List<String> command, ClipRange range) {
        if (range == null) throw new IllegalArgumentException("Clip range is required");
        command.addAll(List.of("--download-sections", "*" + range.startFormatted() + "-" + range.endFormatted(),
                "--force-keyframes-at-cuts"));
    }

    private int parseHeight(String value) {
        try {
            int parsed = Integer.parseInt(value == null ? "720" : value.replaceAll("[^0-9]", ""));
            return Math.max(144, Math.min(4320, parsed));
        } catch (NumberFormatException ignored) {
            return 720;
        }
    }

    private int parseAudioQuality(String value) {
        try {
            int parsed = Integer.parseInt(value == null ? "192" : value.replaceAll("[^0-9]", ""));
            return Math.max(64, Math.min(320, parsed));
        } catch (NumberFormatException ignored) {
            return 192;
        }
    }
}
