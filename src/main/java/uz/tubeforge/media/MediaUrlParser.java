package uz.tubeforge.media;

import org.springframework.stereotype.Component;

import java.util.Optional;

/** Single entry point for source detection used by Telegram and retry flows. */
@Component
public class MediaUrlParser {
    private final YouTubeUrlParser youtube;
    private final InstagramUrlParser instagram;

    public MediaUrlParser(YouTubeUrlParser youtube, InstagramUrlParser instagram) {
        this.youtube = youtube;
        this.instagram = instagram;
    }

    public Optional<ParsedMediaUrl> find(String text) {
        Optional<ParsedYouTubeUrl> youtubeUrl = youtube.find(text);
        if (youtubeUrl.isPresent()) return youtubeUrl.map(value -> value);
        return instagram.find(text).map(value -> value);
    }

    public Optional<ParsedMediaUrl> parse(String text) {
        Optional<ParsedYouTubeUrl> youtubeUrl = youtube.parse(text);
        if (youtubeUrl.isPresent()) return youtubeUrl.map(value -> value);
        return instagram.parse(text).map(value -> value);
    }
}
