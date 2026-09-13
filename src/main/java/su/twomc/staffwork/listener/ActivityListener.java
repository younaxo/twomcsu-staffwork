package su.twomc.staffwork.listener;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import su.twomc.staffwork.scheduler.PlatformScheduler;
import su.twomc.staffwork.service.AutoStatusService;
import su.twomc.staffwork.service.EmployeeService;

/**
 * Отслеживает активность игрока стандартными средствами Bukkit (движение, чат, команды,
 * взаимодействие) для сброса таймера AFK — без чтения сетевых пакетов, как и требуется для 0.1.
 *
 * <p>{@link PlayerMoveEvent} вызывается очень часто, поэтому фактическая работа с сотрудником
 * (асинхронный поиск + обновление) троттлится: не чаще, чем раз в {@link #THROTTLE_MILLIS} на
 * игрока, иначе пул асинхронных задач захлёбывался бы событиями движения.
 *
 * <p>Используется {@link AsyncPlayerChatEvent} (классический Spigot API), а не более новый
 * Paper-only {@code AsyncChatEvent} — так один и тот же класс слушателя работает и на Paper,
 * и на обычном Spigot без дублирования кода.
 */
public final class ActivityListener implements Listener {

    private static final long THROTTLE_MILLIS = 5000L;

    private final EmployeeService employeeService;
    private final AutoStatusService autoStatusService;
    private final PlatformScheduler scheduler;
    private final ConcurrentHashMap<UUID, Long> lastSubmitted = new ConcurrentHashMap<>();

    public ActivityListener(EmployeeService employeeService, AutoStatusService autoStatusService, PlatformScheduler scheduler) {
        this.employeeService = employeeService;
        this.autoStatusService = autoStatusService;
        this.scheduler = scheduler;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        markActive(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        markActive(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        markActive(event.getPlayer());
    }

    @SuppressWarnings("deprecation") // см. Javadoc класса — намеренный выбор ради совместимости со Spigot
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        markActive(event.getPlayer());
    }

    private void markActive(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long previous = lastSubmitted.get(uuid);
        if (previous != null && now - previous < THROTTLE_MILLIS) {
            return;
        }
        lastSubmitted.put(uuid, now);
        scheduler.runAsync(() -> employeeService
                .findEmployee(uuid)
                .ifPresent(employee -> autoStatusService.recordActivity(employee, Instant.now())));
    }
}
