package su.twomc.staffwork.listener;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import su.twomc.staffwork.command.Permissions;
import su.twomc.staffwork.config.ConfigManager;
import su.twomc.staffwork.config.MessagesManager;
import su.twomc.staffwork.model.Employee;
import su.twomc.staffwork.scheduler.PlatformScheduler;
import su.twomc.staffwork.service.AutoStatusService;
import su.twomc.staffwork.service.EmployeeService;
import su.twomc.staffwork.telegram.NotificationDispatcher;

/**
 * Реагирует на вход/выход игрока: применяет автоматические статусы, шлёт уведомления другим
 * сотрудникам онлайн и в Telegram. Вся работа с хранилищем выполняется асинхронно — обработчики
 * событий сами по себе только считывают неизменяемые поля игрока (UUID, ник) и планируют задачу.
 */
public final class PlayerConnectionListener implements Listener {

    private final EmployeeService employeeService;
    private final AutoStatusService autoStatusService;
    private final NotificationDispatcher notificationDispatcher;
    private final PlatformScheduler scheduler;
    private final ConfigManager configManager;
    private final MessagesManager messages;

    public PlayerConnectionListener(
            EmployeeService employeeService,
            AutoStatusService autoStatusService,
            NotificationDispatcher notificationDispatcher,
            PlatformScheduler scheduler,
            ConfigManager configManager,
            MessagesManager messages) {
        this.employeeService = employeeService;
        this.autoStatusService = autoStatusService;
        this.notificationDispatcher = notificationDispatcher;
        this.scheduler = scheduler;
        this.configManager = configManager;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        scheduler.runAsync(() -> employeeService.findEmployee(uuid).ifPresent(employee -> {
            employeeService.updateLastKnownName(uuid, name);
            autoStatusService.onJoin(employee, Instant.now(), configManager.pluginConfig().serverId());
            notificationDispatcher.notifyLogin(employee);
            broadcastToStaff("notify.login", employee, uuid);
        }));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        scheduler.runAsync(() -> employeeService.findEmployee(uuid).ifPresent(employee -> {
            autoStatusService.onQuit(employee, Instant.now());
            notificationDispatcher.notifyLogout(employee);
            broadcastToStaff("notify.logout", employee, uuid);
        }));
    }

    private void broadcastToStaff(String messageKey, Employee employee, UUID exclude) {
        Map<String, String> placeholders = Map.of("player", employee.lastKnownName());
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(exclude) || !online.hasPermission(Permissions.STAFF_LIST)) {
                continue;
            }
            scheduler.runForEntity(online, () -> messages.send(online, messageKey, placeholders), null);
        }
    }
}
