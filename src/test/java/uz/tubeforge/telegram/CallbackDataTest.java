package uz.tubeforge.telegram;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class CallbackDataTest {
    @Test
    void roundTripsCallbackData() {
        String id = UUID.randomUUID().toString();
        String encoded = CallbackData.of("dla", id, "mp3", 320);
        CallbackData parsed = CallbackData.parse(encoded);
        assertThat(parsed.action()).isEqualTo("dla");
        assertThat(parsed.arguments()).containsExactly(id, "mp3", "320");
    }

    @Test
    void enforcesTelegramLimit() {
        assertThatThrownBy(() -> CallbackData.of("x", "a".repeat(70)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
