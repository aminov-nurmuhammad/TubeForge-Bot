package uz.tubeforge.media;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import uz.tubeforge.config.MediaProperties;
import uz.tubeforge.domain.SourceType;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MediaInspectionServiceTest {

    @Test
    void classifiesInstagramRateLimitBeforeGenericYouTubeRules() {
        ManagedProcessRunner runner = mock(ManagedProcessRunner.class);
        when(runner.capture(any(), any(), any())).thenReturn(new ProcessResult(1,
                "ERROR: HTTP Error 429: Too Many Requests", false, false));
        YtDlpCommandFactory commands = mock(YtDlpCommandFactory.class);
        when(commands.inspect(any())).thenReturn(List.of("yt-dlp"));
        MediaInspectionService service = new MediaInspectionService(commands, runner,
                mock(MediaMetadataParser.class), properties(), new ObjectMapper());

        assertThatThrownBy(() -> service.inspect(new ParsedInstagramUrl(
                "https://www.instagram.com/reel/ABC123/", SourceType.INSTAGRAM_REEL, "ABC123")))
                .isInstanceOfSatisfying(MediaProcessingException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.getCode())
                                .isEqualTo("INSTAGRAM_RATE_LIMITED"));
    }

    private MediaProperties properties() {
        return new MediaProperties("yt-dlp", "ffmpeg", "ffprobe", null, "", Path.of("storage"),
                Duration.ofMinutes(1), Duration.ofSeconds(10), Duration.ofSeconds(5),
                10_800, 20, 2, 4, 100, 200, 4, 5,
                Duration.ofHours(24), Duration.ofSeconds(3));
    }
}
