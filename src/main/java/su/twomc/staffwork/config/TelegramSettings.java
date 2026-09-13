package su.twomc.staffwork.config;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * Настройки интеграции с Telegram. По умолчанию интеграция выключена — {@code botToken} не
 * задан, и {@link su.twomc.staffwork.telegram.TelegramManager} не запускается вовсе, чтобы
 * отсутствие/некорректность токена не мешало старту плагина.
 */
public record TelegramSettings(
        boolean enabled,
        String botToken,
        int pollIntervalSeconds,
        Duration linkCodeTtl,
        int rateLimitMaxAttempts,
        Duration rateLimitWindow,
        Set<String> enabledEvents,
        Map<String, String> messageTemplates) {

    public boolean isEventEnabled(String event) {
        return enabledEvents.contains(event);
    }
}
