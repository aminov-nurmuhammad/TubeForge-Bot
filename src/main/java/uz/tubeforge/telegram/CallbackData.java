package uz.tubeforge.telegram;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public record CallbackData(String action, List<String> arguments) {
    public static CallbackData parse(String value) {
        if (value == null || value.isBlank()) return new CallbackData("", List.of());
        String[] parts = value.split(":", -1);
        return new CallbackData(parts[0], Arrays.stream(parts).skip(1).toList());
    }

    public static String of(String action, Object... arguments) {
        StringBuilder value = new StringBuilder(action);
        for (Object argument : arguments) value.append(':').append(argument == null ? "" : argument);
        if (value.toString().getBytes(StandardCharsets.UTF_8).length > 64) {
            throw new IllegalArgumentException("Telegram callback data exceeds 64 bytes");
        }
        return value.toString();
    }

    public String arg(int index) {
        if (index < 0 || index >= arguments.size()) throw new IllegalArgumentException("Missing callback argument");
        return arguments.get(index);
    }
}
