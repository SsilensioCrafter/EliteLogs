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
    private static final List<String> BUNDLED = Arrays.asList("en", "ru");

    public Lang(Plugin plugin){ this.plugin = plugin; }

    public void load(){
        String raw = plugin.getConfig().getString("language","en");
        String code = raw != null ? raw.trim().toLowerCase(Locale.ROOT) : "en";
        if (code.isEmpty()) code = "en";
        this.activeCode = code;
        File langDir = new File(plugin.getDataFolder(), "lang");
        if (!langDir.exists()) langDir.mkdirs();
        migrateLegacy(langDir);
        ensureBundledLanguages(langDir);

        File file = new File(langDir, code + ".yml");
        if (!file.exists()) {
            this.activeCode = "en";
            file = new File(langDir, "en.yml");
        }
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);

        YamlConfiguration defaults = loadBundledYaml(this.activeCode);
        YamlConfiguration englishDefaults = loadBundledYaml("en");

        if (defaults == null && englishDefaults != null) {
            defaults = englishDefaults;
        }

        if (defaults != null) {
            if (!"en".equals(this.activeCode) && englishDefaults != null && defaults != englishDefaults) {
                defaults.addDefaults(englishDefaults.getValues(true));
            }
            loaded.setDefaults(defaults);
            loaded.options().copyDefaults(true);
        } else if (englishDefaults != null) {
            loaded.setDefaults(englishDefaults);
            loaded.options().copyDefaults(true);
        }

        try { loaded.save(file); } catch (IOException ignored) {}

        this.lang = loaded;
    }

    private void ensureBundledLanguages(File langDir) {
        for (String bundled : BUNDLED) {
            File out = new File(langDir, bundled + ".yml");
            if (!out.exists()) {
                try {
                    copyRes(bundled, out);
                } catch (IOException ignored) {}
            }
        }
    }

    private void copyRes(String code, File out) throws IOException {
        try (InputStream in = openBundled(code)) {
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

    private InputStream openBundled(String code) {
        InputStream in = plugin.getResource("lang/" + code + ".yml");
        if (in == null) {
            in = plugin.getResource("lang_" + code + ".yml");
        }
        return in;
    }

    private YamlConfiguration loadBundledYaml(String code) {
        if (code == null) {
            return null;
        }
        try (InputStream in = openBundled(code)) {
            if (in == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            return null;
        }
    }

    private void migrateLegacy(File langDir) {
        for (String code : BUNDLED) {
            File legacy = new File(plugin.getDataFolder(), "lang_" + code + ".yml");
            if (legacy.exists()) {
                File target = new File(langDir, code + ".yml");
                if (!target.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    legacy.renameTo(target);
                }
            }
        }
    }

    public String get(String key){
        String v = lang.getString(key);
        if (v == null) {
            try (InputStream in = openBundled("en")) {
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
        try (InputStream in = openBundled("en")) {
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
