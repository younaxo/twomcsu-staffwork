package su.twomc.staffwork.util;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Простой скользящий лимитер попыток по ключу (например, UUID игрока). Используется для защиты
 * привязки Telegram и других чувствительных операций от перебора. Потокобезопасен.
 */
public final class RateLimiter {

    private record Window(AtomicInteger count, Instant resetAt) {}

    private final Map<Object, Window> windows = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final Duration window;

    public RateLimiter(int maxAttempts, Duration window) {
        this.maxAttempts = maxAttempts;
        this.window = window;
    }

    /** Возвращает {@code true}, если попытка допустима, и сразу учитывает её. */
    public boolean tryAcquire(Object key) {
        Instant now = Instant.now();
        Window current = windows.compute(key, (k, existing) -> {
            if (existing == null || now.isAfter(existing.resetAt())) {
                return new Window(new AtomicInteger(0), now.plus(window));
            }
            return existing;
        });
        return current.count().incrementAndGet() <= maxAttempts;
    }

    public void reset(Object key) {
        windows.remove(key);
    }

    /** Периодическая очистка устаревших окон, чтобы карта не росла бесконечно. */
    public void cleanup() {
        Instant now = Instant.now();
        windows.entrySet().removeIf(e -> now.isAfter(e.getValue().resetAt()));
    }
}
