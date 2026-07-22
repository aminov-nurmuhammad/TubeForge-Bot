package uz.tubeforge.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uz.tubeforge.config.TelegramProperties;
import uz.tubeforge.domain.AppUser;
import uz.tubeforge.domain.JobType;
import uz.tubeforge.domain.Language;
import uz.tubeforge.domain.SourceType;
import uz.tubeforge.media.MediaFileTools;
import uz.tubeforge.media.MediaInfo;
import uz.tubeforge.telegram.TelegramApiClient;
import uz.tubeforge.telegram.TelegramApiException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MediaDeliveryServiceTest {
    @TempDir
    Path directory;

    private final TelegramApiClient telegram = mock(TelegramApiClient.class);
    private final MediaFileTools fileTools = mock(MediaFileTools.class);
    private final MediaDeliveryService service = new MediaDeliveryService(
            new TelegramProperties("test", "https://api.telegram.org", false, 1, 50_000_000),
            telegram, fileTools);
    private final AppUser user = new AppUser(1, "demo", "Demo", null, Language.EN, Instant.now());
    private final MediaInfo info = new MediaInfo("id", "Title", "Channel", 60, "", "",
            SourceType.VIDEO, 0, "", "", 0, List.of(), List.of());

    @Test
    void sendsUnsupportedTelegramAudioContainersAsDocuments() throws Exception {
        Path wav = Files.write(directory.resolve("track.wav"), new byte[] {1, 2, 3});

        service.deliver(10, wav, JobType.AUDIO, info, user);

        verify(telegram).sendDocument(eq(10L), eq(wav), anyString());
        verify(telegram, never()).sendAudio(anyLong(), any(Path.class), anyString(), anyString(), anyString());
    }

    @Test
    void normalizesPlayableVideosBeforeUploading() throws Exception {
        Path input = Files.write(directory.resolve("video.webm"), new byte[] {1});
        Path normalized = Files.write(directory.resolve("telegram-video.mp4"), new byte[] {1, 2});
        when(fileTools.prepareTelegramVideo(input)).thenReturn(normalized);

        service.deliver(10, input, JobType.VIDEO, info, user);

        verify(telegram).sendVideo(10, normalized, "✅ <b>TubeForge</b>\nTitle", true);
    }

    @Test
    void fallsBackToDocumentWhenTelegramRejectsAPhoto() throws Exception {
        Path photo = Files.write(directory.resolve("thumb.jpg"), new byte[] {1, 2, 3});
        when(telegram.sendPhoto(anyLong(), any(Path.class), anyString()))
                .thenThrow(new TelegramApiException(400, "wrong file identifier"));

        service.deliver(10, photo, JobType.THUMBNAIL, info, user);

        verify(telegram).sendDocument(eq(10L), eq(photo), anyString());
    }
}
