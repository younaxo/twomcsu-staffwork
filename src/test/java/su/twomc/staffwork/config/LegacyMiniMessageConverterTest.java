package su.twomc.staffwork.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LegacyMiniMessageConverterTest {

    private final LegacyMiniMessageConverter converter = new LegacyMiniMessageConverter();

    @Test
    void colorTagIsConvertedToLegacyCode() {
        assertEquals("§aHello", converter.toLegacy("<green>Hello"));
    }

    @Test
    void closingTagResetsFormatting() {
        assertEquals("§cRed§r plain", converter.toLegacy("<red>Red</red> plain"));
    }

    @Test
    void unknownTagIsIgnoredWithoutBreakingRestOfMessage() {
        assertEquals("beforeafter", converter.toLegacy("before<unknown_tag>after"));
    }

    @Test
    void formattingTagsAreSupported() {
        assertEquals("§lBold", converter.toLegacy("<bold>Bold"));
    }
}
