package su.twomc.staffwork.config;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Упрощённый транслятор небольшого подмножества тегов MiniMessage в legacy-коды {@code §} —
 * используется только на серверах, где нет нативного Adventure (обычный Spigot без Paper).
 * На Paper/Folia сообщения рендерятся полноценным MiniMessage без этого класса.
 *
 * <p>Ограничение: закрывающие теги (`</...>`) сбрасывают все стили целиком, а не только тег,
 * который закрывают — для честного legacy-фолбэка этого достаточно, полноценный стек стилей
 * MiniMessage поддерживается только на Paper.
 */
final class LegacyMiniMessageConverter {

    private static final Pattern TAG = Pattern.compile("</?([a-zA-Z_]+)>");

    private static final Map<String, String> FORMATTING_CODES = Map.of(
            "obfuscated", "§k",
            "bold", "§l",
            "strikethrough", "§m",
            "underlined", "§n",
            "italic", "§o",
            "reset", "§r");

    String toLegacy(String miniMessage) {
        Matcher matcher = TAG.matcher(miniMessage);
        StringBuilder result = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            result.append(miniMessage, last, matcher.start());
            boolean closing = matcher.group(0).startsWith("</");
            String tag = matcher.group(1).toLowerCase(Locale.ROOT);
            if (closing) {
                result.append(FORMATTING_CODES.get("reset"));
            } else {
                String color = ColorCodes.legacy(tag);
                result.append(!color.isEmpty() ? color : FORMATTING_CODES.getOrDefault(tag, ""));
            }
            last = matcher.end();
        }
        result.append(miniMessage.substring(last));
        return result.toString();
    }
}
