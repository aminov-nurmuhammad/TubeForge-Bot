package uz.tubeforge.media;

import uz.tubeforge.domain.SourceType;

public record ParsedYouTubeUrl(String normalizedUrl, SourceType sourceType) {
}
