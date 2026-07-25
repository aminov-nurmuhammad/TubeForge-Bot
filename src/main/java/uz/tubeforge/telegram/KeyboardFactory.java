package uz.tubeforge.telegram;

import org.springframework.stereotype.Component;
import uz.tubeforge.config.FeatureProperties;
import uz.tubeforge.domain.AppUser;
import uz.tubeforge.domain.JobType;
import uz.tubeforge.domain.Language;
import uz.tubeforge.domain.MetadataState;
import uz.tubeforge.domain.SourceType;
import uz.tubeforge.media.MediaInfo;
import uz.tubeforge.media.SubtitleOption;
import uz.tubeforge.media.VideoFormatOption;
import uz.tubeforge.telegram.model.InlineButton;
import uz.tubeforge.telegram.model.InlineKeyboard;
import uz.tubeforge.util.HumanFormat;

import java.util.ArrayList;
import java.util.List;

import static uz.tubeforge.telegram.CallbackData.of;
import static uz.tubeforge.telegram.model.InlineButton.callback;

@Component
public class KeyboardFactory {
    private final FeatureProperties features;

    public KeyboardFactory(FeatureProperties features) {
        this.features = features;
    }

    public InlineKeyboard acceptTerms() {
        return InlineKeyboard.of(List.of(List.of(callback("✅ I agree — continue", of("accept")))));
    }

    public InlineKeyboard preview(String requestId, MediaInfo info, AppUser user) {
        return preview(requestId, info, user, MetadataState.READY);
    }

    public InlineKeyboard preview(String requestId, MediaInfo info, AppUser user, MetadataState metadataState) {
        List<List<InlineButton>> rows = new ArrayList<>();
        if (info.sourceType() == uz.tubeforge.domain.SourceType.PLAYLIST) {
            if (features.playlists()) {
                rows.add(List.of(callback("🎥 Playlist video", of("pvideo", requestId)),
                        callback("🎵 Playlist audio", of("paudio", requestId))));
            }
        } else {
            if (features.videoDownload() || features.audioDownload()) {
                List<InlineButton> row = new ArrayList<>();
                if (features.videoDownload()) {
                    boolean reel = info.sourceType() == SourceType.INSTAGRAM_REEL;
                    String quality = reel ? "best" : user.getDefaultVideoQuality();
                    row.add(callback(reel ? "⚡ Best Reel" : "⚡ " + user.getDefaultVideoQuality() + "p video",
                            of("qv", requestId, quality)));
                }
                if (features.audioDownload()) row.add(callback("⚡ " + user.getDefaultAudioFormat() + " audio",
                        of("qa", requestId, user.getDefaultAudioFormat().toLowerCase(java.util.Locale.ROOT))));
                rows.add(row);
            }
            if (metadataState == MetadataState.READY && (features.videoDownload() || features.audioDownload())) {
                List<InlineButton> row = new ArrayList<>();
                if (features.videoDownload()) row.add(callback("🎛 Video formats", of("video", requestId)));
                if (features.audioDownload()) row.add(callback("🎧 Audio formats", of("audio", requestId)));
                rows.add(row);
            }
            boolean transcriptTools = !info.subtitles().isEmpty()
                    && (features.subtitles() || features.transcripts() || features.aiStudio());
            if (metadataState == MetadataState.READY
                    && (features.thumbnails() || features.clips() || transcriptTools)) {
                rows.add(List.of(callback("🧰 Tools", of("tools", requestId)),
                        callback("ℹ️ Info", of("info", requestId))));
            }
        }
        if (metadataState == MetadataState.PENDING) {
            List<InlineButton> row = new ArrayList<>();
            if (features.thumbnails() && !info.thumbnailUrl().isBlank()) {
                row.add(callback("🖼 Cover", of("dlt", requestId)));
            }
            row.add(callback("⏳ Details loading", of("meta", requestId)));
            rows.add(row);
        } else if (metadataState == MetadataState.DEGRADED) {
            rows.add(List.of(callback("🔄 Retry details", of("metaretry", requestId))));
        }
        if (info.webpageUrl() != null && info.webpageUrl().startsWith("https://")) {
            rows.add(List.of(InlineButton.link(info.sourceType() == SourceType.INSTAGRAM_REEL
                            ? "📸 Open on Instagram" : "▶️ Open on YouTube", info.webpageUrl()),
                    callback("❌ Close", of("close"))));
        } else {
            rows.add(List.of(callback("❌ Close", of("close"))));
        }
        return InlineKeyboard.of(rows);
    }

    public InlineKeyboard videoFormats(String requestId, MediaInfo info) {
        return videoFormats(requestId, info, false);
    }

