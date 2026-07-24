package uz.tubeforge.media;

import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import uz.tubeforge.domain.SourceType;

import java.util.*;

@Component
public class MediaMetadataParser {

    public MediaInfo parse(JsonNode root, SourceType hintedType) {
        SourceType sourceType = detectType(root, hintedType);
        return new MediaInfo(
                text(root, "id", "unknown"),
                text(root, "title", sourceType == SourceType.PLAYLIST ? "YouTube playlist" : "Untitled video"),
                firstText(root, List.of("channel", "uploader", "channel_id"), "Unknown channel"),
                root.path("duration").asLong(0),
                findThumbnail(root),
                firstText(root, List.of("webpage_url", "original_url"), ""),
                sourceType,
                root.path("view_count").asLong(0),
                text(root, "upload_date", ""),
                text(root, "description", ""),
                root.path("playlist_count").asInt(root.path("entries").size()),
                parseFormats(root.path("formats"), root.path("duration").asLong(0)),
                parseSubtitles(root)
        );
    }

    private SourceType detectType(JsonNode root, SourceType hint) {
        if ("playlist".equals(root.path("_type").asText()) || root.path("entries").isArray()) {
            return SourceType.PLAYLIST;
        }
        if (root.path("is_live").asBoolean(false) || "is_live".equals(root.path("live_status").asText())) {
            return SourceType.LIVE;
        }
        String url = root.path("webpage_url").asText("");
        return url.contains("/shorts/") ? SourceType.SHORT : hint;
    }

    private List<VideoFormatOption> parseFormats(JsonNode formats, long durationSeconds) {
        if (!formats.isArray()) return List.of();
        long bestAudioBytes = 0;
        for (JsonNode format : formats) {
            if (!"none".equals(format.path("acodec").asText("none"))
                    && "none".equals(format.path("vcodec").asText("none"))) {
                bestAudioBytes = Math.max(bestAudioBytes, estimatedSize(format, durationSeconds));
            }
        }
        Map<Integer, VideoFormatOption> bestByHeight = new TreeMap<>(Comparator.reverseOrder());
        for (JsonNode format : formats) {
            int height = format.path("height").asInt(0);
            String vcodec = format.path("vcodec").asText("none");
            if (height <= 0 || "none".equals(vcodec)) continue;
            long size = estimatedSize(format, durationSeconds);
            if ("none".equals(format.path("acodec").asText("none"))) size += bestAudioBytes;
            int fps = (int) Math.round(format.path("fps").asDouble(0));
            String ext = format.path("ext").asText("mp4");
            String formatId = format.path("format_id").asText("");
            boolean combined = !"none".equals(format.path("acodec").asText("none"));
            String selector = formatId.isBlank()
                    ? "height:" + height
                    : "format:" + formatId + (combined ? ":combined" : ":video");
            VideoFormatOption candidate = new VideoFormatOption(height, size, fps, ext, selector, combined);
            VideoFormatOption current = bestByHeight.get(height);
            if (current == null || candidate.estimatedBytes() > current.estimatedBytes()) {
                bestByHeight.put(height, candidate);
            }
        }
        return bestByHeight.values().stream().limit(10).toList();
    }

    private long estimatedSize(JsonNode format, long durationSeconds) {
        long exact = format.path("filesize").asLong(format.path("filesize_approx").asLong(0));
        if (exact > 0 || durationSeconds <= 0) return exact;
        double bitrateKbps = format.path("tbr").asDouble(0);
        return bitrateKbps <= 0 ? 0 : Math.round(bitrateKbps * 1000 / 8 * durationSeconds);
    }

    private List<SubtitleOption> parseSubtitles(JsonNode root) {
        Map<String, SubtitleOption> result = new TreeMap<>();
        addSubtitleObject(result, root.path("subtitles"), false);
        addSubtitleObject(result, root.path("automatic_captions"), true);
        return result.values().stream().limit(30).toList();
    }

    private void addSubtitleObject(Map<String, SubtitleOption> result, JsonNode node, boolean automatic) {
        if (!node.isObject()) return;
        node.propertyNames().forEach(code -> result.compute(code, (key, existing) -> {
            if (existing != null && !existing.automatic()) return existing;
            return new SubtitleOption(code, languageName(code), automatic);
        }));
    }

    private String languageName(String code) {
        Locale locale = Locale.forLanguageTag(code.replace('_', '-'));
        String name = locale.getDisplayLanguage(Locale.ENGLISH);
        return name == null || name.isBlank() ? code : name;
    }

    private String findThumbnail(JsonNode root) {
        String direct = root.path("thumbnail").asText("");
        if (!direct.isBlank()) return direct;
        JsonNode thumbnails = root.path("thumbnails");
        if (thumbnails.isArray() && !thumbnails.isEmpty()) {
            return thumbnails.get(thumbnails.size() - 1).path("url").asText("");
        }
        return "";
    }

    private String firstText(JsonNode root, List<String> fields, String fallback) {
        for (String field : fields) {
            String value = root.path(field).asText("");
            if (!value.isBlank()) return value;
        }
        return fallback;
    }

    private String text(JsonNode root, String field, String fallback) {
        String value = root.path(field).asText("");
        return value.isBlank() ? fallback : value;
    }
}
