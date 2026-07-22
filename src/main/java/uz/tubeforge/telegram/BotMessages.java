package uz.tubeforge.telegram;

import org.springframework.stereotype.Component;
import uz.tubeforge.domain.AppUser;
import uz.tubeforge.domain.Language;
import uz.tubeforge.media.MediaInfo;
import uz.tubeforge.util.Html;
import uz.tubeforge.util.HumanFormat;

@Component
public class BotMessages {
    public String welcome(AppUser user) {
        return switch (user.getLanguage()) {
            case RU -> "<b>Добро пожаловать в TubeForge</b> ⚡\n\nОтправьте ссылку YouTube. Я проанализирую её и покажу доступные видео, аудио, превью, субтитры, расшифровки и клипы.\n\nВсе инструменты работают прямо через кнопки под сообщением.";
            case UZ -> "<b>TubeForge’ga xush kelibsiz</b> ⚡\n\nYouTube havolasini yuboring. Men uni tekshirib, video, audio, prevyu, subtitr, transkript va klip variantlarini ko‘rsataman.\n\nBarcha amallar xabar ostidagi tugmalar orqali ishlaydi.";
            default -> "<b>Welcome to TubeForge</b> ⚡\n\nSend a YouTube link. I’ll inspect it and show the available video, audio, thumbnail, subtitle, transcript and clip tools.\n\nEverything works through the buttons beneath each message.";
        };
    }

    public String help(Language language) {
        return switch (language) {
            case RU -> "<b>Как пользоваться TubeForge</b>\n\n1. Отправьте ссылку YouTube.\n2. Выберите действие кнопкой под сообщением.\n3. Выберите качество или формат.\n4. Дождитесь обработки и получите файл.\n\nПоддерживаются обычные видео, Shorts и публичные плейлисты. Команда /jobs показывает обработку, /history — недавние ссылки, /settings — настройки.";
            case UZ -> "<b>TubeForge’dan foydalanish</b>\n\n1. YouTube havolasini yuboring.\n2. Xabar ostidagi amalni tanlang.\n3. Sifat yoki formatni tanlang.\n4. Tayyor faylni kuting.\n\nOddiy videolar, Shorts va ochiq pleylistlar qo‘llanadi. /jobs — jarayonlar, /history — tarix, /settings — sozlamalar.";
            default -> "<b>How to use TubeForge</b>\n\n1. Send a YouTube link.\n2. Choose an action beneath the preview.\n3. Select a quality or format.\n4. Wait for processing and receive the result.\n\nNormal videos, Shorts and public playlists are supported. Use /jobs for progress, /history for recent links and /settings for preferences.";
        };
    }

    public String terms() {
        return "<b>Terms of use</b>\n\nTubeForge is for media you own, public-domain or Creative Commons media, and other content you are authorized to download or process. Do not use it to infringe copyright, bypass access controls, or obtain private, paid, DRM-protected or members-only content.\n\nBy continuing, you confirm that you have the necessary rights and will follow YouTube’s terms and applicable law.";
    }

    public String privacy() {
        return "<b>Privacy</b> 🔐\n\nTubeForge stores your Telegram ID, preferences, link history and job status. Media files are temporary and are automatically removed after the configured retention period. Bot tokens and administrator settings stay on your server.\n\nUse /history to review recent items. The owner can remove the local database and storage directory at any time.";
    }

    public String inspecting() {
        return "🔎 <b>Inspecting your YouTube link…</b>\n\nChecking formats, audio, thumbnails and subtitles.";
    }

    public String preview(MediaInfo info) {
        String type = switch (info.sourceType()) {
            case SHORT -> "YouTube Short";
            case PLAYLIST -> "YouTube Playlist";
            case LIVE -> "Livestream";
            default -> "YouTube Video";
        };
        String extra = switch (info.sourceType()) {
            case PLAYLIST -> "\n📚 <b>Items:</b> " + info.playlistCount();
            case LIVE -> "\n🔴 <b>Status:</b> Live stream";
            default -> "\n⏱ <b>Duration:</b> " + HumanFormat.duration(info.durationSeconds());
        };
        return "🎬 <b>" + Html.escape(info.title()) + "</b>\n\n"
                + "📺 <b>Channel:</b> " + Html.escape(info.channel())
                + extra
                + (info.viewCount() > 0 ? "\n👁 <b>Views:</b> " + HumanFormat.number(info.viewCount()) : "")
                + "\n🔗 <b>Type:</b> " + type
                + "\n\nChoose a one-tap result or open advanced tools:";
    }

    public String settings(AppUser user) {
        return "⚙️ <b>Settings</b>\n\n"
                + "🌐 <b>Language:</b> " + user.getLanguage().label() + "\n"
                + "🎥 <b>Default video:</b> " + user.getDefaultVideoQuality() + "p\n"
                + "🎵 <b>Default audio:</b> " + user.getDefaultAudioFormat() + "\n"
                + "📎 <b>Send video as:</b> " + (user.isSendAsDocument() ? "Document" : "Playable video") + "\n"
                + "📦 <b>Auto-compress:</b> " + (user.isAutoCompress() ? "On" : "Off");
    }

    public String processing(String label, int progress, String detail) {
        int blocks = Math.max(0, Math.min(10, progress / 10));
        String bar = "█".repeat(blocks) + "░".repeat(10 - blocks);
        return "⚙️ <b>" + Html.escape(label) + "</b>\n\n<code>" + bar + "</code> " + progress + "%"
                + (detail == null || detail.isBlank() ? "" : "\n" + Html.escape(detail))
                + "\n\nYou can cancel while processing.";
    }

    public String aiStudio() {
        return "✨ <b>TubeForge AI Studio</b>\n\nCreate a smart summary, timestamped chapters, key moments or study notes from the video's subtitles.\n\nThe built-in local engine is free and always available. Owners can optionally connect Ollama for deeper LLM analysis.";
    }
}
