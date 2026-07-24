package uz.tubeforge.media;

import uz.tubeforge.domain.SourceType;
import uz.tubeforge.util.Sha256;

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

    public static MediaInfo provisional(ParsedYouTubeUrl url) {
        String id = url.videoId().or(() -> url.playlistId())
                .orElse("url-" + Sha256.hex(url.normalizedUrl()).substring(0, 24));
        String title = url.sourceType() == SourceType.PLAYLIST ? "YouTube playlist" : "YouTube video";
        String thumbnail = url.videoId()
                .map(videoId -> "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg")
                .orElse("");
        return new MediaInfo(id, title, "Loading details…", 0, thumbnail, url.normalizedUrl(),
                url.sourceType(), 0, "", "", 0, List.of(), List.of());
    }
}
