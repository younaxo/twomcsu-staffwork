package su.twomc.staffwork.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ColorCodesTest {

    @Test
    void knownColorNameResolvesToLegacyCode() {
        assertEquals("§a", ColorCodes.legacy("green"));
        assertEquals("§c", ColorCodes.legacy("RED"));
    }

    @Test
    void unknownColorNameResolvesToEmptyString() {
        assertEquals("", ColorCodes.legacy("not-a-color"));
        assertEquals("", ColorCodes.legacy(null));
    }

    @Test
    void miniMessageTagIsProducedOnlyForKnownColors() {
        assertEquals("<green>", ColorCodes.miniMessageTag("green"));
        assertEquals("", ColorCodes.miniMessageTag("not-a-color"));
    }
}
