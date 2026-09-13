package su.twomc.staffwork.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import su.twomc.staffwork.model.Rank;
import su.twomc.staffwork.util.Validation;

/** Загружает внутренние ранги сотрудников из {@code ranks.yml}. */
public final class RanksConfig {

    private final Map<String, Rank> ranks;
    private final String defaultRankId;

    private RanksConfig(Map<String, Rank> ranks, String defaultRankId) {
        this.ranks = ranks;
        this.defaultRankId = defaultRankId;
    }

    public static RanksConfig load(FileConfiguration config) {
        Map<String, Rank> ranks = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("ranks");
        if (section != null) {
            for (String rawId : section.getKeys(false)) {
                if (!Validation.isValidRankId(rawId)) {
                    continue;
                }
                String id = rawId.toLowerCase(java.util.Locale.ROOT);
                ConfigurationSection rankSection = section.getConfigurationSection(rawId);
                String displayName = rankSection != null ? rankSection.getString("display-name", id) : id;
                int weight = rankSection != null ? rankSection.getInt("weight", 0) : 0;
                ranks.put(id, new Rank(id, displayName, weight));
            }
        }
        String defaultRankId = config.getString("default-rank", "trainee");
        if (!ranks.containsKey(defaultRankId)) {
            ranks.put(defaultRankId, new Rank(defaultRankId, defaultRankId, 0));
        }
        return new RanksConfig(ranks, defaultRankId);
    }

    public Optional<Rank> find(String id) {
        return Optional.ofNullable(ranks.get(id));
    }

    public Map<String, Rank> all() {
        return ranks;
    }

    public String defaultRankId() {
        return defaultRankId;
    }
}
