package su.twomc.staffwork.repository.sql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import java.util.logging.Logger;

/** Встроенная файловая база данных H2 — альтернатива SQLite без внешних нативных библиотек. */
public final class H2StaffRepository extends AbstractSqlStaffRepository {

    public H2StaffRepository(File databaseFile, Logger logger) {
        super(createDataSource(databaseFile), SqlDialect.H2, logger);
    }

    private static HikariDataSource createDataSource(File databaseFile) {
        File parent = databaseFile.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.h2.Driver");
        config.setJdbcUrl("jdbc:h2:file:" + databaseFile.getAbsolutePath());
        config.setMaximumPoolSize(4);
        config.setPoolName("tmc-staffwork-h2");
        return new HikariDataSource(config);
    }
}
