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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KeyboardFactoryTest {
    private final KeyboardFactory keyboards = new KeyboardFactory(
            new FeatureProperties(true, true, true, true, true, true, true, true));

    @Test
    void subtitleButtonsUseCompactIndexes() {
        String requestId = UUID.randomUUID().toString();
        var keyboard = keyboards.subtitleMenu(requestId,
                List.of(new SubtitleOption("a-very-long-language-variant-code", "Test language", true)), "dls");
        String data = keyboard.rows().get(0).get(0).callbackData();
        assertThat(data).isEqualTo("dls:" + requestId + ":0");
        assertThat(data.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(64);
    }

    @Test
    void formatPickerShowsBestAvailableQualityAndRealSourceFormats() {
        String requestId = UUID.randomUUID().toString();
        AppUser user = new AppUser(1, "demo", "Demo", null, Language.EN, Instant.now());
        MediaInfo info = new MediaInfo("id", "Title", "Channel", 60, "", "https://youtu.be/id",
                SourceType.VIDEO, 0, "", "", 0,
                List.of(new VideoFormatOption(2160, 1, 30, "mp4", "format:401:video", false), new VideoFormatOption(1080, 1, 30, "mp4"),
                        new VideoFormatOption(720, 1, 30, "mp4"), new VideoFormatOption(480, 1, 30, "mp4"),
                        new VideoFormatOption(360, 1, 30, "mp4")), List.of());

        var preview = keyboards.preview(requestId, info, user);
        var compactFormats = keyboards.videoFormats(requestId, info);

        assertThat(preview.rows().get(0).get(0).text()).contains("720p video");
        assertThat(preview.rows().get(0).get(1).text()).contains("MP3 audio");
        var labels = compactFormats.rows().stream().flatMap(List::stream).map(button -> button.text()).toList();
        assertThat(labels).contains("⭐ Best available · 2160p")
                .anyMatch(value -> value.startsWith("2160p"))
                .doesNotContain("🎛 All available formats");
        String exactCallback = compactFormats.rows().stream().flatMap(List::stream)
                .filter(button -> button.text().startsWith("2160p"))
                .findFirst().orElseThrow().callbackData();
        assertThat(CallbackData.parse(exactCallback).arguments()).containsExactly(requestId, "format~401~video");
    }

    @Test
    void instantPreviewExposesFastActionsButNotUnreadyTools() {
        String requestId = UUID.randomUUID().toString();
        AppUser user = new AppUser(1, "demo", "Demo", null, Language.EN, Instant.now());
        MediaInfo info = MediaInfo.provisional(
                new uz.tubeforge.media.ParsedYouTubeUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ", SourceType.VIDEO));

        var labels = keyboards.preview(requestId, info, user, MetadataState.PENDING).rows().stream()
                .flatMap(List::stream).map(button -> button.text()).toList();

        assertThat(labels).anyMatch(value -> value.contains("video"));
        assertThat(labels).anyMatch(value -> value.contains("audio"));
        assertThat(labels).contains("⏳ Details loading");
        assertThat(labels).noneMatch(value -> value.contains("Transcript Studio"));
        assertThat(labels).noneMatch(value -> value.contains("Video options"));
    }
}
