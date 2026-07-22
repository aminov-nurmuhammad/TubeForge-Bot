package uz.tubeforge.telegram.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TgMessage(
        @JsonProperty("message_id") long messageId,
        TgUser from,
        TgChat chat,
        long date,
        String text,
        String caption,
        List<TgMediaFile> photo,
        TgMediaFile video,
        TgMediaFile audio,
        TgMediaFile document
) {
}
