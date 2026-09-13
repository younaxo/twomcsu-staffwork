package su.twomc.staffwork.repository.sql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.logging.Logger;
import su.twomc.staffwork.repository.RepositoryException;

/**
 * Собственная система версионирования схемы (без Flyway — он не имеет официальной устойчивой
 * поддержки SQLite, а набор миграций у плагина небольшой и стабильный). Версия хранится в таблице
 * {@code schema_version}; каждая миграция применяется не более одного раза и в транзакции.
 */
public final class SchemaMigrator {

    private final SqlDialect dialect;
    private final Logger logger;

    public SchemaMigrator(SqlDialect dialect, Logger logger) {
        this.dialect = dialect;
        this.logger = logger;
    }

    private List<String> migrationV1() {
        String pk = dialect.autoIncrementIdColumn();
        return List.of(
                """
                CREATE TABLE IF NOT EXISTS tmc_employees (
                    uuid VARCHAR(36) PRIMARY KEY,
                    last_known_name VARCHAR(16),
                    rank_id VARCHAR(32) NOT NULL,
                    enabled INTEGER NOT NULL,
                    added_at BIGINT NOT NULL,
                    added_by VARCHAR(36),
                    current_status VARCHAR(16) NOT NULL,
                    current_status_since BIGINT NOT NULL
                )
                """,
                "CREATE TABLE IF NOT EXISTS tmc_work_sessions (" + pk + ", "
                        + """
                    employee_uuid VARCHAR(36) NOT NULL,
                    started_at BIGINT NOT NULL,
                    ended_at BIGINT,
                    end_reason VARCHAR(32),
                    server_id VARCHAR(64) NOT NULL
                )
                """,
                "CREATE TABLE IF NOT EXISTS tmc_status_intervals (" + pk + ", "
                        + """
                    employee_uuid VARCHAR(36) NOT NULL,
                    session_id BIGINT,
                    status VARCHAR(16) NOT NULL,
                    started_at BIGINT NOT NULL,
                    ended_at BIGINT
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS tmc_telegram_links (
                    employee_uuid VARCHAR(36) PRIMARY KEY,
                    telegram_user_id BIGINT NOT NULL,
                    linked_at BIGINT NOT NULL
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS tmc_telegram_link_codes (
                    employee_uuid VARCHAR(36) PRIMARY KEY,
                    code_hash VARCHAR(128) NOT NULL,
                    expires_at BIGINT NOT NULL,
                    attempts INTEGER NOT NULL
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS tmc_server_heartbeat (
                    server_id VARCHAR(64) PRIMARY KEY,
                    last_seen BIGINT NOT NULL
                )
                """,
                "CREATE INDEX IF NOT EXISTS idx_tmc_status_intervals_employee "
                        + "ON tmc_status_intervals(employee_uuid, started_at)",
                "CREATE INDEX IF NOT EXISTS idx_tmc_work_sessions_employee "
                        + "ON tmc_work_sessions(employee_uuid, ended_at)",
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_tmc_telegram_links_user "
                        + "ON tmc_telegram_links(telegram_user_id)");
    }

    /** Список миграций в порядке применения; индекс в списке + 1 = номер версии схемы. */
    private List<List<String>> migrations() {
        return List.of(migrationV1());
    }

    public void migrate(Connection connection) {
        try {
            ensureVersionTable(connection);
            int current = currentVersion(connection);
            List<List<String>> migrations = migrations();
            for (int version = current + 1; version <= migrations.size(); version++) {
                applyMigration(connection, version, migrations.get(version - 1));
            }
        } catch (SQLException e) {
            throw new RepositoryException("Не удалось применить миграции схемы базы данных", e);
        }
    }

    private void ensureVersionTable(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS tmc_schema_version (version INTEGER NOT NULL)");
        }
        int rows;
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM tmc_schema_version")) {
            rs.next();
            rows = rs.getInt(1);
        }
        if (rows == 0) {
            try (Statement st = connection.createStatement()) {
                st.execute("INSERT INTO tmc_schema_version(version) VALUES (0)");
            }
        }
    }

    private int currentVersion(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery("SELECT version FROM tmc_schema_version")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private void applyMigration(Connection connection, int version, List<String> statements) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (Statement st = connection.createStatement()) {
                for (String sql : statements) {
                    st.execute(sql);
                }
                st.execute("UPDATE tmc_schema_version SET version = " + version);
            }
            connection.commit();
            logger.info("Схема базы данных обновлена до версии " + version);
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }
}
