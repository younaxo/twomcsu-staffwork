package su.twomc.staffwork.model;

/** Причина завершения рабочей сессии — используется в логах и статистике. */
public enum SessionEndReason {
    MANUAL_STOP,
    LOGOUT,
    SERVER_SHUTDOWN,
    CRASH_RECOVERED,
    ADMIN_ACTION
}
