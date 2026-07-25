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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.tubeforge.config.TelegramProperties;
import uz.tubeforge.service.PerformanceMetrics;
import uz.tubeforge.telegram.model.InlineKeyboard;
import uz.tubeforge.telegram.model.TgMessage;
import uz.tubeforge.telegram.model.TgUpdate;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

@Component
public class TelegramApiClient {
    private static final Logger log = LoggerFactory.getLogger(TelegramApiClient.class);
    private static final TypeReference<List<TgUpdate>> UPDATE_LIST = new TypeReference<>() {};

    private final TelegramProperties properties;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;
    private final PerformanceMetrics metrics;

    public TelegramApiClient(TelegramProperties properties, ObjectMapper objectMapper, WebClient.Builder builder,
                             PerformanceMetrics metrics) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webClient = builder.baseUrl(properties.botApiUrl()).build();
        this.metrics = metrics;
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
        body.put("reply_markup", keyboard == null ? InlineKeyboard.of(List.of()) : keyboard);
        postJson("editMessageText", body, Duration.ofSeconds(30));
    }

    public void editCaption(long chatId, long messageId, String caption, InlineKeyboard keyboard) {
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("caption", caption);
        body.put("parse_mode", "HTML");
        body.put("reply_markup", keyboard == null ? InlineKeyboard.of(List.of()) : keyboard);
        postJson("editMessageCaption", body, Duration.ofSeconds(30));
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

    public TgMessage sendVideo(long chatId, String fileId, String caption, boolean supportsStreaming) {
        return sendFileId("sendVideo", "video", chatId, fileId, caption,
                Map.of("supports_streaming", supportsStreaming));
    }

    public TgMessage sendPhoto(long chatId, Path file, String caption) {
        return upload("sendPhoto", "photo", chatId, file, caption, Map.of());
    }

    public TgMessage sendPhoto(long chatId, String fileId, String caption) {
        return sendFileId("sendPhoto", "photo", chatId, fileId, caption, Map.of());
    }

    public TgMessage sendAudio(long chatId, Path file, String caption, String title, String performer) {
        return upload("sendAudio", "audio", chatId, file, caption,
                Map.of("title", safeMetadata(title), "performer", safeMetadata(performer)));
    }

    public TgMessage sendAudio(long chatId, String fileId, String caption, String title, String performer) {
        return sendFileId("sendAudio", "audio", chatId, fileId, caption,
                Map.of("title", safeMetadata(title), "performer", safeMetadata(performer)));
    }

    public TgMessage sendDocument(long chatId, Path file, String caption) {
        return upload("sendDocument", "document", chatId, file, caption, Map.of());
    }

    public TgMessage sendDocument(long chatId, String fileId, String caption) {
        return sendFileId("sendDocument", "document", chatId, fileId, caption, Map.of());
    }

    public List<TgMessage> sendLongMessage(long chatId, String text) {
        if (text == null || text.isBlank()) return List.of();
        List<TgMessage> sent = new java.util.ArrayList<>();
        for (String part : splitText(text, 3900)) sent.add(sendMessage(chatId, part, null));
        return List.copyOf(sent);
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
                Map.of("command", "id", "description", "Show your Telegram ID"),
                Map.of("command", "admin", "description", "Owner control center")
        );
        postJson("setMyCommands", Map.of("commands", commands), Duration.ofSeconds(20));
    }

    private TgMessage upload(String method, String field, long chatId, Path file, String caption,
                             Map<String, String> extras) {
        JsonNode result = withTelegramRetry(method, () -> {
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
                    .exchangeToMono(this::responseBody)
                    .timeout(Duration.ofHours(2))
                    .onErrorMap(this::mapTransportError)
                    .block();
            return requireResult(response);
        });
        return objectMapper.convertValue(result, TgMessage.class);
    }

    private TgMessage sendFileId(String method, String field, long chatId, String fileId, String caption,
                                 Map<String, ?> extras) {
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("chat_id", chatId);
        body.put(field, fileId);
        body.put("caption", caption == null ? "" : caption);
        body.put("parse_mode", "HTML");
        body.putAll(extras);
        return objectMapper.convertValue(postJson(method, body, Duration.ofSeconds(60)), TgMessage.class);
    }

    private JsonNode postJson(String method, Object body, Duration timeout) {
        return withTelegramRetry(method, () -> {
            JsonNode response = webClient.post()
                    .uri("/" + method)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .exchangeToMono(this::responseBody)
                    .timeout(timeout)
                    .onErrorMap(this::mapTransportError)
                    .block();
            return requireResult(response);
        });
    }

    private Mono<JsonNode> responseBody(org.springframework.web.reactive.function.client.ClientResponse response) {
        int status = response.statusCode().value();
        return response.bodyToMono(JsonNode.class)
                .onErrorMap(error -> new TelegramApiException(status,
                        "Telegram returned an unreadable HTTP " + status + " response"))
                .switchIfEmpty(Mono.error(new TelegramApiException(status,
                        "Telegram returned an empty HTTP " + status + " response")));
    }

    private JsonNode withTelegramRetry(String method, Supplier<JsonNode> request) {
        for (int attempt = 0; ; attempt++) {
            try {
                return request.get();
            } catch (TelegramApiException error) {
                if (!retryable(error) || attempt >= properties.apiMaxRetries()) throw error;
                Duration delay = retryDelay(error, attempt);
                metrics.telegramRetry();
                log.debug("Retrying Telegram method {} after {} ms (HTTP/API {})",
                        method, delay.toMillis(), error.getErrorCode());
                waitFor(delay);
            }
        }
    }

    private boolean retryable(TelegramApiException error) {
        int code = error.getErrorCode();
        // Do not retry an unknown transport outcome: Telegram may already have accepted
        // a send operation and a blind retry could duplicate the user's file/message.
        return code == 429 || code >= 500;
    }

    private Duration retryDelay(TelegramApiException error, int attempt) {
        if (error.getRetryAfterSeconds() > 0) {
            return Duration.ofSeconds(Math.min(60, error.getRetryAfterSeconds()));
        }
        long baseMillis = Math.max(10, properties.apiRetryBaseDelay().toMillis());
        long exponential = Math.min(30_000, baseMillis * (1L << Math.min(10, attempt)));
        long jitter = ThreadLocalRandom.current().nextLong(Math.max(1, exponential / 4 + 1));
        return Duration.ofMillis(Math.min(30_000, exponential + jitter));
    }

    private void waitFor(Duration delay) {
        if (Thread.currentThread().isInterrupted()) {
            Thread.currentThread().interrupt();
            throw new TelegramApiException(0, "Telegram retry was interrupted");
        }
        LockSupport.parkNanos(delay.toNanos());
        if (Thread.interrupted()) {
            Thread.currentThread().interrupt();
            throw new TelegramApiException(0, "Telegram retry was interrupted");
        }
    }

    private JsonNode requireResult(JsonNode response) {
        if (response == null) throw new TelegramApiException(0, "Telegram returned an empty response");
        if (!response.path("ok").asBoolean(false)) {
            throw new TelegramApiException(response.path("error_code").asInt(0),
                    response.path("description").asText("Telegram API request failed"),
                    response.path("parameters").path("retry_after").asInt(0));
        }
        return response.path("result");
    }

    private Throwable mapTransportError(Throwable error) {
        if (error instanceof TelegramApiException) return error;
        String message = error.getMessage();
        return new TelegramApiException(0, message == null || message.isBlank()
                ? "Telegram API transport failed" : message);
    }

    private String safeMetadata(String value) {
        if (value == null) return "";
        String clean = value.strip();
        return clean.length() <= 64 ? clean : clean.substring(0, 64);
    }

    private List<String> splitText(String text, int limit) {
        List<String> parts = new java.util.ArrayList<>();
        String remaining = text.strip();
        while (remaining.length() > limit) {
            int cut = remaining.lastIndexOf('\n', limit);
            if (cut < limit / 2) cut = remaining.lastIndexOf(' ', limit);
            if (cut < limit / 2) cut = limit;
            parts.add(remaining.substring(0, cut).strip());
            remaining = remaining.substring(cut).strip();
        }
        if (!remaining.isBlank()) parts.add(remaining);
        return parts;
    }
}
