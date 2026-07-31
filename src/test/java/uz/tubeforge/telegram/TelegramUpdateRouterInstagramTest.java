package uz.tubeforge.telegram;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import uz.tubeforge.config.AiProperties;
import uz.tubeforge.config.FeatureProperties;
import uz.tubeforge.domain.AppUser;
import uz.tubeforge.domain.JobType;
import uz.tubeforge.domain.Language;
import uz.tubeforge.domain.MediaRequest;
import uz.tubeforge.domain.SourceType;
import uz.tubeforge.health.MediaToolsHealthIndicator;
import uz.tubeforge.media.MediaUrlParser;
import uz.tubeforge.media.ParsedInstagramUrl;
import uz.tubeforge.repository.AppUserRepository;
import uz.tubeforge.repository.DownloadJobRepository;
import uz.tubeforge.service.*;
import uz.tubeforge.telegram.model.TgChat;
import uz.tubeforge.telegram.model.TgMessage;
import uz.tubeforge.telegram.model.TgUpdate;
import uz.tubeforge.telegram.model.TgUser;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TelegramUpdateRouterInstagramTest {

    @Test
    void reelLinkQueuesBestVideoWithoutSendingAPreviewCardOrInspectingMetadata() {
        TelegramApiClient telegram = mock(TelegramApiClient.class);
        UserService users = mock(UserService.class);
        AccessService access = mock(AccessService.class);
        MediaRequestService requests = mock(MediaRequestService.class);
        MediaInspectionCoordinator inspection = mock(MediaInspectionCoordinator.class);
        MediaJobService jobs = mock(MediaJobService.class);
        MediaUrlParser parser = mock(MediaUrlParser.class);
        AppUser user = new AppUser(7, "demo", "Demo", null, Language.EN, Instant.now());
        ParsedInstagramUrl url = new ParsedInstagramUrl(
                "https://www.instagram.com/reel/ABC123/", SourceType.INSTAGRAM_REEL, "ABC123");
        MediaRequest request = MediaRequest.instant(7, 70, url.normalizedUrl(), SourceType.INSTAGRAM_REEL,
                "{}", "ABC123", "Instagram Reel", "Instagram", 0L, "", Instant.now(),
                Instant.now().plusSeconds(3600));
        when(users.getOrCreate(any())).thenReturn(user);
        when(access.isAllowed(7)).thenReturn(true);
        when(access.hasAcceptedTerms(user)).thenReturn(true);
        when(access.canInspectLink(7)).thenReturn(true);
        when(parser.find(anyString())).thenReturn(Optional.of(url));
        when(requests.createInstant(7, 70, url)).thenReturn(request);

        TelegramUpdateRouter router = new TelegramUpdateRouter(telegram, users, access,
                mock(SessionService.class), requests, inspection, jobs, parser, mock(BotMessages.class),
                mock(KeyboardFactory.class), new FeatureProperties(true, true, true, true, true, true, true, true, true),
                mock(AppUserRepository.class), mock(DownloadJobRepository.class), mock(ArtifactCacheService.class),
                mock(AiInsightCacheService.class), mock(PerformanceMetrics.class), mock(AiProperties.class),
                mock(MediaToolsHealthIndicator.class), mock(TaskExecutor.class));
        TgUser sender = new TgUser(7, false, "Demo", null, "demo", "en");
        TgMessage message = new TgMessage(1, sender, new TgChat(70, "private", null, null), 0,
                url.normalizedUrl(), null, null, null, null, null);

        router.handle(new TgUpdate(1, message, null));

        verify(requests).createInstant(7, 70, url);
        verify(jobs).queue(7, request.getId(), JobType.VIDEO, "best", null);
        verifyNoInteractions(inspection);
        verify(telegram, never()).sendPhotoUrl(anyLong(), anyString(), anyString(), any());
        verify(telegram, never()).sendMessage(anyLong(), anyString(), any());
    }
}
