package su.twomc.staffwork.config;

import java.time.Duration;
import su.twomc.staffwork.model.StaffStatus;

/** Настройки автоматического управления статусами при входе/выходе и по бездействию (AFK). */
public record AutoStatusSettings(
        StaffStatus statusOnJoin,
        boolean autoStartSessionOnJoin,
        QuitBehavior quitBehavior,
        StaffStatus statusOnQuit,
        boolean afkEnabled,
        Duration afkThreshold,
        boolean afkAutoReturn) {

    /** Поведение при выходе сотрудника с сервера. */
    public enum QuitBehavior {
        STOP_SESSION,
        SET_STATUS,
        NOTHING
    }
}
