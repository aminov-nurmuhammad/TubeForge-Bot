package uz.tubeforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tubeforge.features")
public record FeatureProperties(
        boolean videoDownload,
        boolean audioDownload,
        boolean thumbnails,
        boolean subtitles,
        boolean transcripts,
        boolean clips,
        boolean playlists,
        boolean aiStudio
) {
}
