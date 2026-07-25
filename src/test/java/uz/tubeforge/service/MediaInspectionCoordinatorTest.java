package uz.tubeforge.service;

import org.junit.jupiter.api.Test;
import uz.tubeforge.domain.SourceType;
import uz.tubeforge.media.MediaInspectionService;
import uz.tubeforge.media.MediaProcessingException;
import uz.tubeforge.media.ParsedInstagramUrl;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class MediaInspectionCoordinatorTest {

    @Test
    void reusesRecentPlatformFailureInsteadOfHammeringTheSameLink() {
        MediaInspectionService inspection = mock(MediaInspectionService.class);
        PerformanceMetrics metrics = mock(PerformanceMetrics.class);
        Clock clock = Clock.fixed(Instant.parse("2026-07-25T10:00:00Z"), ZoneOffset.UTC);
        MediaInspectionCoordinator coordinator = new MediaInspectionCoordinator(inspection, metrics, clock);
        ParsedInstagramUrl url = new ParsedInstagramUrl(
                "https://www.instagram.com/reel/ABC123/", SourceType.INSTAGRAM_REEL, "ABC123");
        when(inspection.inspect(url)).thenThrow(new MediaProcessingException(
                "INSTAGRAM_RATE_LIMITED", "Instagram temporarily rate-limited this server."));

        assertThatThrownBy(() -> coordinator.inspect(url))
                .isInstanceOf(MediaProcessingException.class)
                .hasMessageContaining("rate-limited");
        assertThatThrownBy(() -> coordinator.inspect(url))
                .isInstanceOf(MediaProcessingException.class)
                .hasMessageContaining("rate-limited");

        verify(inspection, times(1)).inspect(url);
        verify(metrics).inspectionCooldownHit();
    }
}
