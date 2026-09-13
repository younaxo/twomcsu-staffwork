package su.twomc.staffwork;

import java.io.File;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import su.twomc.staffwork.command.StaffWorkCommand;
import su.twomc.staffwork.config.ConfigManager;
import su.twomc.staffwork.config.MessageFormatter;
import su.twomc.staffwork.config.MessagesManager;
import su.twomc.staffwork.config.MySqlSettings;
import su.twomc.staffwork.config.PluginConfig;
import su.twomc.staffwork.config.TelegramSettings;
import su.twomc.staffwork.listener.ActivityListener;
import su.twomc.staffwork.listener.PlayerConnectionListener;
import su.twomc.staffwork.model.StaffStatus;
import su.twomc.staffwork.placeholder.TmcStaffPlaceholderExpansion;
import su.twomc.staffwork.repository.RepositoryException;
import su.twomc.staffwork.repository.StaffRepository;
import su.twomc.staffwork.repository.sql.H2StaffRepository;
import su.twomc.staffwork.repository.sql.MySqlStaffRepository;
import su.twomc.staffwork.repository.sql.SqliteStaffRepository;
import su.twomc.staffwork.repository.yaml.YamlStaffRepository;
import su.twomc.staffwork.scheduler.CancellableTask;
import su.twomc.staffwork.scheduler.PlatformScheduler;
import su.twomc.staffwork.scheduler.SchedulerProvider;
import su.twomc.staffwork.service.AfkSweeper;
import su.twomc.staffwork.service.AutoStatusService;
import su.twomc.staffwork.service.EmployeeService;
import su.twomc.staffwork.service.PlaceholderCache;
import su.twomc.staffwork.service.RecoveryService;
import su.twomc.staffwork.service.SessionService;
import su.twomc.staffwork.service.StatisticsService;
import su.twomc.staffwork.service.StatusService;
import su.twomc.staffwork.telegram.LinkCodeService;
import su.twomc.staffwork.telegram.NotificationDispatcher;
import su.twomc.staffwork.telegram.TelegramManager;

