package su.twomc.staffwork.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Интервал непрерывного пребывания сотрудника в одном статусе. Именно по этим интервалам,
 * а не по постоянно обновляемому счётчику, считается статистика рабочего времени —
 * это позволяет не писать в базу каждую секунду и корректно считать переходы через полночь.
 * {@code endedAt == null} означает, что интервал ещё открыт (статус активен прямо сейчас).
 */
public record StatusInterval(
        long id, UUID employeeUuid, Long sessionId, StaffStatus status, Instant startedAt, Instant endedAt) {

    public boolean isOpen() {
        return endedAt == null;
    }

    /** Конец интервала для целей расчёта: открытый интервал считается длящимся до {@code now}. */
    public Instant effectiveEnd(Instant now) {
        return endedAt == null ? now : endedAt;
    }
}
