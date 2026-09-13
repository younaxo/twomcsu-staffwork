package su.twomc.staffwork.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import su.twomc.staffwork.model.Employee;
import su.twomc.staffwork.model.SessionEndReason;
import su.twomc.staffwork.model.StaffStatus;
import su.twomc.staffwork.model.StatusInterval;
import su.twomc.staffwork.model.TelegramLinkCode;
import su.twomc.staffwork.model.WorkSession;

/**
 * Единый интерфейс хранилища данных плагина. Бизнес-логика (сервисы) работает только через него
 * и не знает, YAML это, SQLite, H2 или MySQL/MariaDB.
 *
 * <p>Реализации синхронны и блокирующие — вызывающая сторона (сервисный слой) обязана выполнять
 * обращения к репозиторию вне основного/регионального потока сервера, используя платформенный
 * планировщик.
 */
public interface StaffRepository extends AutoCloseable {

    /** Создаёт структуру хранилища и применяет недостающие миграции. Вызывается один раз при старте. */
    void initialize();

    // --- Сотрудники ---

    Optional<Employee> findEmployee(UUID uuid);

    List<Employee> findAllEmployees();

    /** Вставляет либо обновляет запись сотрудника (по UUID). */
    void saveEmployee(Employee employee);

    void deleteEmployee(UUID uuid);

    // --- Рабочие сессии ---

    WorkSession openSession(UUID employeeUuid, Instant startedAt, String serverId);

    void closeSession(long sessionId, Instant endedAt, SessionEndReason reason);

    Optional<WorkSession> findActiveSession(UUID employeeUuid);

    /** Все незакрытые сессии — используется при старте плагина для восстановления после аварийной остановки. */
    List<WorkSession> findAllActiveSessions();

    // --- Интервалы статусов ---

    StatusInterval openStatusInterval(UUID employeeUuid, Long sessionId, StaffStatus status, Instant startedAt);

    void closeStatusInterval(long intervalId, Instant endedAt);

    Optional<StatusInterval> findOpenStatusInterval(UUID employeeUuid);

    List<StatusInterval> findAllOpenStatusIntervals();

    /** Интервалы, пересекающиеся с [from, to). */
    List<StatusInterval> findIntervals(UUID employeeUuid, Instant from, Instant to);

    // --- Telegram ---

    void saveTelegramLinkCode(TelegramLinkCode code);

    Optional<TelegramLinkCode> findTelegramLinkCode(UUID employeeUuid);

    /** Все ещё не удалённые коды привязки (включая просроченные — их отсеивает вызывающая сторона). */
    List<TelegramLinkCode> findAllTelegramLinkCodes();

    void deleteTelegramLinkCode(UUID employeeUuid);

    void saveTelegramLink(UUID employeeUuid, long telegramUserId);

    Optional<Long> findTelegramUserId(UUID employeeUuid);

    Optional<UUID> findEmployeeByTelegramUserId(long telegramUserId);

    void deleteTelegramLink(UUID employeeUuid);

    // --- Heartbeat (для восстановления после аварийной остановки) ---

    /** Отмечает, что этот сервер (по {@code server-id}) был жив в момент {@code at}. */
    void saveHeartbeat(String serverId, Instant at);

    Optional<Instant> findHeartbeat(String serverId);

    @Override
    void close();
}
