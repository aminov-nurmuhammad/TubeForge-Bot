package uz.tubeforge.telegram.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TgUpdate(
        @JsonProperty("update_id") long updateId,
        TgMessage message,
        @JsonProperty("callback_query") TgCallbackQuery callbackQuery
) {
}
