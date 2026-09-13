package su.twomc.staffwork.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ValidationTest {

    @Test
    void rankIdIsNormalizedToLowerCase() {
        assertEquals("moderator", Validation.normalizeRankId("Moderator"));
    }

    @Test
    void rankIdRejectsInvalidCharacters() {
        assertThrows(IllegalArgumentException.class, () -> Validation.normalizeRankId("admin!"));
        assertFalse(Validation.isValidRankId("admin with spaces"));
        assertFalse(Validation.isValidRankId("../../etc"));
    }

    @Test
    void playerNameFollowsMinecraftRules() {
        assertTrue(Validation.isValidPlayerName("Notch"));
        assertTrue(Validation.isValidPlayerName("a_b_C_1"));
        assertFalse(Validation.isValidPlayerName(""));
        assertFalse(Validation.isValidPlayerName("way_too_long_for_mc"));
        assertFalse(Validation.isValidPlayerName("bad name"));
    }

    @Test
    void permissionNodeFormatIsEnforced() {
        assertTrue(Validation.isValidPermissionNode("tmc.staffwork.command.help"));
        assertTrue(Validation.isValidPermissionNode("tmc.staffwork.staff.rank.set"));
        assertTrue(Validation.isValidPermissionNode("tmc.staffwork.*"));
        assertFalse(Validation.isValidPermissionNode("tmc.staffwork"));
        assertFalse(Validation.isValidPermissionNode("tmc.other.thing"));
        assertFalse(Validation.isValidPermissionNode(null));
    }

    @Test
    void stripMarkupRemovesAngleBracketsUsedByMiniMessageTags() {
        assertEquals("‹red›hacked‹/red›", Validation.stripMarkup("<red>hacked</red>"));
    }

    @Test
    void truncateNeverExceedsMaxLength() {
        assertEquals("abc", Validation.truncate("abcdef", 3));
        assertEquals("ab", Validation.truncate("ab", 5));
    }
}
