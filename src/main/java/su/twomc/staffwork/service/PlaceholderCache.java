package su.twomc.staffwork.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import su.twomc.staffwork.model.Employee;
import su.twomc.staffwork.model.StatsPeriod;

/**
 * Кэш посчитанной статистики для PlaceholderAPI. PlaceholderAPI запрашивает значения синхронно
 * и не умеет ждать асинхронный результат, поэтому обращаться к хранилищу прямо в момент запроса
 * плейсхолдера нельзя — вместо этого значения периодически пересчитываются в асинхронном потоке
 * (см. {@link su.twomc.staffwork.scheduler.PlatformScheduler#runAsyncTimer}) и читаются отсюда
 * без какого-либо обращения к диску/сети.
 */
public final class PlaceholderCache {

    public record Snapshot(
            Employee employee, Duration today, Duration week, Duration month, Duration all, Duration currentSession) {}

    private final EmployeeService employeeService;
    private final SessionService sessionService;
    private final StatisticsService statisticsService;
    private final Map<UUID, Snapshot> snapshots = new ConcurrentHashMap<>();

    public PlaceholderCache(EmployeeService employeeService, SessionService sessionService, StatisticsService statisticsService) {
        this.employeeService = employeeService;
        this.sessionService = sessionService;
        this.statisticsService = statisticsService;
    }

    public void refresh(Collection<UUID> onlineUuids) {
        Instant now = Instant.now();
        for (UUID uuid : onlineUuids) {
            employeeService
                    .findEmployee(uuid)
                    .ifPresentOrElse(employee -> snapshots.put(uuid, buildSnapshot(employee, now)), () -> snapshots.remove(uuid));
        }
        snapshots.keySet().retainAll(onlineUuids);
    }

    private Snapshot buildSnapshot(Employee employee, Instant now) {
        Duration today = statisticsService.calculate(employee.uuid(), StatsPeriod.TODAY, employee.addedAt(), now);
        Duration week = statisticsService.calculate(employee.uuid(), StatsPeriod.WEEK, employee.addedAt(), now);
        Duration month = statisticsService.calculate(employee.uuid(), StatsPeriod.MONTH, employee.addedAt(), now);
        Duration all = statisticsService.calculate(employee.uuid(), StatsPeriod.ALL, employee.addedAt(), now);
        Duration currentSession = sessionService
                .activeSession(employee.uuid())
                .map(session -> Duration.between(session.startedAt(), now))
                .orElse(Duration.ZERO);
        return new Snapshot(employee, today, week, month, all, currentSession);
    }

    public Optional<Snapshot> get(UUID uuid) {
        return Optional.ofNullable(snapshots.get(uuid));
    }
}
