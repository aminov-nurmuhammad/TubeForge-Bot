package uz.tubeforge.service;

import org.junit.jupiter.api.Test;
import uz.tubeforge.config.CacheProperties;
import uz.tubeforge.domain.AppUser;
import uz.tubeforge.domain.JobType;
import uz.tubeforge.domain.Language;
import uz.tubeforge.domain.MediaRequest;
import uz.tubeforge.domain.SourceType;
import uz.tubeforge.repository.MediaArtifactRepository;
import uz.tubeforge.telegram.TelegramApiClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ArtifactCacheServiceTest {

    @Test
    void platformIsPartOfTheArtifactIdentity() {
        ArtifactCacheService cache = new ArtifactCacheService(mock(MediaArtifactRepository.class),
                mock(TelegramApiClient.class), new CacheProperties(true, Duration.ofDays(30), Duration.ofDays(7)),
                mock(PerformanceMetrics.class),
                Clock.fixed(Instant.parse("2026-07-25T10:00:00Z"), ZoneOffset.UTC));
        Instant now = Instant.parse("2026-07-25T10:00:00Z");
        MediaRequest youtube = MediaRequest.instant(1, 1, "https://youtu.be/same", SourceType.VIDEO,
                "{}", "same", "Title", "Channel", 10L, "", now, now.plusSeconds(60));
        MediaRequest instagram = MediaRequest.instant(1, 1, "https://www.instagram.com/reel/same/",
                SourceType.INSTAGRAM_REEL, "{}", "same", "Title", "Channel", 10L, "", now,
                now.plusSeconds(60));
        AppUser user = new AppUser(1, "demo", "Demo", null, Language.EN, now);

        assertThat(cache.key(youtube, JobType.VIDEO, "best", user))
                .isNotEqualTo(cache.key(instagram, JobType.VIDEO, "best", user));
    }

    @Test
    void reelVideoCacheIgnoresYouTubeDeliveryPreferencesBecauseReelsAreAlwaysInline() {
        ArtifactCacheService cache = new ArtifactCacheService(mock(MediaArtifactRepository.class),
                mock(TelegramApiClient.class), new CacheProperties(true, Duration.ofDays(30), Duration.ofDays(7)),
                mock(PerformanceMetrics.class),
                Clock.fixed(Instant.parse("2026-07-25T10:00:00Z"), ZoneOffset.UTC));
        Instant now = Instant.parse("2026-07-25T10:00:00Z");
        MediaRequest reel = MediaRequest.instant(1, 1, "https://www.instagram.com/reel/ABC123/",
                SourceType.INSTAGRAM_REEL, "{}", "ABC123", "Instagram Reel", "Instagram", 0L, "", now,
                now.plusSeconds(60));
        AppUser inline = new AppUser(1, "one", "One", null, Language.EN, now);
        AppUser document = new AppUser(2, "two", "Two", null, Language.EN, now);
        document.setSendAsDocument(true);
        document.setAutoCompress(false);

        assertThat(cache.key(reel, JobType.VIDEO, "best", inline))
                .isEqualTo(cache.key(reel, JobType.VIDEO, "best", document));
    }
}
