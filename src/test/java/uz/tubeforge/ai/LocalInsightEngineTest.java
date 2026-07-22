package uz.tubeforge.ai;

import org.junit.jupiter.api.Test;
import uz.tubeforge.domain.Language;
import uz.tubeforge.domain.SourceType;
import uz.tubeforge.media.MediaInfo;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalInsightEngineTest {
    private final LocalInsightEngine engine = new LocalInsightEngine(new SrtTranscriptParser());
    private final MediaInfo info = new MediaInfo("video", "Fast systems", "TubeForge", 90,
            "", "", SourceType.VIDEO, 0, "", "", 0, List.of(), List.of());
    private final String srt = """
            1
            00:00:00,000 --> 00:00:10,000
            Caching avoids repeated network downloads and makes frequently requested media arrive almost instantly.

            2
            00:00:10,000 --> 00:00:20,000
            A Telegram file identifier lets the bot resend a server-side file without uploading its bytes again.

            3
            00:00:20,000 --> 00:00:30,000
            Single flight coordination ensures that many identical requests share one active processing operation.

            4
            00:00:30,000 --> 00:00:40,000
            Metadata caching also removes repeated YouTube inspection work for users who send the same link.

            5
            00:00:40,000 --> 00:00:50,000
            Reliable systems keep bounded queues, clear timeouts, useful metrics and safe fallbacks under pressure.

            6
            00:00:50,000 --> 00:01:00,000
            Local language processing can generate summaries and study notes without requiring a paid cloud API.
            """;

    @Test
    void createsFreeSummaryAndTimestampedChapters() {
        AiInsightResult summary = engine.generate(InsightType.SUMMARY, srt, info, Language.EN);
        AiInsightResult chapters = engine.generate(InsightType.CHAPTERS, srt, info, Language.EN);

        assertThat(summary.provider()).isEqualTo("local-smart");
        assertThat(summary.content()).contains("Smart summary", "Caching avoids repeated");
        assertThat(chapters.content()).contains("Chapters and key moments", "00:00", "00:20");
    }
}
