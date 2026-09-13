package su.twomc.staffwork.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import su.twomc.staffwork.model.Employee;
import su.twomc.staffwork.model.SessionEndReason;
import su.twomc.staffwork.model.StaffStatus;
import su.twomc.staffwork.model.WorkSession;
import su.twomc.staffwork.repository.StaffRepository;

/**
 * Начало и завершение рабочих сессий. Одновременно у сотрудника может быть только одна активная
 * сессия — попытка открыть вторую отклоняется {@link IllegalStateException} с понятным текстом,
 * который слой команд превращает в сообщение игроку.
 */
public final class SessionService {

    private final StaffRepository repository;
    private final StatusService statusService;
    private final ConcurrentHashMap<UUID, Object> locks = new ConcurrentHashMap<>();

    public SessionService(StaffRepository repository, StatusService statusService) {
        this.repository = repository;
        this.statusService = statusService;
    }

    private Object lockFor(UUID uuid) {
        return locks.computeIfAbsent(uuid, k -> new Object());
    }

    public WorkSession startSession(Employee employee, StaffStatus initialStatus, Instant now, String serverId) {
        synchronized (lockFor(employee.uuid())) {
            if (repository.findActiveSession(employee.uuid()).isPresent()) {
                throw new IllegalStateException("У сотрудника уже есть активная рабочая сессия");
            }
            WorkSession session = repository.openSession(employee.uuid(), now, serverId);
            statusService.changeStatus(employee, initialStatus, now);
            return session;
        }
    }

    public WorkSession stopSession(Employee employee, StaffStatus statusAfterStop, Instant now, SessionEndReason reason) {
        synchronized (lockFor(employee.uuid())) {
            WorkSession active = repository
                    .findActiveSession(employee.uuid())
                    .orElseThrow(() -> new IllegalStateException("У сотрудника нет активной рабочей сессии"));
            repository.findOpenStatusInterval(employee.uuid()).ifPresent(open -> repository.closeStatusInterval(open.id(), now));
            repository.closeSession(active.id(), now, reason);
            employee.setCurrentStatus(statusAfterStop, now);
            repository.saveEmployee(employee);
            repository.openStatusInterval(employee.uuid(), null, statusAfterStop, now);
            return new WorkSession(active.id(), active.employeeUuid(), active.startedAt(), now, reason, active.serverId());
        }
    }

    public Optional<WorkSession> activeSession(UUID employeeUuid) {
        return repository.findActiveSession(employeeUuid);
    }
}
