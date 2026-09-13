package su.twomc.staffwork.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import su.twomc.staffwork.testutil.InMemoryStaffRepository;

class LinkCodeServiceTest {

    @Test
    void correctCodeLinksTelegramAccountAndConsumesCode() {
        InMemoryStaffRepository repository = new InMemoryStaffRepository();
        LinkCodeService service = new LinkCodeService(repository, Duration.ofMinutes(5), 5, Duration.ofMinutes(1));
        UUID employeeUuid = UUID.randomUUID();
        Instant now = Instant.parse("2025-01-01T00:00:00Z");

        String code = service.generateCode(employeeUuid, now);
        LinkCodeService.Result result = service.tryLink(code, 12345L, now.plusSeconds(10));

        assertEquals(LinkCodeService.Status.SUCCESS, result.status());
        assertEquals(employeeUuid, result.employeeUuid());
        assertEquals(12345L, repository.findTelegramUserId(employeeUuid).orElseThrow());
        // Код одноразовый — повторное использование должно быть отклонено.
        LinkCodeService.Result second = service.tryLink(code, 99999L, now.plusSeconds(20));
        assertEquals(LinkCodeService.Status.INVALID_OR_EXPIRED, second.status());
    }

    @Test
    void codeIsCaseInsensitiveButWrongCodeIsRejected() {
        InMemoryStaffRepository repository = new InMemoryStaffRepository();
        LinkCodeService service = new LinkCodeService(repository, Duration.ofMinutes(5), 5, Duration.ofMinutes(1));
        UUID employeeUuid = UUID.randomUUID();
        Instant now = Instant.parse("2025-01-01T00:00:00Z");

        String code = service.generateCode(employeeUuid, now);

        LinkCodeService.Result wrong = service.tryLink("WRONGCODE", 1L, now);
        assertEquals(LinkCodeService.Status.INVALID_OR_EXPIRED, wrong.status());

        LinkCodeService.Result lower = service.tryLink(code.toLowerCase(java.util.Locale.ROOT), 2L, now);
        assertEquals(LinkCodeService.Status.SUCCESS, lower.status());
    }

    @Test
    void expiredCodeIsRejected() {
        InMemoryStaffRepository repository = new InMemoryStaffRepository();
        LinkCodeService service = new LinkCodeService(repository, Duration.ofMinutes(5), 5, Duration.ofMinutes(1));
        UUID employeeUuid = UUID.randomUUID();
        Instant now = Instant.parse("2025-01-01T00:00:00Z");

        String code = service.generateCode(employeeUuid, now);
        LinkCodeService.Result result = service.tryLink(code, 1L, now.plus(Duration.ofMinutes(6)));

        assertEquals(LinkCodeService.Status.INVALID_OR_EXPIRED, result.status());
    }

    @Test
    void tooManyAttemptsAreRateLimited() {
        InMemoryStaffRepository repository = new InMemoryStaffRepository();
        LinkCodeService service = new LinkCodeService(repository, Duration.ofMinutes(5), 3, Duration.ofMinutes(1));
        UUID employeeUuid = UUID.randomUUID();
        Instant now = Instant.parse("2025-01-01T00:00:00Z");
        service.generateCode(employeeUuid, now);
        long attacker = 777L;

        for (int i = 0; i < 3; i++) {
            service.tryLink("WRONG" + i, attacker, now);
        }
        LinkCodeService.Result blocked = service.tryLink("WRONG4", attacker, now);

        assertEquals(LinkCodeService.Status.RATE_LIMITED, blocked.status());
    }

    @Test
    void generatingNewCodeInvalidatesPreviousOne() {
        InMemoryStaffRepository repository = new InMemoryStaffRepository();
        LinkCodeService service = new LinkCodeService(repository, Duration.ofMinutes(5), 5, Duration.ofMinutes(1));
        UUID employeeUuid = UUID.randomUUID();
        Instant now = Instant.parse("2025-01-01T00:00:00Z");

        String first = service.generateCode(employeeUuid, now);
        String second = service.generateCode(employeeUuid, now);
        assertNotEquals(first, second);

        LinkCodeService.Result result = service.tryLink(first, 1L, now);
        assertEquals(LinkCodeService.Status.INVALID_OR_EXPIRED, result.status());
    }
}
