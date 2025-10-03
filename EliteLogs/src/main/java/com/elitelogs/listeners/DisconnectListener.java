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

import java.util.logging.Level;

public class DisconnectListener implements Listener {
    private final Plugin plugin;
    private final LogRouter router;
    private final DisconnectPacketInterceptor packetInterceptor;

    public DisconnectListener(Plugin plugin, LogRouter router) {
        this.plugin = plugin;
        this.router = router;
        HookAttempt attempt = attemptProtocolLibHook(plugin, router);
        this.packetInterceptor = attempt.interceptor();
        attempt.log(plugin);
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
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

    private HookAttempt attemptProtocolLibHook(Plugin plugin, LogRouter router) {
        if (plugin == null || router == null) {
            return HookAttempt.silent();
        }
        if (!plugin.getConfig().getBoolean("logs.disconnects.capture-screen", true)) {
            return HookAttempt.info(DisconnectPacketInterceptor.NOOP,
                    "[EliteLogs] Disconnect screen capture disabled via config (logs.disconnects.capture-screen = false).");
        }
        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) {
            return HookAttempt.info(DisconnectPacketInterceptor.NOOP,
                    "[EliteLogs] ProtocolLib plugin not detected; disconnect screen capture limited to Bukkit events.");
        }
        try {
            Class.forName("com.comphenix.protocol.ProtocolLibrary");
        } catch (Throwable ex) {
            return HookAttempt.failure(ex);
        }
        try {
            DisconnectPacketInterceptor interceptor = new ProtocolLibDisconnectInterceptor(plugin, router);
            return HookAttempt.info(interceptor,
                    "[EliteLogs] ProtocolLib detected; disconnect screen capture enabled.");
        } catch (LinkageError | RuntimeException ex) {
            return HookAttempt.failure(ex);
        }
    }

    private record HookAttempt(DisconnectPacketInterceptor interceptor, Level level, String message, Throwable error) {
        private static HookAttempt silent() {
            return new HookAttempt(DisconnectPacketInterceptor.NOOP, Level.FINE, null, null);
        }

        private static HookAttempt info(DisconnectPacketInterceptor interceptor, String message) {
            return new HookAttempt(interceptor, Level.INFO, message, null);
        }

        private static HookAttempt warn(String message) {
            return new HookAttempt(DisconnectPacketInterceptor.NOOP, Level.WARNING, message, null);
        }

        private static HookAttempt failure(Throwable error) {
            return new HookAttempt(DisconnectPacketInterceptor.NOOP, Level.WARNING,
                    "[EliteLogs] Failed to enable ProtocolLib disconnect screen capture; falling back to vanilla events.", error);
        }

        private void log(Plugin plugin) {
            if (plugin == null || message == null) {
                return;
            }
            if (error != null) {
                plugin.getLogger().log(level, message, error);
            } else {
                plugin.getLogger().log(level, message);
            }
        }
    }

}
