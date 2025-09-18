package com.elitelogs.inspector;

import com.elitelogs.EliteLogsPlugin;
import com.elitelogs.utils.DiscordAlerter;
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

    public Inspector(EliteLogsPlugin plugin){
        this.plugin = plugin;
        this.outDir = new File(plugin.getDataFolder(), "reports/inspector");
        this.serverRoot = plugin.getServer().getWorldContainer();
        this.configDir = new File(serverRoot, "config");
        this.serverConfigDir = new File(serverRoot, "serverconfig");
        if (!outDir.exists()) outDir.mkdirs();
    }

    public void runAll(){
        String ts = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        File f = new File(outDir, "inspector-report-" + ts + ".txt");
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8))){
            writePlugins(pw); writeMods(pw); writeConfigs(pw); writeGarbage(pw); writeServerInfo(pw);
            DiscordAlerter.maybeSend("inspector","Inspector report updated: " + f.getName());
        } catch (Exception ignored){}
    }

    private void writePlugins(PrintWriter pw){
        pw.println("=== Plugins ===");
        Plugin[] ps = Bukkit.getPluginManager().getPlugins();
        for (Plugin p : ps){
            String ver = (p.getDescription()!=null && p.getDescription().getVersion()!=null && !p.getDescription().getVersion().isEmpty())
                    ? p.getDescription().getVersion() : "unknown";
            pw.printf(Locale.ROOT, "%s\t%s\t%s%n", p.getName(), p.isEnabled() ? "ENABLED" : "DISABLED", ver);
        }
        pw.println();
    }

    private void writeMods(PrintWriter pw){
        if (!plugin.getConfig().getBoolean("inspector.include-mods", true)) return;
        pw.println("=== Mods ===");
        try {
            Class<?> modList = Class.forName("net.minecraftforge.fml.ModList");
            Object inst = modList.getMethod("get").invoke(null);
            Collection<?> mods = (Collection<?>) modList.getMethod("getMods").invoke(inst);
            for (Object m : mods){
                String id = String.valueOf(m.getClass().getMethod("getModId").invoke(m));
                String ver = String.valueOf(m.getClass().getMethod("getVersion").invoke(m));
                pw.println(id + "\t" + ver);
            }
        } catch (Throwable t){
            pw.println("Forge/Arclight not detected or inaccessible.");
        }
        pw.println();
    }

    private void writeConfigs(PrintWriter pw){
        if (!plugin.getConfig().getBoolean("inspector.include-configs", true)) return;
        List<String> okExt = Arrays.asList(".yml",".yaml",".json",".toml",".cfg");
        pw.println("=== Configs ===");
        for (File dir : new File[]{configDir, serverConfigDir}){
            if (dir == null || !dir.exists()) continue;
            try {
                Files.walk(dir.toPath()).filter(Files::isRegularFile).forEach(p -> {
                    File cf = p.toFile();
                    String rel = dir.toPath().relativize(p).toString().replace("\\","/");
                    String name = cf.getName().toLowerCase(Locale.ROOT);
                    String ext = name.contains(".") ? name.substring(name.lastIndexOf(".")) : "";
                    String status = "OK";
                    if (cf.length() == 0) status = "EMPTY";
                    else if (!okExt.contains(ext)) status = "NOT NEEDED";
                    String base = name.replace(ext,"");
                    boolean pluginExists = Arrays.stream(Bukkit.getPluginManager().getPlugins())
                            .anyMatch(pl -> pl.getName().equalsIgnoreCase(base));
                    if (!pluginExists && okExt.contains(ext)) status = "ORPHANED";
                    try { pw.printf("[%s] %s: %s%n", dir.getName(), rel, status); } catch(Exception ignored){}
                });
            } catch (Exception ignored){}
        }
        pw.println();
    }

    private void writeGarbage(PrintWriter pw){
        if (!plugin.getConfig().getBoolean("inspector.include-garbage", true)) return;
        pw.println("=== Garbage ===");
        scanGarbage(pw, new File(serverRoot, "plugins"), ".jar");
        scanGarbage(pw, new File(serverRoot, "mods"), ".jar");
        scanGarbage(pw, configDir, ".yml",".yaml",".json",".toml",".cfg");
        scanGarbage(pw, serverConfigDir, ".yml",".yaml",".json",".toml",".cfg");
        File[] files = serverRoot.listFiles();
        if (files != null) for (File x : files){
            String n = x.getName().toLowerCase(Locale.ROOT);
            if (n.endsWith(".zip") || n.endsWith(".rar") || n.endsWith(".old") || n.endsWith(".log")){
                pw.println("[root] " + x.getName());
            }
        }
        pw.println();
    }

    private void scanGarbage(PrintWriter pw, File dir, String... goodExts){
        if (dir == null || !dir.exists()) return;
        Set<String> ok = new HashSet<>(Arrays.asList(goodExts));
        File[] files = dir.listFiles(); if (files == null) return;
        for (File f : files){
            if (f.isDirectory()) continue;
            String n = f.getName().toLowerCase(Locale.ROOT);
            String ext = n.contains(".") ? n.substring(n.lastIndexOf(".")) : "";
            if (!ok.contains(ext)) pw.println("[" + dir.getName() + "] " + f.getName());
        }
    }

    private void writeServerInfo(PrintWriter pw){
        if (!plugin.getConfig().getBoolean("inspector.include-server-info", true)) return;
        pw.println("=== Server ===");
        pw.println("Bukkit: " + Bukkit.getVersion());
        pw.println("MC: " + Bukkit.getBukkitVersion());
        pw.println("Java: " + System.getProperty("java.version"));
        pw.println("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
        pw.println("CPU: " + ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors() + " cores");
        pw.println();
    }
}
