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
import uz.tubeforge.telegram.model.InlineKeyboard;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
            new TelegramProperties("test", "https://api.telegram.org", false, 1, 50_000_000,
                    2, 50, 0, Duration.ofMillis(10)),
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
    void validatesVideoEvenWhenUserChoosesDocumentDelivery() throws Exception {
        Path input = Files.write(directory.resolve("document-video.webm"), new byte[] {1});
        Path normalized = Files.write(directory.resolve("document-video.mp4"), new byte[] {1, 2});
        user.setSendAsDocument(true);
        when(fileTools.prepareTelegramVideo(input)).thenReturn(normalized);

        service.deliver(10, input, JobType.VIDEO, info, user);

        verify(fileTools).prepareTelegramVideo(input);
        verify(telegram).sendDocument(10, normalized, "✅ <b>TubeForge</b>\nTitle");
    }

    @Test
    void fallsBackToDocumentWhenTelegramRejectsAPhoto() throws Exception {
        Path photo = Files.write(directory.resolve("thumb.jpg"), new byte[] {1, 2, 3});
        when(telegram.sendPhoto(anyLong(), any(Path.class), anyString()))
                .thenThrow(new TelegramApiException(400, "wrong file identifier"));

        service.deliver(10, photo, JobType.THUMBNAIL, info, user);

        verify(telegram).sendDocument(eq(10L), eq(photo), anyString());
    }

    @Test
    void alwaysDeliversInstagramReelsAsInlineVideoWithActions() throws Exception {
        Path input = Files.write(directory.resolve("reel.mp4"), new byte[] {1});
        when(fileTools.prepareTelegramVideo(input)).thenReturn(input);
        user.setSendAsDocument(true);
        MediaInfo reel = new MediaInfo("ABC", "Instagram Reel", "Instagram", 20, "", "",
                SourceType.INSTAGRAM_REEL, 0, "", "", 0, List.of(), List.of());
        InlineKeyboard keyboard = InlineKeyboard.of(List.of());

        service.deliver(10, input, JobType.VIDEO, reel, user, keyboard);

        verify(telegram).sendVideo(10, input, "📸 <b>Instagram Reel</b>", true, keyboard);
        verify(telegram, never()).sendDocument(anyLong(), any(Path.class), anyString(), any(InlineKeyboard.class));
    }

    @Test
    void oversizedReelKeepsSingleVideoContractEvenWhenYouTubeCompressionIsDisabled() throws Exception {
        Path input = Files.write(directory.resolve("large-reel.mp4"), new byte[1_000_001]);
        Path compressed = Files.write(directory.resolve("compressed-reel.mp4"), new byte[] {1, 2, 3});
        user.setAutoCompress(false);
        when(fileTools.prepareTelegramVideo(input)).thenReturn(input);
        when(fileTools.compressVideo(input, 920_000)).thenReturn(compressed);
        MediaInfo reel = new MediaInfo("ABC", "Instagram Reel", "Instagram", 20, "", "",
                SourceType.INSTAGRAM_REEL, 0, "", "", 0, List.of(), List.of());
        MediaDeliveryService limited = new MediaDeliveryService(
                new TelegramProperties("test", "https://api.telegram.org", false, 1, 1_000_000,
                        2, 50, 0, Duration.ofMillis(10)), telegram, fileTools);

        limited.deliver(10, input, JobType.VIDEO, reel, user);

        verify(fileTools).compressVideo(input, 920_000);
        verify(telegram).sendVideo(10, compressed, "📸 <b>Instagram Reel</b>", true);
    }
}
