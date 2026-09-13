package su.twomc.staffwork.model;

import java.util.Locale;
import java.util.Optional;

/** Период, за который запрашивается статистика в /staffwork stats. */
public enum StatsPeriod {
    TODAY,
    WEEK,
    MONTH,
    ALL;

    public static Optional<StatsPeriod> fromArg(String arg) {
        if (arg == null) {
            return Optional.empty();
        }
        String normalized = arg.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "today", "day" -> Optional.of(TODAY);
            case "week" -> Optional.of(WEEK);
            case "month" -> Optional.of(MONTH);
            case "all" -> Optional.of(ALL);
            default -> Optional.empty();
        };
    }
}
