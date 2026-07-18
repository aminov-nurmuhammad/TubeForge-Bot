package uz.tubeforge.telegram.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TgUser(
        long id,
        @JsonProperty("is_bot") boolean bot,
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName,
        String username,
        @JsonProperty("language_code") String languageCode
) {
}
