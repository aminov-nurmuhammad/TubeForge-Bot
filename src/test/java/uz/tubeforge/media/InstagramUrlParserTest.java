package uz.tubeforge.media;

import org.junit.jupiter.api.Test;
import uz.tubeforge.domain.SourceType;

import static org.assertj.core.api.Assertions.assertThat;

class InstagramUrlParserTest {
    private final InstagramUrlParser parser = new InstagramUrlParser();

    @Test
    void normalizesReelAndReelsLinks() {
        var reel = parser.parse("https://www.instagram.com/reel/ABC_123/?igsh=tracking").orElseThrow();
        assertThat(reel.sourceType()).isEqualTo(SourceType.INSTAGRAM_REEL);
        assertThat(reel.reelId()).isEqualTo("ABC_123");
        assertThat(reel.normalizedUrl()).isEqualTo("https://www.instagram.com/reel/ABC_123/");
        assertThat(parser.parse("instagram.com/reels/xyz-987").orElseThrow().reelId()).isEqualTo("xyz-987");
    }

    @Test
    void findsReelInsideTextAndRejectsLookalikes() {
        assertThat(parser.find("Here: https://m.instagram.com/reel/abc123/.")).isPresent();
        assertThat(parser.parse("https://instagram.com.evil.example/reel/abc123")).isEmpty();
        assertThat(parser.parse("https://user:pass@instagram.com/reel/abc123")).isEmpty();
        assertThat(parser.parse("https://www.instagram.com/p/abc123")).isEmpty();
    }
}