/** Точка входа плагина — собирает все слои и управляет их жизненным циклом. */
public final class TmcStaffWorkPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private PlatformScheduler scheduler;
    private StaffRepository repository;
    private MessagesManager messages;

    private EmployeeService employeeService;
    private StatusService statusService;
    private SessionService sessionService;
    private StatisticsService statisticsService;
    private AutoStatusService autoStatusService;
    private PlaceholderCache placeholderCache;
    private AfkSweeper afkSweeper;

    private LinkCodeService linkCodeService;
    private TelegramManager telegramManager;
    private NotificationDispatcher notificationDispatcher;

    private CancellableTask heartbeatTask;
    private CancellableTask maintenanceTask;

    @Override
    public void onEnable() {
        this.scheduler = SchedulerProvider.detect(this);
        getLogger()
                .info(scheduler.isFolia()
                        ? "Обнаружена Folia — используются региональные планировщики Paper API."
                        : "Используется стандартный планировщик Bukkit/Paper.");

        this.configManager = new ConfigManager(this);
        configManager.load();

        try {
            this.repository = buildRepository();
            repository.initialize();
        } catch (RepositoryException e) {
            getLogger().log(Level.SEVERE, "Не удалось инициализировать хранилище данных — плагин будет отключён.", e);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        MessageFormatter formatter = new MessageFormatter();
        this.messages = new MessagesManager(this, formatter);
        messages.reload();

        wireServices();
        startTelegramIfEnabled();

        String serverId = configManager.pluginConfig().serverId();
        new RecoveryService(repository, getLogger()).recoverCrashedSessions(serverId, StaffStatus.OFF_DUTY);

        startMaintenanceTasks();
        registerCommand();
        registerListeners();
        registerPlaceholderApi();

        getLogger().info("TMC StaffWork " + getPluginMeta().getVersion() + " успешно запущен.");
    }

    @Override
    public void onDisable() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
        }
        if (maintenanceTask != null) {
            maintenanceTask.cancel();
        }
        if (telegramManager != null) {
            telegramManager.stop();
        }
        if (repository != null && configManager != null && configManager.pluginConfig() != null) {
            try {
                new RecoveryService(repository, getLogger())
                        .closeAllForShutdown(configManager.pluginConfig().serverId(), StaffStatus.OFF_DUTY);
            } catch (RepositoryException e) {
                getLogger().log(Level.WARNING, "Не удалось корректно закрыть активные сессии при остановке", e);
            }
            repository.close();
        }
        getLogger().info("TMC StaffWork остановлен.");
    }

    private StaffRepository buildRepository() {
        PluginConfig cfg = configManager.pluginConfig();
        return switch (cfg.storageType()) {
            case YAML -> new YamlStaffRepository(new File(getDataFolder(), cfg.yamlFile()), getLogger());
            case SQLITE -> new SqliteStaffRepository(new File(getDataFolder(), cfg.sqliteFile()), getLogger());
            case H2 -> new H2StaffRepository(new File(getDataFolder(), cfg.h2File()), getLogger());
            case MYSQL -> {
                MySqlSettings s = cfg.mySqlSettings();
                yield new MySqlStaffRepository(
                        s.host(), s.port(), s.database(), s.username(), s.password(), s.useSsl(), s.poolSize(), getLogger());
            }
        };
    }

    private void wireServices() {
        PluginConfig cfg = configManager.pluginConfig();
        this.statusService = new StatusService(repository);
        this.sessionService = new SessionService(repository, statusService);
        this.statisticsService = new StatisticsService(repository, cfg.countedStatuses(), cfg.zoneId());
        this.employeeService = new EmployeeService(repository);
        this.autoStatusService = new AutoStatusService(statusService, sessionService, cfg.autoStatusSettings());
        this.placeholderCache = new PlaceholderCache(employeeService, sessionService, statisticsService);
        this.afkSweeper = new AfkSweeper(employeeService, autoStatusService, this::onlineUuids);

        TelegramSettings telegramSettings = cfg.telegramSettings();
        this.linkCodeService = new LinkCodeService(
                repository, telegramSettings.linkCodeTtl(), telegramSettings.rateLimitMaxAttempts(), telegramSettings.rateLimitWindow());
    }

    private void startTelegramIfEnabled() {
        TelegramSettings settings = configManager.pluginConfig().telegramSettings();
        if (settings.enabled() && settings.botToken() != null && !settings.botToken().isBlank()) {
            this.telegramManager =
                    new TelegramManager(settings.botToken(), linkCodeService, scheduler, getLogger(), settings.messageTemplates());
            telegramManager.start();
        } else {
            this.telegramManager = null;
            if (settings.enabled()) {
                getLogger().warning("Telegram включён в конфигурации, но bot-token не задан — интеграция не запущена.");
            }
        }
        this.notificationDispatcher = new NotificationDispatcher(settings, telegramManager);
    }

    private void startMaintenanceTasks() {
        String serverId = configManager.pluginConfig().serverId();
        long heartbeatMillis = configManager.pluginConfig().heartbeatIntervalSeconds() * 1000L;
        this.heartbeatTask = scheduler.runAsyncTimer(
                () -> repository.saveHeartbeat(serverId, Instant.now()), 0, heartbeatMillis);

        this.maintenanceTask = scheduler.runAsyncTimer(
                () -> {
                    afkSweeper.run();
                    placeholderCache.refresh(onlineUuids());
                },
                5000,
                15000);
    }

    private Collection<UUID> onlineUuids() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList();
    }

    private void registerCommand() {
        StaffWorkCommand command = new StaffWorkCommand(
                this,
                scheduler,
                employeeService,
                sessionService,
                statusService,
                statisticsService,
                configManager,
                messages,
                linkCodeService,
                this::reloadAll);
        var pluginCommand = getCommand("staffwork");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        } else {
            getLogger().severe("Команда 'staffwork' не найдена в plugin.yml — регистрация невозможна.");
        }
    }

    private void registerListeners() {
        Bukkit.getPluginManager()
                .registerEvents(
                        new PlayerConnectionListener(
                                employeeService, autoStatusService, notificationDispatcher, scheduler, configManager, messages),
                        this);
        Bukkit.getPluginManager().registerEvents(new ActivityListener(employeeService, autoStatusService, scheduler), this);
    }

    private void registerPlaceholderApi() {
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new TmcStaffPlaceholderExpansion(this, placeholderCache, configManager.pluginConfig().statusColors()).register();
            getLogger().info("Обнаружен PlaceholderAPI — плейсхолдеры tmcstaff_* зарегистрированы.");
        } else {
            getLogger().info("PlaceholderAPI не найден — плейсхолдеры недоступны (это не ошибка, зависимость мягкая).");
        }
    }

    /** Безопасный reload: конфигурация, сообщения и настраиваемое поведение сервисов — без пересоздания хранилища. */
    private void reloadAll() {
        configManager.load();
        messages.reload();
        PluginConfig cfg = configManager.pluginConfig();
        statisticsService.updateSettings(cfg.countedStatuses(), cfg.zoneId());
        autoStatusService.updateSettings(cfg.autoStatusSettings());
        notificationDispatcher.updateSettings(cfg.telegramSettings());
        getLogger()
                .info("Конфигурация перезагружена. Изменение типа хранилища данных и токена Telegram "
                        + "требует полного перезапуска сервера.");
    }
}
