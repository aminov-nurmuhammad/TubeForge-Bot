package uz.tubeforge.service;

import org.junit.jupiter.api.Test;
import uz.tubeforge.config.AccessProperties;
import uz.tubeforge.repository.DownloadJobRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AccessServiceTest {

    @Test
    void limitsLinkFloodsButNeverRestrictsTheConfiguredOwner() {
        PerformanceMetrics metrics = mock(PerformanceMetrics.class);
        AccessService access = new AccessService(new AccessProperties(AccessProperties.AccessMode.PUBLIC,
                Set.of(99L), Set.of(), 20, true, 2), mock(DownloadJobRepository.class),
                Clock.fixed(Instant.parse("2026-07-25T10:00:00Z"), ZoneOffset.UTC), metrics);

        assertThat(access.canInspectLink(1)).isTrue();
        assertThat(access.canInspectLink(1)).isTrue();
        assertThat(access.canInspectLink(1)).isFalse();
        assertThat(access.canInspectLink(99)).isTrue();
        assertThat(access.canInspectLink(99)).isTrue();
        assertThat(access.canInspectLink(99)).isTrue();
    }
}
