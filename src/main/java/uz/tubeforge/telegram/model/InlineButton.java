package uz.tubeforge.telegram.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InlineButton(
        String text,
        @JsonProperty("callback_data") String callbackData,
        String url
) {
    public static InlineButton callback(String text, String data) {
        return new InlineButton(text, data, null);
    }

    public static InlineButton link(String text, String url) {
        return new InlineButton(text, null, url);
    }
}
