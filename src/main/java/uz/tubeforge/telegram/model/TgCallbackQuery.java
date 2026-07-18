package uz.tubeforge.telegram.model;

public record TgCallbackQuery(String id, TgUser from, TgMessage message, String data) {
}
