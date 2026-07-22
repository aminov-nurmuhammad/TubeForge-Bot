package uz.tubeforge.media;

import org.junit.jupiter.api.Test;
import uz.tubeforge.config.MediaProperties;
import uz.tubeforge.domain.JobType;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class YtDlpCommandFactoryTest {
    private final YtDlpCommandFactory factory = new YtDlpCommandFactory(new MediaProperties(
            "yt-dlp", "ffmpeg", "ffprobe", null, "", Path.of("storage"), Duration.ofHours(2),
            Duration.ofSeconds(90), Duration.ofSeconds(30), 10_800, 20, 2, 4, 100, 200, 4, 5,
            Duration.ofHours(24), Duration.ofSeconds(3)));

    @Test
    void buildsArgumentsWithoutShellInterpolation() {
        String url = "https://youtube.com/watch?v=abc&x=$(danger)";
        var command = factory.download(JobType.VIDEO, "720", url, Path.of("/tmp/job"), null, 20);
        assertThat(command.get(0)).isEqualTo("yt-dlp");
        assertThat(command).filteredOn(url::equals).hasSize(1);
        assertThat(command).contains("--ignore-config", "--ffmpeg-location", "ffmpeg", "--no-playlist",
                "--merge-output-format", "--concurrent-fragments", "4");
        assertThat(String.join(" ", command)).contains("b[height=720][ext=mp4]")
                .contains("height<=720").contains("acodec!=none");
    }

    @Test
    void addsClipRangeAndAudioPostprocessing() {
        var command = factory.download(JobType.CLIP_AUDIO, "mp3:192", "https://youtu.be/abc",
                Path.of("/tmp/job"), ClipRange.parse("1:00-1:10"), 20);
        assertThat(command).contains("--download-sections", "*00:01:00-00:01:10", "--audio-format", "mp3");
        assertThat(command).contains("--embed-metadata").doesNotContain("--embed-thumbnail");
    }
}
