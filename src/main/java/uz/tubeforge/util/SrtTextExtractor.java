package uz.tubeforge.util;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

public final class SrtTextExtractor {
    private static final Pattern TIMESTAMP = Pattern.compile("^\\d{2}:\\d{2}:\\d{2}[,.]\\d{3}\\s+-->.*$");

    private SrtTextExtractor() {}

    public static String extract(String srt) {
        StringBuilder result = new StringBuilder();
        Set<String> previousBlock = new LinkedHashSet<>();
        for (String raw : srt.replace("\r", "").split("\n")) {
            String line = raw.strip();
            if (line.isEmpty()) {
                if (!previousBlock.isEmpty()) {
                    previousBlock.forEach(text -> result.append(text).append(' '));
                    result.append('\n');
                    previousBlock.clear();
                }
                continue;
            }
            if (line.matches("\\d+") || TIMESTAMP.matcher(line).matches()) continue;
            line = line.replaceAll("<[^>]+>", "").trim();
            if (!line.isBlank()) previousBlock.add(line);
        }
        previousBlock.forEach(text -> result.append(text).append(' '));
        return result.toString().replaceAll(" +", " ").replaceAll(" ?\\n ?", "\n").trim();
    }
}
