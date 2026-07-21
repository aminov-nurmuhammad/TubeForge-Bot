package uz.tubeforge.media;

import org.springframework.stereotype.Service;
import uz.tubeforge.config.MediaProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class MediaFileTools {
    private final MediaProperties properties;
    private final ManagedProcessRunner runner;

    public MediaFileTools(MediaProperties properties, ManagedProcessRunner runner) {
        this.properties = properties;
        this.runner = runner;
    }

    public double durationSeconds(Path input) {
        List<String> command = List.of(properties.ffprobePath(), "-v", "error", "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1", input.toString());
        ProcessResult result = runner.capture(command, input.getParent(), Duration.ofSeconds(30));
        if (!result.successful()) return 0;
        try { return Double.parseDouble(result.output().trim()); } catch (NumberFormatException ignored) { return 0; }
    }

    public Path prepareTelegramVideo(Path input) {
        String videoCodec = codec(input, "v:0");
        String audioCodec = codec(input, "a:0");
        if (videoCodec.isBlank()) {
            throw new MediaProcessingException("VIDEO_STREAM_MISSING", "The downloaded file does not contain a video stream.");
        }
        if (audioCodec.isBlank()) {
            throw new MediaProcessingException("AUDIO_TRACK_MISSING",
                    "YouTube returned a video-only stream. TubeForge stopped it instead of sending a silent video; try the link again.");
        }

        Path output = input.getParent().resolve("telegram-" + stripExtension(input.getFileName().toString()) + ".mp4");
        List<String> command = new ArrayList<>(List.of(
                properties.ffmpegPath(), "-y", "-i", input.toString(),
                "-map", "0:v:0", "-map", "0:a:0", "-map_metadata", "0"
        ));
        if ("h264".equals(videoCodec)) {
            command.addAll(List.of("-c:v", "copy"));
        } else {
            command.addAll(List.of("-c:v", "libx264", "-preset", "veryfast", "-crf", "23"));
        }
        if ("aac".equals(audioCodec)) {
            command.addAll(List.of("-c:a", "copy"));
        } else {
            command.addAll(List.of("-c:a", "aac", "-b:a", "160k"));
        }
        command.addAll(List.of("-movflags", "+faststart", "-avoid_negative_ts", "make_zero", output.toString()));
        ProcessResult result = runner.capture(command, input.getParent(), properties.processTimeout());
        if (!result.successful() || !Files.isRegularFile(output) || size(output) == 0) {
            throw new MediaProcessingException("VIDEO_NORMALIZATION_FAILED",
                    "The video was downloaded but could not be prepared for reliable Telegram playback.");
        }
        return output;
    }

    public Path compressVideo(Path input, long targetBytes) {
        double duration = durationSeconds(input);
        if (duration <= 0) throw new MediaProcessingException("DURATION_UNKNOWN", "This large video could not be compressed safely.");
        long totalKbps = Math.max(180, Math.round((targetBytes * 8.0 / duration) / 1000 * 0.92));
        long audioKbps = Math.min(96, Math.max(48, totalKbps / 8));
        long videoKbps = Math.max(120, totalKbps - audioKbps);
        Path output = input.getParent().resolve("compressed-" + stripExtension(input.getFileName().toString()) + ".mp4");
        List<String> command = List.of(properties.ffmpegPath(), "-y", "-i", input.toString(),
                "-map", "0:v:0", "-map", "0:a:0", "-c:v", "libx264", "-preset", "veryfast",
                "-b:v", videoKbps + "k", "-maxrate", Math.round(videoKbps * 1.15) + "k",
                "-bufsize", Math.round(videoKbps * 2.0) + "k", "-c:a", "aac", "-b:a", audioKbps + "k",
                "-movflags", "+faststart", output.toString());
        ProcessResult result = runner.capture(command, input.getParent(), properties.processTimeout());
        if (!result.successful() || !Files.isRegularFile(output)) {
            throw new MediaProcessingException("COMPRESSION_FAILED", "The video was too large and compression failed.");
        }
        return output;
    }

    public Path compressAudio(Path input, long targetBytes) {
        double duration = durationSeconds(input);
        long kbps = duration > 0 ? Math.max(48, Math.min(128, Math.round(targetBytes * 8.0 / duration / 1000 * 0.9))) : 96;
        Path output = input.getParent().resolve("compressed-" + stripExtension(input.getFileName().toString()) + ".mp3");
        List<String> command = List.of(properties.ffmpegPath(), "-y", "-i", input.toString(), "-vn",
                "-c:a", "libmp3lame", "-b:a", kbps + "k", output.toString());
        ProcessResult result = runner.capture(command, input.getParent(), properties.processTimeout());
        if (!result.successful() || !Files.isRegularFile(output)) {
            throw new MediaProcessingException("COMPRESSION_FAILED", "The audio was too large and compression failed.");
        }
        return output;
    }

    public List<Path> split(Path input, long maxBytes) {
        double duration = durationSeconds(input);
        long size = size(input);
        if (duration <= 0 || size <= 0) return List.of();
        long segmentSeconds = Math.max(10, (long) Math.floor(duration * maxBytes * 0.80 / size));
        String extension = extension(input);
        Path template = input.getParent().resolve("part-%03d." + extension);
        List<String> command = new ArrayList<>(List.of(properties.ffmpegPath(), "-y", "-i", input.toString(),
                "-map", "0", "-c", "copy", "-f", "segment", "-segment_time", Long.toString(segmentSeconds),
                "-reset_timestamps", "1", template.toString()));
        ProcessResult result = runner.capture(command, input.getParent(), properties.processTimeout());
        if (!result.successful()) return List.of();
        try (var stream = Files.list(input.getParent())) {
            return stream.filter(path -> path.getFileName().toString().matches("part-\\d{3}\\..+"))
                    .sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private long size(Path path) {
        try { return Files.size(path); } catch (IOException e) { return 0; }
    }

    private String codec(Path input, String stream) {
        List<String> command = List.of(properties.ffprobePath(), "-v", "error", "-select_streams", stream,
                "-show_entries", "stream=codec_name", "-of", "default=noprint_wrappers=1:nokey=1", input.toString());
        ProcessResult result = runner.capture(command, input.getParent(), Duration.ofSeconds(30));
        if (!result.successful()) return "";
        return result.output().lines().map(String::strip).filter(line -> !line.isBlank()).findFirst().orElse("")
                .toLowerCase(Locale.ROOT);
    }

    private String extension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "bin" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }
}
