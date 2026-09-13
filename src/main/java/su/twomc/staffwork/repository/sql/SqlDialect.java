package su.twomc.staffwork.repository.sql;

/** Диалект SQL — различия между поддерживаемыми базами сведены к минимуму намеренно. */
public enum SqlDialect {
    SQLITE {
        @Override
        public String autoIncrementIdColumn() {
            return "id INTEGER PRIMARY KEY AUTOINCREMENT";
        }
    },
    H2 {
        @Override
        public String autoIncrementIdColumn() {
            return "id BIGINT AUTO_INCREMENT PRIMARY KEY";
        }
    },
    MYSQL {
        @Override
        public String autoIncrementIdColumn() {
            return "id BIGINT AUTO_INCREMENT PRIMARY KEY";
        }
    };

    public abstract String autoIncrementIdColumn();
}
