package su.twomc.staffwork.command;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import su.twomc.staffwork.config.ConfigManager;
import su.twomc.staffwork.config.MessagesManager;
import su.twomc.staffwork.model.Employee;
import su.twomc.staffwork.model.Rank;
import su.twomc.staffwork.model.SessionEndReason;
import su.twomc.staffwork.model.StaffStatus;
import su.twomc.staffwork.model.StatsPeriod;
import su.twomc.staffwork.model.WorkSession;
import su.twomc.staffwork.scheduler.PlatformScheduler;
import su.twomc.staffwork.service.EmployeeService;
import su.twomc.staffwork.service.SessionService;
import su.twomc.staffwork.service.StatisticsService;
import su.twomc.staffwork.service.StatusService;
import su.twomc.staffwork.telegram.LinkCodeService;
import su.twomc.staffwork.util.TimeUtil;
import su.twomc.staffwork.util.Validation;

/**
 * Единая точка входа для {@code /staffwork} (алиас {@code /sw}). Каждая подкоманда: проверяет
 * права и валидирует аргументы синхронно (дёшево, без обращений к диску/сети), затем переносит
 * работу с хранилищем в асинхронный поток через {@link PlatformScheduler#runAsync}, а результат
 * отправляет обратно через планировщик, подходящий отправителю (см. {@link #reply}).
 */
