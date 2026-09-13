package su.twomc.staffwork.placeholder;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import su.twomc.staffwork.config.ColorCodes;
import su.twomc.staffwork.model.StaffStatus;
import su.twomc.staffwork.service.PlaceholderCache;
import su.twomc.staffwork.util.TimeUtil;

/**
 * Расширение PlaceholderAPI. Идентификатор — {@code tmcstaff} (без точки — PlaceholderAPI не
 * допускает точки в идентификаторах расширений, поэтому ранее предлагавшийся формат
 * {@code %tmc.staff_...%} технически невозможен и не заявляется рабочим).
 */
public final class TmcStaffPlaceholderExpansion extends PlaceholderExpansion {

    private final Plugin plugin;
    private final PlaceholderCache cache;
    private final Map<StaffStatus, String> statusColors;

    public TmcStaffPlaceholderExpansion(Plugin plugin, PlaceholderCache cache, Map<StaffStatus, String> statusColors) {
        this.plugin = plugin;
        this.cache = cache;
        this.statusColors = statusColors;
    }

    @Override
    public String getIdentifier() {
        return "tmcstaff";
    }

    @Override
    public String getAuthor() {
        return "younaxo";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) {
            return "";
        }
        return cache.get(player.getUniqueId())
                .map(snapshot -> resolve(params.toLowerCase(Locale.ROOT), snapshot))
                .orElse("");
    }

    private String resolve(String params, PlaceholderCache.Snapshot snapshot) {
        StaffStatus status = snapshot.employee().currentStatus();
        return switch (params) {
            case "staff_status" -> status.name();
            case "staff_status_color" -> ColorCodes.legacy(statusColors.get(status));
            case "staff_status_color_minimessage" -> ColorCodes.miniMessageTag(statusColors.get(status));
            case "staff_is_working" -> String.valueOf(status == StaffStatus.WORKING);
            case "staff_work_time_today" -> TimeUtil.formatDuration(snapshot.today());
            case "staff_work_time_week" -> TimeUtil.formatDuration(snapshot.week());
            case "staff_work_time_month" -> TimeUtil.formatDuration(snapshot.month());
            case "staff_total_work_time" -> TimeUtil.formatDuration(snapshot.all());
            case "staff_current_session_time" -> formatSessionTime(snapshot);
            default -> "";
        };
    }

    private String formatSessionTime(PlaceholderCache.Snapshot snapshot) {
        Duration session = snapshot.currentSession();
        return session.isZero() ? "-" : TimeUtil.formatDuration(session);
    }
}
