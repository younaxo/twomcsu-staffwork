package su.twomc.staffwork.repository.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import su.twomc.staffwork.model.Employee;
import su.twomc.staffwork.model.SessionEndReason;
import su.twomc.staffwork.model.StaffStatus;
import su.twomc.staffwork.model.WorkSession;

class YamlStaffRepositoryTest {

    private static final Logger LOGGER = Logger.getLogger(YamlStaffRepositoryTest.class.getName());

    @Test
    void savedEmployeeSurvivesReloadFromDisk(@TempDir File tempDir) {
        File file = new File(tempDir, "data.yml");
        UUID uuid = UUID.randomUUID();
        Instant now = Instant.parse("2025-01-01T00:00:00Z");

        YamlStaffRepository first = new YamlStaffRepository(file, LOGGER);
        first.initialize();
        first.saveEmployee(new Employee(uuid, "Tester", "moderator", true, now, null, StaffStatus.WORKING, now));

        assertTrue(file.exists(), "Файл данных должен быть создан на диске");

        YamlStaffRepository reloaded = new YamlStaffRepository(file, LOGGER);
        reloaded.initialize();
        Employee employee = reloaded.findEmployee(uuid).orElseThrow();

        assertEquals("Tester", employee.lastKnownName());
        assertEquals("moderator", employee.rankId());
        assertEquals(StaffStatus.WORKING, employee.currentStatus());
    }

    @Test
    void sessionAndIntervalLifecycleRoundTrips(@TempDir File tempDir) {
        File file = new File(tempDir, "data.yml");
        YamlStaffRepository repository = new YamlStaffRepository(file, LOGGER);
        repository.initialize();
        UUID uuid = UUID.randomUUID();
        Instant now = Instant.parse("2025-01-01T00:00:00Z");

        WorkSession session = repository.openSession(uuid, now, "server1");
        assertTrue(repository.findActiveSession(uuid).isPresent());

        repository.closeSession(session.id(), now.plusSeconds(3600), SessionEndReason.MANUAL_STOP);
        assertTrue(repository.findActiveSession(uuid).isEmpty());

        var interval = repository.openStatusInterval(uuid, session.id(), StaffStatus.WORKING, now);
        assertTrue(repository.findOpenStatusInterval(uuid).isPresent());
        repository.closeStatusInterval(interval.id(), now.plusSeconds(1800));
        assertTrue(repository.findOpenStatusInterval(uuid).isEmpty());

        assertEquals(1, repository.findIntervals(uuid, now.minusSeconds(10), now.plusSeconds(3600)).size());
    }

    @Test
    void heartbeatIsPersisted(@TempDir File tempDir) {
        File file = new File(tempDir, "data.yml");
        YamlStaffRepository repository = new YamlStaffRepository(file, LOGGER);
        repository.initialize();
        Instant now = Instant.parse("2025-01-01T00:00:00Z");

        repository.saveHeartbeat("server1", now);

        assertEquals(now, repository.findHeartbeat("server1").orElseThrow());
        assertTrue(repository.findHeartbeat("server2").isEmpty());
    }
}
