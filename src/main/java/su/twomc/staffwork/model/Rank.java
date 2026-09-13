package su.twomc.staffwork.model;

/**
 * Внутренний ранг (должность) сотрудника. Настраивается в {@code ranks.yml}, не связан
 * напрямую с правами Bukkit — используется только для отображения и сортировки в /staffwork list.
 *
 * @param id          нормализованный идентификатор ранга (латиница, цифры, "-", "_")
 * @param displayName отображаемое имя, поддерживает MiniMessage-разметку
 * @param weight      вес для сортировки (больше — выше в списке)
 */
public record Rank(String id, String displayName, int weight) {
}
