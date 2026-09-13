package su.twomc.staffwork.command;

/** Узлы прав доступа. Формат: {@code tmc.staffwork.<категория>[.<подкатегория>[.<функция>]]}. */
public final class Permissions {

    public static final String COMMAND_HELP = "tmc.staffwork.command.help";
    public static final String COMMAND_VERSION = "tmc.staffwork.command.version";
    public static final String COMMAND_RELOAD = "tmc.staffwork.command.reload";

    public static final String STAFF_LIST = "tmc.staffwork.staff.list";
    public static final String STAFF_INFO_SELF = "tmc.staffwork.staff.info.self";
    public static final String STAFF_INFO_OTHERS = "tmc.staffwork.staff.info.others";
    public static final String STAFF_ADD = "tmc.staffwork.staff.add";
    public static final String STAFF_REMOVE = "tmc.staffwork.staff.remove";
    public static final String STAFF_RANK_SET = "tmc.staffwork.staff.rank.set";
    public static final String STAFF_ENABLE = "tmc.staffwork.staff.enable";
    public static final String STAFF_DISABLE = "tmc.staffwork.staff.disable";

    public static final String STATUS_SET_SELF = "tmc.staffwork.status.set.self";
    public static final String STATUS_SET_OTHERS = "tmc.staffwork.status.set.others";

    public static final String SESSION_START_SELF = "tmc.staffwork.session.start.self";
    public static final String SESSION_STOP_SELF = "tmc.staffwork.session.stop.self";
    public static final String SESSION_MANAGE_OTHERS = "tmc.staffwork.session.manage.others";

    public static final String STATISTICS_VIEW_SELF = "tmc.staffwork.statistics.view.self";
    public static final String STATISTICS_VIEW_OTHERS = "tmc.staffwork.statistics.view.others";

    public static final String TELEGRAM_LINK = "tmc.staffwork.telegram.link";
    public static final String TELEGRAM_UNLINK = "tmc.staffwork.telegram.unlink";

    public static final String ADMIN_RELOAD = "tmc.staffwork.admin.reload";
    public static final String ADMIN_DATABASE_MIGRATE = "tmc.staffwork.admin.database.migrate";

    private Permissions() {}
}
