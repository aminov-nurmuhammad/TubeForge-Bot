package uz.tubeforge.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uz.tubeforge.config.MediaProperties;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaFileToolsTest {
    @TempDir
    Path directory;

    @Test
    void skipsFfmpegForTelegramReadyMp4() throws Exception {
        Path input = Files.createFile(directory.resolve("ready.mp4"));
        ManagedProcessRunner runner = mock(ManagedProcessRunner.class);
        when(runner.capture(any(), any(), any())).thenReturn(new ProcessResult(0,
                "codec_name=h264|codec_type=video\ncodec_name=aac|codec_type=audio\n", false, false));

        Path result = new MediaFileTools(properties(), runner).prepareTelegramVideo(input);

        assertThat(result).isEqualTo(input);
        verify(runner, times(1)).capture(any(), any(), any());
    }

    @Test
    void refusesSilentVideoAfterSingleProbe() throws Exception {
        Path input = Files.createFile(directory.resolve("silent.mp4"));
        ManagedProcessRunner runner = mock(ManagedProcessRunner.class);
        when(runner.capture(any(), any(), any())).thenReturn(new ProcessResult(0,
                "codec_name=h264|codec_type=video\n", false, false));

        assertThatThrownBy(() -> new MediaFileTools(properties(), runner).prepareTelegramVideo(input))
                .isInstanceOf(MediaProcessingException.class)
                .hasMessageContaining("video-only");
        verify(runner, times(1)).capture(any(), any(), any());
    }

    private MediaProperties properties() {
        return new MediaProperties("yt-dlp", "ffmpeg", "ffprobe", null, "", directory,
                Duration.ofMinutes(1), Duration.ofSeconds(10), Duration.ofSeconds(5),
                10_800, 20, 2, 4, 500, 1_000, 4, 5,
                Duration.ofHours(24), Duration.ofSeconds(3));
    }
}
