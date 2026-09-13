package su.twomc.staffwork.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Разбор аргументов /staffwork status и /staffwork stats должен принимать только известные значения. */
class CommandArgumentParsingTest {

    @Test
    void statsPeriodAcceptsKnownAliasesCaseInsensitively() {
        assertEquals(StatsPeriod.TODAY, StatsPeriod.fromArg("today").orElseThrow());
        assertEquals(StatsPeriod.TODAY, StatsPeriod.fromArg("DAY").orElseThrow());
        assertEquals(StatsPeriod.WEEK, StatsPeriod.fromArg("Week").orElseThrow());
        assertEquals(StatsPeriod.MONTH, StatsPeriod.fromArg("month").orElseThrow());
        assertEquals(StatsPeriod.ALL, StatsPeriod.fromArg("all").orElseThrow());
    }

    @Test
    void statsPeriodRejectsUnknownInput() {
        assertTrue(StatsPeriod.fromArg("yesterday").isEmpty());
        assertTrue(StatsPeriod.fromArg("<red>hack").isEmpty());
        assertTrue(StatsPeriod.fromArg(null).isEmpty());
    }

    @Test
    void staffStatusAcceptsOnlyDeclaredNames() {
        assertEquals(StaffStatus.WORKING, StaffStatus.fromId("working").orElseThrow());
        assertEquals(StaffStatus.OFF_DUTY, StaffStatus.fromId("OFF_DUTY").orElseThrow());
        assertTrue(StaffStatus.fromId("vacation").isEmpty());
        assertTrue(StaffStatus.fromId("").isEmpty());
    }
}
