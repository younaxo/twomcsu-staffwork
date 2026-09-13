package su.twomc.staffwork.config;

import java.io.File;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * Загрузка {@code config.yml} и {@code ranks.yml}, безопасный reload и заготовка миграции версий
 * конфигурации. В версии 0.1 существует только версия конфигурации {@value PluginConfig#CURRENT_CONFIG_VERSION},
 * миграция сведена к дозаполнению отсутствующих ключей значениями по умолчанию из jar.
 */
public final class ConfigManager {

    private final Plugin plugin;
    private PluginConfig pluginConfig;
    private RanksConfig ranksConfig;

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        migrateIfNeeded();

        File ranksFile = new File(plugin.getDataFolder(), "ranks.yml");
        if (!ranksFile.exists()) {
            plugin.saveResource("ranks.yml", false);
        }
        YamlConfiguration ranksYaml = YamlConfiguration.loadConfiguration(ranksFile);

        this.pluginConfig = PluginConfig.load(plugin.getConfig(), plugin.getLogger());
        this.ranksConfig = RanksConfig.load(ranksYaml);
    }

    private void migrateIfNeeded() {
        int fileVersion = plugin.getConfig().getInt("config-version", PluginConfig.CURRENT_CONFIG_VERSION);
        if (fileVersion < PluginConfig.CURRENT_CONFIG_VERSION) {
            plugin.getLogger()
                    .info("Обнаружена устаревшая версия config.yml (" + fileVersion
                            + "), недостающие ключи будут дополнены значениями по умолчанию.");
        }
        plugin.getConfig().options().copyDefaults(true);
        plugin.getConfig().set("config-version", PluginConfig.CURRENT_CONFIG_VERSION);
        plugin.saveConfig();
    }

    /** {@code true}, если тип хранилища не совпадает с уже применённым — требует перезапуска, а не reload. */
    public boolean storageTypeChanged() {
        PluginConfig fresh = PluginConfig.load(plugin.getConfig(), plugin.getLogger());
        return pluginConfig != null && fresh.storageType() != pluginConfig.storageType();
    }

    public PluginConfig pluginConfig() {
        return pluginConfig;
    }

    public RanksConfig ranksConfig() {
        return ranksConfig;
    }
}
