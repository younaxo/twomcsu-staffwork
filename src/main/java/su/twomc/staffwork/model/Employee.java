package su.twomc.staffwork.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Запись о сотруднике. UUID — основной и единственный устойчивый идентификатор,
 * ник хранится только как последний известный (может устаревать у офлайн-игроков).
 */
public final class Employee {

    private final UUID uuid;
    private String lastKnownName;
    private String rankId;
    private boolean enabled;
    private final Instant addedAt;
    private final UUID addedBy;
    private StaffStatus currentStatus;
    private Instant currentStatusSince;

    public Employee(
            UUID uuid,
            String lastKnownName,
            String rankId,
            boolean enabled,
            Instant addedAt,
            UUID addedBy,
            StaffStatus currentStatus,
            Instant currentStatusSince) {
        this.uuid = uuid;
        this.lastKnownName = lastKnownName;
        this.rankId = rankId;
        this.enabled = enabled;
        this.addedAt = addedAt;
        this.addedBy = addedBy;
        this.currentStatus = currentStatus;
        this.currentStatusSince = currentStatusSince;
    }

    public UUID uuid() {
        return uuid;
    }

    public String lastKnownName() {
        return lastKnownName;
    }

    public void setLastKnownName(String lastKnownName) {
        this.lastKnownName = lastKnownName;
    }

    public String rankId() {
        return rankId;
    }

    public void setRankId(String rankId) {
        this.rankId = rankId;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant addedAt() {
        return addedAt;
    }

    public UUID addedBy() {
        return addedBy;
    }

    public StaffStatus currentStatus() {
        return currentStatus;
    }

    public Instant currentStatusSince() {
        return currentStatusSince;
    }

    public void setCurrentStatus(StaffStatus currentStatus, Instant since) {
        this.currentStatus = currentStatus;
        this.currentStatusSince = since;
    }
}
