package su.twomc.staffwork.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import su.twomc.staffwork.model.Employee;
import su.twomc.staffwork.model.SessionEndReason;
import su.twomc.staffwork.model.StaffStatus;
import su.twomc.staffwork.model.StatusInterval;
import su.twomc.staffwork.model.WorkSession;
import su.twomc.staffwork.repository.StaffRepository;

/**
 * Восстанавливает состояние после аварийной остановки сервера (когда {@code onDisable} не успел
 * выполниться и не закрыл сессии сам). Без этого сотрудник навсегда "застревал" бы с активной
 * сессией и не смог бы начать новую после перезапуска.
 *
 * <p>Момент завершения сессии оценивается по последнему сохранённому heartbeat этого сервера,
 * а не по текущему времени — иначе простой между крашем и перезапуском засчитался бы как работа.
 * Если heartbeat не найден (первый запуск), используется время начала сессии — это занижает,
 * а не завышает рабочее время, что безопаснее для сотрудника.
 */
public final class RecoveryService {

    private final StaffRepository repository;
    private final Logger logger;

    public RecoveryService(StaffRepository repository, Logger logger) {
        this.repository = repository;
        this.logger = logger;
    }

    public void recoverCrashedSessions(String serverId, StaffStatus statusAfterRecovery) {
        Instant recoveryPoint = repository.findHeartbeat(serverId).orElse(null);
        List<WorkSession> active = repository.findAllActiveSessions();
        int recovered = 0;
        for (WorkSession session : active) {
            if (!serverId.equals(session.serverId())) {
                continue; // сессии других серверов сети сейчас могут быть активны легитимно
            }
            Instant endedAt = recoveryPoint != null && recoveryPoint.isAfter(session.startedAt())
                    ? recoveryPoint
                    : session.startedAt();
            repository.closeSession(session.id(), endedAt, SessionEndReason.CRASH_RECOVERED);

            Optional<StatusInterval> openInterval = repository.findOpenStatusInterval(session.employeeUuid());
            openInterval
                    .filter(interval -> session.id() == (interval.sessionId() == null ? -1 : interval.sessionId()))
                    .ifPresent(interval -> repository.closeStatusInterval(interval.id(), endedAt));

            repository
                    .findEmployee(session.employeeUuid())
                    .ifPresent(employee -> resetEmployeeStatus(employee, statusAfterRecovery, endedAt));
            recovered++;
        }
        if (recovered > 0) {
            logger.warning("Обнаружено и восстановлено " + recovered
                    + " незавершённых рабочих сессий этого сервера — вероятно, произошла аварийная остановка.");
        }
    }

    /**
     * Корректно закрывает все активные сессии этого сервера при штатной остановке ({@code onDisable}).
     * Выполняется синхронно и блокирующе — это ожидаемо и безопасно на этом этапе жизненного цикла:
     * операция короткая, а откладывать её в асинхронный поток нельзя, так как classloader плагина
     * может быть выгружен раньше, чем такая задача успеет выполниться.
     */
    public void closeAllForShutdown(String serverId, StaffStatus statusAfterStop) {
        Instant now = Instant.now();
        for (WorkSession session : repository.findAllActiveSessions()) {
            if (!serverId.equals(session.serverId())) {
                continue;
            }
            repository
                    .findOpenStatusInterval(session.employeeUuid())
                    .filter(interval -> session.id() == (interval.sessionId() == null ? -1 : interval.sessionId()))
                    .ifPresent(interval -> repository.closeStatusInterval(interval.id(), now));
            repository.closeSession(session.id(), now, SessionEndReason.SERVER_SHUTDOWN);
            repository.findEmployee(session.employeeUuid()).ifPresent(employee -> resetEmployeeStatus(employee, statusAfterStop, now));
        }
        repository.saveHeartbeat(serverId, now);
    }

    private void resetEmployeeStatus(Employee employee, StaffStatus status, Instant since) {
        employee.setCurrentStatus(status, since);
        repository.saveEmployee(employee);
        repository.openStatusInterval(employee.uuid(), null, status, since);
    }
}
