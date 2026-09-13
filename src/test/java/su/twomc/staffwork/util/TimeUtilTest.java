package su.twomc.staffwork.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;
import su.twomc.staffwork.model.StatsPeriod;

class TimeUtilTest {

    @Test
    void todayBoundsCoverFullLocalDay() {
        ZoneId moscow = ZoneId.of("Europe/Moscow");
        Instant now = ZonedDateTime.of(2025, 6, 15, 23, 30, 0, 0, moscow).toInstant();

        Instant[] bounds = TimeUtil.periodBounds(StatsPeriod.TODAY, now, moscow, null);

        assertEquals(ZonedDateTime.of(2025, 6, 15, 0, 0, 0, 0, moscow).toInstant(), bounds[0]);
        assertEquals(ZonedDateTime.of(2025, 6, 16, 0, 0, 0, 0, moscow).toInstant(), bounds[1]);
    }

    @Test
    void differentTimeZonesProduceDifferentDayBoundaries() {
        Instant now = Instant.parse("2025-06-15T23:30:00Z");

        Instant[] utcBounds = TimeUtil.periodBounds(StatsPeriod.TODAY, now, ZoneOffset.UTC, null);
        Instant[] tokyoBounds = TimeUtil.periodBounds(StatsPeriod.TODAY, now, ZoneId.of("Asia/Tokyo"), null);

        // В Токио в этот момент UTC уже наступили следующие сутки — границы дня должны отличаться.
        assertEquals(Instant.parse("2025-06-15T00:00:00Z"), utcBounds[0]);
        assertEquals(Instant.parse("2025-06-15T15:00:00Z"), tokyoBounds[0]);
    }

    @Test
    void weekStartsOnMonday() {
        ZoneId utc = ZoneOffset.UTC;
        // 2025-06-18 — среда.
        Instant now = ZonedDateTime.of(2025, 6, 18, 12, 0, 0, 0, utc).toInstant();

        Instant[] bounds = TimeUtil.periodBounds(StatsPeriod.WEEK, now, utc, null);

        assertEquals(ZonedDateTime.of(2025, 6, 16, 0, 0, 0, 0, utc).toInstant(), bounds[0]);
        assertEquals(ZonedDateTime.of(2025, 6, 23, 0, 0, 0, 0, utc).toInstant(), bounds[1]);
    }

    @Test
    void monthBoundsCoverCalendarMonth() {
        ZoneId utc = ZoneOffset.UTC;
        Instant now = ZonedDateTime.of(2025, 2, 10, 0, 0, 0, 0, utc).toInstant();

        Instant[] bounds = TimeUtil.periodBounds(StatsPeriod.MONTH, now, utc, null);

        assertEquals(ZonedDateTime.of(2025, 2, 1, 0, 0, 0, 0, utc).toInstant(), bounds[0]);
        assertEquals(ZonedDateTime.of(2025, 3, 1, 0, 0, 0, 0, utc).toInstant(), bounds[1]);
    }

    @Test
    void allTimeUsesEmployeeAddedAtAsLowerBound() {
        ZoneId utc = ZoneOffset.UTC;
        Instant addedAt = Instant.parse("2024-01-01T00:00:00Z");
        Instant now = Instant.parse("2025-01-01T00:00:00Z");

        Instant[] bounds = TimeUtil.periodBounds(StatsPeriod.ALL, now, utc, addedAt);

        assertEquals(addedAt, bounds[0]);
        assertEquals(now, bounds[1]);
    }

    @Test
    void formatDurationHandlesZeroAndHours() {
        assertEquals("0м", TimeUtil.formatDuration(Duration.ZERO));
        assertEquals("45м", TimeUtil.formatDuration(Duration.ofMinutes(45)));
        assertEquals("2ч 5м", TimeUtil.formatDuration(Duration.ofMinutes(125)));
    }
}
