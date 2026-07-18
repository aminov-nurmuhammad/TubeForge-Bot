package uz.tubeforge.telegram.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record InlineKeyboard(@JsonProperty("inline_keyboard") List<List<InlineButton>> rows) {
    public static InlineKeyboard of(List<List<InlineButton>> rows) {
        return new InlineKeyboard(rows);
    }
}
