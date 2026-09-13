package su.twomc.staffwork.service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import su.twomc.staffwork.model.Employee;
import su.twomc.staffwork.model.StaffStatus;
import su.twomc.staffwork.model.WorkSession;
import su.twomc.staffwork.repository.StaffRepository;

/**
 * Переходы между рабочими статусами. Каждое изменение закрывает предыдущий открытый интервал
 * статуса и открывает новый — так расчёт времени в {@link StatisticsService} всегда работает
 * с завершёнными (кроме последнего) интервалами.
 *
 * <p>Смена статуса одного сотрудника сериализуется по его UUID: это защищает от гонки, когда,
 * например, игрок и администратор одновременно меняют статус — второй вызов дождётся первого
 * и применится поверх его результата, а не перезапишет промежуточное состояние вслепую.
 */
public final class StatusService {

    private final StaffRepository repository;
    private final ConcurrentHashMap<UUID, Object> locks = new ConcurrentHashMap<>();

    public StatusService(StaffRepository repository) {
        this.repository = repository;
    }

    private Object lockFor(UUID uuid) {
        return locks.computeIfAbsent(uuid, k -> new Object());
    }

    /** Меняет статус сотрудника, закрывая предыдущий интервал и открывая новый, привязанный к активной сессии (если есть). */
    public void changeStatus(Employee employee, StaffStatus newStatus, Instant now) {
        synchronized (lockFor(employee.uuid())) {
            boolean alreadyInStatus = repository
                    .findOpenStatusInterval(employee.uuid())
                    .map(open -> {
                        if (open.status() == newStatus) {
                            return true;
                        }
                        repository.closeStatusInterval(open.id(), now);
                        return false;
                    })
                    .orElse(false);
            if (!alreadyInStatus) {
                Long sessionId =
                        repository.findActiveSession(employee.uuid()).map(WorkSession::id).orElse(null);
                repository.openStatusInterval(employee.uuid(), sessionId, newStatus, now);
            }
            employee.setCurrentStatus(newStatus, now);
            repository.saveEmployee(employee);
        }
    }
}
