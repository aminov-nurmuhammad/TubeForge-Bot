package uz.tubeforge.telegram;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import uz.tubeforge.config.TelegramProperties;
import uz.tubeforge.telegram.model.InlineKeyboard;
import uz.tubeforge.telegram.model.TgMessage;
import uz.tubeforge.telegram.model.TgUpdate;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class TelegramApiClient {
    private static final TypeReference<List<TgUpdate>> UPDATE_LIST = new TypeReference<>() {};

    private final TelegramProperties properties;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public TelegramApiClient(TelegramProperties properties, ObjectMapper objectMapper, WebClient.Builder builder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webClient = builder.baseUrl(properties.botApiUrl()).build();
    }

    public List<TgUpdate> getUpdates(long offset) {
        JsonNode result = postJson("getUpdates", Map.of(
                "offset", offset,
                "timeout", properties.pollingTimeoutSeconds(),
                "allowed_updates", List.of("message", "callback_query")
        ), Duration.ofSeconds(properties.pollingTimeoutSeconds() + 10L));
        return objectMapper.convertValue(result, UPDATE_LIST);
    }

    public TgMessage sendMessage(long chatId, String text, InlineKeyboard keyboard) {
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("chat_id", chatId);
        body.put("text", text);
        body.put("parse_mode", "HTML");
        body.put("disable_web_page_preview", true);
        if (keyboard != null) body.put("reply_markup", keyboard);
        return objectMapper.convertValue(postJson("sendMessage", body, Duration.ofSeconds(30)), TgMessage.class);
    }

    public void editMessage(long chatId, long messageId, String text, InlineKeyboard keyboard) {
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("text", text);
        body.put("parse_mode", "HTML");
        body.put("disable_web_page_preview", true);
        if (keyboard != null) body.put("reply_markup", keyboard);
        postJson("editMessageText", body, Duration.ofSeconds(30));
    }

    public void answerCallback(String callbackId, String text, boolean alert) {
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("callback_query_id", callbackId);
        body.put("show_alert", alert);
        if (text != null && !text.isBlank()) body.put("text", text);
        postJson("answerCallbackQuery", body, Duration.ofSeconds(20));
    }

    public void deleteMessage(long chatId, long messageId) {
        postJson("deleteMessage", Map.of("chat_id", chatId, "message_id", messageId), Duration.ofSeconds(20));
    }

    public void sendChatAction(long chatId, String action) {
        postJson("sendChatAction", Map.of("chat_id", chatId, "action", action), Duration.ofSeconds(20));
    }

    public TgMessage sendPhotoUrl(long chatId, String photoUrl, String caption, InlineKeyboard keyboard) {
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("chat_id", chatId);
        body.put("photo", photoUrl);
        body.put("caption", caption);
        body.put("parse_mode", "HTML");
        if (keyboard != null) body.put("reply_markup", keyboard);
        return objectMapper.convertValue(postJson("sendPhoto", body, Duration.ofSeconds(60)), TgMessage.class);
    }

    public TgMessage sendVideo(long chatId, Path file, String caption, boolean supportsStreaming) {
        return upload("sendVideo", "video", chatId, file, caption,
                Map.of("supports_streaming", Boolean.toString(supportsStreaming)));
    }

    public TgMessage sendPhoto(long chatId, Path file, String caption) {
        return upload("sendPhoto", "photo", chatId, file, caption, Map.of());
    }

    public TgMessage sendAudio(long chatId, Path file, String caption, String title, String performer) {
        return upload("sendAudio", "audio", chatId, file, caption,
                Map.of("title", safe(title), "performer", safe(performer)));
    }

    public TgMessage sendDocument(long chatId, Path file, String caption) {
        return upload("sendDocument", "document", chatId, file, caption, Map.of());
    }

    public void setCommands() {
        List<Map<String, String>> commands = List.of(
                Map.of("command", "start", "description", "Open TubeForge"),
                Map.of("command", "help", "description", "How to use the bot"),
                Map.of("command", "history", "description", "Recent media"),
                Map.of("command", "jobs", "description", "Current and recent jobs"),
                Map.of("command", "settings", "description", "Preferences"),
                Map.of("command", "terms", "description", "Terms of use"),
                Map.of("command", "privacy", "description", "Privacy information"),
                Map.of("command", "id", "description", "Show your Telegram ID")
        );
        postJson("setMyCommands", Map.of("commands", commands), Duration.ofSeconds(20));
    }

    private TgMessage upload(String method, String field, long chatId, Path file, String caption,
                             Map<String, String> extras) {
        MultipartBodyBuilder multipart = new MultipartBodyBuilder();
        multipart.part("chat_id", Long.toString(chatId));
        multipart.part(field, new FileSystemResource(file));
        multipart.part("caption", caption == null ? "" : caption);
        multipart.part("parse_mode", "HTML");
        extras.forEach((key, value) -> {
            if (value != null && !value.isBlank()) multipart.part(key, value);
        });

        JsonNode response = webClient.post()
                .uri("/" + method)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(multipart.build()))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofHours(2))
                .onErrorResume(error -> Mono.error(new TelegramApiException(0, error.getMessage())))
                .block();
        return objectMapper.convertValue(requireResult(response), TgMessage.class);
    }

    private JsonNode postJson(String method, Object body, Duration timeout) {
        JsonNode response = webClient.post()
                .uri("/" + method)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(timeout)
                .onErrorResume(error -> Mono.error(new TelegramApiException(0, error.getMessage())))
                .block();
        return requireResult(response);
    }

    private JsonNode requireResult(JsonNode response) {
        if (response == null) throw new TelegramApiException(0, "Telegram returned an empty response");
        if (!response.path("ok").asBoolean(false)) {
            throw new TelegramApiException(response.path("error_code").asInt(0),
                    response.path("description").asText("Telegram API request failed"));
        }
        return response.path("result");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
