package su.twomc.staffwork.repository.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;
import su.twomc.staffwork.model.Employee;
import su.twomc.staffwork.model.SessionEndReason;
import su.twomc.staffwork.model.StaffStatus;
import su.twomc.staffwork.model.StatusInterval;
import su.twomc.staffwork.model.TelegramLinkCode;
import su.twomc.staffwork.model.WorkSession;
import su.twomc.staffwork.repository.RepositoryException;
import su.twomc.staffwork.repository.StaffRepository;

/**
 * Общая JDBC-реализация {@link StaffRepository} для SQLite/H2/MySQL-MariaDB — диалекты
 * различаются только определением автоинкрементного столбца (см. {@link SqlDialect}) и
 * настройками пула соединений, которые задаются конкретной реализацией.
 */
public abstract class AbstractSqlStaffRepository implements StaffRepository {

    protected final DataSource dataSource;
    protected final SqlDialect dialect;
    protected final Logger logger;

    protected AbstractSqlStaffRepository(DataSource dataSource, SqlDialect dialect, Logger logger) {
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.logger = logger;
    }

    @Override
    public void initialize() {
        try (Connection connection = dataSource.getConnection()) {
            new SchemaMigrator(dialect, logger).migrate(connection);
        } catch (SQLException e) {
            throw new RepositoryException("Не удалось подключиться к базе данных для миграции схемы", e);
        }
    }

