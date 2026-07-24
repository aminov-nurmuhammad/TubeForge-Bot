package uz.tubeforge.ai;

import org.springframework.stereotype.Component;
import uz.tubeforge.domain.Language;
import uz.tubeforge.media.MediaInfo;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class LocalInsightEngine {
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}']+");
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "and", "or", "but", "to", "of", "in", "on", "for", "is", "are", "was", "were",
            "it", "this", "that", "with", "as", "at", "be", "by", "from", "you", "we", "they", "i", "so",
            "и", "в", "во", "не", "на", "что", "это", "как", "к", "по", "из", "за", "для", "он", "она", "мы",
            "va", "bu", "bir", "uchun", "bilan", "ham", "emas", "qanday", "men", "siz", "ular", "bor", "edi");

    private final SrtTranscriptParser parser;

    public LocalInsightEngine(SrtTranscriptParser parser) {
        this.parser = parser;
    }

    public AiInsightResult generate(InsightType type, String srt, MediaInfo info, Language language) {
        List<TranscriptCue> cues = parser.parse(srt);
        if (cues.isEmpty()) throw new IllegalArgumentException("Transcript is empty");
        String content = switch (type) {
            case SUMMARY -> summary(cues, language);
            case CHAPTERS -> chapters(cues, language);
            case STUDY_NOTES -> notes(cues, language);
        };
        return new AiInsightResult(content, "local-smart");
    }

    private String summary(List<TranscriptCue> cues, Language language) {
        List<String> best = ranked(cues, 7);
        return heading(language, "summary") + "\n\n" + best.stream().map(value -> "• " + value)
                .collect(Collectors.joining("\n"));
    }

    private String chapters(List<TranscriptCue> cues, Language language) {
        int chapterCount = Math.max(3, Math.min(8, cues.size() / 20 + 3));
        long duration = Math.max(1, cues.get(cues.size() - 1).endSeconds());
        List<String> chapters = new ArrayList<>();
        for (int index = 0; index < chapterCount; index++) {
            long start = duration * index / chapterCount;
            long end = duration * (index + 1) / chapterCount;
            TranscriptCue representative = cues.stream()
                    .filter(cue -> cue.startSeconds() >= start && cue.startSeconds() < end)
                    .max(Comparator.comparingInt(cue -> cue.text().length()))
                    .orElse(cues.get(Math.min(cues.size() - 1, index * cues.size() / chapterCount)));
            chapters.add(timestamp(start) + " — " + shorten(representative.text(), 110));
        }
        return heading(language, "chapters") + "\n\n" + String.join("\n", chapters);
    }

    private String notes(List<TranscriptCue> cues, Language language) {
        List<String> best = ranked(cues, 6);
        List<String> keywords = frequencies(cues).entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(12).map(Map.Entry::getKey).toList();
        return heading(language, "notes") + "\n\n"
                + best.stream().map(value -> "• " + value).collect(Collectors.joining("\n"))
                + "\n\n" + switch (language) {
                    case RU -> "Ключевые слова: ";
                    case UZ -> "Kalit so‘zlar: ";
                    default -> "Keywords: ";
                } + String.join(", ", keywords);
    }

    private List<String> ranked(List<TranscriptCue> cues, int limit) {
        Map<String, Integer> frequency = frequencies(cues);
        List<ScoredSentence> candidates = new ArrayList<>();
        List<IndexedText> grouped = groupedText(cues);
        for (IndexedText value : grouped) {
            String sentence = shorten(value.text(), 240);
            if (sentence.length() < 20) continue;
            double score = words(sentence).stream().mapToDouble(word -> frequency.getOrDefault(word, 0)).sum();
            score /= Math.max(8, words(sentence).size());
            score += value.index() < Math.max(3, grouped.size() / 12) ? 1.5 : 0;
            candidates.add(new ScoredSentence(value.index(), sentence, score));
        }
        List<ScoredSentence> selected = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ScoredSentence candidate : candidates.stream()
                .sorted(Comparator.comparingDouble(ScoredSentence::score).reversed()).toList()) {
            String fingerprint = fingerprint(candidate.text());
            if (!fingerprint.isBlank() && seen.add(fingerprint)) selected.add(candidate);
            if (selected.size() == limit) break;
        }
        if (selected.isEmpty()) {
            return grouped.stream().map(IndexedText::text).filter(value -> !value.isBlank())
                    .limit(limit).map(value -> shorten(value, 240)).toList();
        }
        return selected.stream().sorted(Comparator.comparingInt(ScoredSentence::index))
                .map(ScoredSentence::text).toList();
    }

    private List<IndexedText> groupedText(List<TranscriptCue> cues) {
        List<IndexedText> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int start = 0;
        for (int index = 0; index < cues.size(); index++) {
            String clean = cues.get(index).text().replaceAll("\\s+", " ").strip();
            if (clean.isBlank()) continue;
            if (current.isEmpty()) start = index;
            if (!current.isEmpty()) current.append(' ');
            current.append(clean);
            boolean sentenceEnd = clean.matches(".*[.!?…]$");
            if (current.length() >= 80 || sentenceEnd || index == cues.size() - 1) {
                result.add(new IndexedText(start, current.toString()));
                current.setLength(0);
            }
        }
        if (!current.isEmpty()) result.add(new IndexedText(start, current.toString()));
        return result;
    }

    private String fingerprint(String value) {
        return words(value).stream().distinct().limit(10).sorted().collect(Collectors.joining("|"));
    }

    private Map<String, Integer> frequencies(List<TranscriptCue> cues) {
        Map<String, Integer> counts = new HashMap<>();
        for (TranscriptCue cue : cues) {
            for (String word : words(cue.text())) counts.merge(word, 1, Integer::sum);
        }
        return counts;
    }

    private List<String> words(String text) {
        List<String> result = new ArrayList<>();
        var matcher = WORD.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String value = matcher.group();
            if (value.length() > 2 && !STOP_WORDS.contains(value)) result.add(value);
        }
        return result;
    }

    private String heading(Language language, String type) {
        return switch (language) {
            case RU -> switch (type) {
                case "chapters" -> "Главы и ключевые моменты";
                case "notes" -> "Учебный конспект";
                default -> "Краткое содержание";
            };
            case UZ -> switch (type) {
                case "chapters" -> "Bo‘limlar va muhim lahzalar";
                case "notes" -> "O‘quv konspekti";
                default -> "Qisqacha mazmun";
            };
            default -> switch (type) {
                case "chapters" -> "Chapters and key moments";
                case "notes" -> "Study notes";
                default -> "Smart summary";
            };
        };
    }

    private String timestamp(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return hours > 0 ? "%d:%02d:%02d".formatted(hours, minutes, secs) : "%02d:%02d".formatted(minutes, secs);
    }

    private String shorten(String value, int max) {
        String clean = value.replaceAll("\\s+", " ").strip();
        return clean.length() <= max ? clean : clean.substring(0, max - 1).strip() + "…";
    }

    private record ScoredSentence(int index, String text, double score) {}
    private record IndexedText(int index, String text) {}
}
