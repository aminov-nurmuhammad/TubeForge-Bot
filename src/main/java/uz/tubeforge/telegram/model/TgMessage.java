package uz.tubeforge.telegram.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TgMessage(
        @JsonProperty("message_id") long messageId,
        TgUser from,
        TgChat chat,
        long date,
        String text,
        String caption
) {
}
