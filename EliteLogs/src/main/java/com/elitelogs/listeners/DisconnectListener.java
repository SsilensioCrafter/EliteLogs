package com.elitelogs.listeners;

import com.elitelogs.logging.DisconnectLogEntry;
import com.elitelogs.logging.LogRouter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

public class DisconnectListener implements Listener {
    private final Plugin plugin;
    private final LogRouter router;
    private final DisconnectPacketInterceptor packetInterceptor;

    public DisconnectListener(Plugin plugin, LogRouter router) {
        this.plugin = plugin;
        this.router = router;
        this.packetInterceptor = createPacketInterceptor(plugin, router);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event == null || event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        DisconnectLogEntry entry = DisconnectLogEntry.phase("prelogin-deny")
                .player(event.getUniqueId(), event.getName())
                .attribute("result", event.getLoginResult().name())
                .attribute("ip", DisconnectEventInspector.describeAddress(event.getAddress()))
                .attribute("message", DisconnectEventInspector.describePreLoginMessage(event))
                .build();
        router.disconnect(entry);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLogin(PlayerLoginEvent event) {
        if (event == null || event.getResult() == PlayerLoginEvent.Result.ALLOWED) {
            return;
        }
        Player player = event.getPlayer();
        DisconnectLogEntry entry = DisconnectLogEntry.phase("login-deny")
                .player(DisconnectEventInspector.resolvePlayerUuid(player), DisconnectEventInspector.resolvePlayerName(player))
                .attribute("result", event.getResult().name())
                .attribute("ip", DisconnectEventInspector.describeAddress(event.getAddress()))
                .attribute("message", DisconnectEventInspector.describeLoginMessage(event))
                .build();
        router.disconnect(entry);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent event) {
        if (event == null) {
            return;
        }
        Player player = event.getPlayer();
        DisconnectLogEntry entry = DisconnectLogEntry.phase("kick")
                .player(DisconnectEventInspector.resolvePlayerUuid(player), DisconnectEventInspector.resolvePlayerName(player))
                .attribute("cause", DisconnectEventInspector.describeKickCause(event))
                .attribute("source", DisconnectEventInspector.describeKicker(event))
                .attribute("message", DisconnectEventInspector.describeKickMessage(event))
                .build();
        router.disconnect(entry);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (event == null) {
            return;
        }
        Player player = event.getPlayer();
        DisconnectLogEntry entry = DisconnectLogEntry.phase("quit")
                .player(DisconnectEventInspector.resolvePlayerUuid(player), DisconnectEventInspector.resolvePlayerName(player))
                .attribute("reason", DisconnectEventInspector.describeQuitReason(event))
                .attribute("message", DisconnectEventInspector.describeQuitMessage(event))
                .build();
        router.disconnect(entry);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onResourcePack(PlayerResourcePackStatusEvent event) {
        if (event == null) {
            return;
        }
        Player player = event.getPlayer();
        DisconnectLogEntry entry = DisconnectLogEntry.phase("resource-pack")
                .player(DisconnectEventInspector.resolvePlayerUuid(player), DisconnectEventInspector.resolvePlayerName(player))
                .attribute("status", event.getStatus() != null ? event.getStatus().name() : "UNKNOWN")
                .build();
        router.disconnect(entry);
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event != null && event.getPlugin() == plugin) {
            packetInterceptor.shutdown();
        }
    }

    private DisconnectPacketInterceptor createPacketInterceptor(Plugin plugin, LogRouter router) {
        if (plugin == null || router == null) {
            return DisconnectPacketInterceptor.NOOP;
        }
        if (!plugin.getConfig().getBoolean("logs.disconnects.capture-screen", true)) {
            return DisconnectPacketInterceptor.NOOP;
        }
        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) {
            return DisconnectPacketInterceptor.NOOP;
        }
        try {
            Class.forName("com.comphenix.protocol.ProtocolLibrary");
            Class<?> hookClass = Class.forName("com.elitelogs.listeners.ProtocolLibDisconnectInterceptor");
            Object instance = hookClass.getConstructor(Plugin.class, LogRouter.class).newInstance(plugin, router);
            if (instance instanceof DisconnectPacketInterceptor) {
                return (DisconnectPacketInterceptor) instance;
            }
        } catch (Throwable ignored) {
        }
        return DisconnectPacketInterceptor.NOOP;
    }

}
