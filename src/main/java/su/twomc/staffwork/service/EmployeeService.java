package su.twomc.staffwork.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import su.twomc.staffwork.model.Employee;
import su.twomc.staffwork.model.SessionEndReason;
import su.twomc.staffwork.model.StaffStatus;
import su.twomc.staffwork.repository.StaffRepository;

/**
 * Управление карточками сотрудников. UUID — основной идентификатор; разрешение ника в UUID
 * (в том числе для офлайн-игроков) выполняется вызывающей стороной (командами) до обращения
 * сюда, чтобы этот сервис не зависел от Bukkit и не выполнял блокирующих обращений к Mojang API.
 */
public final class EmployeeService {

    private final StaffRepository repository;

    public EmployeeService(StaffRepository repository) {
        this.repository = repository;
    }

    public Employee addEmployee(UUID uuid, String lastKnownName, String rankId, UUID addedBy, Instant now) {
        if (repository.findEmployee(uuid).isPresent()) {
            throw new IllegalStateException("Этот игрок уже добавлен в систему учёта персонала");
        }
        Employee employee = new Employee(uuid, lastKnownName, rankId, true, now, addedBy, StaffStatus.OFF_DUTY, now);
        repository.saveEmployee(employee);
        repository.openStatusInterval(uuid, null, StaffStatus.OFF_DUTY, now);
        return employee;
    }

    public void removeEmployee(UUID uuid) {
        Employee employee = requireEmployee(uuid);
        repository
                .findActiveSession(uuid)
                .ifPresent(session -> repository.closeSession(session.id(), Instant.now(), SessionEndReason.ADMIN_ACTION));
        repository.findOpenStatusInterval(uuid).ifPresent(interval -> repository.closeStatusInterval(interval.id(), Instant.now()));
        repository.deleteEmployee(employee.uuid());
    }

    public Optional<Employee> findEmployee(UUID uuid) {
        return repository.findEmployee(uuid);
    }

    public Employee requireEmployee(UUID uuid) {
        return repository
                .findEmployee(uuid)
                .orElseThrow(() -> new IllegalStateException("Игрок не найден в системе учёта персонала"));
    }

    public List<Employee> listEmployees() {
        return repository.findAllEmployees();
    }

    public void setRank(UUID uuid, String rankId) {
        Employee employee = requireEmployee(uuid);
        employee.setRankId(rankId);
        repository.saveEmployee(employee);
    }

    public void setEnabled(UUID uuid, boolean enabled) {
        Employee employee = requireEmployee(uuid);
        employee.setEnabled(enabled);
        repository.saveEmployee(employee);
    }

    public void updateLastKnownName(UUID uuid, String name) {
        repository.findEmployee(uuid).ifPresent(employee -> {
            if (!name.equals(employee.lastKnownName())) {
                employee.setLastKnownName(name);
                repository.saveEmployee(employee);
            }
        });
    }
}
