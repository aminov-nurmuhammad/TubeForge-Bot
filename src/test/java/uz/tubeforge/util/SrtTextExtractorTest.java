package uz.tubeforge.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SrtTextExtractorTest {
    @Test
    void removesIndexesTimestampsMarkupAndDuplicateLines() {
        String srt = """
                1
                00:00:00,000 --> 00:00:01,000
                <i>Hello world</i>

                2
                00:00:01,000 --> 00:00:02,000
                Hello world
                This is TubeForge
                """;
        assertThat(SrtTextExtractor.extract(srt)).isEqualTo("Hello world\nHello world This is TubeForge");
    }
}
