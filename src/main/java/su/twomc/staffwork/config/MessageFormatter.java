package su.twomc.staffwork.config;

import java.util.Map;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

/**
 * Отправка отформатированных сообщений. На Paper/Folia (и их совместимых форках) сервер
 * предоставляет Adventure нативно — {@link CommandSender} сам является {@link Audience}, и
 * сообщения рендерятся полноценным MiniMessage. На обычном Spigot без Paper Adventure
 * отсутствует на classpath сервера, поэтому используется упрощённый legacy-конвертер
 * ({@link LegacyMiniMessageConverter}), который не требует наличия классов Adventure в рантайме.
 */
public final class MessageFormatter {

    private final boolean nativeAdventure;
    private final LegacyMiniMessageConverter legacyConverter = new LegacyMiniMessageConverter();

    public MessageFormatter() {
        this.nativeAdventure = detectNativeAdventure();
    }

    private static boolean detectNativeAdventure() {
        try {
            return Audience.class.isAssignableFrom(CommandSender.class);
        } catch (LinkageError e) {
            return false;
        }
    }

    public boolean isNativeAdventure() {
        return nativeAdventure;
    }

    public void send(CommandSender target, String miniMessageTemplate, Map<String, String> placeholders) {
        String substituted = substitute(miniMessageTemplate, placeholders);
        if (nativeAdventure) {
            sendNative(target, substituted);
        } else {
            target.sendMessage(legacyConverter.toLegacy(substituted));
        }
    }

    // Изолировано в отдельном методе намеренно: пока он не вызывается, классы net.kyori.adventure.*
    // не резолвятся JVM, и обычный Spigot без Paper продолжает работать без NoClassDefFoundError.
    private void sendNative(CommandSender target, String text) {
        Component component = MiniMessage.miniMessage().deserialize(text);
        ((Audience) target).sendMessage(component);
    }

    private String substitute(String text, Map<String, String> placeholders) {
        String result = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}
