package uz.tubeforge.util;

import java.util.Locale;

public final class HumanFormat {
    private HumanFormat() {}

    public static String duration(long seconds) {
        if (seconds <= 0) return "Live / unknown";
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long rest = seconds % 60;
        return hours > 0 ? String.format("%d:%02d:%02d", hours, minutes, rest)
                : String.format("%d:%02d", minutes, rest);
    }

    public static String bytes(long bytes) {
        if (bytes <= 0) return "size unknown";
        String[] units = {"B", "KB", "MB", "GB"};
        double value = bytes;
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format(Locale.ROOT, value >= 10 || unit == 0 ? "%.0f %s" : "%.1f %s", value, units[unit]);
    }

    public static String number(long value) {
        if (value <= 0) return "—";
        if (value >= 1_000_000_000) return String.format(Locale.ROOT, "%.1fB", value / 1_000_000_000.0);
        if (value >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
        if (value >= 1_000) return String.format(Locale.ROOT, "%.1fK", value / 1_000.0);
        return Long.toString(value);
    }
}
