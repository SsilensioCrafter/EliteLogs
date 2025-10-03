package com.elitelogs;

import com.elitelogs.api.ApiServer;
import com.elitelogs.bootstrap.DataDirectoryManager;
import com.elitelogs.bootstrap.InspectorBootstrap;
import com.elitelogs.bootstrap.ListenerRegistrar;
import com.elitelogs.bootstrap.LoggingBootstrap;
import com.elitelogs.bootstrap.LoggingBootstrap.LoggingServices;
import com.elitelogs.bootstrap.MetricsBootstrap;
import com.elitelogs.bootstrap.SessionBootstrap;
import com.elitelogs.commands.ApiKeySubcommand;
import com.elitelogs.commands.EliteLogsCommand;
import com.elitelogs.commands.ExportSubcommand;
import com.elitelogs.commands.HelpSubcommand;
import com.elitelogs.commands.InspectorSubcommand;
import com.elitelogs.commands.LogsSubcommand;
import com.elitelogs.commands.MetricsSubcommand;
import com.elitelogs.commands.ReloadSubcommand;
import com.elitelogs.commands.RotateSubcommand;
import com.elitelogs.commands.SessionSubcommand;
import com.elitelogs.commands.VersionSubcommand;
import com.elitelogs.compat.ServerCompat;
import com.elitelogs.inspector.Inspector;
import com.elitelogs.integration.DiscordAlerter;
import com.elitelogs.integration.VaultEconomyTracker;
import com.elitelogs.localization.Lang;
import com.elitelogs.logging.ConsoleHook;
import com.elitelogs.logging.ConsoleTee;
import com.elitelogs.logging.LogRouter;
import com.elitelogs.metrics.MetricsCollector;
import com.elitelogs.metrics.Watchdog;
import com.elitelogs.players.PlayerTracker;
import com.elitelogs.reporting.SessionManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.Arrays;
public class EliteLogsPlugin extends JavaPlugin {

    private Lang lang;
    private DataDirectoryManager dataDirectories;
    private PlayerTracker playerTracker;
    private LoggingBootstrap loggingBootstrap;
    private LoggingServices loggingServices;
    private LogRouter logRouter;
    private ConsoleHook consoleHook;
    private ConsoleTee consoleTee;
    private MetricsBootstrap metricsBootstrap;
    private MetricsCollector metricsCollector;
    private VaultEconomyTracker economyTracker;
    private Watchdog watchdog;
    private SessionBootstrap sessionBootstrap;
    private SessionManager sessionManager;
    private InspectorBootstrap inspectorBootstrap;
    private Inspector inspector;
    private ListenerRegistrar listenerRegistrar;
    private ApiServer apiServer;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public void onEnable() {
        safeLoadConfig();
        ensureApiToken(true);

        this.lang = new Lang(this);
        lang.load();

        this.dataDirectories = new DataDirectoryManager(this);
        dataDirectories.ensureStructure();

        this.playerTracker = new PlayerTracker(getDataFolder());

        getLogger().info("[EliteLogs] Server version: " + ServerCompat.describeServerVersion());
        getLogger().info("[EliteLogs] Compatibility range: " + ServerCompat.describeSupportedRange());

        this.loggingBootstrap = new LoggingBootstrap(this);
        this.loggingServices = loggingBootstrap.start(playerTracker);
        this.logRouter = loggingServices.router();
        this.consoleHook = loggingServices.hook();
        this.consoleTee = loggingServices.tee();

        DiscordAlerter.init(this);

        this.listenerRegistrar = new ListenerRegistrar(this, logRouter, playerTracker);
        listenerRegistrar.registerAll();

        this.metricsBootstrap = new MetricsBootstrap(this);
        metricsBootstrap.start(logRouter, playerTracker);
        this.metricsCollector = metricsBootstrap.getMetricsCollector();
        this.economyTracker = metricsBootstrap.getEconomyTracker();
        this.watchdog = metricsBootstrap.getWatchdog();

        this.sessionBootstrap = new SessionBootstrap(this);
        this.sessionManager = sessionBootstrap.start(logRouter);

        this.inspectorBootstrap = new InspectorBootstrap(this, lang);
        this.inspector = inspectorBootstrap.start();

        this.apiServer = new ApiServer(this, logRouter, metricsCollector, sessionManager, watchdog);
        apiServer.start();

        registerCommands();

        dataDirectories.logLastSessionSummary();
        printModules();

        getLogger().info(Lang.colorize(lang.get("plugin-enabled").replace("{version}", getDescription().getVersion())));
    }

    @Override
    public void onDisable() {
        if (metricsBootstrap != null) {
            metricsBootstrap.shutdown();
        }
        if (sessionBootstrap != null) {
            sessionBootstrap.shutdown();
        }
        if (loggingBootstrap != null) {
            loggingBootstrap.shutdown();
        }
        if (apiServer != null) {
            apiServer.stop();
        }
        getLogger().info(Lang.colorize(lang.get("plugin-disabled")));
    }

