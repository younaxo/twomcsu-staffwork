package su.twomc.staffwork.service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import su.twomc.staffwork.model.StaffStatus;
import su.twomc.staffwork.model.StatsPeriod;
import su.twomc.staffwork.model.StatusInterval;
import su.twomc.staffwork.repository.StaffRepository;
import su.twomc.staffwork.util.TimeUtil;

/**
 * Расчёт рабочего времени по сохранённым интервалам статусов. Время не накапливается построчно
 * в базе — оно всегда пересчитывается по временным меткам начала/конца интервалов, что делает
 * расчёт устойчивым к перезапускам и позволяет корректно учитывать переходы через полночь.
 */
public final class StatisticsService {

    private final StaffRepository repository;
    private volatile Set<StaffStatus> countedStatuses;
    private volatile ZoneId zone;

    public StatisticsService(StaffRepository repository, Set<StaffStatus> countedStatuses, ZoneId zone) {
        this.repository = repository;
        this.countedStatuses = countedStatuses;
        this.zone = zone;
    }

    /** Применяет новые настройки без пересоздания сервиса — используется при {@code /staffwork reload}. */
    public void updateSettings(Set<StaffStatus> countedStatuses, ZoneId zone) {
        this.countedStatuses = countedStatuses;
        this.zone = zone;
    }

    public Duration calculate(UUID employeeUuid, StatsPeriod period, Instant employeeAddedAt, Instant now) {
        Instant[] bounds = TimeUtil.periodBounds(period, now, zone, employeeAddedAt);
        List<StatusInterval> intervals = repository.findIntervals(employeeUuid, bounds[0], bounds[1]);
        return sumCountedDuration(intervals, bounds[0], bounds[1], now);
    }

    /**
     * Чистая функция суммирования засчитываемого времени по списку интервалов, обрезанных
     * границами [rangeStart, rangeEnd). Открытые интервалы ({@code endedAt == null}) считаются
     * длящимися до {@code now}. Вынесена отдельно, чтобы её можно было протестировать без
     * обращения к хранилищу.
     */
    public Duration sumCountedDuration(List<StatusInterval> intervals, Instant rangeStart, Instant rangeEnd, Instant now) {
        Duration total = Duration.ZERO;
        for (StatusInterval interval : intervals) {
            if (!countedStatuses.contains(interval.status())) {
                continue;
            }
            Instant start = interval.startedAt().isBefore(rangeStart) ? rangeStart : interval.startedAt();
            Instant end = interval.effectiveEnd(now);
            if (end.isAfter(rangeEnd)) {
                end = rangeEnd;
            }
            if (end.isAfter(start)) {
                total = total.plus(Duration.between(start, end));
            }
        }
        return total;
    }

    public ZoneId zone() {
        return zone;
    }

    public Set<StaffStatus> countedStatuses() {
        return countedStatuses;
    }
}
