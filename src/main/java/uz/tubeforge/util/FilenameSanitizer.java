package uz.tubeforge.util;

public final class FilenameSanitizer {
    private FilenameSanitizer() {}

    public static String sanitize(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String result = value.replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", " ").trim();
        if (result.equals(".") || result.equals("..") || result.isBlank()) return fallback;
        return result.length() > 120 ? result.substring(0, 120).trim() : result;
    }
}
