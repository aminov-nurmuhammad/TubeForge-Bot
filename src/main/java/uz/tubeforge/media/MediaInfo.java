package uz.tubeforge.media;

import uz.tubeforge.domain.SourceType;

import java.util.List;

public record MediaInfo(
        String id,
        String title,
        String channel,
        long durationSeconds,
        String thumbnailUrl,
        String webpageUrl,
        SourceType sourceType,
        long viewCount,
        String uploadDate,
        String description,
        int playlistCount,
        List<VideoFormatOption> videoFormats,
        List<SubtitleOption> subtitles
) {
    public MediaInfo {
        videoFormats = videoFormats == null ? List.of() : List.copyOf(videoFormats);
        subtitles = subtitles == null ? List.of() : List.copyOf(subtitles);
    }
}
