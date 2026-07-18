package uz.tubeforge.media;

import org.springframework.stereotype.Component;
import uz.tubeforge.config.MediaProperties;
import uz.tubeforge.domain.JobType;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class YtDlpCommandFactory {
    private final MediaProperties properties;

    public YtDlpCommandFactory(MediaProperties properties) {
        this.properties = properties;
    }

    public List<String> inspect(ParsedYouTubeUrl url) {
        List<String> command = base();
        command.addAll(List.of("--dump-single-json", "--skip-download", "--no-warnings", "--no-progress"));
        if (url.sourceType() == uz.tubeforge.domain.SourceType.PLAYLIST) {
            command.addAll(List.of("--yes-playlist", "--playlist-end", Integer.toString(properties.maxPlaylistItems())));
        } else {
            command.add("--no-playlist");
        }
        command.add(url.normalizedUrl());
        return command;
    }

    public List<String> download(JobType type, String formatCode, String url, Path outputDirectory,
                                 ClipRange clipRange, int maxPlaylistItems) {
        List<String> command = base();
        command.addAll(List.of("--newline", "--no-warnings", "--restrict-filenames",
                "--progress-template", "download:%(progress._percent_str)s|%(progress._speed_str)s|%(progress._eta_str)s",
                "-o", outputDirectory.resolve("%(title).120B-%(id)s.%(ext)s").toString()));

        switch (type) {
            case VIDEO -> addVideo(command, formatCode);
            case AUDIO -> addAudio(command, formatCode);
            case THUMBNAIL -> command.addAll(List.of("--skip-download", "--write-thumbnail", "--convert-thumbnails", "jpg"));
            case ALL_THUMBNAILS -> command.addAll(List.of("--skip-download", "--write-all-thumbnails", "--convert-thumbnails", "jpg"));
            case SUBTITLES, TRANSCRIPT -> addSubtitles(command, formatCode);
            case CLIP_VIDEO -> {
                addVideo(command, formatCode == null || formatCode.isBlank() ? "720" : formatCode);
                addClip(command, clipRange);
            }
            case CLIP_AUDIO -> {
                addAudio(command, formatCode == null || formatCode.isBlank() ? "mp3:192" : formatCode);
                addClip(command, clipRange);
            }
            case PLAYLIST_VIDEO -> {
                command.addAll(List.of("--yes-playlist", "--playlist-end", Integer.toString(maxPlaylistItems)));
                addVideo(command, formatCode);
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
        return new ArrayList<>(List.of(properties.ytDlpPath(), "--ignore-config"));
    }

    private void addVideo(List<String> command, String quality) {
        String selector;
        if ("best".equalsIgnoreCase(quality)) {
            selector = "bv*[ext=mp4]+ba[ext=m4a]/bv*+ba/b";
        } else if ("small".equalsIgnoreCase(quality)) {
            selector = "worst[ext=mp4]/worst";
        } else {
            int height = parseHeight(quality);
            selector = "bv*[height<=" + height + "][ext=mp4]+ba[ext=m4a]/"
                    + "bv*[height<=" + height + "]+ba/b[height<=" + height + "]";
        }
        command.addAll(List.of("-f", selector, "--merge-output-format", "mp4", "--remux-video", "mp4"));
    }

    private void addAudio(List<String> command, String code) {
        String[] parts = (code == null ? "mp3:192" : code).split(":", 2);
        String format = switch (parts[0].toLowerCase()) {
            case "m4a", "wav", "ogg", "flac" -> parts[0].toLowerCase();
            default -> "mp3";
        };
        String quality = parts.length > 1 ? parts[1] : "192";
        command.addAll(List.of("-f", "bestaudio/best", "-x", "--audio-format", format,
                "--audio-quality", quality + "K", "--add-metadata"));
        if (!"wav".equals(format)) command.add("--embed-thumbnail");
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
}
