package su.twomc.staffwork.config;

import java.util.Locale;
import java.util.Optional;

/** Поддерживаемые типы хранилища данных. */
public enum StorageType {
    YAML,
    SQLITE,
    H2,
    MYSQL;

    public static Optional<StorageType> fromConfig(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(StorageType.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
