package com.elitelogs;

import com.elitelogs.handlers.EpicCmd;
import com.elitelogs.inspector.Inspector;
import com.elitelogs.listeners.*;
import com.elitelogs.utils.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import java.io.File;
import java.io.Writer;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
public class EliteLogsPlugin extends JavaPlugin {

    private Lang lang;
    private LogRouter logRouter;
    private ConsoleHook consoleHook; private ConsoleTee consoleTee;
    private MetricsCollector metricsCollector; private VaultEconomyTracker eco;
    private Watchdog watchdog;
    private SessionManager sessionManager;
    private PlayerTracker playerTracker;
    private Inspector inspector;

    @Override
    public void onEnable() {
        safeLoadConfig();

        // Lang + folders
        this.lang = new Lang(this); lang.load();
        createFolderTree();

        if (getConfig().getBoolean("banner.enabled", true)) {
            AsciiBanner.print(getLogger(), getDescription().getVersion(), getConfig().getString("banner.style","block"));
        }

        this.logRouter = new LogRouter(this);
        this.playerTracker = new PlayerTracker(getDataFolder());
        this.logRouter.setPlayerTracker(playerTracker);
        this.consoleHook = new ConsoleHook(logRouter);
        consoleHook.hook(); this.consoleTee = new ConsoleTee(logRouter); consoleTee.hook();
        DiscordAlerter.init(this);

        // Listeners
        if (getConfig().getBoolean("logs.types.chat", true)) Bukkit.getPluginManager().registerEvents(new ChatListener(logRouter, playerTracker), this);
        if (getConfig().getBoolean("logs.types.commands", true)) Bukkit.getPluginManager().registerEvents(new CommandListener(this, logRouter, playerTracker), this);
        if (getConfig().getBoolean("logs.types.combat", true)) Bukkit.getPluginManager().registerEvents(new DeathListener(logRouter), this);
        if (getConfig().getBoolean("logs.types.players", true)) Bukkit.getPluginManager().registerEvents(new JoinQuitListener(logRouter, playerTracker), this);
        if (getConfig().getBoolean("logs.types.combat", true)) Bukkit.getPluginManager().registerEvents(new CombatListener(logRouter), this);
        if (getConfig().getBoolean("logs.types.inventory", true)) Bukkit.getPluginManager().registerEvents(new InventoryListener(logRouter, playerTracker), this);
        if (getConfig().getBoolean("logs.types.economy", true)) Bukkit.getPluginManager().registerEvents(new EconomyListener(), this);
        if (getConfig().getBoolean("logs.types.rcon", true)) Bukkit.getPluginManager().registerEvents(new RconListener(logRouter), this);

        // Metrics + Watchdog + Economy
        this.metricsCollector = new MetricsCollector(this, logRouter); this.eco = new VaultEconomyTracker(this, logRouter, playerTracker);
        if (getConfig().getBoolean("metrics.enabled", true)) metricsCollector.start(); if (getConfig().getBoolean("logs.types.economy", true)) eco.start();

        this.watchdog = new Watchdog(this, logRouter, metricsCollector);
        if (getConfig().getBoolean("watchdog.enabled", true)) watchdog.start();

        // Sessions
        this.sessionManager = new SessionManager(this, logRouter);
        if (getConfig().getBoolean("sessions.enabled", true)) sessionManager.begin();

        // Inspector
        this.inspector = new Inspector(this, lang);
        if (getConfig().getBoolean("inspector.enabled", true)) {
            // delay to allow other plugins finish enabling
            getServer().getScheduler().runTaskLater(this, inspector::runAll, 20L * 10);
        }

        // Commands
        EpicCmd cmd = new EpicCmd(this, lang, logRouter, metricsCollector, inspector, sessionManager);
        if (getCommand("epiclogs") != null) {
            getCommand("epiclogs").setExecutor(cmd);
            getCommand("epiclogs").setTabCompleter(cmd);
        }

        showLastSessionSummary();
        printModules();

        getLogger().info(Lang.colorize(lang.get("plugin-enabled").replace("{version}", getDescription().getVersion())));
    }

    @Override
    public void onDisable() {
        if (metricsCollector != null) metricsCollector.stop(); if (eco != null) eco.stop();
        if (watchdog != null) watchdog.stop();
        if (consoleHook != null) consoleHook.unhook(); if (consoleTee != null) consoleTee.unhook();
        if (sessionManager != null) sessionManager.end();
        getLogger().info(Lang.colorize(lang.get("plugin-disabled")));
    }

    private void showLastSessionSummary() {
        try {
            File f = new File(getDataFolder(), "reports/sessions/last-session.txt");
            if (f.exists()) {
                getLogger().info("=== Last Session Summary ===");
                try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
                    String line; int i=0;
                    while ((line = br.readLine()) != null && i++ < 12) getLogger().info(line);
                }
            }
        } catch (Exception ignored){}
    }

    private void createFolderTree() {
        File data = getDataFolder();
        if (!data.exists()) {
            data.mkdirs();
        }
        List<String> folders = Arrays.asList(
                "logs/info","logs/warns","logs/errors","logs/chat","logs/commands","logs/players",
                "logs/combat","logs/inventory","logs/economy","logs/stats","logs/console","logs/rcon","logs/suppressed",
                "reports/sessions","reports/inspector",
                "archive","exports","lang"
        );
        for (String path : folders) {
            File f = new File(data, path);
            if (!f.exists()) f.mkdirs();
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

    public Lang lang(){ return lang; }

    // === Config auto-generate & self-heal ===
    private void writeDefaultConfigFile(File cfg) {
        cfg.getParentFile().mkdirs();
        final String[] LINES = new String[]{
            "# ============================",
            "#  EliteLogs Configuration",
            "#  (All comments are English only)",
            "# ============================",
            "# Encoding: UTF-8 (no BOM)",
            "# Indentation: 2 spaces (tabs are NOT allowed)",
            "",
            "enabled: true",
            "debug: false",
            "language: en",
            "",
            "ansi:",
            "  enabled: true",
            "  color-ok: \"§a\"",
            "  color-warn: \"§e\"",
            "  color-fail: \"§c\"",
            "  reset: \"§f\"",
            "",
            "banner:",
            "  enabled: true",
            "  show-version: true",
            "  style: block",
            "  color: default",
            "",
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
            "logs:",
            "  rotate: true",
            "  keep-days: 30",
            "  archive: true",
            "  split-by-player: true",
            "  legacy:",
            "    flat-player-files: false",
            "  types:",
            "    info: true",
            "    warns: true",
            "    errors: true",
            "    chat: true",
            "    commands: true",
            "    players: true",
            "    combat: true",
            "    inventory: true",
            "    economy: true",
            "    stats: true",
            "    console: true",
            "    suppressed: true",
            "",
            "sessions:",
            "  enabled: true",
            "  autosave-minutes: 10",
            "  save-global: true",
            "  save-players: true",
            "",
            "inspector:",
            "  enabled: true",
            "  include-mods: true",
            "  include-configs: true",
            "  include-garbage: true",
            "  include-server-info: true",
            "",
            "metrics:",
            "  enabled: true",
            "  interval-seconds: 60",
            "",
            "suppressor:",
            "  enabled: true",
            "  mode: blacklist",
            "  spam-limit: 1000",
            "  filters: []",
            "",
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
            for (String s : LINES) { w.write(s); w.write('\n'); }
        } catch (Exception ex) {
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
    
}