package uz.tubeforge.media;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record ClipRange(Duration start, Duration end) {
    private static final Pattern RANGE = Pattern.compile("^\\s*([0-9:]+)\\s*[-–—]\\s*([0-9:]+)\\s*$");

    public ClipRange {
        if (start.isNegative() || end.compareTo(start) <= 0) throw new IllegalArgumentException("Invalid clip range");
        if (end.minus(start).compareTo(Duration.ofMinutes(30)) > 0) {
            throw new IllegalArgumentException("Clip cannot be longer than 30 minutes");
        }
    }

    public static ClipRange parse(String input) {
        Matcher matcher = RANGE.matcher(input == null ? "" : input);
        if (!matcher.matches()) throw new IllegalArgumentException("Use a range such as 01:20-03:45");
        return new ClipRange(parseTime(matcher.group(1)), parseTime(matcher.group(2)));
    }

    private static Duration parseTime(String text) {
        String[] parts = text.split(":");
        if (parts.length < 1 || parts.length > 3) throw new IllegalArgumentException("Invalid timestamp");
        long seconds = 0;
        for (int index = 0; index < parts.length; index++) {
            int value = Integer.parseInt(parts[index]);
            if (index > 0 && value > 59) throw new IllegalArgumentException("Invalid timestamp");
            seconds = seconds * 60 + value;
        }
        return Duration.ofSeconds(seconds);
    }

    public String startFormatted() { return format(start); }
    public String endFormatted() { return format(end); }

    private String format(Duration value) {
        long seconds = value.toSeconds();
        return String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }
}
