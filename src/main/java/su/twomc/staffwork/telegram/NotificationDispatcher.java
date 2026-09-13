package su.twomc.staffwork.telegram;

import java.util.Map;
import su.twomc.staffwork.config.TelegramSettings;
import su.twomc.staffwork.model.Employee;

/**
 * Связывает доменные события (вход/выход, начало/конец рабочей сессии) с отправкой уведомлений
 * в Telegram. Если интеграция выключена, все методы — no-op, поэтому вызывающему коду
 * (слушателям, сервисам) не нужно самим проверять, включён ли Telegram.
 */
public final class NotificationDispatcher {

    private volatile TelegramSettings settings;
    private final TelegramManager telegramManager;

    public NotificationDispatcher(TelegramSettings settings, TelegramManager telegramManager) {
        this.settings = settings;
        this.telegramManager = telegramManager;
    }

    /** Применяет новые шаблоны/список включённых событий без пересоздания диспетчера при {@code /staffwork reload}. */
    public void updateSettings(TelegramSettings settings) {
        this.settings = settings;
    }

    public void notifyLogin(Employee employee) {
        dispatch("login", employee, Map.of("player", employee.lastKnownName()));
    }

    public void notifyLogout(Employee employee) {
        dispatch("logout", employee, Map.of("player", employee.lastKnownName()));
    }

    public void notifySessionStart(Employee employee) {
        dispatch("session-start", employee, Map.of("player", employee.lastKnownName()));
    }

    public void notifySessionStop(Employee employee, String duration) {
        dispatch(
                "session-stop", employee, Map.of("player", employee.lastKnownName(), "duration", duration));
    }

    private void dispatch(String eventKey, Employee employee, Map<String, String> placeholders) {
        if (!settings.enabled() || telegramManager == null || !settings.isEventEnabled(eventKey)) {
            return;
        }
        telegramManager
                .linkedChatId(employee.uuid())
                .ifPresent(chatId -> telegramManager.notifyChat(chatId, format(eventKey, placeholders)));
    }

    private String format(String eventKey, Map<String, String> placeholders) {
        String template = settings.messageTemplates().getOrDefault(eventKey, "{player}: " + eventKey);
        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}
