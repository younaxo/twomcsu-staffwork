package su.twomc.staffwork.repository.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import su.twomc.staffwork.model.Employee;
import su.twomc.staffwork.model.StaffStatus;

/**
 * Проверяет ту же общую логику {@link AbstractSqlStaffRepository}, что и SQLite-тесты, но поверх
 * H2 — диалект различается только определением автоинкрементного столбца (см. {@link SqlDialect}).
 * Полноценный тест MySQL/MariaDB в CI не выполняется — для него требуется реальный сервер БД
 * (см. README, раздел "Известные ограничения").
 */
class H2StaffRepositoryTest {

    private static final Logger LOGGER = Logger.getLogger(H2StaffRepositoryTest.class.getName());

    @Test
    void schemaIsCreatedAndEmployeeCanBeStoredAndDeleted(@TempDir File tempDir) {
        File dbFile = new File(tempDir, "database");
        H2StaffRepository repository = new H2StaffRepository(dbFile, LOGGER);
        repository.initialize();
        UUID uuid = UUID.randomUUID();
        Instant now = Instant.parse("2025-01-01T00:00:00Z");

        repository.saveEmployee(new Employee(uuid, "Tester", "trainee", true, now, null, StaffStatus.OFF_DUTY, now));
        assertTrue(repository.findEmployee(uuid).isPresent());

        repository.deleteEmployee(uuid);
        assertEquals(0, repository.findAllEmployees().size());

        repository.close();
    }
}
