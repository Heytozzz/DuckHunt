package dev.heytozzz.duckhunt.lang;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Pads a chat message with leading spaces so it renders roughly centered
 * in the default Minecraft chat box, based on each character's actual
 * pixel width in the client's default (non-bold) font. This is a visual
 * approximation, not pixel-perfect — bold/italic formatting and unusual
 * characters aren't accounted for — but it's close enough for headers
 * and short announcement lines.
 */
public final class ChatCenter {

    // Total usable width (in pixels) of the default chat box at 100% GUI
    // scale. This is the same constant widely used by chat-centering
    // utilities across the Bukkit ecosystem.
    private static final int CHAT_BOX_WIDTH_PX = 320;
    private static final int DEFAULT_CHAR_WIDTH_PX = 6;
    private static final int SPACE_WIDTH_PX = 4;

    private static final Map<Character, Integer> CHAR_WIDTHS = new HashMap<>();

    static {
        put(2, 'i', 'l', '.', ',', ':', ';', '|', '!', '\'');
        put(3, '`', 't', 'I', '[', ']');
        put(4, 'f', 'k', '"', '(', ')', '{', '}', '<', '>', 'j', ' ');
        put(5, '*', 'r');
        put(6, 'a', 'b', 'c', 'd', 'e', 'g', 'h', 'n', 'o', 'p', 'q', 's', 'u', 'v', 'w', 'x', 'y', 'z',
                'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S',
                'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                '@', '#', '$', '%', '^', '&', '-', '_', '=', '+', '?', '/', '\\', '~');
    }

    private static void put(int width, char... chars) {
        for (char c : chars) {
            CHAR_WIDTHS.put(c, width);
        }
    }

    private ChatCenter() {
    }

    /**
     * Returns a copy of {@code message} with enough leading spaces
     * prepended to roughly center it in the chat box. If the message is
     * already as wide as (or wider than) the chat box, it's returned
     * unchanged.
     */
    public static Component center(Component message) {
        String plain = PlainTextComponentSerializer.plainText().serialize(message);
        int widthPx = widthOf(plain);
        int paddingPx = (CHAT_BOX_WIDTH_PX - widthPx) / 2;
        if (paddingPx <= 0) {
            return message;
        }

        int spaces = paddingPx / SPACE_WIDTH_PX;
        if (spaces <= 0) {
            return message;
        }
        return Component.text(" ".repeat(spaces)).append(message);
    }

    private static int widthOf(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            width += CHAR_WIDTHS.getOrDefault(c, DEFAULT_CHAR_WIDTH_PX);
            width += 1; // 1px of default kerning between characters
        }
        return Math.max(0, width - (text.isEmpty() ? 0 : 1));
    }
}