    private void registerCommands() {
        HelpSubcommand help = new HelpSubcommand(this, lang);
        EliteLogsCommand elite = new EliteLogsCommand(
                lang,
                help,
                Arrays.asList(
                        new ReloadSubcommand(this, lang, logRouter),
                        new VersionSubcommand(this, lang),
                        new MetricsSubcommand(this, lang, metricsCollector),
                        new RotateSubcommand(this, lang, logRouter),
                        new ExportSubcommand(this, lang),
                        new InspectorSubcommand(this, lang, inspector),
                        new ApiKeySubcommand(this, lang),
                        new LogsSubcommand(this, lang, logRouter),
                        new SessionSubcommand(this, lang, sessionManager)
                )
        );
        if (getCommand("elitelogs") != null) {
            getCommand("elitelogs").setExecutor(elite);
            getCommand("elitelogs").setTabCompleter(elite);
        }
    }

    private void printModules() {
        getLogger().info(Lang.colorize(lang.get("modules-header")));
        getLogger().info(Lang.colorize(lang.formatModule("ConsoleHook", consoleHook != null)));
        getLogger().info(Lang.colorize(lang.formatModule("Suppressor", true)));
        getLogger().info(Lang.colorize(lang.formatModule("MetricsCollector", metricsCollector != null)));
        getLogger().info(Lang.colorize(lang.formatModule("Watchdog", watchdog != null)));
        getLogger().info(Lang.colorize(lang.formatModule("Inspector", inspector != null)));
        getLogger().info(Lang.colorize(lang.formatModule("SessionManager", sessionManager != null)));
        getLogger().info(Lang.colorize(lang.formatModule("PlayerTracker", playerTracker != null)));
    }

    public Lang lang() {
        return lang;
    }

    public Inspector inspector() {
        return inspector;
    }

    public void reloadApi() {
        if (apiServer != null) {
            ensureApiToken(false);
            apiServer.reload();
        }
    }

