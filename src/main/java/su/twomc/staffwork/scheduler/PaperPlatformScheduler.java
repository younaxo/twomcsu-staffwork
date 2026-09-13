package su.twomc.staffwork.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.concurrent.TimeUnit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/**
 * Реализация на основе региональных планировщиков Paper API ({@code GlobalRegionScheduler},
 * {@code RegionScheduler}, {@code AsyncScheduler}, {@code EntityScheduler}). Paper предоставляет
 * эти методы как на обычных Paper-серверах (где они прозрачно исполняются в главном потоке),
 * так и на Folia (где они действительно распределяют задачи по потокам регионов) — поэтому один
 * и тот же код планировщика корректен на обеих платформах.
 */
public final class PaperPlatformScheduler implements PlatformScheduler {

    private final Plugin plugin;
    private final boolean folia;

    public PaperPlatformScheduler(Plugin plugin, boolean folia) {
        this.plugin = plugin;
        this.folia = folia;
    }

    @Override
    public boolean isFolia() {
        return folia;
    }

    @Override
    public void runGlobal(Runnable task) {
        plugin.getServer().getGlobalRegionScheduler().run(plugin, ignored -> task.run());
    }

    @Override
    public CancellableTask runGlobalTimer(Runnable task, long delayTicks, long periodTicks) {
        ScheduledTask scheduled = plugin.getServer()
                .getGlobalRegionScheduler()
                .runAtFixedRate(plugin, ignored -> task.run(), Math.max(1, delayTicks), Math.max(1, periodTicks));
        return scheduled::cancel;
    }

    @Override
    public void runAsync(Runnable task) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, ignored -> task.run());
    }

    @Override
    public CancellableTask runAsyncTimer(Runnable task, long delayMillis, long periodMillis) {
        ScheduledTask scheduled = plugin.getServer()
                .getAsyncScheduler()
                .runAtFixedRate(
                        plugin,
                        ignored -> task.run(),
                        Math.max(1, delayMillis),
                        Math.max(1, periodMillis),
                        TimeUnit.MILLISECONDS);
        return scheduled::cancel;
    }

    @Override
    public void runForEntity(Entity entity, Runnable task, Runnable ifRetired) {
        entity.getScheduler().run(plugin, ignored -> task.run(), ifRetired);
    }

    @Override
    public void runForRegion(Location location, Runnable task) {
        plugin.getServer().getRegionScheduler().run(plugin, location, ignored -> task.run());
    }
}
