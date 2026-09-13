package su.twomc.staffwork.telegram;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Тонкий клиент Telegram Bot API поверх стандартного {@link HttpClient} — без дополнительных
 * HTTP-библиотек. Все вызовы блокирующие и должны выполняться только в асинхронном потоке
 * (см. {@link TelegramManager}), никогда в главном/региональном потоке сервера.
 */
final class TelegramApiClient {

    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String token;

    TelegramApiClient(String token) {
        this.token = token;
    }

    JsonObject getUpdates(long offset, int timeoutSeconds) throws IOException, InterruptedException {
        String url = "https://api.telegram.org/bot" + token + "/getUpdates?timeout=" + timeoutSeconds + "&offset=" + offset;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds + 15L))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Telegram API getUpdates: HTTP " + response.statusCode());
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    void sendMessage(long chatId, String text) throws IOException, InterruptedException {
        JsonObject payload = new JsonObject();
        payload.addProperty("chat_id", chatId);
        payload.addProperty("text", text);
        String url = "https://api.telegram.org/bot" + token + "/sendMessage";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Telegram API sendMessage: HTTP " + response.statusCode());
        }
    }
}
