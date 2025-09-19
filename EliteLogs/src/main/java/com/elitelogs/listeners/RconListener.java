package com.elitelogs.listeners;

import com.elitelogs.utils.LogRouter;
import org.bukkit.command.CommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.RemoteServerCommandEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;

public class RconListener implements Listener {
    private final LogRouter router;

    public RconListener(LogRouter router) {
        this.router = router;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRemoteCommand(RemoteServerCommandEvent event) {
        CommandSender sender = event.getSender();
        String address = resolveAddress(sender);
        String command = event.getCommand();
        String name = sender != null ? sender.getName() : "unknown";
        String message = String.format("[rcon] sender=%s addr=%s cmd=%s", name, address, command);
        router.rcon(message);
        router.console(message);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsoleCommand(ServerCommandEvent event) {
        if (event.getSender() instanceof RemoteConsoleCommandSender) {
            return; // already handled as RCON
        }
        CommandSender sender = event.getSender();
        String name = sender != null ? sender.getName() : "console";
        String command = event.getCommand();
        String message = String.format("[console] sender=%s cmd=%s", name, command);
        router.console(message);
    }

    private String resolveAddress(CommandSender sender) {
        if (sender instanceof RemoteConsoleCommandSender) {
            try {
                InetSocketAddress address = ((RemoteConsoleCommandSender) sender).getAddress();
                if (address != null) {
                    return address.getAddress().getHostAddress() + ":" + address.getPort();
                }
            } catch (Throwable ignored) {
            }
        }
        if (sender != null) {
            try {
                Method m = sender.getClass().getMethod("getAddress");
                Object value = m.invoke(sender);
                if (value instanceof InetSocketAddress) {
                    InetSocketAddress inet = (InetSocketAddress) value;
                    if (inet.getAddress() != null) {
                        return inet.getAddress().getHostAddress() + ":" + inet.getPort();
                    }
                } else if (value != null) {
                    return String.valueOf(value);
                }
            } catch (Throwable ignored) {
            }
            return sender.getName();
        }
        return "unknown";
    }
}
