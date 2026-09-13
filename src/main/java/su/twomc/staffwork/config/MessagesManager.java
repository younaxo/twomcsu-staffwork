package su.twomc.staffwork.config;

import java.io.File;
import java.util.Map;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/** Загружает и выдаёт локализованные сообщения из {@code messages_ru.yml} с подстановкой плейсхолдеров вида {@code {name}}. */
public final class MessagesManager {

    private final Plugin plugin;
    private final MessageFormatter formatter;
    private YamlConfiguration messages;

    public MessagesManager(Plugin plugin, MessageFormatter formatter) {
        this.plugin = plugin;
        this.formatter = formatter;
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages_ru.yml");
        if (!file.exists()) {
            plugin.saveResource("messages_ru.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(file);
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                new java.io.InputStreamReader(plugin.getResource("messages_ru.yml"), java.nio.charset.StandardCharsets.UTF_8));
        messages.setDefaults(defaults);
    }

    public String raw(String key) {
        String value = messages.getString(key);
        return value != null ? value : "<red>Отсутствует сообщение: " + key + "</red>";
    }

    public void send(CommandSender target, String key) {
        send(target, key, Map.of());
    }

    public void send(CommandSender target, String key, Map<String, String> placeholders) {
        formatter.send(target, raw(key), placeholders);
    }

    public String format(String key, Map<String, String> placeholders) {
        String template = raw(key);
        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}
