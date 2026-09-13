package su.twomc.staffwork.util;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import su.twomc.staffwork.model.StatsPeriod;

/**
 * Хранение времени всегда в UTC ({@link Instant}); часовой пояс сервера используется только
 * здесь — для построения границ периодов (сегодня/неделя/месяц) и форматирования вывода.
 */
public final class TimeUtil {

    private TimeUtil() {}

    /**
     * Возвращает полуоткрытый интервал [start, end) в UTC для заданного периода,
     * рассчитанный относительно {@code now} в часовом поясе {@code zone}.
     */
    public static Instant[] periodBounds(StatsPeriod period, Instant now, ZoneId zone, Instant employeeAddedAt) {
        ZonedDateTime nowZoned = now.atZone(zone);
        return switch (period) {
            case TODAY -> {
                ZonedDateTime start = nowZoned.toLocalDate().atStartOfDay(zone);
                yield new Instant[] {start.toInstant(), start.plusDays(1).toInstant()};
            }
            case WEEK -> {
                LocalDate monday = nowZoned.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                ZonedDateTime start = monday.atStartOfDay(zone);
                yield new Instant[] {start.toInstant(), start.plusWeeks(1).toInstant()};
            }
            case MONTH -> {
                ZonedDateTime start =
                        nowZoned.toLocalDate().withDayOfMonth(1).atStartOfDay(zone);
                yield new Instant[] {start.toInstant(), start.plusMonths(1).toInstant()};
            }
            case ALL -> {
                Instant start = employeeAddedAt != null && employeeAddedAt.isBefore(now) ? employeeAddedAt : Instant.EPOCH;
                yield new Instant[] {start, now};
            }
        };
    }

    /** Форматирует продолжительность как "Nч Nм", а для нулевой/отрицательной — "0м". */
    public static String formatDuration(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return "0м";
        }
        long totalMinutes = duration.toMinutes();
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours == 0) {
            return minutes + "м";
        }
        return hours + "ч " + minutes + "м";
    }
}
