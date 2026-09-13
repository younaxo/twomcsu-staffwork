package su.twomc.staffwork.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import su.twomc.staffwork.model.Employee;
import su.twomc.staffwork.model.StaffStatus;
import su.twomc.staffwork.testutil.InMemoryStaffRepository;

/** Логика, лежащая в основе плейсхолдеров PlaceholderAPI (см. TmcStaffPlaceholderExpansion). */
class PlaceholderCacheTest {

    @Test
    void refreshComputesSnapshotForOnlineEmployeeAndDropsOffline() {
        // PlaceholderCache.refresh считает "сегодня" от реального Instant.now(), поэтому интервал
        // здесь тоже привязан к реальному времени, а не к произвольной фиксированной дате.
        InMemoryStaffRepository repository = new InMemoryStaffRepository();
        Instant realNow = Instant.now();
        UUID uuid = UUID.randomUUID();
        Instant addedAt = realNow.minus(Duration.ofDays(1));
        Employee employee = new Employee(uuid, "Tester", "trainee", true, addedAt, null, StaffStatus.WORKING, addedAt);
        repository.saveEmployee(employee);
        repository.openStatusInterval(uuid, null, StaffStatus.WORKING, realNow.minus(Duration.ofMinutes(2)));

        EmployeeService employeeService = new EmployeeService(repository);
        StatusService statusService = new StatusService(repository);
        SessionService sessionService = new SessionService(repository, statusService);
        StatisticsService statisticsService =
                new StatisticsService(repository, EnumSet.of(StaffStatus.WORKING), ZoneOffset.UTC);
        PlaceholderCache cache = new PlaceholderCache(employeeService, sessionService, statisticsService);

        cache.refresh(List.of(uuid));
        PlaceholderCache.Snapshot snapshot = cache.get(uuid).orElseThrow();
        assertTrue(
                !snapshot.today().isNegative() && snapshot.today().compareTo(Duration.ofMinutes(3)) <= 0,
                "Ожидалось около 2 минут отработанного времени, получено: " + snapshot.today());
        assertTrue(cache.get(UUID.randomUUID()).isEmpty());

        cache.refresh(List.of());
        assertTrue(cache.get(uuid).isEmpty(), "После выхода сотрудника offline его снимок должен удаляться из кэша");
    }
}
