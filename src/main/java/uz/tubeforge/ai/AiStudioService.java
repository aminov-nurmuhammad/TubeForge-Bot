package uz.tubeforge.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import uz.tubeforge.config.AiProperties;
import uz.tubeforge.domain.Language;
import uz.tubeforge.media.MediaInfo;
import uz.tubeforge.service.PerformanceMetrics;

import java.util.List;
import java.util.Map;

@Service
public class AiStudioService {
    private static final Logger log = LoggerFactory.getLogger(AiStudioService.class);

    private final AiProperties properties;
    private final LocalInsightEngine local;
    private final PerformanceMetrics metrics;
    private final WebClient ollama;

    public AiStudioService(AiProperties properties, LocalInsightEngine local,
                           PerformanceMetrics metrics, WebClient.Builder builder) {
        this.properties = properties;
        this.local = local;
        this.metrics = metrics;
        this.ollama = builder.baseUrl(properties.baseUrl()).build();
    }

    public AiInsightResult generate(InsightType type, String srt, MediaInfo info, Language language) {
        if (properties.ollama()) {
            try {
                AiInsightResult result = ollama(type, srt, info, language);
                metrics.aiOllama();
                return result;
            } catch (RuntimeException e) {
                log.warn("Ollama AI failed; using the built-in local insight engine: {}", e.getMessage());
            }
        }
        metrics.aiLocal();
        return local.generate(type, srt, info, language);
    }

    public String configuredProvider() {
        return properties.ollama() ? "ollama:" + properties.model() : "local-smart";
    }

    private AiInsightResult ollama(InsightType type, String srt, MediaInfo info, Language language) {
        String transcript = srt.length() <= properties.maxTranscriptCharacters()
                ? srt : srt.substring(0, properties.maxTranscriptCharacters());
        String task = switch (type) {
            case SUMMARY -> "Create a concise but complete summary and 7 key takeaways.";
            case CHAPTERS -> "Create 6-10 timestamped chapters and identify the most important moments.";
            case STUDY_NOTES -> "Create structured study notes with key ideas, definitions, action items, and keywords.";
        };
        String languageName = switch (language) {
            case RU -> "Russian";
            case UZ -> "Uzbek";
            default -> "English";
        };
        String prompt = "You are TubeForge Transcript Studio. " + task + " Answer in " + languageName
                + ". Use plain text, short sections and bullets. Never invent facts not present in the transcript."
                + "\n\nVideo title: " + info.title() + "\nChannel: " + info.channel()
                + "\n\nSubtitle transcript:\n" + transcript;
        Map<String, Object> body = Map.of(
                "model", properties.model(),
                "stream", false,
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "options", Map.of("temperature", 0.2)
        );
        JsonNode response = ollama.post().uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(properties.timeout())
                .block();
        String content = response == null ? "" : response.path("message").path("content").asText("").strip();
        if (content.isBlank()) throw new IllegalStateException("Ollama returned an empty response");
        return new AiInsightResult(content, "ollama:" + properties.model());
    }
}
