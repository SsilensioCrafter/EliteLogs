package com.elitelogs.inspector;

import com.elitelogs.EliteLogsPlugin;
import com.elitelogs.utils.DiscordAlerter;
import com.elitelogs.utils.Lang;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;

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
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8))){
            writePlugins(pw); writeMods(pw); writeConfigs(pw); writeGarbage(pw); writeServerInfo(pw);
            DiscordAlerter.maybeSend("inspector","Inspector report updated: " + f.getName());
        } catch (Exception ignored){}
    }

    private void writePlugins(PrintWriter pw){
        pw.println("plugins:");
        pw.println("  header: " + quote(t("inspector.plugins-header")));
        Plugin[] ps = Bukkit.getPluginManager().getPlugins();
        if (ps.length == 0) {
            pw.println("  entries: []");
            pw.println();
            return;
        }
        pw.println("  entries:");
        for (Plugin p : ps){
            String ver = (p.getDescription()!=null && p.getDescription().getVersion()!=null && !p.getDescription().getVersion().isEmpty())
                    ? p.getDescription().getVersion() : t("inspector.plugins-version-unknown");
            String state = p.isEnabled() ? t("inspector.plugins-enabled") : t("inspector.plugins-disabled");
            pw.println("    - name: " + quote(p.getName()));
            pw.println("      state: " + quote(state));
            pw.println("      version: " + quote(ver));
        }
        pw.println();
    }

    private void writeMods(PrintWriter pw){
        boolean include = plugin.getConfig().getBoolean("inspector.include-mods", true);
        pw.println("mods:");
        pw.println("  header: " + quote(t("inspector.mods-header")));
        pw.println("  included: " + include);
        if (!include) {
            pw.println();
            return;
        }
        try {
            Class<?> modList = Class.forName("net.minecraftforge.fml.ModList");
            Object inst = modList.getMethod("get").invoke(null);
            Collection<?> mods = (Collection<?>) modList.getMethod("getMods").invoke(inst);
            if (mods == null || mods.isEmpty()) {
                pw.println("  entries: []");
            } else {
                pw.println("  entries:");
                for (Object m : mods){
                    String id = String.valueOf(m.getClass().getMethod("getModId").invoke(m));
                    String ver = String.valueOf(m.getClass().getMethod("getVersion").invoke(m));
                    pw.println("    - id: " + quote(id));
                    pw.println("      version: " + quote(ver));
                }
            }
        } catch (Throwable t){
            pw.println("  error: " + quote(t("inspector.mods-missing")));
        }
        pw.println();
    }

    private void writeConfigs(PrintWriter pw){
        boolean include = plugin.getConfig().getBoolean("inspector.include-configs", true);
        List<String> okExt = Arrays.asList(".yml",".yaml",".json",".toml",".cfg");
        pw.println("configs:");
        pw.println("  header: " + quote(t("inspector.configs-header")));
        pw.println("  included: " + include);
        if (!include) {
            pw.println();
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
                    try {
                        entries.add(new String[]{dir.getName(), rel, status});
                    } catch(Exception ignored){}
                });
            } catch (Exception ignored){}
        }
        if (entries.isEmpty()) {
            pw.println("  entries: []");
        } else {
            pw.println("  entries:");
            for (String[] entry : entries) {
                pw.println("    - folder: " + quote(entry[0]));
                pw.println("      path: " + quote(entry[1]));
                pw.println("      status: " + quote(entry[2]));
            }
        }
        pw.println();
    }

    private void writeGarbage(PrintWriter pw){
        boolean include = plugin.getConfig().getBoolean("inspector.include-garbage", true);
        pw.println("garbage:");
        pw.println("  header: " + quote(t("inspector.garbage-header")));
        pw.println("  included: " + include);
        if (!include) {
            pw.println();
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
            pw.println("  entries: []");
        } else {
            pw.println("  entries:");
            for (String entry : entries) {
                pw.println("    - " + quote(entry));
            }
        }
        pw.println();
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

    private void writeServerInfo(PrintWriter pw){
        boolean include = plugin.getConfig().getBoolean("inspector.include-server-info", true);
        pw.println("server:");
        pw.println("  header: " + quote(t("inspector.server-header")));
        pw.println("  included: " + include);
        if (!include) {
            pw.println();
            return;
        }
        pw.println("  details:");
        pw.println("    bukkit: " + quote(Bukkit.getVersion()));
        pw.println("    minecraft: " + quote(Bukkit.getBukkitVersion()));
        pw.println("    java: " + quote(System.getProperty("java.version")));
        pw.println("    os: " + quote(System.getProperty("os.name") + " " + System.getProperty("os.version")));
        pw.println("    cpu-cores: " + quote(String.valueOf(ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors())));
        pw.println();
    }

    private String t(String key){
        return lang.get(key);
    }

    private String quote(String value) {
        if (value == null) {
            return "null";
        }
        String sanitized = value.replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + sanitized + "\"";
    }
}
