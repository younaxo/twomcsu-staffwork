package su.twomc.staffwork.model;

import java.util.Locale;
import java.util.Optional;

/**
 * Рабочий статус сотрудника. Набор статусов фиксирован в версии 0.1 — то, засчитывается ли
 * конкретный статус в рабочее время, задаётся конфигурацией (см. {@code work-time.counted-statuses}),
 * а {@link #countsAsWorkByDefault} лишь описывает поведение "из коробки".
 */
public enum StaffStatus {
    WORKING(true),
    AFK(false),
    OFF_DUTY(false),
    BREAK(false),
    MEETING(true),
    TRAINING(true);

    private final boolean countsAsWorkByDefault;

    StaffStatus(boolean countsAsWorkByDefault) {
        this.countsAsWorkByDefault = countsAsWorkByDefault;
    }

    public boolean countsAsWorkByDefault() {
        return countsAsWorkByDefault;
    }

    public static Optional<StaffStatus> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(StaffStatus.valueOf(id.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
