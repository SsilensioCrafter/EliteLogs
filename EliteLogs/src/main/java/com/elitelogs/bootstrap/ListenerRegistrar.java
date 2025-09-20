package com.elitelogs.bootstrap;

import com.elitelogs.EliteLogsPlugin;
import com.elitelogs.listeners.ChatListener;
import com.elitelogs.listeners.CommandListener;
import com.elitelogs.listeners.CombatListener;
import com.elitelogs.listeners.DeathListener;
import com.elitelogs.listeners.EconomyListener;
import com.elitelogs.listeners.InventoryListener;
import com.elitelogs.listeners.JoinQuitListener;
import com.elitelogs.listeners.RconListener;
import com.elitelogs.logging.LogRouter;
import com.elitelogs.players.PlayerTracker;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;

public class ListenerRegistrar {
    private final EliteLogsPlugin plugin;
    private final LogRouter router;
    private final PlayerTracker tracker;

    public ListenerRegistrar(EliteLogsPlugin plugin, LogRouter router, PlayerTracker tracker) {
        this.plugin = plugin;
        this.router = router;
        this.tracker = tracker;
    }

    public void registerAll() {
        registerIfEnabled("logs.types.chat", new ChatListener(router, tracker));
        registerIfEnabled("logs.types.commands", new CommandListener(plugin, router, tracker));
        registerIfEnabled("logs.types.combat", new DeathListener(router));
        registerIfEnabled("logs.types.players", new JoinQuitListener(router, tracker));
        registerIfEnabled("logs.types.combat", new CombatListener(router));
        if (plugin.getConfig().getBoolean("logs.types.inventory", true)) {
            InventoryListener inventoryListener = new InventoryListener(router, tracker);
            Bukkit.getPluginManager().registerEvents(inventoryListener, plugin);
            inventoryListener.registerCompatibilityListeners(plugin);
        }
        registerIfEnabled("logs.types.economy", new EconomyListener());
        registerIfEnabled("logs.types.rcon", new RconListener(router));
    }

    private void registerIfEnabled(String path, Listener listener) {
        if (plugin.getConfig().getBoolean(path, true)) {
            Bukkit.getPluginManager().registerEvents(listener, plugin);
        }
    }
}