public final class StaffWorkCommand implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final PlatformScheduler scheduler;
    private final EmployeeService employeeService;
    private final SessionService sessionService;
    private final StatusService statusService;
    private final StatisticsService statisticsService;
    private final ConfigManager configManager;
    private final MessagesManager messages;
    private final LinkCodeService linkCodeService;
    private final Runnable reloadHook;

    public StaffWorkCommand(
            Plugin plugin,
            PlatformScheduler scheduler,
            EmployeeService employeeService,
            SessionService sessionService,
            StatusService statusService,
            StatisticsService statisticsService,
            ConfigManager configManager,
            MessagesManager messages,
            LinkCodeService linkCodeService,
            Runnable reloadHook) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.employeeService = employeeService;
        this.sessionService = sessionService;
        this.statusService = statusService;
        this.statisticsService = statisticsService;
        this.configManager = configManager;
        this.messages = messages;
        this.linkCodeService = linkCodeService;
        this.reloadHook = reloadHook;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (sub) {
            case "help" -> sendHelp(sender);
            case "version" -> version(sender);
            case "reload" -> reload(sender);
            case "list" -> list(sender);
            case "info" -> info(sender, rest);
            case "add" -> add(sender, rest);
            case "remove" -> remove(sender, rest);
            case "rank" -> rank(sender, rest);
            case "status" -> status(sender, rest);
            case "start" -> start(sender);
            case "stop" -> stop(sender);
            case "stats" -> stats(sender, rest);
            case "telegram" -> telegram(sender, rest);
            default -> messages.send(sender, "error.unknown-subcommand", Map.of("input", Validation.stripMarkup(sub)));
        }
        return true;
    }

    // --- help / version / reload ---

    private void sendHelp(CommandSender sender) {
        if (!sender.hasPermission(Permissions.COMMAND_HELP)) {
            denied(sender);
            return;
        }
        messages.send(sender, "help.header");
        List<String> lines = List.of(
                "help.line.info",
                "help.line.list",
                "help.line.status",
                "help.line.start",
                "help.line.stop",
                "help.line.stats",
                "help.line.add",
                "help.line.remove",
                "help.line.rank",
                "help.line.reload",
                "help.line.version");
        for (String key : lines) {
            messages.send(sender, key);
        }
    }

    private void version(CommandSender sender) {
        if (!sender.hasPermission(Permissions.COMMAND_VERSION)) {
            denied(sender);
            return;
        }
        messages.send(
                sender,
                "version.line",
                Map.of("version", plugin.getPluginMeta().getVersion(), "name", plugin.getPluginMeta().getName()));
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission(Permissions.COMMAND_RELOAD) && !sender.hasPermission(Permissions.ADMIN_RELOAD)) {
            denied(sender);
            return;
        }
        boolean storageChanged = configManager.storageTypeChanged();
        reloadHook.run();
        if (storageChanged) {
            messages.send(sender, "reload.storage-changed");
        } else {
            messages.send(sender, "reload.success");
        }
    }

    // --- list / info ---

    private void list(CommandSender sender) {
        if (!sender.hasPermission(Permissions.STAFF_LIST)) {
            denied(sender);
            return;
        }
        async(sender, () -> {
            List<Employee> employees = employeeService.listEmployees();
            return () -> {
                if (employees.isEmpty()) {
                    messages.send(sender, "staff.list-empty");
                    return;
                }
                messages.send(sender, "staff.list-header", Map.of("count", String.valueOf(employees.size())));
                for (Employee employee : employees) {
                    Rank rank = configManager
                            .ranksConfig()
                            .find(employee.rankId())
                            .orElse(new Rank(employee.rankId(), employee.rankId(), 0));
                    messages.send(
                            sender,
                            "staff.list-entry",
                            Map.of(
                                    "player", employee.lastKnownName(),
                                    "rank", rank.displayName(),
                                    "status", employee.currentStatus().name(),
                                    "enabled", employee.enabled() ? "да" : "нет"));
                }
            };
        });
    }

    private void info(CommandSender sender, String[] args) {
        boolean self = args.length == 0;
        if (self && !(sender instanceof Player)) {
            messages.send(sender, "error.player-only");
            return;
        }
        if (self && !sender.hasPermission(Permissions.STAFF_INFO_SELF)) {
            denied(sender);
            return;
        }
        if (!self && !sender.hasPermission(Permissions.STAFF_INFO_OTHERS)) {
            denied(sender);
            return;
        }
        resolveTarget(sender, self ? null : args[0], target -> {
            async(sender, () -> {
                Optional<Employee> employeeOpt = employeeService.findEmployee(target.getUniqueId());
                if (employeeOpt.isEmpty()) {
                    return () -> messages.send(sender, "error.not-employee");
                }
                Employee employee = employeeOpt.get();
                Duration today = statisticsService.calculate(
                        employee.uuid(), StatsPeriod.TODAY, employee.addedAt(), Instant.now());
                Rank rank = configManager
                        .ranksConfig()
                        .find(employee.rankId())
                        .orElse(new Rank(employee.rankId(), employee.rankId(), 0));
                return () -> messages.send(
                        sender,
                        "staff.info",
                        Map.of(
                                "player", employee.lastKnownName(),
                                "rank", rank.displayName(),
                                "status", employee.currentStatus().name(),
                                "enabled", employee.enabled() ? "да" : "нет",
                                "today", TimeUtil.formatDuration(today)));
            });
        });
    }

    // --- add / remove / rank ---

    private void add(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.STAFF_ADD)) {
            denied(sender);
            return;
        }
        if (args.length < 1) {
            usage(sender, "/staffwork add <игрок> [ранг]");
            return;
        }
        String rankId = args.length >= 2 ? args[1] : configManager.ranksConfig().defaultRankId();
        if (!Validation.isValidRankId(rankId)) {
            messages.send(sender, "error.invalid-rank", Map.of("rank", Validation.stripMarkup(rankId)));
            return;
        }
        String finalRankId = rankId.toLowerCase(Locale.ROOT);
        UUID addedBy = sender instanceof Player player ? player.getUniqueId() : null;
        resolveTarget(sender, args[0], target -> async(sender, () -> {
            try {
                employeeService.addEmployee(target.getUniqueId(), target.getName(), finalRankId, addedBy, Instant.now());
                return () -> messages.send(
                        sender, "staff.added", Map.of("player", String.valueOf(target.getName()), "rank", finalRankId));
            } catch (IllegalStateException e) {
                return () -> messages.send(sender, "error.already-employee");
            }
        }));
    }

    private void remove(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.STAFF_REMOVE)) {
            denied(sender);
            return;
        }
        if (args.length < 1) {
            usage(sender, "/staffwork remove <игрок>");
            return;
        }
        resolveTarget(sender, args[0], target -> async(sender, () -> {
            try {
                employeeService.removeEmployee(target.getUniqueId());
                return () -> messages.send(sender, "staff.removed", Map.of("player", String.valueOf(target.getName())));
            } catch (IllegalStateException e) {
                return () -> messages.send(sender, "error.not-employee");
            }
        }));
    }

    private void rank(CommandSender sender, String[] args) {
        if (args.length < 3 || !args[0].equalsIgnoreCase("set")) {
            usage(sender, "/staffwork rank set <игрок> <ранг>");
            return;
        }
        if (!sender.hasPermission(Permissions.STAFF_RANK_SET)) {
            denied(sender);
            return;
        }
        String rankId = args[2];
        if (!Validation.isValidRankId(rankId)) {
            messages.send(sender, "error.invalid-rank", Map.of("rank", Validation.stripMarkup(rankId)));
            return;
        }
        String finalRankId = rankId.toLowerCase(Locale.ROOT);
        resolveTarget(sender, args[1], target -> async(sender, () -> {
            try {
                employeeService.setRank(target.getUniqueId(), finalRankId);
                return () -> messages.send(
                        sender, "staff.rank-updated", Map.of("player", String.valueOf(target.getName()), "rank", finalRankId));
            } catch (IllegalStateException e) {
                return () -> messages.send(sender, "error.not-employee");
            }
        }));
    }

    // --- status ---

    private void status(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[0].equalsIgnoreCase("set")) {
            statusSetOthers(sender, args);
            return;
        }
        if (args.length == 0) {
            statusShowOrSetSelf(sender, null);
        } else {
            statusShowOrSetSelf(sender, args[0]);
        }
    }

    private void statusShowOrSetSelf(CommandSender sender, String statusArg) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "error.player-only");
            return;
        }
        if (statusArg == null) {
            async(sender, () -> {
                Optional<Employee> employee = employeeService.findEmployee(player.getUniqueId());
                return () -> employee.ifPresentOrElse(
                        e -> messages.send(sender, "status.current", Map.of("status", e.currentStatus().name())),
                        () -> messages.send(sender, "error.not-employee"));
            });
            return;
        }
        if (!sender.hasPermission(Permissions.STATUS_SET_SELF)) {
            denied(sender);
            return;
        }
        Optional<StaffStatus> parsed = StaffStatus.fromId(statusArg);
        if (parsed.isEmpty()) {
            messages.send(sender, "error.invalid-status", Map.of("status", Validation.stripMarkup(statusArg)));
            return;
        }
        async(sender, () -> {
            Optional<Employee> employeeOpt = employeeService.findEmployee(player.getUniqueId());
            if (employeeOpt.isEmpty()) {
                return () -> messages.send(sender, "error.not-employee");
            }
            statusService.changeStatus(employeeOpt.get(), parsed.get(), Instant.now());
            return () -> messages.send(sender, "status.updated-self", Map.of("status", parsed.get().name()));
        });
    }

    private void statusSetOthers(CommandSender sender, String[] args) {
        if (args.length < 3) {
            usage(sender, "/staffwork status set <игрок> <статус>");
            return;
        }
        if (!sender.hasPermission(Permissions.STATUS_SET_OTHERS)) {
            denied(sender);
            return;
        }
        Optional<StaffStatus> parsed = StaffStatus.fromId(args[2]);
        if (parsed.isEmpty()) {
            messages.send(sender, "error.invalid-status", Map.of("status", Validation.stripMarkup(args[2])));
            return;
        }
        resolveTarget(sender, args[1], target -> async(sender, () -> {
            Optional<Employee> employeeOpt = employeeService.findEmployee(target.getUniqueId());
            if (employeeOpt.isEmpty()) {
                return () -> messages.send(sender, "error.not-employee");
            }
            statusService.changeStatus(employeeOpt.get(), parsed.get(), Instant.now());
            return () -> messages.send(
                    sender,
                    "status.updated-others",
                    Map.of("player", String.valueOf(target.getName()), "status", parsed.get().name()));
        }));
    }

    // --- start / stop ---

    private void start(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "error.player-only");
            return;
        }
        if (!sender.hasPermission(Permissions.SESSION_START_SELF)) {
            denied(sender);
            return;
        }
        async(sender, () -> {
            Optional<Employee> employeeOpt = employeeService.findEmployee(player.getUniqueId());
            if (employeeOpt.isEmpty()) {
                return () -> messages.send(sender, "error.not-employee");
            }
            Employee employee = employeeOpt.get();
            try {
                sessionService.startSession(employee, StaffStatus.WORKING, Instant.now(), configManager.pluginConfig().serverId());
                return () -> messages.send(sender, "session.started");
            } catch (IllegalStateException e) {
                return () -> messages.send(sender, "session.already-active");
            }
        });
    }

    private void stop(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "error.player-only");
            return;
        }
        if (!sender.hasPermission(Permissions.SESSION_STOP_SELF)) {
            denied(sender);
            return;
        }
        async(sender, () -> {
            Optional<Employee> employeeOpt = employeeService.findEmployee(player.getUniqueId());
            if (employeeOpt.isEmpty()) {
                return () -> messages.send(sender, "error.not-employee");
            }
            Employee employee = employeeOpt.get();
            try {
                WorkSession session = sessionService.stopSession(
                        employee, StaffStatus.OFF_DUTY, Instant.now(), SessionEndReason.MANUAL_STOP);
                String duration = TimeUtil.formatDuration(Duration.between(session.startedAt(), session.endedAt()));
                return () -> messages.send(sender, "session.stopped", Map.of("duration", duration));
            } catch (IllegalStateException e) {
                return () -> messages.send(sender, "session.not-active");
            }
        });
    }

    // --- stats ---

    private void stats(CommandSender sender, String[] args) {
        boolean self = args.length == 0 || StatsPeriod.fromArg(args[0]).isPresent();
        String targetName = self ? null : args[0];
        String periodArg = self ? (args.length >= 1 ? args[0] : null) : (args.length >= 2 ? args[1] : null);
        StatsPeriod period = periodArg != null ? StatsPeriod.fromArg(periodArg).orElse(null) : StatsPeriod.TODAY;
        if (period == null) {
            messages.send(sender, "error.invalid-period", Map.of("period", Validation.stripMarkup(periodArg)));
            return;
        }
        if (self && !(sender instanceof Player)) {
            messages.send(sender, "error.player-only");
            return;
        }
        if (self && !sender.hasPermission(Permissions.STATISTICS_VIEW_SELF)) {
            denied(sender);
            return;
        }
        if (!self && !sender.hasPermission(Permissions.STATISTICS_VIEW_OTHERS)) {
            denied(sender);
            return;
        }
        resolveTarget(sender, targetName, target -> async(sender, () -> {
            Optional<Employee> employeeOpt = employeeService.findEmployee(target.getUniqueId());
            if (employeeOpt.isEmpty()) {
                return () -> messages.send(sender, "error.not-employee");
            }
            Employee employee = employeeOpt.get();
            Duration duration = statisticsService.calculate(employee.uuid(), period, employee.addedAt(), Instant.now());
            return () -> messages.send(
                    sender,
                    "stats.line",
                    Map.of(
                            "player", String.valueOf(target.getName()),
                            "period", period.name(),
                            "duration", TimeUtil.formatDuration(duration)));
        }));
    }

    // --- telegram ---

    private void telegram(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "error.player-only");
            return;
        }
        if (args.length < 1) {
            usage(sender, "/staffwork telegram <link|unlink>");
            return;
        }
        if (args[0].equalsIgnoreCase("link")) {
            if (!sender.hasPermission(Permissions.TELEGRAM_LINK)) {
                denied(sender);
                return;
            }
            async(sender, () -> {
                Optional<Employee> employeeOpt = employeeService.findEmployee(player.getUniqueId());
                if (employeeOpt.isEmpty()) {
                    return () -> messages.send(sender, "error.not-employee");
                }
                String code = linkCodeService.generateCode(player.getUniqueId(), Instant.now());
                return () -> messages.send(sender, "telegram.code-issued", Map.of("code", code));
            });
        } else if (args[0].equalsIgnoreCase("unlink")) {
            if (!sender.hasPermission(Permissions.TELEGRAM_UNLINK)) {
                denied(sender);
                return;
            }
            async(sender, () -> {
                linkCodeService.unlink(player.getUniqueId());
                return () -> messages.send(sender, "telegram.unlinked");
            });
        } else {
            usage(sender, "/staffwork telegram <link|unlink>");
        }
    }

    // --- вспомогательные методы ---

    private void denied(CommandSender sender) {
        messages.send(sender, "error.no-permission");
    }

    private void usage(CommandSender sender, String usage) {
        messages.send(sender, "error.usage", Map.of("usage", usage));
    }

    /**
     * Разрешает имя игрока в {@link OfflinePlayer}. Онлайн-игрок ищется сразу (дёшево),
     * для офлайн-игрока поиск уходит в асинхронный поток, так как {@code Bukkit.getOfflinePlayer}
     * может обратиться к кэшу профилей и заблокироваться.
     */
    private void resolveTarget(CommandSender sender, String name, java.util.function.Consumer<OfflinePlayer> onResolved) {
        if (name == null) {
            if (sender instanceof Player player) {
                onResolved.accept(player);
            } else {
                messages.send(sender, "error.player-only");
            }
            return;
        }
        if (!Validation.isValidPlayerName(name)) {
            messages.send(sender, "error.player-not-found", Map.of("player", Validation.stripMarkup(name)));
            return;
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            onResolved.accept(online);
            return;
        }
        scheduler.runAsync(() -> {
            // Метод по нику официально не рекомендован (UUID предпочтительнее), но альтернативы для
            // разрешения ника, введённого в команде, в Bukkit API нет — это единственный способ.
            @SuppressWarnings("deprecation")
            OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
            if (offline.getName() == null && !offline.hasPlayedBefore()) {
                reply(sender, () -> messages.send(sender, "error.player-not-found", Map.of("player", name)));
                return;
            }
            reply(sender, () -> onResolved.accept(offline));
        });
    }

    private void async(CommandSender sender, Supplier<Runnable> work) {
        scheduler.runAsync(() -> {
            Runnable followUp;
            try {
                followUp = work.get();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Ошибка выполнения команды /staffwork", e);
                followUp = () -> messages.send(sender, "error.internal");
            }
            reply(sender, followUp);
        });
    }

    private void reply(CommandSender sender, Runnable action) {
        if (sender instanceof Entity entity) {
            scheduler.runForEntity(entity, action, null);
        } else {
            scheduler.runGlobal(action);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(
                    List.of(
                            "help", "version", "reload", "list", "info", "add", "remove", "rank", "status", "start", "stop",
                            "stats", "telegram"),
                    args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "info" -> args.length == 2 ? onlinePlayerNames(args[1]) : List.of();
            case "stats" -> switch (args.length) {
                case 2 -> {
                    List<String> options = new ArrayList<>(periodNames());
                    options.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
                    yield filter(options, args[1]);
                }
                case 3 -> filter(periodNames(), args[2]);
                default -> List.of();
            };
            case "add", "remove" -> args.length == 2 ? onlinePlayerNames(args[1]) : List.of();
            case "rank" -> switch (args.length) {
                case 2 -> filter(List.of("set"), args[1]);
                case 3 -> onlinePlayerNames(args[2]);
                case 4 -> filter(new ArrayList<>(configManager.ranksConfig().all().keySet()), args[3]);
                default -> List.of();
            };
            case "status" -> switch (args.length) {
                case 2 -> filter(statusNamesAndSet(), args[1]);
                case 3 -> onlinePlayerNames(args[2]);
                case 4 -> filter(statusNames(), args[3]);
                default -> List.of();
            };
            case "telegram" -> args.length == 2 ? filter(List.of("link", "unlink"), args[1]) : List.of();
            default -> List.of();
        };
    }

    private List<String> periodNames() {
        return Arrays.stream(StatsPeriod.values())
                .map(p -> p.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.toList());
    }

    private List<String> statusNames() {
        return Arrays.stream(StaffStatus.values()).map(Enum::name).collect(Collectors.toList());
    }

    private List<String> statusNamesAndSet() {
        List<String> result = new ArrayList<>(statusNames());
        result.add("set");
        return result;
    }

    private List<String> onlinePlayerNames(String prefix) {
        return filter(
                Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), prefix);
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower)).collect(Collectors.toList());
    }
}
