package su.twomc.staffwork.telegram;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import su.twomc.staffwork.scheduler.PlatformScheduler;

/**
 * Управляет long-polling опросом Telegram Bot API. Работает только в асинхронном потоке —
 * запускается через {@link PlatformScheduler#runAsync}, не открывает входящих портов (никакого
 * webhook-сервера), поэтому не требует дополнительной сетевой экспозиции сервера.
 *
 * <p>При сетевых сбоях повторяет попытку с экспоненциальной задержкой (до 60 секунд), чтобы не
 * заваливать Telegram API запросами и не создавать нагрузку при длительной недоступности сети.
 */
public final class TelegramManager {

    private final TelegramApiClient apiClient;
    private final LinkCodeService linkCodeService;
    private final PlatformScheduler scheduler;
    private final Logger logger;
    private final Map<String, String> messageTemplates;

    private volatile boolean running;
    private volatile long updateOffset;
    private int backoffSeconds = 1;

    public TelegramManager(
            String botToken,
            LinkCodeService linkCodeService,
            PlatformScheduler scheduler,
            Logger logger,
            Map<String, String> messageTemplates) {
        this.apiClient = new TelegramApiClient(botToken);
        this.linkCodeService = linkCodeService;
        this.scheduler = scheduler;
        this.logger = logger;
        this.messageTemplates = messageTemplates;
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        scheduler.runAsync(this::pollLoop);
        logger.info("Telegram-интеграция запущена (long polling)");
    }

    public void stop() {
        running = false;
    }

    public void notifyChat(long chatId, String text) {
        scheduler.runAsync(() -> {
            try {
                apiClient.sendMessage(chatId, text);
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                // Сообщение об ошибке намеренно не включает текст исключения целиком — это защита
                // от случайного попадания токена бота в лог, если конкретная реализация HttpClient
                // когда-либо включит URL запроса в текст исключения.
                logger.warning("Не удалось отправить уведомление в Telegram (" + e.getClass().getSimpleName() + ")");
            }
        });
    }

    public Optional<Long> linkedChatId(UUID employeeUuid) {
        return linkCodeService.findLinkedTelegramId(employeeUuid);
    }

    private void pollLoop() {
        while (running) {
            try {
                JsonObject response = apiClient.getUpdates(updateOffset, 30);
                backoffSeconds = 1;
                if (response.has("result") && response.get("result").isJsonArray()) {
                    processUpdates(response.getAsJsonArray("result"));
                }
            } catch (IOException e) {
                logger.warning("Ошибка опроса Telegram API (" + e.getClass().getSimpleName() + "), повтор через "
                        + backoffSeconds + " с");
                sleepBackoff();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Некорректный ответ Telegram API, обновление пропущено", e);
            }
        }
    }

    private void processUpdates(JsonArray updates) {
        for (int i = 0; i < updates.size(); i++) {
            JsonObject update = updates.get(i).getAsJsonObject();
            long updateId = update.get("update_id").getAsLong();
            updateOffset = Math.max(updateOffset, updateId + 1);

            if (!update.has("message")) {
                continue;
            }
            JsonObject message = update.getAsJsonObject("message");
            if (!message.has("text") || !message.has("chat")) {
                continue;
            }
            long chatId = message.getAsJsonObject("chat").get("id").getAsLong();
            String text = message.get("text").getAsString().trim();
            handleMessage(chatId, text);
        }
    }

    private void handleMessage(long chatId, String text) {
        if (text.startsWith("/start")) {
            notifyChat(chatId, template("start", "Отправьте код привязки, выданный командой /staffwork telegram link в игре."));
            return;
        }
        LinkCodeService.Result result = linkCodeService.tryLink(text, chatId, Instant.now());
        switch (result.status()) {
            case SUCCESS -> notifyChat(chatId, template("link-success", "Аккаунт успешно привязан!"));
            case INVALID_OR_EXPIRED -> notifyChat(
                    chatId, template("link-invalid", "Код неверен или истёк. Получите новый код командой в игре."));
            case RATE_LIMITED -> notifyChat(
                    chatId, template("link-rate-limited", "Слишком много попыток. Подождите немного и попробуйте снова."));
            default -> throw new IllegalStateException("Неизвестный результат привязки: " + result.status());
        }
    }

    private String template(String key, String fallback) {
        return messageTemplates.getOrDefault(key, fallback);
    }

    private void sleepBackoff() {
        try {
            Thread.sleep(backoffSeconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
            return;
        }
        backoffSeconds = Math.min(60, backoffSeconds * 2);
    }
}
