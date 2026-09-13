package su.twomc.staffwork.config;

import java.time.Duration;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import su.twomc.staffwork.model.StaffStatus;

/** Типизированное представление {@code config.yml}, полученное после парсинга и валидации. */
public final class PluginConfig {

    public static final int CURRENT_CONFIG_VERSION = 1;

    private final int configVersion;
    private final String serverId;
    private final ZoneId zoneId;
    private final StorageType storageType;
    private final String yamlFile;
    private final String sqliteFile;
    private final String h2File;
    private final MySqlSettings mySqlSettings;
    private final Set<StaffStatus> countedStatuses;
    private final AutoStatusSettings autoStatusSettings;
    private final Map<StaffStatus, String> statusColors;
    private final TelegramSettings telegramSettings;
    private final int heartbeatIntervalSeconds;

    private PluginConfig(
            int configVersion,
            String serverId,
            ZoneId zoneId,
            StorageType storageType,
            String yamlFile,
            String sqliteFile,
            String h2File,
            MySqlSettings mySqlSettings,
            Set<StaffStatus> countedStatuses,
            AutoStatusSettings autoStatusSettings,
            Map<StaffStatus, String> statusColors,
            TelegramSettings telegramSettings,
            int heartbeatIntervalSeconds) {
        this.configVersion = configVersion;
        this.serverId = serverId;
        this.zoneId = zoneId;
        this.storageType = storageType;
        this.yamlFile = yamlFile;
        this.sqliteFile = sqliteFile;
        this.h2File = h2File;
        this.mySqlSettings = mySqlSettings;
        this.countedStatuses = countedStatuses;
        this.autoStatusSettings = autoStatusSettings;
        this.statusColors = statusColors;
        this.telegramSettings = telegramSettings;
        this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
    }

    public static PluginConfig load(FileConfiguration c, Logger logger) {
        int configVersion = c.getInt("config-version", CURRENT_CONFIG_VERSION);

        String serverId = c.getString("server-id", "server1");
        if (!serverId.matches("[A-Za-z0-9_-]+")) {
            logger.warning("server-id содержит недопустимые символы, использую значение по умолчанию 'server1'");
            serverId = "server1";
        }

        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(c.getString("timezone", "UTC"));
        } catch (Exception e) {
            logger.warning("Некорректный часовой пояс в конфигурации, использую UTC");
            zoneId = ZoneId.of("UTC");
        }

        StorageType storageType = StorageType.fromConfig(c.getString("storage.type", "sqlite"))
                .orElseGet(() -> {
                    logger.warning("Неизвестный тип хранилища, использую sqlite");
                    return StorageType.SQLITE;
                });

        String yamlFile = c.getString("storage.yaml.file", "data.yml");
        String sqliteFile = c.getString("storage.sqlite.file", "database.db");
        String h2File = c.getString("storage.h2.file", "database");
        MySqlSettings mySqlSettings = new MySqlSettings(
                c.getString("storage.mysql.host", "localhost"),
                c.getInt("storage.mysql.port", 3306),
                c.getString("storage.mysql.database", "tmc_staffwork"),
                c.getString("storage.mysql.username", "root"),
                c.getString("storage.mysql.password", ""),
                c.getBoolean("storage.mysql.use-ssl", false),
                c.getInt("storage.mysql.pool-size", 10));

        Set<StaffStatus> countedStatuses = parseCountedStatuses(c, logger);
        AutoStatusSettings autoStatusSettings = parseAutoStatus(c, logger);
        Map<StaffStatus, String> statusColors = parseStatusColors(c);
        TelegramSettings telegramSettings = parseTelegram(c);
        int heartbeatIntervalSeconds = Math.max(5, c.getInt("recovery.heartbeat-interval-seconds", 30));

