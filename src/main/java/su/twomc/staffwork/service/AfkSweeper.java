package su.twomc.staffwork.service;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import java.util.function.Supplier;
import su.twomc.staffwork.model.Employee;

/**
 * Периодическая проверка бездействия онлайн-сотрудников. Не содержит Bukkit-вызовов — список
 * UUID онлайн-игроков передаётся снаружи, что делает класс проверяемым модульными тестами.
 */
public final class AfkSweeper implements Runnable {

    private final EmployeeService employeeService;
    private final AutoStatusService autoStatusService;
    private final Supplier<Collection<UUID>> onlinePlayers;

    public AfkSweeper(EmployeeService employeeService, AutoStatusService autoStatusService, Supplier<Collection<UUID>> onlinePlayers) {
        this.employeeService = employeeService;
        this.autoStatusService = autoStatusService;
        this.onlinePlayers = onlinePlayers;
    }

    @Override
    public void run() {
        Instant now = Instant.now();
        for (UUID uuid : onlinePlayers.get()) {
            employeeService.findEmployee(uuid).ifPresent(employee -> checkOne(employee, now));
        }
    }

    private void checkOne(Employee employee, Instant now) {
        autoStatusService.checkAfk(employee, now);
    }
}
