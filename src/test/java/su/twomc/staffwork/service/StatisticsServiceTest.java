package su.twomc.staffwork.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import su.twomc.staffwork.model.StaffStatus;
import su.twomc.staffwork.model.StatusInterval;
import su.twomc.staffwork.testutil.InMemoryStaffRepository;

class StatisticsServiceTest {

    private final UUID employee = UUID.randomUUID();

    private StatisticsService service() {
        return new StatisticsService(
                new InMemoryStaffRepository(), EnumSet.of(StaffStatus.WORKING, StaffStatus.MEETING, StaffStatus.TRAINING), ZoneOffset.UTC);
    }

    @Test
    void onlyCountedStatusesContributeToTotal() {
        StatisticsService service = service();
        Instant start = Instant.parse("2025-01-01T10:00:00Z");
        Instant rangeStart = Instant.parse("2025-01-01T00:00:00Z");
        Instant rangeEnd = Instant.parse("2025-01-02T00:00:00Z");

        List<StatusInterval> intervals = List.of(
                new StatusInterval(1, employee, null, StaffStatus.WORKING, start, start.plus(Duration.ofHours(2))),
                new StatusInterval(
                        2, employee, null, StaffStatus.AFK, start.plus(Duration.ofHours(2)), start.plus(Duration.ofHours(3))),
                new StatusInterval(
                        3, employee, null, StaffStatus.MEETING, start.plus(Duration.ofHours(3)), start.plus(Duration.ofHours(4))));

        Duration total = service.sumCountedDuration(intervals, rangeStart, rangeEnd, start.plus(Duration.ofHours(5)));

        assertEquals(Duration.ofHours(3), total);
    }

    @Test
    void openIntervalIsClippedAtNow() {
        StatisticsService service = service();
        Instant start = Instant.parse("2025-01-01T10:00:00Z");
        Instant now = start.plus(Duration.ofMinutes(90));
        List<StatusInterval> intervals = List.of(new StatusInterval(1, employee, null, StaffStatus.WORKING, start, null));

        Duration total = service.sumCountedDuration(
                intervals, Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2025-01-02T00:00:00Z"), now);

        assertEquals(Duration.ofMinutes(90), total);
    }

    @Test
    void intervalCrossingMidnightIsSplitBetweenTwoDays() {
        StatisticsService service = service();
        // Сотрудник работал с 23:00 первого дня до 01:00 второго — час должен попасть в статистику
        // каждого из дней, а не быть посчитан целиком в одном из них или потерян.
        Instant start = Instant.parse("2025-01-01T23:00:00Z");
        Instant end = Instant.parse("2025-01-02T01:00:00Z");
        StatusInterval interval = new StatusInterval(1, employee, null, StaffStatus.WORKING, start, end);

        Duration firstDay = service.sumCountedDuration(
                List.of(interval), Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2025-01-02T00:00:00Z"), end);
        Duration secondDay = service.sumCountedDuration(
                List.of(interval), Instant.parse("2025-01-02T00:00:00Z"), Instant.parse("2025-01-03T00:00:00Z"), end);

        assertEquals(Duration.ofHours(1), firstDay);
        assertEquals(Duration.ofHours(1), secondDay);
    }
}
