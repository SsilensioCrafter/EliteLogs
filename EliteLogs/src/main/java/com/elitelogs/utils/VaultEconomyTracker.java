package com.elitelogs.utils;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class VaultEconomyTracker {
    private final Plugin plugin;
    private final LogRouter router;
    private final Map<UUID, Double> last = new HashMap<>();
    private Object economy; // net.milkbowl.vault.economy.Economy
    private BukkitTask task;

    public VaultEconomyTracker(Plugin plugin, LogRouter router){
        this.plugin = plugin; this.router = router;
        hook();
    }

    private void hook(){
        try {
            Class<?> ecoClass = Class.forName("net.milkbowl.vault.economy.Economy");
            Object rsp = Bukkit.getServicesManager().getRegistration(ecoClass);
            if (rsp == null) return;
            Method getProv = rsp.getClass().getMethod("getProvider");
            economy = getProv.invoke(rsp);
        } catch (Throwable ignored){}
    }

    public void start(){
        if (economy == null) return;
        int poll = plugin.getConfig().getInt("economy.poll-seconds", 30);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L*poll, 20L*poll);
        router.write("economy", "[vault] tracker started, interval=" + poll + "s");
    }
    public void stop(){ if (task != null) task.cancel(); }

    private void tick(){
        if (economy == null) return;
        try {
            Method balMethod = economy.getClass().getMethod("getBalance", OfflinePlayer.class);
            for (Player p : Bukkit.getOnlinePlayers()){
                double bal = (double) balMethod.invoke(economy, (OfflinePlayer) p);
                Double prev = last.put(p.getUniqueId(), bal);
                if (prev != null && Math.abs(bal - prev) > 0.001){
                    String msg = String.format("[eco] %s: %.2f -> %.2f (Δ %.2f)", p.getName(), prev, bal, (bal-prev));
                    router.write("economy", msg);
                    if (PlayerTrackerHolder.get()!=null) PlayerTrackerHolder.get().action(p, msg);
                }
            }
        } catch (Throwable ignored){}
    }
}
