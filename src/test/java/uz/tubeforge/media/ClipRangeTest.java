package uz.tubeforge.media;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ClipRangeTest {
    @Test
    void parsesCommonTimestampRanges() {
        ClipRange range = ClipRange.parse("01:20-03:45");
        assertThat(range.start().toSeconds()).isEqualTo(80);
        assertThat(range.end().toSeconds()).isEqualTo(225);
        assertThat(range.startFormatted()).isEqualTo("00:01:20");
    }

    @Test
    void rejectsReversedLongAndMalformedRanges() {
        assertThatThrownBy(() -> ClipRange.parse("03:00-01:00")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClipRange.parse("00:00-31:00")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClipRange.parse("1:99-2:00")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClipRange.parse("hello")).isInstanceOf(IllegalArgumentException.class);
    }
}