    // === Config auto-generate & self-heal ===
    private void writeDefaultConfigFile(File cfg) {
        cfg.getParentFile().mkdirs();
        try (InputStream in = getResource("config.yml")) {
            if (in != null) {
                Files.copy(in, cfg.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return;
            }
        } catch (IOException ex) {
            getLogger().warning("Failed to copy bundled config.yml, falling back to minimal template: " + ex.getMessage());
        }

        final String version = getDescription().getVersion();
        final String[] lines = new String[]{
                "# ============================",
                "#  EliteLogs Configuration",
                "#  (All comments are English only)",
                "# ============================",
                "# Encoding: UTF-8 (no BOM)",
                "# Indentation: 2 spaces (tabs are NOT allowed)",
                "# Tip: values prefixed with \"§\" use Minecraft color codes.",
                "",
                "# ── Version metadata ─────────────────────────────────────────────────────────",
                "# Automatically updated during builds; keep it untouched for support diagnostics.",
                "version: \"" + version + "\"",
                "",
                "# ── Core behaviour ──────────────────────────────────────────────────────────────",
                "enabled: true",
                "debug: false",
                "language: en",
                "",
                "# ── Console colors ──────────────────────────────────────────────────────────────",
                "ansi:",
                "  enabled: true",
                "  color-ok: \"§a\"",
                "  color-warn: \"§e\"",
                "  color-fail: \"§c\"",
                "  reset: \"§f\"",
                "",
                "# ── Start-up banner ─────────────────────────────────────────────────────────────",
                "banner:",
                "  enabled: true",
                "  show-version: true",
                "  style: block",
                "  color: default",
                "",
                "# ── Discord webhooks ───────────────────────────────────────────────────────────",
                "discord:",
                "  enabled: false",
                "  webhook-url: \"\"",
                "  rate-limit-seconds: 10",
                "  send:",
                "    errors: true",
                "    warns: true",
                "    sessions: true",
                "    watchdog: true",
                "    inspector: true",
                "",
                "# ── File logging ───────────────────────────────────────────────────────────────",
                "logs:",
                "  rotate: true",
                "  keep-days: 30",
                "  archive: true",
                "  split-by-player: true",
                "  legacy:",
                "    flat-player-files: false",
                "  types:",
                "    warns: true",
                "    errors: true",
                "    chat: true",
                "    commands: true",
                "    players: true",
                "    disconnects: true",
                "    combat: true",
                "    inventory: true",
                "    economy: true",
                "    stats: true",
                "    console: true",
                "    rcon: true",
                "    suppressed: true",
                "  disconnects:",
                "    capture-screen: true",
                "",
                "# ── Database mirroring ───────────────────────────────────────────────────────",
                "storage:",
                "  database:",
                "    enabled: false",
                "    table-prefix: \"elitelogs_\"",
                "    auto-upgrade: true",
                "    batching:",
                "      size: 100",
                "      flush-interval-ticks: 2",
                "    connection:",
                "      jdbc-url: \"\"",
                "      host: \"127.0.0.1\"",
                "      port: 3306",
                "      database: \"elitelogs\"",
                "      username: \"elitelogs\"",
                "      password: \"\"",
                "      properties:",
                "        useSSL: false",
                "        allowPublicKeyRetrieval: true",
                "        rewriteBatchedStatements: true",
                "        serverTimezone: UTC",
                "        characterEncoding: UTF-8",
                "    pool:",
                "      minimum-idle: 1",
                "      maximum-pool-size: 8",
                "      connection-timeout-millis: 8000",
                "      max-lifetime-millis: 1800000",
                "",
                "# ── Session reports ───────────────────────────────────────────────────────────",
                "sessions:",
                "  enabled: true",
                "  autosave-minutes: 10",
                "  save-global: true",
                "  save-players: true",
                "",
                "# ── Inspector snapshots ────────────────────────────────────────────────────────",
                "inspector:",
                "  enabled: true",
                "  include-mods: true",
                "  include-configs: true",
                "  include-garbage: true",
                "  include-server-info: true",
                "",
                "# ── Metrics collector ──────────────────────────────────────────────────────────",
                "metrics:",
                "  enabled: true",
                "  interval-seconds: 60",
                "",
                "# ── HTTP API ───────────────────────────────────────────────────────────────────",
                "api:",
                "  enabled: false",
                "  bind: \"127.0.0.1\"",
                "  port: 9173",
                "  auth-token: \"\"       # Leave blank to auto-generate, manage via /elogs apikey",
                "  log-history: 250      # Size of the in-memory buffer used for instant responses",
                "  default-source: buffer",
                "  sources:",
                "    buffer:",
                "      enabled: true",
                "    files:",
                "      enabled: true",
                "      root: logs        # Relative path under the plugin data folder for file lookups",
                "    database:",
                "      enabled: true",
                "  endpoints:",
                "    status:",
                "      enabled: true",
                "    metrics:",
                "      enabled: true",
                "    watchdog:",
                "      enabled: true",
                "    sessions:",
                "      enabled: true",
                "    logs:",
                "      enabled: true",
                "      default-limit: 250",
                "      allow-sources:",
                "        - buffer",
                "        - files",
                "        - database",
                "    search:",
                "      enabled: true",
                "      default-limit: 250",
                "      allow-sources:",
                "        - database",
                "        - files",
                "",
                "# ── Chat suppressor ────────────────────────────────────────────────────────────",
                "suppressor:",
                "  enabled: true",
                "  mode: blacklist",
                "  spam-limit: 1000",
                "  cache-max-entries: 10000",
                "  cache-ttl-seconds: 300",
                "  filters: []",
                "",
                "# ── Watchdog automation ────────────────────────────────────────────────────────",
                "watchdog:",
                "  enabled: true",
                "  tps-threshold: 5.0",
                "  error-threshold: 50",
                "  actions:",
                "    run-inspector: true",
                "    create-crash-report: true",
                "    discord-alert: true"
        };
        try (Writer w = new OutputStreamWriter(new FileOutputStream(cfg), StandardCharsets.UTF_8)) {
            for (String s : lines) {
                w.write(s);
                w.write('\n');
            }
        } catch (IOException ex) {
            getLogger().severe("Failed to write default config.yml: " + ex.getMessage());
        }
    }

    private void safeLoadConfig() {
        File data = getDataFolder();
        File cfg = new File(data, "config.yml");

        if (!cfg.exists()) {
            try { saveResource("config.yml", false); } catch (Throwable ignore) {}
            if (!cfg.exists()) { writeDefaultConfigFile(cfg); }
        }

        try { this.reloadConfig(); }
        catch (Throwable ex) {
            getLogger().severe("Invalid config.yml detected. Replacing with default: " + ex.getMessage());
            try {
                File bad = new File(data, "config.invalid." + System.currentTimeMillis() + ".yml");
                if (cfg.exists()) {
                    Files.move(cfg.toPath(), bad.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception ignore) {}
            try { saveResource("config.yml", true); } catch (Throwable ignore) {}
            if (!cfg.exists()) { writeDefaultConfigFile(cfg); }
            try { this.reloadConfig(); } catch (Throwable ignore) {}
        }
    }
    // === End config helpers ===

    public synchronized String ensureApiToken(boolean announceCreation) {
        String raw = getConfig().getString("api.auth-token", "");
        String sanitized = raw != null ? raw.trim() : "";
        if (!sanitized.isEmpty()) {
            return sanitized;
        }
        String generated = generateApiToken();
        getConfig().set("api.auth-token", generated);
        saveConfig();
        if (announceCreation) {
            getLogger().info("[EliteLogs] Generated new API key (store it safely): " + generated);
        }
        return generated;
    }

    public synchronized String regenerateApiToken() {
        String generated = generateApiToken();
        getConfig().set("api.auth-token", generated);
        saveConfig();
        reloadApi();
        return generated;
    }

    public synchronized String getApiToken() {
        String raw = getConfig().getString("api.auth-token", "");
        return raw != null ? raw.trim() : "";
    }

    private String generateApiToken() {
        final char[] alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789".toCharArray();
        final int length = 48;
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = secureRandom.nextInt(alphabet.length);
            builder.append(alphabet[index]);
        }
        return builder.toString();
    }

}
