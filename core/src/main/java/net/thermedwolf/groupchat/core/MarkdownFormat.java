package net.thermedwolf.groupchat.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Translates a small Markdown/MiniMessage-style subset into the legacy
 * section-sign codes both platforms understand. Used for admin-authored
 * text (server announcements, join messages).
 *
 * <p>Supported markers: {@code **bold**}, {@code *italic*}, {@code __underline__},
 * {@code ~~strikethrough~~}, {@code ||spoiler|| (obfuscated)}, {@code `code`}.
 * Supported tags: {@code <red>...</red>} and the other 15 colour names, plus
 * {@code <bold> <italic> <underlined> <strikethrough> <obfuscated> <reset>}
 * (short aliases {@code <b> <i> <u> <s>} and {@code <spoiler>} work too).
 * Nesting is handled properly: closing an inner tag resets the formatting and
 * re-applies everything still open, so {@code <red>a <bold>b</bold> c</red>}
 * renders "c" in red but no longer bold.
 *
 * <p>Input is always stripped of raw section signs first, so this can never be
 * used to smuggle formatting codes past sanitisation. Legacy {@code &}-codes in
 * the input are left untouched - {@link ChatFormat#color} translates them at
 * send time as before.
 */
public final class MarkdownFormat {

    private static final char SECTION = '\u00A7';
    private static final int MAX_TAG_LENGTH = 24;

    /** Tag name (lowercase, no brackets) -> legacy code string. */
    private static final Map<String, String> TAGS = Map.ofEntries(
            Map.entry("black", "0"),
            Map.entry("dark_blue", "1"),
            Map.entry("dark_green", "2"),
            Map.entry("dark_aqua", "3"),
            Map.entry("dark_red", "4"),
            Map.entry("dark_purple", "5"),
            Map.entry("gold", "6"),
            Map.entry("gray", "7"),
            Map.entry("grey", "7"),
            Map.entry("dark_gray", "8"),
            Map.entry("dark_grey", "8"),
            Map.entry("blue", "9"),
            Map.entry("green", "a"),
            Map.entry("aqua", "b"),
            Map.entry("cyan", "b"),
            Map.entry("red", "c"),
            Map.entry("light_purple", "d"),
            Map.entry("pink", "d"),
            Map.entry("yellow", "e"),
            Map.entry("white", "f"),
            Map.entry("bold", "l"),
            Map.entry("b", "l"),
            Map.entry("italic", "o"),
            Map.entry("em", "o"),
            Map.entry("i", "o"),
            Map.entry("underlined", "n"),
            Map.entry("underline", "n"),
            Map.entry("u", "n"),
            Map.entry("strikethrough", "m"),
            Map.entry("strike", "m"),
            Map.entry("s", "m"),
            Map.entry("obfuscated", "k"),
            Map.entry("magic", "k"),
            Map.entry("spoiler", "k"),
            Map.entry("code", "7"),
            Map.entry("reset", "r"));

    /** Inline marker -> legacy code string. Longest markers must be tried first. */
    // Note: some markers and tags share a legacy code (`code`/`<code>` and
    // <gray> all emit 7; *italic* and <italic> both emit o), so their open
    // states are indistinguishable and can cross-close each other
    // (e.g. <gray>a `b</gray>` closes the gray early). Impact is limited to
    // admin-authored one-line texts; if that ever matters, give markers
    // distinct sentinel codes resolved to real codes at emit time.
    private static final Map<String, String> MARKERS = Map.of(
            "**", "l",
            "__", "n",
            "~~", "m",
            "||", "k",
            "*", "o",
            "`", "7");

    private static final String[] TWO_CHAR_MARKERS = {"**", "__", "~~", "||"};

    private MarkdownFormat() {
    }

    public static String toMinecraft(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String text = ChatFormat.stripSectionCodes(input);
        StringBuilder out = new StringBuilder(text.length() + 32);
        List<String> open = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);

            if (c == '<') {
                int consumed = consumeTag(text, i, out, open);
                if (consumed > 0) {
                    i += consumed;
                    continue;
                }
            }

            if (i + 1 < text.length()) {
                String two = text.substring(i, i + 2);
                if (isTwoCharMarker(two)) {
                    toggleMarker(two, text, i, 2, out, open);
                    i += 2;
                    continue;
                }
            }

            if (c == '*' || c == '`') {
                String one = String.valueOf(c);
                toggleMarker(one, text, i, 1, out, open);
                i += 1;
                continue;
            }

            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static boolean isTwoCharMarker(String two) {
        for (String marker : TWO_CHAR_MARKERS) {
            if (marker.equals(two)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Opens or closes an inline marker. Closing is always allowed; opening
     * requires a non-whitespace character right after it so prose like
     * "2 * 3 + 4" stays untouched.
     */
    private static void toggleMarker(String marker, String text, int position, int length,
                                     StringBuilder out, List<String> open) {
        String code = MARKERS.get(marker);
        boolean isClose = open.contains(code);
        if (!isClose) {
            int after = position + length;
            if (after >= text.length() || Character.isWhitespace(text.charAt(after))) {
                out.append(text, position, position + length);
                return;
            }
        }
        toggle(out, open, code);
    }

    /**
     * Tries to read {@code <tag>} or {@code </tag>} at {@code start}.
     * Returns the number of characters consumed, or 0 if this is not a known
     * tag (the '<' then falls through as a literal character). Closing tags
     * without a matching open are swallowed silently.
     */
    private static int consumeTag(String text, int start, StringBuilder out, List<String> open) {
        int end = text.indexOf('>', start + 1);
        if (end < 0 || end - start - 1 > MAX_TAG_LENGTH) {
            return 0;
        }
        String inner = text.substring(start + 1, end).toLowerCase(Locale.ROOT);
        boolean closing = inner.startsWith("/");
        if (closing) {
            inner = inner.substring(1);
        }
        String code = TAGS.get(inner);
        if (code == null) {
            return 0;
        }
        if (closing) {
            closeAt(out, open, open.lastIndexOf(code));
        } else {
            open.add(code);
            out.append(SECTION).append(code);
        }
        return end - start + 1;
    }

    private static void toggle(StringBuilder out, List<String> open, String code) {
        int index = open.lastIndexOf(code);
        if (index >= 0) {
            closeAt(out, open, index);
        } else {
            open.add(code);
            out.append(SECTION).append(code);
        }
    }

    /** Closes the code at {@code index}: resets formatting, re-applies what is still open. */
    private static void closeAt(StringBuilder out, List<String> open, int index) {
        if (index < 0) {
            return;
        }
        open.remove(index);
        out.append(SECTION).append('r');
        for (String stillOpen : open) {
            out.append(SECTION).append(stillOpen);
        }
    }
}
