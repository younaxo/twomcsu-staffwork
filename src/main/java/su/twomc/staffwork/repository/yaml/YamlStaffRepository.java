package su.twomc.staffwork.repository.yaml;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import su.twomc.staffwork.model.Employee;
import su.twomc.staffwork.model.SessionEndReason;
import su.twomc.staffwork.model.StaffStatus;
import su.twomc.staffwork.model.StatusInterval;
import su.twomc.staffwork.model.TelegramLinkCode;
import su.twomc.staffwork.model.WorkSession;
import su.twomc.staffwork.repository.RepositoryException;
import su.twomc.staffwork.repository.StaffRepository;

/**
 * Хранилище для небольших серверов — один YAML-файл со всеми данными. Каждая запись сразу
 * пишется на диск через временный файл с последующей атомарной заменой ({@link StandardCopyOption
 * #ATOMIC_MOVE}), чтобы аварийное завершение процесса посреди записи не повредило файл данных.
 * Все операции синхронизированы одной блокировкой — для объёма данных небольшого сервера
 * это не является узким местом, а простота важнее теоретической параллельности.
 */
public final class YamlStaffRepository implements StaffRepository {

    private final File file;
    private final Logger logger;
    private final ReentrantLock lock = new ReentrantLock();
    private YamlConfiguration config;

