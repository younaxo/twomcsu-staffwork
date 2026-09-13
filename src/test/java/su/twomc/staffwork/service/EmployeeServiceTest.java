package su.twomc.staffwork.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import su.twomc.staffwork.model.Employee;
import su.twomc.staffwork.testutil.InMemoryStaffRepository;

class EmployeeServiceTest {

    @Test
    void addingSameEmployeeTwiceIsRejected() {
        InMemoryStaffRepository repository = new InMemoryStaffRepository();
        EmployeeService service = new EmployeeService(repository);
        UUID uuid = UUID.randomUUID();
        Instant now = Instant.parse("2025-01-01T00:00:00Z");

        service.addEmployee(uuid, "Tester", "trainee", null, now);

        assertThrows(IllegalStateException.class, () -> service.addEmployee(uuid, "Tester", "trainee", null, now));
    }

    @Test
    void removingUnknownEmployeeIsRejected() {
        InMemoryStaffRepository repository = new InMemoryStaffRepository();
        EmployeeService service = new EmployeeService(repository);

        assertThrows(IllegalStateException.class, () -> service.removeEmployee(UUID.randomUUID()));
    }

    @Test
    void addedEmployeeUsesUuidAsPrimaryKeyNotName() {
        InMemoryStaffRepository repository = new InMemoryStaffRepository();
        EmployeeService service = new EmployeeService(repository);
        UUID uuid = UUID.randomUUID();
        Instant now = Instant.parse("2025-01-01T00:00:00Z");

        Employee employee = service.addEmployee(uuid, "OldName", "trainee", null, now);
        service.updateLastKnownName(uuid, "NewName");

        Employee reloaded = service.requireEmployee(uuid);
        assertEquals(uuid, reloaded.uuid());
        assertEquals("NewName", reloaded.lastKnownName());
        assertTrue(employee.enabled());
    }

    @Test
    void settingRankOnUnknownEmployeeIsRejected() {
        InMemoryStaffRepository repository = new InMemoryStaffRepository();
        EmployeeService service = new EmployeeService(repository);

        assertThrows(IllegalStateException.class, () -> service.setRank(UUID.randomUUID(), "moderator"));
    }
}
