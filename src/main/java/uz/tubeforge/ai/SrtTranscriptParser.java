package uz.tubeforge.ai;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SrtTranscriptParser {
    private static final Pattern TIMESTAMP = Pattern.compile(
            "(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{3})\\s*-->\\s*(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{3})");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    public List<TranscriptCue> parse(String srt) {
        if (srt == null || srt.isBlank()) return List.of();
        List<TranscriptCue> cues = new ArrayList<>();
        for (String block : srt.replace("\r", "").split("\\n\\s*\\n")) {
            String[] lines = block.lines().toArray(String[]::new);
            int timestampIndex = -1;
            Matcher timestamp = null;
            for (int i = 0; i < lines.length; i++) {
                Matcher candidate = TIMESTAMP.matcher(lines[i]);
                if (candidate.find()) {
                    timestampIndex = i;
                    timestamp = candidate;
                    break;
                }
            }
            if (timestampIndex < 0 || timestamp == null) continue;
            StringBuilder text = new StringBuilder();
            for (int i = timestampIndex + 1; i < lines.length; i++) {
                String clean = TAG.matcher(lines[i]).replaceAll("").replace("&nbsp;", " ").strip();
                if (!clean.isBlank()) {
                    if (!text.isEmpty()) text.append(' ');
                    text.append(clean);
                }
            }
            String cleanText = text.toString().replaceAll("\\s+", " ").strip();
            if (!cleanText.isBlank()) cues.add(new TranscriptCue(seconds(timestamp, 1), seconds(timestamp, 5), cleanText));
        }
        return List.copyOf(cues);
    }

    private long seconds(Matcher matcher, int offset) {
        return Long.parseLong(matcher.group(offset)) * 3600
                + Long.parseLong(matcher.group(offset + 1)) * 60
                + Long.parseLong(matcher.group(offset + 2));
    }
}
