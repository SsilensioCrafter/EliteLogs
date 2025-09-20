package com.elitelogs.integration;

import com.elitelogs.compat.ServerCompat;
import com.elitelogs.logging.LogRouter;
import com.elitelogs.players.PlayerTracker;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
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
    private final PlayerTracker playerTracker;
    private final Map<UUID, Double> last = new HashMap<>();
    private Object economy; // net.milkbowl.vault.economy.Economy
    private BalanceAccessor balanceAccessor;
    private boolean balanceAccessorResolved;
    private String accessorDescription;
    private boolean warnedNoProvider;
    private boolean warnedNoAccessor;
    private boolean announcedHook;
    private BukkitTask task;
    private Listener transactionListener;
    private boolean transactionHookAttempted;

    private interface BalanceAccessor {
        double get(Player player) throws Exception;
    }

    public VaultEconomyTracker(Plugin plugin, LogRouter router, PlayerTracker tracker){
        this.plugin = plugin;
        this.router = router;
        this.playerTracker = tracker;
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
        hookTransactionEvents();
    }

    public void stop(){
        if (task != null) task.cancel();
        if (transactionListener != null) {
            HandlerList.unregisterAll(transactionListener);
            transactionListener = null;
        }
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
            for (Player p : ServerCompat.getOnlinePlayers()){
                try {
                    double bal = accessor.get(p);
                    Double prev = last.put(p.getUniqueId(), bal);
                    if (prev == null) {
                        String msg = String.format(Locale.US, "[vault] balance %.2f (initial)", bal);
                        router.economy(p.getUniqueId(), p.getName(), msg);
                        if (playerTracker != null) playerTracker.action(p, msg);
                    } else if (Math.abs(bal - prev) > 0.001){
                        double delta = bal - prev;
                        String msg = String.format(Locale.US, "[vault] balance %.2f -> %.2f (Δ %.2f)", prev, bal, delta);
                        router.economy(p.getUniqueId(), p.getName(), msg);
                        if (playerTracker != null) playerTracker.action(p, msg);
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

    @SuppressWarnings("unchecked")
    private void hookTransactionEvents() {
        if (transactionListener != null || transactionHookAttempted) {
            return;
        }
        transactionHookAttempted = true;
        try {
            Class<?> eventClass = Class.forName("net.milkbowl.vault.event.EconomyTransactionEvent");
            final Method getPlayer = eventClass.getMethod("getPlayer");
            final Method getAmount = eventClass.getMethod("getAmount");
            final Method getBalance = eventClass.getMethod("getBalance");
            final Method getType = eventClass.getMethod("getTransactionType");
            Method wasSuccessful = null;
            try { wasSuccessful = eventClass.getMethod("wasSuccessful"); } catch (NoSuchMethodException ignored) {}
            Method getCurrency = null;
            try { getCurrency = eventClass.getMethod("getCurrency"); } catch (NoSuchMethodException ignored) {}
            Method getWorld = null;
            try { getWorld = eventClass.getMethod("getWorld"); } catch (NoSuchMethodException ignored) {}

            Method finalWasSuccessful = wasSuccessful;
            Method finalGetCurrency = getCurrency;
            Method finalGetWorld = getWorld;
            transactionListener = new Listener() {};
            EventExecutor executor = (listener, event) -> {
                if (!eventClass.isInstance(event)) {
                    return;
                }
                try {
                    if (finalWasSuccessful != null) {
                        Object successObj = finalWasSuccessful.invoke(event);
                        if (successObj instanceof Boolean && !((Boolean) successObj)) {
                            return;
                        }
                    }
                    Object playerObj = getPlayer.invoke(event);
                    UUID uuid = null;
                    String name = null;
                    if (playerObj instanceof Player) {
                        Player player = (Player) playerObj;
                        uuid = player.getUniqueId();
                        name = player.getName();
                    } else if (playerObj instanceof OfflinePlayer) {
                        OfflinePlayer offline = (OfflinePlayer) playerObj;
                        uuid = offline.getUniqueId();
                        name = offline.getName();
                    } else if (playerObj instanceof UUID) {
                        uuid = (UUID) playerObj;
                    } else if (playerObj instanceof String) {
                        name = (String) playerObj;
                    }

                    double amount = ((Number) getAmount.invoke(event)).doubleValue();
                    double balance = ((Number) getBalance.invoke(event)).doubleValue();
                    Object typeObj = getType.invoke(event);
                    String type = typeObj != null ? typeObj.toString() : "UNKNOWN";
                    String currency = finalGetCurrency != null ? String.valueOf(finalGetCurrency.invoke(event)) : "";
                    if (currency == null) currency = "";
                    String world = finalGetWorld != null ? String.valueOf(finalGetWorld.invoke(event)) : "";
                    if (world == null) world = "";

                    StringBuilder detail = new StringBuilder("[vault-event] ");
                    detail.append(type.toLowerCase(Locale.ROOT));
                    if (!currency.isEmpty()) {
                        detail.append(' ').append(currency);
                    }
                    detail.append(String.format(Locale.US, " %.2f", amount));
                    detail.append(String.format(Locale.US, " -> %.2f", balance));
                    if (!world.isEmpty()) {
                        detail.append(" world=").append(world);
                    }
                    String finalLine = detail.toString();

                    if (uuid != null) {
                        router.economy(uuid, name, finalLine);
                        if (playerTracker != null) {
                            playerTracker.action(uuid, name, finalLine);
                        }
                    } else if (name != null && !name.isEmpty()) {
                        router.write("economy", finalLine + " player=" + name);
                    } else {
                        router.write("economy", finalLine);
                    }
                } catch (Throwable t) {
                    router.write("economy", "[vault] transaction event error: " + safeError(t));
                }
            };
            Bukkit.getPluginManager().registerEvent((Class<? extends org.bukkit.event.Event>) eventClass, transactionListener,
                    EventPriority.MONITOR, executor, plugin, true);
            router.write("economy", "[vault] transaction events hooked");
        } catch (ClassNotFoundException ignored) {
            // Vault does not expose transaction events
        } catch (Throwable t) {
            router.write("economy", "[vault] failed to hook transaction events: " + safeError(t));
        }
    }

    private String safeError(Throwable t) {
        return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
    }
}
