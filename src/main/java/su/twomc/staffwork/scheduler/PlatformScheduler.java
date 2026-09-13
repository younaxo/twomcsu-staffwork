package su.twomc.staffwork.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

/**
 * Абстракция над планировщиками Bukkit/Paper/Folia. Бизнес-код никогда не обращается
 * к {@code Bukkit.getScheduler()} или региональным API напрямую — только через этот интерфейс,
 * что делает поведение корректным независимо от того, на каком ядре запущен сервер.
 *
 * <p>Правила использования: операции с конкретным игроком/сущностью — через
 * {@link #runForEntity}; операции с миром/чанком (например, чтение блока) — через
 * {@link #runForRegion}; действия, не привязанные к региону (регистрация слушателей, работа
 * с базой данных, сеть) — через {@link #runGlobal}/{@link #runAsync}. Обращения к базе данных
 * и сети всегда должны идти через {@link #runAsync}, а не через глобальный планировщик.
 */
public interface PlatformScheduler {

    /** {@code true}, если сервер работает на Folia (несколько независимых регионов-потоков). */
    boolean isFolia();

    /** Выполняет задачу в глобальном региональном потоке (или главном потоке на Bukkit/Spigot). */
    void runGlobal(Runnable task);

    /** Периодическая задача в глобальном региональном потоке. Периоды — в тиках (20 = 1 секунда). */
    CancellableTask runGlobalTimer(Runnable task, long delayTicks, long periodTicks);

    /** Выполняет задачу в общем пуле асинхронных потоков — единственное место для сети и SQL. */
    void runAsync(Runnable task);

    /** Периодическая асинхронная задача. Задержка и период — в миллисекундах. */
    CancellableTask runAsyncTimer(Runnable task, long delayMillis, long periodMillis);

    /**
     * Выполняет задачу в потоке, владеющем сущностью {@code entity} (Entity Scheduler на Folia).
     * {@code ifRetired} вызывается вместо задачи, если сущность будет удалена раньше выполнения
     * (например, игрок вышел с сервера) — может быть {@code null}.
     */
    void runForEntity(Entity entity, Runnable task, Runnable ifRetired);

    /** Выполняет задачу в потоке, владеющем регионом, которому принадлежит {@code location}. */
    void runForRegion(Location location, Runnable task);
}