        return new PluginConfig(
                configVersion,
                serverId,
                zoneId,
                storageType,
                yamlFile,
                sqliteFile,
                h2File,
                mySqlSettings,
                countedStatuses,
                autoStatusSettings,
                statusColors,
                telegramSettings,
                heartbeatIntervalSeconds);
    }

    private static Set<StaffStatus> parseCountedStatuses(FileConfiguration c, Logger logger) {
        Set<StaffStatus> result = EnumSet.noneOf(StaffStatus.class);
        for (String raw : c.getStringList("work-time.counted-statuses")) {
            StaffStatus.fromId(raw)
                    .ifPresentOrElse(
                            result::add,
                            () -> logger.warning("Неизвестный статус в work-time.counted-statuses: " + raw));
        }
        if (result.isEmpty()) {
            for (StaffStatus status : StaffStatus.values()) {
                if (status.countsAsWorkByDefault()) {
                    result.add(status);
                }
            }
        }
        return result;
    }

    private static AutoStatusSettings parseAutoStatus(FileConfiguration c, Logger logger) {
        String joinRaw = c.getString("auto-status.status-on-join", "WORKING");
        StaffStatus statusOnJoin =
                (joinRaw == null || joinRaw.isBlank()) ? null : StaffStatus.fromId(joinRaw).orElse(StaffStatus.WORKING);

        boolean autoStart = c.getBoolean("auto-status.auto-start-session-on-join", true);

        String quitRaw = c.getString("auto-status.on-quit", "stop-session").toLowerCase(Locale.ROOT);
        AutoStatusSettings.QuitBehavior quitBehavior =
                switch (quitRaw) {
                    case "set-status" -> AutoStatusSettings.QuitBehavior.SET_STATUS;
                    case "nothing" -> AutoStatusSettings.QuitBehavior.NOTHING;
                    default -> AutoStatusSettings.QuitBehavior.STOP_SESSION;
                };
        StaffStatus statusOnQuit =
                StaffStatus.fromId(c.getString("auto-status.status-on-quit", "OFF_DUTY")).orElse(StaffStatus.OFF_DUTY);

        boolean afkEnabled = c.getBoolean("auto-status.afk.enabled", true);
        int afkThresholdSeconds = Math.max(10, c.getInt("auto-status.afk.threshold-seconds", 300));
        boolean afkAutoReturn = c.getBoolean("auto-status.afk.auto-return", true);

        return new AutoStatusSettings(
                statusOnJoin,
                autoStart,
                quitBehavior,
                statusOnQuit,
                afkEnabled,
                Duration.ofSeconds(afkThresholdSeconds),
                afkAutoReturn);
    }

    private static Map<StaffStatus, String> parseStatusColors(FileConfiguration c) {
        Map<StaffStatus, String> colors = new HashMap<>();
        colors.put(StaffStatus.WORKING, "green");
        colors.put(StaffStatus.AFK, "yellow");
        colors.put(StaffStatus.OFF_DUTY, "red");
        colors.put(StaffStatus.BREAK, "gray");
        colors.put(StaffStatus.MEETING, "gray");
        colors.put(StaffStatus.TRAINING, "gray");
        ConfigurationSection section = c.getConfigurationSection("placeholders.status-colors");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                StaffStatus.fromId(key).ifPresent(status -> colors.put(status, section.getString(key)));
            }
        }
        return colors;
    }

    private static TelegramSettings parseTelegram(FileConfiguration c) {
        boolean enabled = c.getBoolean("telegram.enabled", false);
        String token = c.getString("telegram.bot-token", "");
        int pollInterval = Math.max(1, c.getInt("telegram.poll-interval-seconds", 3));
        Duration linkTtl = Duration.ofSeconds(Math.max(30, c.getInt("telegram.link-code-ttl-seconds", 300)));
        int rateLimitMax = Math.max(1, c.getInt("telegram.rate-limit.max-attempts", 5));
        Duration rateLimitWindow = Duration.ofSeconds(Math.max(5, c.getInt("telegram.rate-limit.window-seconds", 60)));

        Set<String> events = new HashSet<>();
        ConfigurationSection eventsSection = c.getConfigurationSection("telegram.events");
        if (eventsSection != null) {
            for (String key : eventsSection.getKeys(false)) {
                if (eventsSection.getBoolean(key)) {
                    events.add(key.toLowerCase(Locale.ROOT));
                }
            }
        }

        Map<String, String> templates = new LinkedHashMap<>();
        ConfigurationSection templatesSection = c.getConfigurationSection("telegram.messages");
        if (templatesSection != null) {
            for (String key : templatesSection.getKeys(false)) {
                templates.put(key.toLowerCase(Locale.ROOT), templatesSection.getString(key));
            }
        }

        return new TelegramSettings(enabled, token, pollInterval, linkTtl, rateLimitMax, rateLimitWindow, events, templates);
    }

    public int configVersion() {
        return configVersion;
    }

    public String serverId() {
        return serverId;
    }

    public ZoneId zoneId() {
        return zoneId;
    }

    public StorageType storageType() {
        return storageType;
    }

    public String yamlFile() {
        return yamlFile;
    }

    public String sqliteFile() {
        return sqliteFile;
    }

    public String h2File() {
        return h2File;
    }

    public MySqlSettings mySqlSettings() {
        return mySqlSettings;
    }

    public Set<StaffStatus> countedStatuses() {
        return countedStatuses;
    }

    public AutoStatusSettings autoStatusSettings() {
        return autoStatusSettings;
    }

    public Map<StaffStatus, String> statusColors() {
        return statusColors;
    }

    public TelegramSettings telegramSettings() {
        return telegramSettings;
    }

    public int heartbeatIntervalSeconds() {
        return heartbeatIntervalSeconds;
    }
}
