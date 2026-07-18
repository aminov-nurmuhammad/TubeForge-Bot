package uz.tubeforge.domain;

import java.util.Locale;

public enum Language {
    EN("English", "🇬🇧"),
    RU("Русский", "🇷🇺"),
    UZ("O‘zbekcha", "🇺🇿");

    private final String displayName;
    private final String flag;

    Language(String displayName, String flag) {
        this.displayName = displayName;
        this.flag = flag;
    }

    public String label() {
        return flag + " " + displayName;
    }

    public static Language fromTelegram(String value) {
        if (value == null) return EN;
        String language = value.toLowerCase(Locale.ROOT);
        if (language.startsWith("ru")) return RU;
        if (language.startsWith("uz")) return UZ;
        return EN;
    }
}
