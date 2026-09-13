package su.twomc.staffwork.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Одноразовый код привязки Telegram-аккаунта. В хранилище попадает только {@link #codeHash} —
 * сам код игроку показывается один раз и нигде, кроме памяти на момент выдачи, не сохраняется.
 */
public record TelegramLinkCode(UUID employeeUuid, String codeHash, Instant expiresAt, int attempts) {

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }
}
