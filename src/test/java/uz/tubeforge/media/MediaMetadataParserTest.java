package uz.tubeforge.media;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import uz.tubeforge.domain.SourceType;

import static org.assertj.core.api.Assertions.assertThat;

class MediaMetadataParserTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final MediaMetadataParser parser = new MediaMetadataParser();

    @Test
    void extractsFormatsAndPrefersOfficialSubtitles() throws Exception {
        var json = mapper.readTree("""
                {
                  "id":"abc", "title":"Demo", "channel":"Channel", "duration":90,
                  "thumbnail":"https://example.test/x.jpg", "webpage_url":"https://youtube.com/watch?v=abc",
                  "view_count":1234,
                  "formats":[
                    {"format_id":"18","height":720,"vcodec":"avc1","acodec":"mp4a.40.2","filesize":1000,"fps":30,"ext":"mp4"},
                    {"format_id":"399","height":1080,"vcodec":"av01","acodec":"none","filesize_approx":2000,"fps":60,"ext":"webm"},
                    {"acodec":"opus","vcodec":"none","ext":"webm"}
                  ],
                  "automatic_captions":{"en":[{}],"ru":[{}]},
                  "subtitles":{"en":[{}]}
                }
                """);
        MediaInfo info = parser.parse(json, SourceType.VIDEO);
        assertThat(info.title()).isEqualTo("Demo");
        assertThat(info.videoFormats()).extracting(VideoFormatOption::height).containsExactly(1080, 720);
        assertThat(info.videoFormats().get(0).selector()).isEqualTo("format:399:video");
        assertThat(info.videoFormats().get(0).combined()).isFalse();
        assertThat(info.videoFormats().get(1).selector()).isEqualTo("format:18:combined");
        assertThat(info.videoFormats().get(1).combined()).isTrue();
        assertThat(info.subtitles()).hasSize(2);
        assertThat(info.subtitles().stream().filter(s -> s.code().equals("en")).findFirst().orElseThrow().automatic()).isFalse();
    }
}
