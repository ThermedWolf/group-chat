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
}
