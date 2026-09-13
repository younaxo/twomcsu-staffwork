package su.twomc.staffwork.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import su.twomc.staffwork.config.AutoStatusSettings;
import su.twomc.staffwork.model.Employee;
import su.twomc.staffwork.model.StaffStatus;
import su.twomc.staffwork.testutil.InMemoryStaffRepository;

class AutoStatusServiceTest {

    private Employee newEmployee(InMemoryStaffRepository repository, Instant now) {
        UUID uuid = UUID.randomUUID();
        Employee employee = new Employee(uuid, "Tester", "trainee", true, now, null, StaffStatus.OFF_DUTY, now);
        repository.saveEmployee(employee);
        repository.openStatusInterval(uuid, null, StaffStatus.OFF_DUTY, now);
        return employee;
    }

    @Test
    void joinAppliesConfiguredStatusAndStartsSession() {
        InMemoryStaffRepository repository = new InMemoryStaffRepository();
        Instant now = Instant.parse("2025-01-01T10:00:00Z");
        Employee employee = newEmployee(repository, now);
        StatusService statusService = new StatusService(repository);
        SessionService sessionService = new SessionService(repository, statusService);
        AutoStatusSettings settings = new AutoStatusSettings(
                StaffStatus.WORKING,
                true,
                AutoStatusSettings.QuitBehavior.STOP_SESSION,
                StaffStatus.OFF_DUTY,
                true,
                Duration.ofMinutes(5),
                true);
        AutoStatusService service = new AutoStatusService(statusService, sessionService, settings);

        service.onJoin(employee, now, "server1");

        assertEquals(StaffStatus.WORKING, employee.currentStatus());
        assertTrue(sessionService.activeSession(employee.uuid()).isPresent());
    }

    @Test
    void quitStopsSessionWhenConfigured() {
        InMemoryStaffRepository repository = new InMemoryStaffRepository();
        Instant now = Instant.parse("2025-01-01T10:00:00Z");
        Employee employee = newEmployee(repository, now);
        StatusService statusService = new StatusService(repository);
        SessionService sessionService = new SessionService(repository, statusService);
        AutoStatusSettings settings = new AutoStatusSettings(
                StaffStatus.WORKING,
                true,
                AutoStatusSettings.QuitBehavior.STOP_SESSION,
                StaffStatus.OFF_DUTY,
                false,
                Duration.ofMinutes(5),
                true);
        AutoStatusService service = new AutoStatusService(statusService, sessionService, settings);

        service.onJoin(employee, now, "server1");
        service.onQuit(employee, now.plusSeconds(60));

        assertTrue(sessionService.activeSession(employee.uuid()).isEmpty());
        assertEquals(StaffStatus.OFF_DUTY, employee.currentStatus());
    }

    @Test
    void idlePastThresholdSwitchesToAfk() {
        InMemoryStaffRepository repository = new InMemoryStaffRepository();
        Instant now = Instant.parse("2025-01-01T10:00:00Z");
        Employee employee = newEmployee(repository, now);
        StatusService statusService = new StatusService(repository);
        SessionService sessionService = new SessionService(repository, statusService);
        AutoStatusSettings settings = new AutoStatusSettings(
                StaffStatus.WORKING,
                false,
                AutoStatusSettings.QuitBehavior.NOTHING,
                StaffStatus.OFF_DUTY,
                true,
                Duration.ofMinutes(5),
                true);
        AutoStatusService service = new AutoStatusService(statusService, sessionService, settings);

        service.onJoin(employee, now, "server1");
        service.recordActivity(employee, now);

        boolean becameAfkTooEarly = service.checkAfk(employee, now.plus(Duration.ofMinutes(4)));
        assertFalse(becameAfkTooEarly);

        boolean becameAfk = service.checkAfk(employee, now.plus(Duration.ofMinutes(6)));
        assertTrue(becameAfk);
        assertEquals(StaffStatus.AFK, employee.currentStatus());
    }

    @Test
    void activityAfterAfkRestoresPreviousStatus() {
        InMemoryStaffRepository repository = new InMemoryStaffRepository();
        Instant now = Instant.parse("2025-01-01T10:00:00Z");
        Employee employee = newEmployee(repository, now);
        StatusService statusService = new StatusService(repository);
        SessionService sessionService = new SessionService(repository, statusService);
        AutoStatusSettings settings = new AutoStatusSettings(
                StaffStatus.WORKING,
                false,
                AutoStatusSettings.QuitBehavior.NOTHING,
                StaffStatus.OFF_DUTY,
                true,
                Duration.ofMinutes(5),
                true);
        AutoStatusService service = new AutoStatusService(statusService, sessionService, settings);

        service.onJoin(employee, now, "server1");
        service.recordActivity(employee, now);
        service.checkAfk(employee, now.plus(Duration.ofMinutes(6)));
        assertEquals(StaffStatus.AFK, employee.currentStatus());

        service.recordActivity(employee, now.plus(Duration.ofMinutes(10)));

        assertEquals(StaffStatus.WORKING, employee.currentStatus());
    }
}
