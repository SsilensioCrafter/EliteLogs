package com.elitelogs.inspector;

import com.elitelogs.EliteLogsPlugin;
import com.elitelogs.utils.DiscordAlerter;
import com.elitelogs.utils.Lang;
import com.elitelogs.utils.YamlReportWriter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;

public class Inspector {
    private final EliteLogsPlugin plugin;
    private final File outDir;
    private final File serverRoot;
    private final File configDir;
    private final File serverConfigDir;
    private final Lang lang;

    public Inspector(EliteLogsPlugin plugin, Lang lang){
        this.plugin = plugin;
        this.lang = lang;
        this.outDir = new File(plugin.getDataFolder(), "reports/inspector");
        this.serverRoot = plugin.getServer().getWorldContainer();
        this.configDir = new File(serverRoot, "config");
        this.serverConfigDir = new File(serverRoot, "serverconfig");
        if (!outDir.exists()) outDir.mkdirs();
    }

    public void runAll(){
        String ts = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        File f = new File(outDir, "inspector-report-" + ts + ".yml");
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8))) {
            YamlReportWriter yaml = new YamlReportWriter(pw);
            writePlugins(yaml);
            writeMods(yaml);
            writeConfigs(yaml);
            writeGarbage(yaml);
            writeServerInfo(yaml);
            yaml.flush();
            DiscordAlerter.maybeSend("inspector","Inspector report updated: " + f.getName());
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "[EliteLogs] Failed to write inspector report " + f.getName(), ex);
        }
    }

    private void writePlugins(YamlReportWriter yaml){
        yaml.section("plugins", section -> {
            section.scalar("header", t("inspector.plugins-header"));
            Plugin[] ps = Bukkit.getPluginManager().getPlugins();
            if (ps.length == 0) {
                section.emptyList("entries");
                return;
            }
            section.list("entries", list -> {
                for (Plugin p : ps){
                    String ver = (p.getDescription()!=null && p.getDescription().getVersion()!=null && !p.getDescription().getVersion().isEmpty())
                            ? p.getDescription().getVersion() : t("inspector.plugins-version-unknown");
                    String state = p.isEnabled() ? t("inspector.plugins-enabled") : t("inspector.plugins-disabled");
                    list.item(item -> {
                        item.scalar("name", p.getName());
                        item.scalar("state", state);
                        item.scalar("version", ver);
                    });
                }
            });
        });
        yaml.blankLine();
    }

    private void writeMods(YamlReportWriter yaml){
        boolean include = plugin.getConfig().getBoolean("inspector.include-mods", true);
        yaml.section("mods", section -> {
            section.scalar("header", t("inspector.mods-header"));
            section.scalar("included", include);
            if (!include) {
                return;
            }
            try {
                Class<?> modList = Class.forName("net.minecraftforge.fml.ModList");
                Object inst = modList.getMethod("get").invoke(null);
                Collection<?> mods = (Collection<?>) modList.getMethod("getMods").invoke(inst);
                if (mods == null || mods.isEmpty()) {
                    section.emptyList("entries");
                } else {
                    section.list("entries", list -> {
                        for (Object m : mods){
                            String id = describeModString(m, "getModId");
                            String ver = describeModString(m, "getVersion");
                            list.item(item -> {
                                item.scalar("id", id);
                                item.scalar("version", ver);
                            });
                        }
                    });
                }
            } catch (Throwable t){
                section.scalar("error", t("inspector.mods-missing"));
            }
        });
        yaml.blankLine();
    }

    private void writeConfigs(YamlReportWriter yaml){
        boolean include = plugin.getConfig().getBoolean("inspector.include-configs", true);
        List<String> okExt = Arrays.asList(".yml",".yaml",".json",".toml",".cfg");
        yaml.section("configs", section -> {
            section.scalar("header", t("inspector.configs-header"));
            section.scalar("included", include);
            if (!include) {
                return;
            }
            List<String[]> entries = new ArrayList<>();
            for (File dir : new File[]{configDir, serverConfigDir}){
                if (dir == null || !dir.exists()) continue;
                try {
                    Files.walk(dir.toPath()).filter(Files::isRegularFile).forEach(p -> {
                        File cf = p.toFile();
                        String rel = dir.toPath().relativize(p).toString().replace("\\","/");
                        String name = cf.getName().toLowerCase(Locale.ROOT);
                        String ext = name.contains(".") ? name.substring(name.lastIndexOf(".")) : "";
                        String status = t("inspector.configs-status-ok");
                        if (cf.length() == 0) status = t("inspector.configs-status-empty");
                        else if (!okExt.contains(ext)) status = t("inspector.configs-status-not-needed");
                        String base = name.replace(ext,"");
                        boolean pluginExists = Arrays.stream(Bukkit.getPluginManager().getPlugins())
                                .anyMatch(pl -> pl.getName().equalsIgnoreCase(base));
                        if (!pluginExists && okExt.contains(ext)) status = t("inspector.configs-status-orphaned");
                        entries.add(new String[]{dir.getName(), rel, status});
                    });
                } catch (IOException ignored){}
            }
            if (entries.isEmpty()) {
                section.emptyList("entries");
            } else {
                section.list("entries", list -> {
                    for (String[] entry : entries) {
                        list.item(item -> {
                            item.scalar("folder", entry[0]);
                            item.scalar("path", entry[1]);
                            item.scalar("status", entry[2]);
                        });
                    }
                });
            }
        });
        yaml.blankLine();
    }

    private void writeGarbage(YamlReportWriter yaml){
        boolean include = plugin.getConfig().getBoolean("inspector.include-garbage", true);
        yaml.section("garbage", section -> {
            section.scalar("header", t("inspector.garbage-header"));
            section.scalar("included", include);
            if (!include) {
                return;
            }
            List<String> entries = new ArrayList<>();
            scanGarbage(entries, new File(serverRoot, "plugins"), ".jar");
            scanGarbage(entries, new File(serverRoot, "mods"), ".jar");
            scanGarbage(entries, configDir, ".yml",".yaml",".json",".toml",".cfg");
            scanGarbage(entries, serverConfigDir, ".yml",".yaml",".json",".toml",".cfg");
            File[] files = serverRoot.listFiles();
            if (files != null) for (File x : files){
                String n = x.getName().toLowerCase(Locale.ROOT);
                if (n.endsWith(".zip") || n.endsWith(".rar") || n.endsWith(".old") || n.endsWith(".log")){
                    entries.add(t("inspector.garbage-root").replace("{name}", x.getName()));
                }
            }
            if (entries.isEmpty()) {
                section.emptyList("entries");
            } else {
                section.list("entries", list -> entries.forEach(list::itemScalar));
            }
        });
        yaml.blankLine();
    }

    private void scanGarbage(List<String> target, File dir, String... goodExts){
        if (dir == null || !dir.exists()) return;
        Set<String> ok = new HashSet<>(Arrays.asList(goodExts));
        File[] files = dir.listFiles(); if (files == null) return;
        for (File f : files){
            if (f.isDirectory()) continue;
            String n = f.getName().toLowerCase(Locale.ROOT);
            String ext = n.contains(".") ? n.substring(n.lastIndexOf(".")) : "";
            if (!ok.contains(ext)) target.add(t("inspector.garbage-entry")
                    .replace("{folder}", dir.getName())
                    .replace("{file}", f.getName()));
        }
    }

    private String describeModString(Object mod, String method) {
        if (mod == null || method == null || method.isEmpty()) {
            return "unknown";
        }
        try {
            Method accessor = mod.getClass().getMethod(method);
            Object result = accessor.invoke(mod);
            if (result != null) {
                return String.valueOf(result);
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINEST, "[EliteLogs] Failed to query mod attribute " + method, t);
        }
        return "unknown";
    }

    private void writeServerInfo(YamlReportWriter yaml){
        boolean include = plugin.getConfig().getBoolean("inspector.include-server-info", true);
        yaml.section("server", section -> {
            section.scalar("header", t("inspector.server-header"));
            section.scalar("included", include);
            if (!include) {
                return;
            }
            section.section("details", details -> {
                details.scalar("bukkit", Bukkit.getVersion());
                details.scalar("minecraft", Bukkit.getBukkitVersion());
                details.scalar("java", System.getProperty("java.version"));
                details.scalar("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
                details.scalar("cpu-cores", String.valueOf(ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors()));
            });
        });
        yaml.blankLine();
    }

    private String t(String key){
        return lang.get(key);
    }
}
