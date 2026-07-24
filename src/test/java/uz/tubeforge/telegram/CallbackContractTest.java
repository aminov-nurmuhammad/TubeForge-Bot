package uz.tubeforge.telegram;

import org.junit.jupiter.api.Test;
import uz.tubeforge.config.FeatureProperties;
import uz.tubeforge.domain.AppUser;
import uz.tubeforge.domain.Language;
import uz.tubeforge.domain.MetadataState;
import uz.tubeforge.domain.SourceType;
import uz.tubeforge.media.MediaInfo;
import uz.tubeforge.media.SubtitleOption;
import uz.tubeforge.media.VideoFormatOption;
import uz.tubeforge.telegram.model.InlineKeyboard;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CallbackContractTest {
    private final KeyboardFactory keyboards = new KeyboardFactory(
            new FeatureProperties(true, true, true, true, true, true, true, true));

    @Test
    void everyGeneratedCallbackHasARouterAction() {
        String id = UUID.randomUUID().toString();
        AppUser user = new AppUser(1, "demo", "Demo", null, Language.EN, Instant.now());
        List<VideoFormatOption> formats = List.of(
                new VideoFormatOption(2160, 1, 60, "mp4"),
                new VideoFormatOption(1080, 1, 30, "mp4"),
                new VideoFormatOption(720, 1, 30, "mp4"),
                new VideoFormatOption(480, 1, 30, "mp4"),
                new VideoFormatOption(360, 1, 30, "mp4"));
        List<SubtitleOption> subtitles = List.of(new SubtitleOption("en", "English", false));
        MediaInfo video = new MediaInfo("video", "Title", "Channel", 60, "https://i.ytimg.com/x.jpg",
                "https://www.youtube.com/watch?v=video", SourceType.VIDEO, 1, "", "", 0, formats, subtitles);
        MediaInfo playlist = new MediaInfo("list", "Playlist", "Channel", 0, "",
                "https://www.youtube.com/playlist?list=list", SourceType.PLAYLIST, 0, "", "", 2,
                List.of(), List.of());

        List<InlineKeyboard> all = new ArrayList<>(List.of(
                keyboards.acceptTerms(),
                keyboards.preview(id, video, user),
                keyboards.preview(id, video, user, MetadataState.PENDING),
                keyboards.preview(id, video, user, MetadataState.DEGRADED),
                keyboards.preview(id, playlist, user),
                keyboards.videoFormats(id, video),
                keyboards.videoFormats(id, video, true),
                keyboards.audioFormats(id),
                keyboards.allAudioFormats(id),
                keyboards.toolsMenu(id),
                keyboards.toolsMenu(id, video),
                keyboards.aiStudio(id),
                keyboards.audioQualities(id, "mp3"),
                keyboards.thumbnailMenu(id),
                keyboards.clipMenu(id),
                keyboards.cancelJob(id),
                keyboards.settings(user),
                keyboards.languageMenu(),
                keyboards.videoQualitySettings(),
                keyboards.audioFormatSettings(),
                keyboards.back(id),
                keyboards.metadataStatus(id, MetadataState.PENDING),
                keyboards.metadataStatus(id, MetadataState.DEGRADED),
                keyboards.admin()
        ));
        for (String action : List.of("dls", "dtr", "ais", "aic", "ain")) {
            all.add(keyboards.subtitleMenu(id, subtitles, action));
        }

        assertThat(all.stream().flatMap(keyboard -> keyboard.rows().stream())
                .flatMap(List::stream)
                .filter(button -> button.callbackData() != null)
                .map(button -> CallbackData.parse(button.callbackData()).action())
                .filter(action -> !TelegramUpdateRouter.supportsCallbackAction(action)))
                .isEmpty();
        assertThat(TelegramUpdateRouter.supportsCallbackAction("open")).isTrue();
    }
}
