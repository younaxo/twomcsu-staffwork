package su.twomc.staffwork.repository.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import su.twomc.staffwork.model.Employee;
import su.twomc.staffwork.model.SessionEndReason;
import su.twomc.staffwork.model.StaffStatus;
import su.twomc.staffwork.model.WorkSession;

class SqliteStaffRepositoryTest {

    private static final Logger LOGGER = Logger.getLogger(SqliteStaffRepositoryTest.class.getName());

    @Test
    void schemaIsCreatedAndEmployeeCanBeUpserted(@TempDir File tempDir) {
        File dbFile = new File(tempDir, "database.db");
        SqliteStaffRepository repository = new SqliteStaffRepository(dbFile, LOGGER);
        repository.initialize();
        UUID uuid = UUID.randomUUID();
        Instant now = Instant.parse("2025-01-01T00:00:00Z");

        repository.saveEmployee(new Employee(uuid, "Tester", "trainee", true, now, null, StaffStatus.OFF_DUTY, now));
        repository.saveEmployee(new Employee(uuid, "TesterRenamed", "moderator", true, now, null, StaffStatus.WORKING, now));

        Employee employee = repository.findEmployee(uuid).orElseThrow();
        assertEquals("TesterRenamed", employee.lastKnownName());
        assertEquals("moderator", employee.rankId());
        assertEquals(1, repository.findAllEmployees().size());

        repository.close();
    }

    @Test
    void reopeningExistingDatabaseDoesNotFailMigration(@TempDir File tempDir) {
        File dbFile = new File(tempDir, "database.db");
        SqliteStaffRepository first = new SqliteStaffRepository(dbFile, LOGGER);
        first.initialize();
        first.close();

        // Повторная инициализация поверх уже существующей БД — миграции должны быть идемпотентны.
        SqliteStaffRepository second = new SqliteStaffRepository(dbFile, LOGGER);
        second.initialize();
        second.close();
    }

    @Test
    void sessionsAndIntervalsRoundTrip(@TempDir File tempDir) {
        File dbFile = new File(tempDir, "database.db");
        SqliteStaffRepository repository = new SqliteStaffRepository(dbFile, LOGGER);
        repository.initialize();
        UUID uuid = UUID.randomUUID();
        Instant now = Instant.parse("2025-01-01T00:00:00Z");

        WorkSession session = repository.openSession(uuid, now, "server1");
        assertTrue(repository.findActiveSession(uuid).isPresent());
        assertEquals(1, repository.findAllActiveSessions().size());

        repository.closeSession(session.id(), now.plusSeconds(1800), SessionEndReason.MANUAL_STOP);
        assertTrue(repository.findActiveSession(uuid).isEmpty());

        List<su.twomc.staffwork.model.StatusInterval> intervals =
                repository.findIntervals(uuid, now.minusSeconds(1), now.plusSeconds(2000));
        assertTrue(intervals.isEmpty());

        repository.close();
    }
}
