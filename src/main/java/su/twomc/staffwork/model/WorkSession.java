package su.twomc.staffwork.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Рабочая сессия — интервал между /start (или авто-входом) и /stop (или выходом/остановкой
 * сервера). {@code id < 0} означает ещё не сохранённую в хранилище сессию.
 */
public record WorkSession(
        long id,
        UUID employeeUuid,
        Instant startedAt,
        Instant endedAt,
        SessionEndReason endReason,
        String serverId) {

    public boolean isActive() {
        return endedAt == null;
    }
}
