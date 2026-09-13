package su.twomc.staffwork.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Резервная реализация для чистого Spigot без Paper — региональных планировщиков там нет,
 * поэтому "региональные" и "сущностные" задачи просто выполняются в главном потоке сервера
 * через классический {@code BukkitScheduler}, что было единственно возможным поведением
 * на этих ядрах и до появления Folia.
 */
public final class BukkitPlatformScheduler implements PlatformScheduler {

    private final Plugin plugin;

    public BukkitPlatformScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isFolia() {
        return false;
    }

    @Override
    public void runGlobal(Runnable task) {
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    @Override
    public CancellableTask runGlobalTimer(Runnable task, long delayTicks, long periodTicks) {
        BukkitTask bukkitTask = plugin.getServer().getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        return bukkitTask::cancel;
    }

    @Override
    public void runAsync(Runnable task) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
    }

    @Override
    public CancellableTask runAsyncTimer(Runnable task, long delayMillis, long periodMillis) {
        long delayTicks = Math.max(1, delayMillis / 50);
        long periodTicks = Math.max(1, periodMillis / 50);
        BukkitTask bukkitTask =
                plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
        return bukkitTask::cancel;
    }

    @Override
    public void runForEntity(Entity entity, Runnable task, Runnable ifRetired) {
        runGlobal(task);
    }

    @Override
    public void runForRegion(Location location, Runnable task) {
        runGlobal(task);
    }
}