    public YamlStaffRepository(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    @Override
    public void initialize() {
        lock.lock();
        try {
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            config = new YamlConfiguration();
            if (file.exists()) {
                try {
                    config.load(file);
                } catch (Exception e) {
                    throw new RepositoryException("Не удалось прочитать файл данных " + file.getName()
                            + " — возможно, он повреждён. Восстановите его из резервной копии.", e);
                }
            }
            if (!config.contains("schema-version")) {
                config.set("schema-version", 1);
            }
            if (!config.contains("next-session-id")) {
                config.set("next-session-id", 1L);
            }
            if (!config.contains("next-interval-id")) {
                config.set("next-interval-id", 1L);
            }
            persistLocked();
        } finally {
            lock.unlock();
        }
    }

    private void persistLocked() {
        try {
            File tmp = File.createTempFile("tmc-staffwork-", ".yml.tmp", file.getParentFile());
            config.save(tmp);
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new RepositoryException("Не удалось сохранить файл данных " + file.getName(), e);
        }
    }

    // --- Сотрудники ---

    @Override
    public Optional<Employee> findEmployee(UUID uuid) {
        lock.lock();
        try {
            ConfigurationSection section = config.getConfigurationSection("employees." + uuid);
            return section == null ? Optional.empty() : Optional.of(mapEmployee(uuid, section));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<Employee> findAllEmployees() {
        lock.lock();
        try {
            List<Employee> result = new ArrayList<>();
            ConfigurationSection employees = config.getConfigurationSection("employees");
            if (employees == null) {
                return result;
            }
            for (String key : employees.getKeys(false)) {
                result.add(mapEmployee(UUID.fromString(key), employees.getConfigurationSection(key)));
            }
            return result;
        } finally {
            lock.unlock();
        }
    }

    private Employee mapEmployee(UUID uuid, ConfigurationSection section) {
        String addedByRaw = section.getString("added-by");
        return new Employee(
                uuid,
                section.getString("last-known-name"),
                section.getString("rank-id"),
                section.getBoolean("enabled"),
                Instant.ofEpochMilli(section.getLong("added-at")),
                addedByRaw == null || addedByRaw.isEmpty() ? null : UUID.fromString(addedByRaw),
                StaffStatus.valueOf(section.getString("current-status")),
                Instant.ofEpochMilli(section.getLong("current-status-since")));
    }

    @Override
    public void saveEmployee(Employee employee) {
        lock.lock();
        try {
            String path = "employees." + employee.uuid();
            config.set(path + ".last-known-name", employee.lastKnownName());
            config.set(path + ".rank-id", employee.rankId());
            config.set(path + ".enabled", employee.enabled());
            config.set(path + ".added-at", employee.addedAt().toEpochMilli());
            config.set(path + ".added-by", employee.addedBy() == null ? null : employee.addedBy().toString());
            config.set(path + ".current-status", employee.currentStatus().name());
            config.set(path + ".current-status-since", employee.currentStatusSince().toEpochMilli());
            persistLocked();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void deleteEmployee(UUID uuid) {
        lock.lock();
        try {
            config.set("employees." + uuid, null);
            persistLocked();
        } finally {
            lock.unlock();
        }
    }

    // --- Рабочие сессии ---

    @Override
    public WorkSession openSession(UUID employeeUuid, Instant startedAt, String serverId) {
        lock.lock();
        try {
            long id = config.getLong("next-session-id", 1L);
            config.set("next-session-id", id + 1);
            String path = "sessions." + id;
            config.set(path + ".employee-uuid", employeeUuid.toString());
            config.set(path + ".started-at", startedAt.toEpochMilli());
            config.set(path + ".server-id", serverId);
            persistLocked();
            return new WorkSession(id, employeeUuid, startedAt, null, null, serverId);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void closeSession(long sessionId, Instant endedAt, SessionEndReason reason) {
        lock.lock();
        try {
            String path = "sessions." + sessionId;
            config.set(path + ".ended-at", endedAt.toEpochMilli());
            config.set(path + ".end-reason", reason.name());
            persistLocked();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<WorkSession> findActiveSession(UUID employeeUuid) {
        lock.lock();
        try {
            ConfigurationSection sessions = config.getConfigurationSection("sessions");
            if (sessions == null) {
                return Optional.empty();
            }
            WorkSession latest = null;
            for (String key : sessions.getKeys(false)) {
                ConfigurationSection s = sessions.getConfigurationSection(key);
                if (s != null
                        && employeeUuid.toString().equals(s.getString("employee-uuid"))
                        && !s.contains("ended-at")) {
                    WorkSession candidate = mapSession(Long.parseLong(key), s);
                    if (latest == null || candidate.startedAt().isAfter(latest.startedAt())) {
                        latest = candidate;
                    }
                }
            }
            return Optional.ofNullable(latest);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<WorkSession> findAllActiveSessions() {
        lock.lock();
        try {
            List<WorkSession> result = new ArrayList<>();
            ConfigurationSection sessions = config.getConfigurationSection("sessions");
            if (sessions == null) {
                return result;
            }
            for (String key : sessions.getKeys(false)) {
                ConfigurationSection s = sessions.getConfigurationSection(key);
                if (s != null && !s.contains("ended-at")) {
                    result.add(mapSession(Long.parseLong(key), s));
                }
            }
            return result;
        } finally {
            lock.unlock();
        }
    }

    private WorkSession mapSession(long id, ConfigurationSection s) {
        String reasonRaw = s.getString("end-reason");
        return new WorkSession(
                id,
                UUID.fromString(s.getString("employee-uuid")),
                Instant.ofEpochMilli(s.getLong("started-at")),
                s.contains("ended-at") ? Instant.ofEpochMilli(s.getLong("ended-at")) : null,
                reasonRaw == null ? null : SessionEndReason.valueOf(reasonRaw),
                s.getString("server-id"));
    }

    // --- Интервалы статусов ---

    @Override
    public StatusInterval openStatusInterval(UUID employeeUuid, Long sessionId, StaffStatus status, Instant startedAt) {
        lock.lock();
        try {
            long id = config.getLong("next-interval-id", 1L);
            config.set("next-interval-id", id + 1);
            String path = "intervals." + id;
            config.set(path + ".employee-uuid", employeeUuid.toString());
            if (sessionId != null) {
                config.set(path + ".session-id", sessionId);
            }
            config.set(path + ".status", status.name());
            config.set(path + ".started-at", startedAt.toEpochMilli());
            persistLocked();
            return new StatusInterval(id, employeeUuid, sessionId, status, startedAt, null);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void closeStatusInterval(long intervalId, Instant endedAt) {
        lock.lock();
        try {
            config.set("intervals." + intervalId + ".ended-at", endedAt.toEpochMilli());
            persistLocked();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<StatusInterval> findOpenStatusInterval(UUID employeeUuid) {
        lock.lock();
        try {
            ConfigurationSection intervals = config.getConfigurationSection("intervals");
            if (intervals == null) {
                return Optional.empty();
            }
            StatusInterval latest = null;
            for (String key : intervals.getKeys(false)) {
                ConfigurationSection s = intervals.getConfigurationSection(key);
                if (s != null
                        && employeeUuid.toString().equals(s.getString("employee-uuid"))
                        && !s.contains("ended-at")) {
                    StatusInterval candidate = mapInterval(Long.parseLong(key), s);
                    if (latest == null || candidate.startedAt().isAfter(latest.startedAt())) {
                        latest = candidate;
                    }
                }
            }
            return Optional.ofNullable(latest);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<StatusInterval> findAllOpenStatusIntervals() {
        lock.lock();
        try {
            List<StatusInterval> result = new ArrayList<>();
            ConfigurationSection intervals = config.getConfigurationSection("intervals");
            if (intervals == null) {
                return result;
            }
            for (String key : intervals.getKeys(false)) {
                ConfigurationSection s = intervals.getConfigurationSection(key);
                if (s != null && !s.contains("ended-at")) {
                    result.add(mapInterval(Long.parseLong(key), s));
                }
            }
            return result;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<StatusInterval> findIntervals(UUID employeeUuid, Instant from, Instant to) {
        lock.lock();
        try {
            List<StatusInterval> result = new ArrayList<>();
            ConfigurationSection intervals = config.getConfigurationSection("intervals");
            if (intervals == null) {
                return result;
            }
            for (String key : intervals.getKeys(false)) {
                ConfigurationSection s = intervals.getConfigurationSection(key);
                if (s == null || !employeeUuid.toString().equals(s.getString("employee-uuid"))) {
                    continue;
                }
                StatusInterval interval = mapInterval(Long.parseLong(key), s);
                boolean overlaps = interval.startedAt().isBefore(to)
                        && (interval.endedAt() == null || interval.endedAt().isAfter(from));
                if (overlaps) {
                    result.add(interval);
                }
            }
            result.sort((a, b) -> a.startedAt().compareTo(b.startedAt()));
            return result;
        } finally {
            lock.unlock();
        }
    }

    private StatusInterval mapInterval(long id, ConfigurationSection s) {
        return new StatusInterval(
                id,
                UUID.fromString(s.getString("employee-uuid")),
                s.contains("session-id") ? s.getLong("session-id") : null,
                StaffStatus.valueOf(s.getString("status")),
                Instant.ofEpochMilli(s.getLong("started-at")),
                s.contains("ended-at") ? Instant.ofEpochMilli(s.getLong("ended-at")) : null);
    }

    // --- Telegram ---

    @Override
    public void saveTelegramLinkCode(TelegramLinkCode code) {
        lock.lock();
        try {
            String path = "telegram-codes." + code.employeeUuid();
            config.set(path + ".code-hash", code.codeHash());
            config.set(path + ".expires-at", code.expiresAt().toEpochMilli());
            config.set(path + ".attempts", code.attempts());
            persistLocked();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<TelegramLinkCode> findTelegramLinkCode(UUID employeeUuid) {
        lock.lock();
        try {
            ConfigurationSection s = config.getConfigurationSection("telegram-codes." + employeeUuid);
            if (s == null) {
                return Optional.empty();
            }
            return Optional.of(new TelegramLinkCode(
                    employeeUuid, s.getString("code-hash"), Instant.ofEpochMilli(s.getLong("expires-at")), s.getInt("attempts")));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<TelegramLinkCode> findAllTelegramLinkCodes() {
        lock.lock();
        try {
            List<TelegramLinkCode> result = new ArrayList<>();
            ConfigurationSection section = config.getConfigurationSection("telegram-codes");
            if (section == null) {
                return result;
            }
            for (String key : section.getKeys(false)) {
                ConfigurationSection s = section.getConfigurationSection(key);
                if (s == null) {
                    continue;
                }
                result.add(new TelegramLinkCode(
                        UUID.fromString(key),
                        s.getString("code-hash"),
                        Instant.ofEpochMilli(s.getLong("expires-at")),
                        s.getInt("attempts")));
            }
            return result;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void deleteTelegramLinkCode(UUID employeeUuid) {
        lock.lock();
        try {
            config.set("telegram-codes." + employeeUuid, null);
            persistLocked();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void saveTelegramLink(UUID employeeUuid, long telegramUserId) {
        lock.lock();
        try {
            config.set("telegram-links." + employeeUuid, telegramUserId);
            persistLocked();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<Long> findTelegramUserId(UUID employeeUuid) {
        lock.lock();
        try {
            String path = "telegram-links." + employeeUuid;
            return config.contains(path) ? Optional.of(config.getLong(path)) : Optional.empty();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<UUID> findEmployeeByTelegramUserId(long telegramUserId) {
        lock.lock();
        try {
            ConfigurationSection links = config.getConfigurationSection("telegram-links");
            if (links == null) {
                return Optional.empty();
            }
            for (String key : links.getKeys(false)) {
                if (links.getLong(key) == telegramUserId) {
                    return Optional.of(UUID.fromString(key));
                }
            }
            return Optional.empty();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void deleteTelegramLink(UUID employeeUuid) {
        lock.lock();
        try {
            config.set("telegram-links." + employeeUuid, null);
            persistLocked();
        } finally {
            lock.unlock();
        }
    }

    // --- Heartbeat ---

    @Override
    public void saveHeartbeat(String serverId, Instant at) {
        lock.lock();
        try {
            config.set("heartbeats." + serverId, at.toEpochMilli());
            persistLocked();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<Instant> findHeartbeat(String serverId) {
        lock.lock();
        try {
            String path = "heartbeats." + serverId;
            return config.contains(path) ? Optional.of(Instant.ofEpochMilli(config.getLong(path))) : Optional.empty();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        // Файл сохраняется после каждой операции, отдельного закрытия ресурсов не требуется.
        logger.log(Level.FINE, "YAML-хранилище остановлено");
    }
}
