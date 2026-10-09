package com.steve.spotifywakeprobe;

import java.util.Locale;

/** English command dialect; independent of travel/location and the offline wake model. */
final class CommandLanguage {
    static final String[] VALUES = {"en-US", "en-AU", "en-GB", "en-NZ", "phone"};
    static final String[] LABELS = {"English (US)", "English (Australia)", "English (UK)",
            "English (New Zealand)", "Use phone language (English)"};

    static String resolve(String selected, Locale phone) {
        if ("phone".equals(selected)) {
            return "en".equals(phone.getLanguage()) ? phone.toLanguageTag() : "en-US";
        }
        for (String value : VALUES) if (!value.equals("phone") && value.equals(selected)) return value;
        return "en-US";
    }
}
