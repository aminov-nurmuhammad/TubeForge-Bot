package uz.tubeforge.media;

public record VideoFormatOption(
        int height,
        long estimatedBytes,
        int fps,
        String extension,
        String selector,
        boolean combined
) {
    public VideoFormatOption(int height, long estimatedBytes, int fps, String extension) {
        this(height, estimatedBytes, fps, extension, "height:" + height, false);
    }

    public VideoFormatOption {
        extension = extension == null || extension.isBlank() ? "mp4" : extension;
        selector = selector == null || selector.isBlank() ? "height:" + height : selector;
    }
}
