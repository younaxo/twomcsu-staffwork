package su.twomc.staffwork.scheduler;

import org.bukkit.plugin.Plugin;

/** Определяет, какая реализация {@link PlatformScheduler} доступна на текущем сервере. */
public final class SchedulerProvider {

    private SchedulerProvider() {}

    public static PlatformScheduler detect(Plugin plugin) {
        if (hasClass("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler")) {
            return new PaperPlatformScheduler(plugin, isFolia());
        }
        return new BukkitPlatformScheduler(plugin);
    }

    /** Официально рекомендованный способ определения Folia — наличие её специфичного класса ядра. */
    public static boolean isFolia() {
        return hasClass("io.papermc.paper.threadedregions.RegionizedServer");
    }

    private static boolean hasClass(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
