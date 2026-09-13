package su.twomc.staffwork.repository.sql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import java.util.logging.Logger;

/**
 * Хранилище по умолчанию для средних серверов. Пул соединений намеренно ограничен одним
 * соединением: SQLite допускает только одного писателя одновременно, и последовательный доступ
 * через единственное соединение в пуле надёжнее, чем ловить "database is locked" под нагрузкой.
 */
public final class SqliteStaffRepository extends AbstractSqlStaffRepository {

    public SqliteStaffRepository(File databaseFile, Logger logger) {
        super(createDataSource(databaseFile), SqlDialect.SQLITE, logger);
    }

    private static HikariDataSource createDataSource(File databaseFile) {
        File parent = databaseFile.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.sqlite.JDBC");
        config.setJdbcUrl("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        config.setMaximumPoolSize(1);
        config.setPoolName("tmc-staffwork-sqlite");
        config.addDataSourceProperty("foreign_keys", "true");
        return new HikariDataSource(config);
    }
}
