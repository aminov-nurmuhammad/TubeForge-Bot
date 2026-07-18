package uz.tubeforge.telegram;

import org.junit.jupiter.api.Test;
import uz.tubeforge.config.FeatureProperties;
import uz.tubeforge.media.SubtitleOption;

import java.nio.charset.StandardCharsets;
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
}
