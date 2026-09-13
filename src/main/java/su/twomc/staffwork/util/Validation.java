package su.twomc.staffwork.util;

import java.util.Locale;
import java.util.regex.Pattern;

/** Проверка и нормализация значений, приходящих из аргументов команд и конфигурации. */
public final class Validation {

    private static final Pattern RANK_ID_PATTERN = Pattern.compile("^[a-z0-9_-]{1,32}$");
    private static final Pattern PLAYER_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
    private static final Pattern PERMISSION_PATTERN =
            Pattern.compile("^tmc\\.staffwork(\\.[a-z0-9]+)+$|^tmc\\.staffwork\\.\\*$");

    private Validation() {}

    /** Приводит идентификатор ранга к нижнему регистру и проверяет допустимый набор символов. */
    public static String normalizeRankId(String rawId) {
        if (rawId == null) {
            throw new IllegalArgumentException("Идентификатор ранга не может быть пустым");
        }
        String normalized = rawId.trim().toLowerCase(Locale.ROOT);
        if (!RANK_ID_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "Идентификатор ранга должен содержать только латиницу, цифры, '-' и '_' (до 32 символов)");
        }
        return normalized;
    }

    public static boolean isValidRankId(String rawId) {
        return rawId != null && RANK_ID_PATTERN.matcher(rawId.trim().toLowerCase(Locale.ROOT)).matches();
    }

    public static boolean isValidPlayerName(String name) {
        return name != null && PLAYER_NAME_PATTERN.matcher(name).matches();
    }

    /** Проверка формата permission-узла: {@code tmc.staffwork.<категория>[.<...>]}. */
    public static boolean isValidPermissionNode(String node) {
        return node != null && PERMISSION_PATTERN.matcher(node).matches();
    }

    /** Обрезает пользовательский ввод до безопасной длины, чтобы не переполнять хранилище/UI. */
    public static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /**
     * Убирает символы {@code <} и {@code >} из значения перед подстановкой в шаблон сообщения.
     * Плейсхолдеры сообщений подставляются простой заменой строк до разбора MiniMessage/legacy-тегов,
     * поэтому непроверенный ввод игрока (например, введённое имя или название статуса) мог бы
     * сформировать собственный тег и повлиять на форматирование — это защита именно от этого.
     */
    public static String stripMarkup(String value) {
        if (value == null) {
            return null;
        }
        return value.replace('<', '‹').replace('>', '›');
    }
}
