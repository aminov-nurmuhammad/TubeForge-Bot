package uz.tubeforge.media;

import org.junit.jupiter.api.Test;
import uz.tubeforge.domain.SourceType;

import static org.assertj.core.api.Assertions.assertThat;

class YouTubeUrlParserTest {
    private final YouTubeUrlParser parser = new YouTubeUrlParser();

    @Test
    void recognizesNormalVideoInsideText() {
        var result = parser.find("Watch this: https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=12s please");
        assertThat(result).isPresent();
        assertThat(result.orElseThrow().sourceType()).isEqualTo(SourceType.VIDEO);
        assertThat(result.orElseThrow().normalizedUrl()).doesNotContain("please");
    }

    @Test
    void recognizesShortAndPlaylist() {
        assertThat(parser.parse("https://youtube.com/shorts/abc123").orElseThrow().sourceType())
                .isEqualTo(SourceType.SHORT);
        assertThat(parser.parse("https://youtube.com/playlist?list=PL123").orElseThrow().sourceType())
                .isEqualTo(SourceType.PLAYLIST);
    }

    @Test
    void acceptsShortHostButRejectsLookalikesAndCredentials() {
        var shortUrl = parser.parse("https://youtu.be/dQw4w9WgXcQ?si=tracking&t=42").orElseThrow();
        assertThat(shortUrl.normalizedUrl()).isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42");
        assertThat(parser.parse("https://youtube.com.evil.example/watch?v=x")).isEmpty();
        assertThat(parser.parse("https://user:pass@youtube.com/watch?v=x")).isEmpty();
        assertThat(parser.parse("file:///etc/passwd")).isEmpty();
    }

    @Test
    void recognizesLinksWithoutSchemeAndCanonicalizesEmbedLinks() {
        assertThat(parser.find("open youtu.be/dQw4w9WgXcQ now")).isPresent();
        assertThat(parser.parse("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ").orElseThrow().normalizedUrl())
                .isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        assertThat(parser.parse("https://youtube.com/watch?v=abc&list=PL123").orElseThrow().sourceType())
                .isEqualTo(SourceType.VIDEO);
    }
}
