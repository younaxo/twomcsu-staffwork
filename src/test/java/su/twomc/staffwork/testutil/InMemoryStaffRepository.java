package su.twomc.staffwork.testutil;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import su.twomc.staffwork.model.Employee;
import su.twomc.staffwork.model.SessionEndReason;
import su.twomc.staffwork.model.StaffStatus;
import su.twomc.staffwork.model.StatusInterval;
import su.twomc.staffwork.model.TelegramLinkCode;
import su.twomc.staffwork.model.WorkSession;
import su.twomc.staffwork.repository.StaffRepository;

/** Реализация {@link StaffRepository} в памяти для модульных тестов — без реальной БД/файлов. */
public final class InMemoryStaffRepository implements StaffRepository {

    private final Map<UUID, Employee> employees = new ConcurrentHashMap<>();
    private final Map<Long, WorkSession> sessions = new ConcurrentHashMap<>();
    private final Map<Long, StatusInterval> intervals = new ConcurrentHashMap<>();
    private final Map<UUID, Long> telegramLinks = new ConcurrentHashMap<>();
    private final Map<UUID, TelegramLinkCode> telegramCodes = new ConcurrentHashMap<>();
    private final Map<String, Instant> heartbeats = new ConcurrentHashMap<>();
    private final AtomicLong sessionIds = new AtomicLong(1);
    private final AtomicLong intervalIds = new AtomicLong(1);

    @Override
    public void initialize() {}

    @Override
    public Optional<Employee> findEmployee(UUID uuid) {
        return Optional.ofNullable(employees.get(uuid));
    }

    @Override
    public List<Employee> findAllEmployees() {
        return new ArrayList<>(employees.values());
    }

    @Override
    public void saveEmployee(Employee employee) {
        employees.put(employee.uuid(), employee);
    }

    @Override
    public void deleteEmployee(UUID uuid) {
        employees.remove(uuid);
    }

    @Override
    public WorkSession openSession(UUID employeeUuid, Instant startedAt, String serverId) {
        long id = sessionIds.getAndIncrement();
        WorkSession session = new WorkSession(id, employeeUuid, startedAt, null, null, serverId);
        sessions.put(id, session);
        return session;
    }

    @Override
    public void closeSession(long sessionId, Instant endedAt, SessionEndReason reason) {
        WorkSession existing = sessions.get(sessionId);
        if (existing != null) {
            sessions.put(
                    sessionId,
                    new WorkSession(existing.id(), existing.employeeUuid(), existing.startedAt(), endedAt, reason, existing.serverId()));
        }
    }

    @Override
    public Optional<WorkSession> findActiveSession(UUID employeeUuid) {
        return sessions.values().stream()
                .filter(s -> s.employeeUuid().equals(employeeUuid) && s.isActive())
                .max((a, b) -> a.startedAt().compareTo(b.startedAt()));
    }

    @Override
    public List<WorkSession> findAllActiveSessions() {
        return sessions.values().stream().filter(WorkSession::isActive).toList();
    }

    @Override
    public StatusInterval openStatusInterval(UUID employeeUuid, Long sessionId, StaffStatus status, Instant startedAt) {
        long id = intervalIds.getAndIncrement();
        StatusInterval interval = new StatusInterval(id, employeeUuid, sessionId, status, startedAt, null);
        intervals.put(id, interval);
        return interval;
    }

    @Override
    public void closeStatusInterval(long intervalId, Instant endedAt) {
        StatusInterval existing = intervals.get(intervalId);
        if (existing != null) {
            intervals.put(
                    intervalId,
                    new StatusInterval(
                            existing.id(),
                            existing.employeeUuid(),
                            existing.sessionId(),
                            existing.status(),
                            existing.startedAt(),
                            endedAt));
        }
    }

    @Override
    public Optional<StatusInterval> findOpenStatusInterval(UUID employeeUuid) {
        return intervals.values().stream()
                .filter(i -> i.employeeUuid().equals(employeeUuid) && i.isOpen())
                .max((a, b) -> a.startedAt().compareTo(b.startedAt()));
    }

    @Override
    public List<StatusInterval> findAllOpenStatusIntervals() {
        return intervals.values().stream().filter(StatusInterval::isOpen).toList();
    }

    @Override
    public List<StatusInterval> findIntervals(UUID employeeUuid, Instant from, Instant to) {
        return intervals.values().stream()
                .filter(i -> i.employeeUuid().equals(employeeUuid))
                .filter(i -> i.startedAt().isBefore(to) && (i.endedAt() == null || i.endedAt().isAfter(from)))
                .sorted((a, b) -> a.startedAt().compareTo(b.startedAt()))
                .toList();
    }

    @Override
    public void saveTelegramLinkCode(TelegramLinkCode code) {
        telegramCodes.put(code.employeeUuid(), code);
    }

    @Override
    public Optional<TelegramLinkCode> findTelegramLinkCode(UUID employeeUuid) {
        return Optional.ofNullable(telegramCodes.get(employeeUuid));
    }

    @Override
    public List<TelegramLinkCode> findAllTelegramLinkCodes() {
        return new ArrayList<>(telegramCodes.values());
    }

    @Override
    public void deleteTelegramLinkCode(UUID employeeUuid) {
        telegramCodes.remove(employeeUuid);
    }

    @Override
    public void saveTelegramLink(UUID employeeUuid, long telegramUserId) {
        telegramLinks.put(employeeUuid, telegramUserId);
    }

    @Override
    public Optional<Long> findTelegramUserId(UUID employeeUuid) {
        return Optional.ofNullable(telegramLinks.get(employeeUuid));
    }

    @Override
    public Optional<UUID> findEmployeeByTelegramUserId(long telegramUserId) {
        return telegramLinks.entrySet().stream()
                .filter(e -> e.getValue() == telegramUserId)
                .map(Map.Entry::getKey)
                .findFirst();
    }

    @Override
    public void deleteTelegramLink(UUID employeeUuid) {
        telegramLinks.remove(employeeUuid);
    }

    @Override
    public void saveHeartbeat(String serverId, Instant at) {
        heartbeats.put(serverId, at);
    }

    @Override
    public Optional<Instant> findHeartbeat(String serverId) {
        return Optional.ofNullable(heartbeats.get(serverId));
    }

    @Override
    public void close() {}
}
