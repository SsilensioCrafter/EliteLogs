package com.elitelogs.utils;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class VaultEconomyTracker {
    private final Plugin plugin;
    private final LogRouter router;
    private final Map<UUID, Double> last = new HashMap<>();
    private Object economy; // net.milkbowl.vault.economy.Economy
    private BalanceAccessor balanceAccessor;
    private boolean balanceAccessorResolved;
    private String accessorDescription;
    private boolean warnedNoProvider;
    private boolean warnedNoAccessor;
    private boolean announcedHook;
    private BukkitTask task;

    private interface BalanceAccessor {
        double get(Player player) throws Exception;
    }

    public VaultEconomyTracker(Plugin plugin, LogRouter router){
        this.plugin = plugin;
        this.router = router;
    }

    private boolean ensureHooked(){
        if (economy != null) {
            return true;
        }
        try {
            Class<?> ecoClass = Class.forName("net.milkbowl.vault.economy.Economy");
            Object rsp = Bukkit.getServicesManager().getRegistration(ecoClass);
            if (rsp == null) {
                return false;
            }
            Method getProv = rsp.getClass().getMethod("getProvider");
            economy = getProv.invoke(rsp);
            balanceAccessor = null;
            balanceAccessorResolved = false;
            accessorDescription = null;
            warnedNoProvider = false;
            warnedNoAccessor = false;
            last.clear();
            if (!announcedHook && economy != null) {
                router.write("economy", "[vault] hooked provider " + economy.getClass().getSimpleName());
                announcedHook = true;
            }
            return economy != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public void start(){
        stop();
        int poll = plugin.getConfig().getInt("economy.poll-seconds", 30);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L*poll);
        Bukkit.getScheduler().runTask(plugin, this::tick);
        router.write("economy", "[vault] tracker scheduled, interval=" + poll + "s");
    }

    public void stop(){
        if (task != null) task.cancel();
    }

    private void tick(){
        if (!ensureHooked()) {
            if (!warnedNoProvider) {
                router.write("economy", "[vault] provider not found (waiting for Vault)");
                warnedNoProvider = true;
            }
            return;
        }

        try {
            BalanceAccessor accessor = resolveBalanceAccessor();
            if (accessor == null) {
                if (!warnedNoAccessor) {
                    router.write("economy", "[vault] provider " + economy.getClass().getSimpleName() + " has no supported getBalance method");
                    warnedNoAccessor = true;
                }
                return;
            }
            warnedNoAccessor = false;
            boolean errorLogged = false;
            for (Player p : Bukkit.getOnlinePlayers()){
                try {
                    double bal = accessor.get(p);
                    Double prev = last.put(p.getUniqueId(), bal);
                    if (prev == null) {
                        String msg = String.format(Locale.US, "[vault] balance %.2f (initial)", bal);
                        router.economy(p.getUniqueId(), p.getName(), msg);
                        if (PlayerTrackerHolder.get()!=null) PlayerTrackerHolder.get().action(p, msg);
                    } else if (Math.abs(bal - prev) > 0.001){
                        double delta = bal - prev;
                        String msg = String.format(Locale.US, "[vault] balance %.2f -> %.2f (Δ %.2f)", prev, bal, delta);
                        router.economy(p.getUniqueId(), p.getName(), msg);
                        if (PlayerTrackerHolder.get()!=null) PlayerTrackerHolder.get().action(p, msg);
                    }
                } catch (Throwable t) {
                    if (!errorLogged) {
                        String reason = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                        router.write("economy", "[vault] failed to query balance via " + accessorDescription + ": " + reason);
                        errorLogged = true;
                    }
                }
            }
        } catch (Throwable t){
            String reason = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            router.write("economy", "[vault] unexpected error: " + reason);
        }
    }

    private BalanceAccessor resolveBalanceAccessor() {
        if (balanceAccessor != null) {
            return balanceAccessor;
        }
        if (balanceAccessorResolved || economy == null) {
            return null;
        }
        balanceAccessorResolved = true;
        Class<?> type = economy.getClass();
        try {
            Method m = type.getMethod("getBalance", OfflinePlayer.class);
            balanceAccessor = player -> ((Number) m.invoke(economy, (OfflinePlayer) player)).doubleValue();
            accessorDescription = signature(m);
            return balanceAccessor;
        } catch (NoSuchMethodException ignored) {}

        try {
            Method m = type.getMethod("getBalance", OfflinePlayer.class, String.class);
            balanceAccessor = player -> ((Number) m.invoke(economy, (OfflinePlayer) player, safeWorld(player))).doubleValue();
            accessorDescription = signature(m);
            return balanceAccessor;
        } catch (NoSuchMethodException ignored) {}

        try {
            Method m = type.getMethod("getBalance", Player.class);
            balanceAccessor = player -> ((Number) m.invoke(economy, player)).doubleValue();
            accessorDescription = signature(m);
            return balanceAccessor;
        } catch (NoSuchMethodException ignored) {}

        try {
            Method m = type.getMethod("getBalance", String.class);
            balanceAccessor = player -> ((Number) m.invoke(economy, player.getName())).doubleValue();
            accessorDescription = signature(m);
            return balanceAccessor;
        } catch (NoSuchMethodException ignored) {}

        try {
            Method m = type.getMethod("getBalance", String.class, String.class);
            balanceAccessor = player -> ((Number) m.invoke(economy, player.getName(), safeWorld(player))).doubleValue();
            accessorDescription = signature(m);
            return balanceAccessor;
        } catch (NoSuchMethodException ignored) {}

        try {
            Method m = type.getMethod("getBalance", UUID.class);
            balanceAccessor = player -> ((Number) m.invoke(economy, player.getUniqueId())).doubleValue();
            accessorDescription = signature(m);
            return balanceAccessor;
        } catch (NoSuchMethodException ignored) {}

        try {
            Method m = type.getMethod("getBalance", UUID.class, String.class);
            balanceAccessor = player -> ((Number) m.invoke(economy, player.getUniqueId(), safeWorld(player))).doubleValue();
            accessorDescription = signature(m);
            return balanceAccessor;
        } catch (NoSuchMethodException ignored) {}

        router.write("economy", "[vault] could not resolve getBalance method on " + type.getName());
        return null;
    }

    private String signature(Method method){
        return method.getDeclaringClass().getSimpleName() + "::" + method.getName();
    }

    private String safeWorld(Player player){
        try {
            return player.getWorld() != null ? player.getWorld().getName() : "";
        } catch (Throwable ignored) {
            return "";
        }
    }
}
