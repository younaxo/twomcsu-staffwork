package su.twomc.staffwork.repository.sql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.logging.Logger;

/**
 * Реализация для MySQL/MariaDB через единый драйвер MariaDB Connector/J, совместимый с обоими
 * серверами. Подходит для крупных серверов и сетей, где несколько серверов делят одну базу
 * (см. настраиваемый {@code server-id} в конфигурации — сессии и интервалы статусов помечаются им).
 */
public final class MySqlStaffRepository extends AbstractSqlStaffRepository {

    public MySqlStaffRepository(
            String host,
            int port,
            String database,
            String username,
            String password,
            boolean useSsl,
            int maxPoolSize,
            Logger logger) {
        super(createDataSource(host, port, database, username, password, useSsl, maxPoolSize), SqlDialect.MYSQL, logger);
    }

    private static HikariDataSource createDataSource(
            String host, int port, String database, String username, String password, boolean useSsl, int maxPoolSize) {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.mariadb.jdbc.Driver");
        config.setJdbcUrl("jdbc:mariadb://" + host + ":" + port + "/" + database
                + "?useSSL=" + useSsl + "&characterEncoding=utf8&useUnicode=true");
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(Math.max(1, maxPoolSize));
        config.setPoolName("tmc-staffwork-mysql");
        config.setInitializationFailTimeout(-1);
        return new HikariDataSource(config);
    }
}