    public InlineKeyboard videoFormats(String requestId, MediaInfo info, boolean expanded) {
        List<List<InlineButton>> rows = new ArrayList<>();
        int highest = info.videoFormats().stream().mapToInt(VideoFormatOption::height).max().orElse(0);
        String highestLabel = highest > 0 ? "⭐ Best available · " + highest + "p" : "⭐ Best available";
        rows.add(List.of(callback(highestLabel, of("dlv", requestId, "best")),
                callback("📱 Smallest file", of("dlv", requestId, "small"))));
        List<InlineButton> current = new ArrayList<>();
        List<VideoFormatOption> formats = expanded ? info.videoFormats() : recommended(info.videoFormats());
        for (VideoFormatOption format : formats) {
            String size = format.estimatedBytes() > 0 ? " • " + HumanFormat.bytes(format.estimatedBytes()) : "";
            String fps = format.fps() >= 50 ? " • " + format.fps() + "fps" : "";
            // CallbackData uses ':' as its field separator; keep the format selector one field.
            current.add(callback(format.height() + "p" + fps + size,
                    of("dlv", requestId, callbackFormat(format.selector()))));
            if (current.size() == 2) {
                rows.add(List.copyOf(current));
                current.clear();
            }
        }
        if (!current.isEmpty()) rows.add(List.copyOf(current));
        if (!expanded && formats.size() < info.videoFormats().size()) {
            rows.add(List.of(callback("🎛 All available formats", of("allv", requestId))));
        }
        rows.add(List.of(callback("🔙 Back", of("back", requestId))));
        return InlineKeyboard.of(rows);
    }

    public InlineKeyboard audioFormats(String requestId) {
        return InlineKeyboard.of(List.of(
                List.of(callback("⚡ MP3 • 192 kbps", of("dla", requestId, "mp3", 192))),
                List.of(callback("⚡ M4A • 192 kbps", of("dla", requestId, "m4a", 192))),
                List.of(callback("🎛 More formats and qualities", of("alla", requestId))),
                List.of(callback("🔙 Back", of("back", requestId)))
        ));
    }

    public InlineKeyboard allAudioFormats(String requestId) {
        return InlineKeyboard.of(List.of(
                List.of(callback("🎧 MP3", of("audfmt", requestId, "mp3")), callback("🎼 M4A", of("audfmt", requestId, "m4a"))),
                List.of(callback("🔊 WAV • lossless", of("audfmt", requestId, "wav")), callback("🎙 OGG", of("audfmt", requestId, "ogg"))),
                List.of(callback("💿 FLAC • lossless", of("audfmt", requestId, "flac"))),
                List.of(callback("🔙 Quick audio", of("audio", requestId)))
        ));
    }

    public InlineKeyboard toolsMenu(String requestId) {
        return toolsMenu(requestId, null);
    }

    public InlineKeyboard toolsMenu(String requestId, MediaInfo info) {
        List<List<InlineButton>> rows = new ArrayList<>();
        boolean subtitlesAvailable = info == null || !info.subtitles().isEmpty();
        List<InlineButton> primary = new ArrayList<>();
        if (features.thumbnails()) primary.add(callback("🖼 Cover image", of("thumb", requestId)));
        if (features.clips()) primary.add(callback("✂️ Clip Studio", of("clip", requestId)));
        if (!primary.isEmpty()) rows.add(primary);
        if (subtitlesAvailable && (features.subtitles() || features.transcripts())) {
            List<InlineButton> transcript = new ArrayList<>();
            if (features.subtitles()) transcript.add(callback("📝 Subtitles", of("subs", requestId)));
            if (features.transcripts()) transcript.add(callback("📄 Transcript", of("trans", requestId)));
            rows.add(transcript);
        }
        if (features.aiStudio() && subtitlesAvailable) {
            rows.add(List.of(callback("🧠 Transcript Studio", of("ai", requestId))));
        }
        rows.add(List.of(callback("🔙 Back", of("back", requestId))));
        return InlineKeyboard.of(rows);
    }

    public InlineKeyboard aiStudio(String requestId) {
        return InlineKeyboard.of(List.of(
                List.of(callback("🧠 Transcript summary", of("aisel", requestId, "summary"))),
                List.of(callback("⏱ Chapters & key moments", of("aisel", requestId, "chapters"))),
                List.of(callback("📚 Study notes", of("aisel", requestId, "notes"))),
                List.of(callback("🔙 Back", of("back", requestId)))
        ));
    }

    public InlineKeyboard metadataStatus(String requestId, MetadataState state) {
        if (state == MetadataState.DEGRADED) {
            return InlineKeyboard.of(List.of(
                    List.of(callback("🔄 Retry now", of("metaretry", requestId))),
                    List.of(callback("🔙 Back", of("back", requestId)))
            ));
        }
        return InlineKeyboard.of(List.of(
                List.of(callback("🔄 Refresh", of("meta", requestId))),
                List.of(callback("🔙 Back", of("back", requestId)))
        ));
    }

    public InlineKeyboard admin() {
        return InlineKeyboard.of(List.of(
                List.of(callback("🔄 Overview", of("adm")),
                        callback("⚙️ Workload", of("admqueue"))),
                List.of(callback("⚡ Cache & speed", of("admcache")),
                        callback("🩺 Tool health", of("admhealth"))),
                List.of(callback("❌ Close", of("close")))
        ));
    }

