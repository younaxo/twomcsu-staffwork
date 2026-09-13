package su.twomc.staffwork.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import su.twomc.staffwork.model.Employee;
import su.twomc.staffwork.model.StaffStatus;
import su.twomc.staffwork.model.WorkSession;
import su.twomc.staffwork.testutil.InMemoryStaffRepository;

class RecoveryServiceTest {

    private static final Logger LOGGER = Logger.getLogger(RecoveryServiceTest.class.getName());

    @Test
    void crashedSessionIsClosedAtLastHeartbeatNotAtRestartTime() {
        InMemoryStaffRepository repository = new InMemoryStaffRepository();
        Instant startedAt = Instant.parse("2025-01-01T10:00:00Z");
        Instant lastHeartbeat = Instant.parse("2025-01-01T10:30:00Z");
        Instant restartTime = Instant.parse("2025-01-01T14:00:00Z"); // сервер простаивал 3.5 часа

        UUID uuid = UUID.randomUUID();
        Employee employee = new Employee(uuid, "Tester", "trainee", true, startedAt, null, StaffStatus.WORKING, startedAt);
        repository.saveEmployee(employee);
        WorkSession session = repository.openSession(uuid, startedAt, "server1");
        repository.openStatusInterval(uuid, session.id(), StaffStatus.WORKING, startedAt);
        repository.saveHeartbeat("server1", lastHeartbeat);

        // Симулируем "перезапуск после краша": сервис создаётся заново поверх того же хранилища.
        new RecoveryService(repository, LOGGER).recoverCrashedSessions("server1", StaffStatus.OFF_DUTY);

        assertTrue(repository.findActiveSession(uuid).isEmpty());
        assertTrue(repository.findAllActiveSessions().isEmpty());

        Employee reloaded = repository.findEmployee(uuid).orElseThrow();
        assertEquals(StaffStatus.OFF_DUTY, reloaded.currentStatus());
        assertEquals(lastHeartbeat, reloaded.currentStatusSince());
    }

    @Test
    void sessionsOfOtherServersAreNotTouched() {
        InMemoryStaffRepository repository = new InMemoryStaffRepository();
        Instant now = Instant.parse("2025-01-01T10:00:00Z");
        UUID uuid = UUID.randomUUID();
        Employee employee = new Employee(uuid, "Tester", "trainee", true, now, null, StaffStatus.WORKING, now);
        repository.saveEmployee(employee);
        repository.openSession(uuid, now, "server-2");

        new RecoveryService(repository, LOGGER).recoverCrashedSessions("server-1", StaffStatus.OFF_DUTY);

        assertTrue(repository.findActiveSession(uuid).isPresent());
    }

    @Test
    void gracefulShutdownClosesSessionAtShutdownTime() {
        InMemoryStaffRepository repository = new InMemoryStaffRepository();
        Instant startedAt = Instant.parse("2025-01-01T10:00:00Z");
        UUID uuid = UUID.randomUUID();
        Employee employee = new Employee(uuid, "Tester", "trainee", true, startedAt, null, StaffStatus.WORKING, startedAt);
        repository.saveEmployee(employee);
        WorkSession session = repository.openSession(uuid, startedAt, "server1");
        repository.openStatusInterval(uuid, session.id(), StaffStatus.WORKING, startedAt);

        new RecoveryService(repository, LOGGER).closeAllForShutdown("server1", StaffStatus.OFF_DUTY);

        assertTrue(repository.findActiveSession(uuid).isEmpty());
        List<su.twomc.staffwork.model.StatusInterval> open = repository.findAllOpenStatusIntervals();
        assertEquals(1, open.size());
        assertEquals(StaffStatus.OFF_DUTY, open.get(0).status());
    }
}
