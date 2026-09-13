package su.twomc.staffwork.config;

/** Параметры подключения к MySQL/MariaDB. Пароль никогда не должен попадать в логи. */
public record MySqlSettings(
        String host, int port, String database, String username, String password, boolean useSsl, int poolSize) {}
