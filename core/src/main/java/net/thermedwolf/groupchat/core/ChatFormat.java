package net.thermedwolf.groupchat.core;

/** Translates '&' colour codes to the section-sign codes both platforms understand. */
public final class ChatFormat {

    private ChatFormat() {
    }

    public static String color(String input) {
        if (input == null) {
            return null;
        }
        return input.replace('&', '\u00A7');
    }

    /**
     * Strip raw section-sign codes that players may have pasted into input.
     * We keep '&' translation intentional, but reject bypass via '§'.
     */
    public static String stripSectionCodes(String input) {
        if (input == null) {
            return null;
        }
        return input.replace('\u00A7', '?');
    }

    /**
     * Sanitize user-controlled free text (chat messages, etc.) — strip § to prevent
     * colour spoofing bypass while preserving all other content.
     */
    public static String sanitizeUserText(String input) {
        return stripSectionCodes(input);
    }
}
