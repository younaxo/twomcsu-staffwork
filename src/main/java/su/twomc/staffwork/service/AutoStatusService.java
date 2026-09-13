package su.twomc.staffwork.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import su.twomc.staffwork.config.AutoStatusSettings;
import su.twomc.staffwork.model.Employee;
import su.twomc.staffwork.model.SessionEndReason;
import su.twomc.staffwork.model.StaffStatus;

/**
 * Автоматическое управление статусом при входе/выходе игрока и определение AFK по простому
 * таймеру бездействия. Активность фиксируется по стандартным действиям игрока (движение, чат,
 * команды, взаимодействие) — без вмешательства в сетевые пакеты, как и требуется для версии 0.1.
 */
public final class AutoStatusService {

    private final StatusService statusService;
    private final SessionService sessionService;
    private volatile AutoStatusSettings settings;
    private final Map<UUID, Instant> lastActivity = new ConcurrentHashMap<>();
    private final Map<UUID, StaffStatus> statusBeforeAfk = new ConcurrentHashMap<>();

    public AutoStatusService(StatusService statusService, SessionService sessionService, AutoStatusSettings settings) {
        this.statusService = statusService;
        this.sessionService = sessionService;
        this.settings = settings;
    }

    /** Применяет новые настройки без пересоздания сервиса — используется при {@code /staffwork reload}. */
    public void updateSettings(AutoStatusSettings settings) {
        this.settings = settings;
    }

    public void onJoin(Employee employee, Instant now, String serverId) {
        lastActivity.put(employee.uuid(), now);
        if (settings.statusOnJoin() != null) {
            statusService.changeStatus(employee, settings.statusOnJoin(), now);
        }
        if (settings.autoStartSessionOnJoin() && sessionService.activeSession(employee.uuid()).isEmpty()) {
            sessionService.startSession(employee, employee.currentStatus(), now, serverId);
        }
    }

    public void onQuit(Employee employee, Instant now) {
        lastActivity.remove(employee.uuid());
        statusBeforeAfk.remove(employee.uuid());
        switch (settings.quitBehavior()) {
            case STOP_SESSION -> {
                if (sessionService.activeSession(employee.uuid()).isPresent()) {
                    sessionService.stopSession(employee, StaffStatus.OFF_DUTY, now, SessionEndReason.LOGOUT);
                }
            }
            case SET_STATUS -> statusService.changeStatus(employee, settings.statusOnQuit(), now);
            case NOTHING -> {
                // Статус и сессия сохраняются как есть — сотрудник считается работающим "удалённо".
            }
        }
    }

    /** Вызывается при любом стандартном действии игрока — сбрасывает таймер AFK и возвращает из AFK при необходимости. */
    public void recordActivity(Employee employee, Instant now) {
        lastActivity.put(employee.uuid(), now);
        if (settings.afkEnabled() && settings.afkAutoReturn() && employee.currentStatus() == StaffStatus.AFK) {
            StaffStatus restore = statusBeforeAfk.getOrDefault(employee.uuid(), StaffStatus.WORKING);
            statusBeforeAfk.remove(employee.uuid());
            statusService.changeStatus(employee, restore, now);
        }
    }

    /** Периодическая проверка бездействия. Возвращает {@code true}, если статус сотрудника был переведён в AFK. */
    public boolean checkAfk(Employee employee, Instant now) {
        if (!settings.afkEnabled() || employee.currentStatus() == StaffStatus.AFK) {
            return false;
        }
        Instant last = lastActivity.get(employee.uuid());
        if (last == null) {
            return false;
        }
        Duration idle = Duration.between(last, now);
        if (idle.compareTo(settings.afkThreshold()) < 0) {
            return false;
        }
        statusBeforeAfk.put(employee.uuid(), employee.currentStatus());
        statusService.changeStatus(employee, StaffStatus.AFK, now);
        return true;
    }

    public boolean isTracked(UUID uuid) {
        return lastActivity.containsKey(uuid);
    }
}
