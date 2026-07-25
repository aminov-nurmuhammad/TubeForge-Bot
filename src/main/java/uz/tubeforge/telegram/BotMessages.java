package uz.tubeforge.telegram;

import org.springframework.stereotype.Component;
import uz.tubeforge.domain.AppUser;
import uz.tubeforge.domain.Language;
import uz.tubeforge.domain.MetadataState;
import uz.tubeforge.media.MediaInfo;
import uz.tubeforge.util.Html;
import uz.tubeforge.util.HumanFormat;

@Component
public class BotMessages {
    public String welcome(AppUser user) {
        return switch (user.getLanguage()) {
            case RU -> "<b>Добро пожаловать в TubeForge</b> ⚡\n\nОтправьте ссылку YouTube или публичного Instagram Reel. Я быстро покажу лучшие доступные действия: видео, аудио, обложку и дополнительные инструменты.\n\nВсе инструменты работают прямо через кнопки под сообщением.";
            case UZ -> "<b>TubeForge’ga xush kelibsiz</b> ⚡\n\nYouTube yoki ochiq Instagram Reel havolasini yuboring. Men eng yaxshi video, audio, prevyu va boshqa amallarni tez ko‘rsataman.\n\nBarcha amallar xabar ostidagi tugmalar orqali ishlaydi.";
            default -> "<b>Welcome to TubeForge</b> ⚡\n\nSend a YouTube or public Instagram Reel link. I’ll quickly show the best available video, audio, cover and useful tools.\n\nEverything works through the buttons beneath each message.";
        };
    }

    public String help(Language language) {
        return switch (language) {
            case RU -> "<b>Как пользоваться TubeForge</b>\n\n1. Отправьте ссылку YouTube или публичного Instagram Reel.\n2. Нажмите быстрое действие под превью — для Reel первая кнопка сразу выбирает максимум качества.\n3. При необходимости выберите точный формат.\n4. Получите файл; повторные запросы используют кэш.\n\nКоманды: /jobs — обработка, /history — ссылки, /settings — настройки.";
            case UZ -> "<b>TubeForge’dan foydalanish</b>\n\n1. YouTube yoki ochiq Instagram Reel havolasini yuboring.\n2. Prevyu ostidagi tezkor amalni bosing — Reel uchun birinchi tugma eng yuqori sifatni tanlaydi.\n3. Kerak bo‘lsa aniq formatni tanlang.\n4. Faylni oling; takroriy so‘rovlar keshdan foydalanadi.\n\nBuyruqlar: /jobs, /history, /settings.";
            default -> "<b>How to use TubeForge</b>\n\n1. Send a YouTube or public Instagram Reel link.\n2. Tap the quick action beneath the preview — Reel’s first button requests the best available quality.\n3. Choose an exact format when needed.\n4. Receive the file; repeated requests use the shared cache.\n\nUse /jobs for progress, /history for recent links and /settings for preferences.";
        };
    }

    public String terms() {
        return "<b>Terms of use</b>\n\nTubeForge is for media you own, public-domain or Creative Commons media, and other content you are authorized to download or process. Do not use it to infringe copyright, bypass access controls, or obtain private, paid, DRM-protected or members-only content.\n\nBy continuing, you confirm that you have the necessary rights and will follow YouTube’s terms and applicable law.";
    }

    public String privacy() {
        return "<b>Privacy</b> 🔐\n\nTubeForge stores your Telegram ID, preferences, link history and job status. Media files are temporary and are automatically removed after the configured retention period. Bot tokens and administrator settings stay on your server.\n\nUse /history to review recent items. The owner can remove the local database and storage directory at any time.";
    }

    public String inspecting() {
        return "🔎 <b>Inspecting your media link…</b>\n\nChecking formats, audio, thumbnails and subtitles.";
    }

    public String preview(MediaInfo info) {
        String type = switch (info.sourceType()) {
            case SHORT -> "YouTube Short";
            case PLAYLIST -> "YouTube Playlist";
            case LIVE -> "Livestream";
            case INSTAGRAM_REEL -> "Instagram Reel";
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

    public String preview(MediaInfo info, MetadataState state) {
        if (state == MetadataState.READY) return preview(info);
        String type = switch (info.sourceType()) {
            case SHORT -> "YouTube Short";
            case PLAYLIST -> "YouTube Playlist";
            case INSTAGRAM_REEL -> "Instagram Reel";
            default -> "YouTube Video";
        };
        String status = state == MetadataState.PENDING
                ? "⏳ <b>Advanced details are loading in the background.</b>"
                : "⚠️ <b>Advanced details are temporarily unavailable.</b>";
        String next = state == MetadataState.PENDING
                ? "One-tap video and audio are already ready to start."
                : "Quick video, audio and thumbnail actions still work. You can retry the advanced analysis.";
        return "⚡ <b>Link accepted instantly</b>\n\n"
                + "🆔 <code>" + Html.escape(info.id()) + "</code>\n"
                + "🔗 <b>Type:</b> " + type + "\n\n"
                + status + "\n" + next;
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
        return "🧠 <b>Transcript Studio</b>\n\nTurn real subtitles into a concise summary, timestamped chapters or structured study notes.\n\nThe free built-in engine performs fast local extractive analysis. If the owner enables Ollama, the same tools automatically use a real local LLM.";
    }
}
