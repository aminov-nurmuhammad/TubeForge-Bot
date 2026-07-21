package uz.tubeforge.telegram;

import org.springframework.stereotype.Component;
import uz.tubeforge.config.FeatureProperties;
import uz.tubeforge.domain.AppUser;
import uz.tubeforge.domain.JobType;
import uz.tubeforge.domain.Language;
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

    public InlineKeyboard preview(String requestId, MediaInfo info) {
        List<List<InlineButton>> rows = new ArrayList<>();
        if (info.sourceType() == uz.tubeforge.domain.SourceType.PLAYLIST) {
            if (features.playlists()) {
                rows.add(List.of(callback("🎥 Playlist video", of("pvideo", requestId)),
                        callback("🎵 Playlist audio", of("paudio", requestId))));
            }
        } else {
            if (features.videoDownload() || features.audioDownload()) {
                List<InlineButton> row = new ArrayList<>();
                if (features.videoDownload()) row.add(callback("🎥 Video", of("video", requestId)));
                if (features.audioDownload()) row.add(callback("🎵 Audio", of("audio", requestId)));
                rows.add(row);
            }
            if (features.thumbnails() || features.subtitles()) {
                List<InlineButton> row = new ArrayList<>();
                if (features.thumbnails()) row.add(callback("🖼 Thumbnail", of("thumb", requestId)));
                if (features.subtitles()) row.add(callback("📝 Subtitles", of("subs", requestId)));
                rows.add(row);
            }
            if (features.transcripts() || features.clips()) {
                List<InlineButton> row = new ArrayList<>();
                if (features.transcripts()) row.add(callback("📄 Transcript", of("trans", requestId)));
                if (features.clips()) row.add(callback("✂️ Create clip", of("clip", requestId)));
                rows.add(row);
            }
        }
        if (features.aiComingSoon()) {
            rows.add(List.of(callback("✨ AI Studio", of("ai", requestId)),
                    callback("ℹ️ More info", of("info", requestId))));
        } else {
            rows.add(List.of(callback("ℹ️ More info", of("info", requestId))));
        }
        rows.add(List.of(callback("❌ Close", of("close"))));
        return InlineKeyboard.of(rows);
    }

    public InlineKeyboard videoFormats(String requestId, MediaInfo info) {
        List<List<InlineButton>> rows = new ArrayList<>();
        List<InlineButton> current = new ArrayList<>();
        for (VideoFormatOption format : info.videoFormats()) {
            String size = format.estimatedBytes() > 0 ? " • " + HumanFormat.bytes(format.estimatedBytes()) : "";
            String fps = format.fps() >= 50 ? " • " + format.fps() + "fps" : "";
            current.add(callback(format.height() + "p" + fps + size, of("dlv", requestId, format.height())));
            if (current.size() == 2) {
                rows.add(List.copyOf(current));
                current.clear();
            }
        }
        if (!current.isEmpty()) rows.add(List.copyOf(current));
        rows.add(List.of(callback("⭐ Best quality", of("dlv", requestId, "best")),
                callback("📱 Smallest file", of("dlv", requestId, "small"))));
        rows.add(List.of(callback("🔙 Back", of("back", requestId))));
        return InlineKeyboard.of(rows);
    }

    public InlineKeyboard audioFormats(String requestId) {
        return InlineKeyboard.of(List.of(
                List.of(callback("🎧 MP3", of("audfmt", requestId, "mp3")), callback("🎼 M4A", of("audfmt", requestId, "m4a"))),
                List.of(callback("🔊 WAV", of("audfmt", requestId, "wav")), callback("🎙 OGG", of("audfmt", requestId, "ogg"))),
                List.of(callback("💿 FLAC", of("audfmt", requestId, "flac"))),
                List.of(callback("🔙 Back", of("back", requestId)))
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
}