    public InlineKeyboard audioQualities(String requestId, String format) {
        return InlineKeyboard.of(List.of(
                List.of(callback("320 kbps", of("dla", requestId, format, 320)), callback("256 kbps", of("dla", requestId, format, 256))),
                List.of(callback("192 kbps", of("dla", requestId, format, 192)), callback("128 kbps", of("dla", requestId, format, 128))),
                List.of(callback("🔙 Formats", of("audio", requestId)))
        ));
    }

    public InlineKeyboard thumbnailMenu(String requestId) {
        return thumbnailMenu(requestId, true);
    }

    public InlineKeyboard thumbnailMenu(String requestId, boolean allThumbnails) {
        if (!allThumbnails) {
            return InlineKeyboard.of(List.of(
                    List.of(callback("⭐ Best cover", of("dlt", requestId))),
                    List.of(callback("🔙 Back", of("back", requestId)))
            ));
        }
        return InlineKeyboard.of(List.of(
                List.of(callback("⭐ Best thumbnail", of("dlt", requestId)), callback("📦 All thumbnails", of("dlta", requestId))),
                List.of(callback("🔙 Back", of("back", requestId)))
        ));
    }

    public InlineKeyboard subtitleMenu(String requestId, List<SubtitleOption> subtitles, String action) {
        List<List<InlineButton>> rows = new ArrayList<>();
        List<InlineButton> current = new ArrayList<>();
        List<SubtitleOption> visible = subtitles.stream().limit(16).toList();
        for (int index = 0; index < visible.size(); index++) {
            SubtitleOption sub = visible.get(index);
            String label = (sub.automatic() ? "🤖 " : "") + sub.name() + " (" + sub.code() + ")";
            current.add(callback(label, of(action, requestId, index)));
            if (current.size() == 2) {
                rows.add(List.copyOf(current));
                current.clear();
            }
        }
        if (!current.isEmpty()) rows.add(List.copyOf(current));
        rows.add(List.of(callback("🔙 Back", of("back", requestId))));
        return InlineKeyboard.of(rows);
    }

    public InlineKeyboard clipMenu(String requestId) {
        return InlineKeyboard.of(List.of(
                List.of(callback("🎥 Video clip", of("cliptype", requestId, "video")),
                        callback("🎵 Audio clip", of("cliptype", requestId, "audio"))),
                List.of(callback("🔙 Back", of("back", requestId)))
        ));
    }

    public InlineKeyboard cancelJob(String jobId) {
        return InlineKeyboard.of(List.of(List.of(callback("❌ Cancel job", of("cancel", jobId)))));
    }

    public InlineKeyboard settings(AppUser user) {
        return InlineKeyboard.of(List.of(
                List.of(callback("🌐 Language", of("setlang"))),
                List.of(callback("🎥 Video quality", of("setvq")), callback("🎵 Audio format", of("setaf"))),
                List.of(callback(user.isSendAsDocument() ? "📎 Send as document ✓" : "▶️ Send as video ✓", of("toggledoc"))),
                List.of(callback(user.isAutoCompress() ? "📦 Auto-compress: ON" : "📦 Auto-compress: OFF", of("togglecmp"))),
                List.of(callback("✅ Done", of("close")))
        ));
    }

    public InlineKeyboard languageMenu() {
        return InlineKeyboard.of(List.of(
                List.of(callback(Language.EN.label(), of("lang", "EN")), callback(Language.RU.label(), of("lang", "RU"))),
                List.of(callback(Language.UZ.label(), of("lang", "UZ"))),
                List.of(callback("🔙 Back", of("settings")))
        ));
    }

    public InlineKeyboard videoQualitySettings() {
        return InlineKeyboard.of(List.of(
                List.of(callback("1080p", of("vq", 1080)), callback("720p", of("vq", 720))),
                List.of(callback("480p", of("vq", 480)), callback("360p", of("vq", 360))),
                List.of(callback("🔙 Back", of("settings")))
        ));
    }

    public InlineKeyboard audioFormatSettings() {
        return InlineKeyboard.of(List.of(
                List.of(callback("MP3", of("af", "MP3")), callback("M4A", of("af", "M4A"))),
                List.of(callback("OGG", of("af", "OGG")), callback("FLAC", of("af", "FLAC"))),
                List.of(callback("🔙 Back", of("settings")))
        ));
    }

    public InlineKeyboard back(String requestId) {
        return InlineKeyboard.of(List.of(List.of(callback("🔙 Back", of("back", requestId)))));
    }

    private List<VideoFormatOption> recommended(List<VideoFormatOption> formats) {
        return formats.stream().limit(4).toList();
    }

    private String callbackFormat(String selector) {
        return selector == null ? "height_720" : selector.replace(':', '~');
    }
}
