package su.twomc.staffwork.config;

import java.util.Locale;
import java.util.Map;

/** Сопоставление имён цветов Minecraft с legacy-кодами {@code §} — используется PlaceholderAPI-выводом и legacy-фолбэком. */
public final class ColorCodes {

    private static final Map<String, String> LEGACY = Map.ofEntries(
            Map.entry("black", "§0"),
            Map.entry("dark_blue", "§1"),
            Map.entry("dark_green", "§2"),
            Map.entry("dark_aqua", "§3"),
            Map.entry("dark_red", "§4"),
            Map.entry("dark_purple", "§5"),
            Map.entry("gold", "§6"),
            Map.entry("gray", "§7"),
            Map.entry("grey", "§7"),
            Map.entry("dark_gray", "§8"),
            Map.entry("dark_grey", "§8"),
            Map.entry("blue", "§9"),
            Map.entry("green", "§a"),
            Map.entry("aqua", "§b"),
            Map.entry("red", "§c"),
            Map.entry("light_purple", "§d"),
            Map.entry("yellow", "§e"),
            Map.entry("white", "§f"));

    private ColorCodes() {}

    /** Legacy-код цвета (например, {@code §a}) — пустая строка для неизвестного имени. */
    public static String legacy(String colorName) {
        if (colorName == null) {
            return "";
        }
        return LEGACY.getOrDefault(colorName.toLowerCase(Locale.ROOT), "");
    }

    /** MiniMessage-тег цвета (например, {@code <green>}) для потребителей, ожидающих разметку MiniMessage. */
    public static String miniMessageTag(String colorName) {
        if (colorName == null || !LEGACY.containsKey(colorName.toLowerCase(Locale.ROOT))) {
            return "";
        }
        return "<" + colorName.toLowerCase(Locale.ROOT) + ">";
    }
}
