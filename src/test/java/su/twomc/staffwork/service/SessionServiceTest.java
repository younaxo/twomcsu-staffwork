package su.twomc.staffwork.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import su.twomc.staffwork.model.Employee;
import su.twomc.staffwork.model.SessionEndReason;
import su.twomc.staffwork.model.StaffStatus;
import su.twomc.staffwork.model.WorkSession;
import su.twomc.staffwork.testutil.InMemoryStaffRepository;

class SessionServiceTest {

    private Employee newEmployee(InMemoryStaffRepository repository, Instant now) {
        UUID uuid = UUID.randomUUID();
        Employee employee = new Employee(uuid, "Tester", "trainee", true, now, null, StaffStatus.OFF_DUTY, now);
        repository.saveEmployee(employee);
        repository.openStatusInterval(uuid, null, StaffStatus.OFF_DUTY, now);
        return employee;
    }

    @Test
    void startingSecondSessionIsRejected() {
        InMemoryStaffRepository repository = new InMemoryStaffRepository();
        Instant now = Instant.parse("2025-01-01T10:00:00Z");
        Employee employee = newEmployee(repository, now);
        SessionService service = new SessionService(repository, new StatusService(repository));

        service.startSession(employee, StaffStatus.WORKING, now, "server1");

        assertThrows(
                IllegalStateException.class, () -> service.startSession(employee, StaffStatus.WORKING, now.plusSeconds(5), "server1"));
    }

    @Test
    void stoppingWithoutActiveSessionIsRejected() {
        InMemoryStaffRepository repository = new InMemoryStaffRepository();
        Instant now = Instant.parse("2025-01-01T10:00:00Z");
        Employee employee = newEmployee(repository, now);
        SessionService service = new SessionService(repository, new StatusService(repository));

        assertThrows(
                IllegalStateException.class,
                () -> service.stopSession(employee, StaffStatus.OFF_DUTY, now, SessionEndReason.MANUAL_STOP));
    }

    @Test
    void startThenStopClosesSessionAndReturnsDuration() {
        InMemoryStaffRepository repository = new InMemoryStaffRepository();
        Instant now = Instant.parse("2025-01-01T10:00:00Z");
        Employee employee = newEmployee(repository, now);
        SessionService service = new SessionService(repository, new StatusService(repository));

        service.startSession(employee, StaffStatus.WORKING, now, "server1");
        Instant stopAt = now.plusSeconds(3600);
        WorkSession stopped = service.stopSession(employee, StaffStatus.OFF_DUTY, stopAt, SessionEndReason.MANUAL_STOP);

        assertFalse(stopped.isActive());
        assertEquals(stopAt, stopped.endedAt());
        assertTrue(repository.findActiveSession(employee.uuid()).isEmpty());
        assertEquals(StaffStatus.OFF_DUTY, employee.currentStatus());
    }
}
