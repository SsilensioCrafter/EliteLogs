package com.elitelogs.utils;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class Lang {
    private final Plugin plugin;
    private YamlConfiguration lang;
    private String activeCode = "en";

    public Lang(Plugin plugin){ this.plugin = plugin; }

    public void load(){
        String raw = plugin.getConfig().getString("language","en");
        String code = raw != null ? raw.trim().toLowerCase(Locale.ROOT) : "en";
        if (code.isEmpty()) code = "en";
        this.activeCode = code;
        File langDir = new File(plugin.getDataFolder(), "lang");
        if (!langDir.exists()) langDir.mkdirs();
        ensureBundledLanguages(langDir);

        File file = new File(langDir, code + ".yml");
        if (!file.exists()) {
            this.activeCode = "en";
            file = new File(langDir, "en.yml");
        }
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource("lang_en.yml")) {
            if (in != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
                loaded.setDefaults(defaults);
                loaded.options().copyDefaults(true);
            }
        } catch (Exception ignored){}
        this.lang = loaded;
    }

    private void ensureBundledLanguages(File langDir) {
        for (String bundled : Arrays.asList("en", "ru")) {
            File out = new File(langDir, bundled + ".yml");
            if (!out.exists()) {
                try {
                    copyRes("lang_" + bundled + ".yml", out);
                } catch (IOException ignored) {}
            }
        }
    }

    private void copyRes(String res, File out) throws IOException {
        try (InputStream in = plugin.getResource(res)) {
            if (in == null) return;
            out.getParentFile().mkdirs();
            try (OutputStream os = new FileOutputStream(out)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
            }
        }
    }

    public String get(String key){
        String v = lang.getString(key);
        if (v == null) {
            try (InputStream in = plugin.getResource("lang_en.yml")) {
                if (in != null) {
                    YamlConfiguration en = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
                    v = en.getString(key, key);
                } else v = key;
            } catch (Exception e){ v = key; }
        }
        return v;
    }
    public List<String> getList(String key){
        if (lang.contains(key)) return lang.getStringList(key);
        try (InputStream in = plugin.getResource("lang_en.yml")) {
            if (in != null) {
                YamlConfiguration en = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
                return en.getStringList(key);
            }
        } catch (Exception ignored){}
        return java.util.Collections.emptyList();
    }
    public static String colorize(String s){ return s.replace("&","§"); }
    public String formatModule(String name, boolean ok){
        String key = ok ? "module-ok" : "module-fail";
        return get(key).replace("{name}", name);
    }

    public String getActiveCode() {
        return activeCode;
    }
}
