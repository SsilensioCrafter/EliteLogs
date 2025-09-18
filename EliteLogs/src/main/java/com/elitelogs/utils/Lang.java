package com.elitelogs.utils;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class Lang {
    private final Plugin plugin;
    private YamlConfiguration lang;

    public Lang(Plugin plugin){ this.plugin = plugin; }

    public void load(){
        String code = plugin.getConfig().getString("language","en");
        File langDir = new File(plugin.getDataFolder(), "lang");
        if (!langDir.exists()) langDir.mkdirs();
        File file = new File(langDir, code + ".yml");
        try {
            if (!file.exists()) copyRes("lang_"+code+".yml", file);
            File en = new File(langDir, "en.yml");
            if (!en.exists()) copyRes("lang_en.yml", en);
            File ru = new File(langDir, "ru.yml");
            if (!ru.exists()) copyRes("lang_ru.yml", ru);
        } catch (Exception ignored){}
        if (!file.exists()) file = new File(langDir, "en.yml");
        this.lang = YamlConfiguration.loadConfiguration(file);
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
}
