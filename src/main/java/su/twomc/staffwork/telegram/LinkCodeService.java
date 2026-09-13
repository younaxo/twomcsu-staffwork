package su.twomc.staffwork.telegram;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import su.twomc.staffwork.model.TelegramLinkCode;
import su.twomc.staffwork.repository.StaffRepository;
import su.twomc.staffwork.util.RateLimiter;

/**
 * Одноразовые коды привязки Telegram-аккаунта. В хранилище попадает только SHA-256 хеш кода —
 * сам код виден игроку один раз в момент выдачи. Попытки подбора ограничены {@link RateLimiter}
 * по ключу отправителя (Telegram user id), а не по коду — это защищает и от подбора чужого кода,
 * и от перебора кодов вообще.
 */
public final class LinkCodeService {

    /** Результат попытки привязки по коду. */
    public enum Status {
        SUCCESS,
        INVALID_OR_EXPIRED,
        RATE_LIMITED
    }

    public record Result(Status status, UUID employeeUuid) {
        static Result of(Status status) {
            return new Result(status, null);
        }
    }

    private static final int CODE_LENGTH = 8;
    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    private final StaffRepository repository;
    private final Duration ttl;
    private final RateLimiter attemptLimiter;
    private final SecureRandom random = new SecureRandom();

    public LinkCodeService(StaffRepository repository, Duration ttl, int maxAttempts, Duration attemptWindow) {
        this.repository = repository;
        this.ttl = ttl;
        this.attemptLimiter = new RateLimiter(maxAttempts, attemptWindow);
    }

    /** Генерирует новый код, инвалидируя предыдущий незавершённый код этого сотрудника. Код возвращается один раз. */
    public String generateCode(UUID employeeUuid, Instant now) {
        String code = randomCode();
        TelegramLinkCode record = new TelegramLinkCode(employeeUuid, hash(code), now.plus(ttl), 0);
        repository.saveTelegramLinkCode(record);
        return code;
    }

    public Result tryLink(String rawCode, long telegramUserId, Instant now) {
        if (!attemptLimiter.tryAcquire(telegramUserId)) {
            return Result.of(Status.RATE_LIMITED);
        }
        String normalized = rawCode.trim().toUpperCase(java.util.Locale.ROOT);
        String candidateHash = hash(normalized);
        for (TelegramLinkCode code : repository.findAllTelegramLinkCodes()) {
            if (code.isExpired(now)) {
                continue;
            }
            if (MessageDigest.isEqual(
                    code.codeHash().getBytes(StandardCharsets.UTF_8), candidateHash.getBytes(StandardCharsets.UTF_8))) {
                repository.saveTelegramLink(code.employeeUuid(), telegramUserId);
                repository.deleteTelegramLinkCode(code.employeeUuid());
                return new Result(Status.SUCCESS, code.employeeUuid());
            }
        }
        return Result.of(Status.INVALID_OR_EXPIRED);
    }

    public void unlink(UUID employeeUuid) {
        repository.deleteTelegramLink(employeeUuid);
    }

    public Optional<Long> findLinkedTelegramId(UUID employeeUuid) {
        return repository.findTelegramUserId(employeeUuid);
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    private static String hash(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] result = digest.digest(code.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(result);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступен в этой JVM", e);
        }
    }
}