    private Connection connection() {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            throw new RepositoryException("База данных временно недоступна", e);
        }
    }

    // --- Сотрудники ---

    @Override
    public Optional<Employee> findEmployee(UUID uuid) {
        String sql = "SELECT * FROM tmc_employees WHERE uuid = ?";
        try (Connection c = connection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapEmployee(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("Ошибка чтения сотрудника из базы данных", e);
        }
    }

    @Override
    public List<Employee> findAllEmployees() {
        String sql = "SELECT * FROM tmc_employees";
        List<Employee> result = new ArrayList<>();
        try (Connection c = connection();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                result.add(mapEmployee(rs));
            }
            return result;
        } catch (SQLException e) {
            throw new RepositoryException("Ошибка чтения списка сотрудников", e);
        }
    }

    @Override
    public void saveEmployee(Employee employee) {
        try (Connection c = connection()) {
            boolean autoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                boolean exists;
                try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM tmc_employees WHERE uuid = ?")) {
                    ps.setString(1, employee.uuid().toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        exists = rs.next();
                    }
                }
                if (exists) {
                    String sql = "UPDATE tmc_employees SET last_known_name = ?, rank_id = ?, enabled = ?, "
                            + "current_status = ?, current_status_since = ? WHERE uuid = ?";
                    try (PreparedStatement ps = c.prepareStatement(sql)) {
                        ps.setString(1, employee.lastKnownName());
                        ps.setString(2, employee.rankId());
                        ps.setInt(3, employee.enabled() ? 1 : 0);
                        ps.setString(4, employee.currentStatus().name());
                        ps.setLong(5, employee.currentStatusSince().toEpochMilli());
                        ps.setString(6, employee.uuid().toString());
                        ps.executeUpdate();
                    }
                } else {
                    String sql = "INSERT INTO tmc_employees(uuid, last_known_name, rank_id, enabled, added_at, "
                            + "added_by, current_status, current_status_since) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement ps = c.prepareStatement(sql)) {
                        ps.setString(1, employee.uuid().toString());
                        ps.setString(2, employee.lastKnownName());
                        ps.setString(3, employee.rankId());
                        ps.setInt(4, employee.enabled() ? 1 : 0);
                        ps.setLong(5, employee.addedAt().toEpochMilli());
                        ps.setString(6, employee.addedBy() == null ? null : employee.addedBy().toString());
                        ps.setString(7, employee.currentStatus().name());
                        ps.setLong(8, employee.currentStatusSince().toEpochMilli());
                        ps.executeUpdate();
                    }
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new RepositoryException("Ошибка сохранения сотрудника", e);
        }
    }

    @Override
    public void deleteEmployee(UUID uuid) {
        try (Connection c = connection();
                PreparedStatement ps = c.prepareStatement("DELETE FROM tmc_employees WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Ошибка удаления сотрудника", e);
        }
    }

    private Employee mapEmployee(ResultSet rs) throws SQLException {
        String addedByRaw = rs.getString("added_by");
        return new Employee(
                UUID.fromString(rs.getString("uuid")),
                rs.getString("last_known_name"),
                rs.getString("rank_id"),
                rs.getInt("enabled") != 0,
                Instant.ofEpochMilli(rs.getLong("added_at")),
                addedByRaw == null ? null : UUID.fromString(addedByRaw),
                StaffStatus.valueOf(rs.getString("current_status")),
                Instant.ofEpochMilli(rs.getLong("current_status_since")));
    }

    // --- Рабочие сессии ---

    @Override
    public WorkSession openSession(UUID employeeUuid, Instant startedAt, String serverId) {
        String sql = "INSERT INTO tmc_work_sessions(employee_uuid, started_at, server_id) VALUES (?, ?, ?)";
        try (Connection c = connection();
                PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, employeeUuid.toString());
            ps.setLong(2, startedAt.toEpochMilli());
            ps.setString(3, serverId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                long id = keys.getLong(1);
                return new WorkSession(id, employeeUuid, startedAt, null, null, serverId);
            }
        } catch (SQLException e) {
            throw new RepositoryException("Ошибка открытия рабочей сессии", e);
        }
    }

    @Override
    public void closeSession(long sessionId, Instant endedAt, SessionEndReason reason) {
        String sql = "UPDATE tmc_work_sessions SET ended_at = ?, end_reason = ? WHERE id = ?";
        try (Connection c = connection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, endedAt.toEpochMilli());
            ps.setString(2, reason.name());
            ps.setLong(3, sessionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Ошибка закрытия рабочей сессии", e);
        }
    }

    @Override
    public Optional<WorkSession> findActiveSession(UUID employeeUuid) {
        String sql = "SELECT * FROM tmc_work_sessions WHERE employee_uuid = ? AND ended_at IS NULL "
                + "ORDER BY started_at DESC LIMIT 1";
        try (Connection c = connection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, employeeUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapSession(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("Ошибка чтения активной сессии", e);
        }
    }

    @Override
    public List<WorkSession> findAllActiveSessions() {
        String sql = "SELECT * FROM tmc_work_sessions WHERE ended_at IS NULL";
        List<WorkSession> result = new ArrayList<>();
        try (Connection c = connection();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                result.add(mapSession(rs));
            }
            return result;
        } catch (SQLException e) {
            throw new RepositoryException("Ошибка чтения активных сессий", e);
        }
    }

    private WorkSession mapSession(ResultSet rs) throws SQLException {
        long endedAtRaw = rs.getLong("ended_at");
        Instant endedAt = rs.wasNull() ? null : Instant.ofEpochMilli(endedAtRaw);
        String reasonRaw = rs.getString("end_reason");
        return new WorkSession(
                rs.getLong("id"),
                UUID.fromString(rs.getString("employee_uuid")),
                Instant.ofEpochMilli(rs.getLong("started_at")),
                endedAt,
                reasonRaw == null ? null : SessionEndReason.valueOf(reasonRaw),
                rs.getString("server_id"));
    }

    // --- Интервалы статусов ---

    @Override
    public StatusInterval openStatusInterval(UUID employeeUuid, Long sessionId, StaffStatus status, Instant startedAt) {
        String sql = "INSERT INTO tmc_status_intervals(employee_uuid, session_id, status, started_at) "
                + "VALUES (?, ?, ?, ?)";
        try (Connection c = connection();
                PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, employeeUuid.toString());
            if (sessionId == null) {
                ps.setNull(2, java.sql.Types.BIGINT);
            } else {
                ps.setLong(2, sessionId);
            }
            ps.setString(3, status.name());
            ps.setLong(4, startedAt.toEpochMilli());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new StatusInterval(keys.getLong(1), employeeUuid, sessionId, status, startedAt, null);
            }
        } catch (SQLException e) {
            throw new RepositoryException("Ошибка открытия интервала статуса", e);
        }
    }

    @Override
    public void closeStatusInterval(long intervalId, Instant endedAt) {
        String sql = "UPDATE tmc_status_intervals SET ended_at = ? WHERE id = ?";
        try (Connection c = connection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, endedAt.toEpochMilli());
            ps.setLong(2, intervalId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Ошибка закрытия интервала статуса", e);
        }
    }

    @Override
    public Optional<StatusInterval> findOpenStatusInterval(UUID employeeUuid) {
        String sql = "SELECT * FROM tmc_status_intervals WHERE employee_uuid = ? AND ended_at IS NULL "
                + "ORDER BY started_at DESC LIMIT 1";
        try (Connection c = connection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, employeeUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapInterval(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("Ошибка чтения текущего интервала статуса", e);
        }
    }

    @Override
    public List<StatusInterval> findAllOpenStatusIntervals() {
        String sql = "SELECT * FROM tmc_status_intervals WHERE ended_at IS NULL";
        List<StatusInterval> result = new ArrayList<>();
        try (Connection c = connection();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                result.add(mapInterval(rs));
            }
            return result;
        } catch (SQLException e) {
            throw new RepositoryException("Ошибка чтения открытых интервалов статусов", e);
        }
    }

    @Override
    public List<StatusInterval> findIntervals(UUID employeeUuid, Instant from, Instant to) {
        String sql = "SELECT * FROM tmc_status_intervals WHERE employee_uuid = ? AND started_at < ? "
                + "AND (ended_at IS NULL OR ended_at > ?) ORDER BY started_at ASC";
        List<StatusInterval> result = new ArrayList<>();
        try (Connection c = connection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, employeeUuid.toString());
            ps.setLong(2, to.toEpochMilli());
            ps.setLong(3, from.toEpochMilli());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapInterval(rs));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RepositoryException("Ошибка чтения интервалов статусов за период", e);
        }
    }

    private StatusInterval mapInterval(ResultSet rs) throws SQLException {
        long sessionIdRaw = rs.getLong("session_id");
        Long sessionId = rs.wasNull() ? null : sessionIdRaw;
        long endedAtRaw = rs.getLong("ended_at");
        Instant endedAt = rs.wasNull() ? null : Instant.ofEpochMilli(endedAtRaw);
        return new StatusInterval(
                rs.getLong("id"),
                UUID.fromString(rs.getString("employee_uuid")),
                sessionId,
                StaffStatus.valueOf(rs.getString("status")),
                Instant.ofEpochMilli(rs.getLong("started_at")),
                endedAt);
    }

    // --- Telegram ---

    @Override
    public void saveTelegramLinkCode(TelegramLinkCode code) {
        try (Connection c = connection()) {
            boolean autoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                try (PreparedStatement del =
                        c.prepareStatement("DELETE FROM tmc_telegram_link_codes WHERE employee_uuid = ?")) {
                    del.setString(1, code.employeeUuid().toString());
                    del.executeUpdate();
                }
                String sql = "INSERT INTO tmc_telegram_link_codes(employee_uuid, code_hash, expires_at, attempts) "
                        + "VALUES (?, ?, ?, ?)";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setString(1, code.employeeUuid().toString());
                    ps.setString(2, code.codeHash());
                    ps.setLong(3, code.expiresAt().toEpochMilli());
                    ps.setInt(4, code.attempts());
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new RepositoryException("Ошибка сохранения кода привязки Telegram", e);
        }
    }

    @Override
    public Optional<TelegramLinkCode> findTelegramLinkCode(UUID employeeUuid) {
        String sql = "SELECT * FROM tmc_telegram_link_codes WHERE employee_uuid = ?";
        try (Connection c = connection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, employeeUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new TelegramLinkCode(
                        employeeUuid,
                        rs.getString("code_hash"),
                        Instant.ofEpochMilli(rs.getLong("expires_at")),
                        rs.getInt("attempts")));
            }
        } catch (SQLException e) {
            throw new RepositoryException("Ошибка чтения кода привязки Telegram", e);
        }
    }

    @Override
    public List<TelegramLinkCode> findAllTelegramLinkCodes() {
        String sql = "SELECT * FROM tmc_telegram_link_codes";
        List<TelegramLinkCode> result = new ArrayList<>();
        try (Connection c = connection();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                result.add(new TelegramLinkCode(
                        UUID.fromString(rs.getString("employee_uuid")),
                        rs.getString("code_hash"),
                        Instant.ofEpochMilli(rs.getLong("expires_at")),
                        rs.getInt("attempts")));
            }
            return result;
        } catch (SQLException e) {
            throw new RepositoryException("Ошибка чтения кодов привязки Telegram", e);
        }
    }

    @Override
    public void deleteTelegramLinkCode(UUID employeeUuid) {
        try (Connection c = connection();
                PreparedStatement ps = c.prepareStatement("DELETE FROM tmc_telegram_link_codes WHERE employee_uuid = ?")) {
            ps.setString(1, employeeUuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Ошибка удаления кода привязки Telegram", e);
        }
    }

    @Override
    public void saveTelegramLink(UUID employeeUuid, long telegramUserId) {
        try (Connection c = connection()) {
            boolean autoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                try (PreparedStatement del =
                        c.prepareStatement("DELETE FROM tmc_telegram_links WHERE employee_uuid = ?")) {
                    del.setString(1, employeeUuid.toString());
                    del.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO tmc_telegram_links(employee_uuid, telegram_user_id, linked_at) "
                                + "VALUES (?, ?, ?)")) {
                    ps.setString(1, employeeUuid.toString());
                    ps.setLong(2, telegramUserId);
                    ps.setLong(3, Instant.now().toEpochMilli());
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new RepositoryException("Ошибка сохранения привязки Telegram", e);
        }
    }

    @Override
    public Optional<Long> findTelegramUserId(UUID employeeUuid) {
        String sql = "SELECT telegram_user_id FROM tmc_telegram_links WHERE employee_uuid = ?";
        try (Connection c = connection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, employeeUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getLong(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("Ошибка чтения привязки Telegram", e);
        }
    }

    @Override
    public Optional<UUID> findEmployeeByTelegramUserId(long telegramUserId) {
        String sql = "SELECT employee_uuid FROM tmc_telegram_links WHERE telegram_user_id = ?";
        try (Connection c = connection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, telegramUserId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(UUID.fromString(rs.getString(1))) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("Ошибка поиска сотрудника по Telegram ID", e);
        }
    }

    @Override
    public void deleteTelegramLink(UUID employeeUuid) {
        try (Connection c = connection();
                PreparedStatement ps = c.prepareStatement("DELETE FROM tmc_telegram_links WHERE employee_uuid = ?")) {
            ps.setString(1, employeeUuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Ошибка удаления привязки Telegram", e);
        }
    }

    // --- Heartbeat ---

    @Override
    public void saveHeartbeat(String serverId, Instant at) {
        try (Connection c = connection()) {
            boolean autoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                try (PreparedStatement del =
                        c.prepareStatement("DELETE FROM tmc_server_heartbeat WHERE server_id = ?")) {
                    del.setString(1, serverId);
                    del.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO tmc_server_heartbeat(server_id, last_seen) VALUES (?, ?)")) {
                    ps.setString(1, serverId);
                    ps.setLong(2, at.toEpochMilli());
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new RepositoryException("Ошибка сохранения heartbeat сервера", e);
        }
    }

    @Override
    public Optional<Instant> findHeartbeat(String serverId) {
        String sql = "SELECT last_seen FROM tmc_server_heartbeat WHERE server_id = ?";
        try (Connection c = connection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, serverId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(Instant.ofEpochMilli(rs.getLong(1))) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("Ошибка чтения heartbeat сервера", e);
        }
    }

    @Override
    public void close() {
        if (dataSource instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Ошибка при закрытии пула соединений с базой данных", e);
            }
        }
    }
}
