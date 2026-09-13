package su.twomc.staffwork.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import su.twomc.staffwork.model.Employee;
import su.twomc.staffwork.model.StaffStatus;
import su.twomc.staffwork.model.StatusInterval;
import su.twomc.staffwork.testutil.InMemoryStaffRepository;

class StatusServiceTest {

    private Employee newEmployee(InMemoryStaffRepository repository, Instant now) {
        UUID uuid = UUID.randomUUID();
        Employee employee = new Employee(uuid, "Tester", "trainee", true, now, null, StaffStatus.OFF_DUTY, now);
        repository.saveEmployee(employee);
        repository.openStatusInterval(uuid, null, StaffStatus.OFF_DUTY, now);
        return employee;
    }

    @Test
    void changingStatusClosesPreviousIntervalAndOpensNew() {
        InMemoryStaffRepository repository = new InMemoryStaffRepository();
        Instant now = Instant.parse("2025-01-01T10:00:00Z");
        Employee employee = newEmployee(repository, now);
        StatusService service = new StatusService(repository);

        Instant changeAt = now.plus(java.time.Duration.ofMinutes(30));
        service.changeStatus(employee, StaffStatus.WORKING, changeAt);

        List<StatusInterval> intervals =
                repository.findIntervals(employee.uuid(), now, changeAt.plus(java.time.Duration.ofDays(1)));
        assertEquals(2, intervals.size());
        assertEquals(StaffStatus.OFF_DUTY, intervals.get(0).status());
        assertEquals(changeAt, intervals.get(0).endedAt());
        assertEquals(StaffStatus.WORKING, intervals.get(1).status());
        assertTrue(intervals.get(1).isOpen());
        assertEquals(StaffStatus.WORKING, employee.currentStatus());
    }

    @Test
    void settingSameStatusDoesNotOpenDuplicateInterval() {
        InMemoryStaffRepository repository = new InMemoryStaffRepository();
        Instant now = Instant.parse("2025-01-01T10:00:00Z");
        Employee employee = newEmployee(repository, now);
        StatusService service = new StatusService(repository);

        service.changeStatus(employee, StaffStatus.OFF_DUTY, now.plusSeconds(10));
        service.changeStatus(employee, StaffStatus.OFF_DUTY, now.plusSeconds(20));

        List<StatusInterval> intervals = repository.findAllOpenStatusIntervals();
        assertEquals(1, intervals.size());
    }

    @Test
    void concurrentStatusChangesAreSerializedWithoutLeavingTwoOpenIntervals() throws InterruptedException {
        InMemoryStaffRepository repository = new InMemoryStaffRepository();
        Instant now = Instant.parse("2025-01-01T10:00:00Z");
        Employee employee = newEmployee(repository, now);
        StatusService service = new StatusService(repository);

        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        StaffStatus[] statuses = StaffStatus.values();
        try {
            for (int i = 0; i < threads; i++) {
                StaffStatus target = statuses[i % statuses.length];
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        go.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    service.changeStatus(employee, target, Instant.now());
                });
            }
            ready.await();
            go.countDown();
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }

        // Независимо от порядка гонки — в хранилище должен остаться ровно один открытый интервал.
        List<StatusInterval> open = repository.findAllOpenStatusIntervals();
        assertEquals(1, open.size());
    }
}
