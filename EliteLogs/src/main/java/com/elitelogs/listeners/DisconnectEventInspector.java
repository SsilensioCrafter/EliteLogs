package com.elitelogs.listeners;

import com.elitelogs.compat.ServerCompat;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class DisconnectEventInspector {
    private static final Map<Class<?>, Map<String, Optional<Method>>> CACHE = new ConcurrentHashMap<>();

    private DisconnectEventInspector() {
    }

    static String describeKickMessage(PlayerKickEvent event) {
        Object value = firstNonNull(event,
                "reason",
                "getReason",
                "leaveMessage",
                "getLeaveMessage");
        return describeComponent(value);
    }

    static String describeKickCause(PlayerKickEvent event) {
        Object value = invoke(event, "getCause");
        if (value == null) {
            return "";
        }
        return value.toString();
    }

    static String describeKicker(PlayerKickEvent event) {
        Object kicker = firstNonNull(event, "getKicker", "kicker");
        if (kicker instanceof CommandSender) {
            return describeCommandSender((CommandSender) kicker);
        }
        return kicker != null ? kicker.toString() : "";
    }

    static String describeQuitReason(PlayerQuitEvent event) {
        Object value = firstNonNull(event, "getQuitReason", "quitReason");
        if (value == null) {
            return "QUIT";
        }
        return value.toString();
    }

    static String describeQuitMessage(PlayerQuitEvent event) {
        Object value = firstNonNull(event, "quitMessage", "getQuitMessage");
        return describeComponent(value);
    }

    static String describePreLoginMessage(AsyncPlayerPreLoginEvent event) {
        Object value = firstNonNull(event, "kickMessage", "getKickMessage");
        return describeComponent(value);
    }

    static String describeLoginMessage(PlayerLoginEvent event) {
        Object value = firstNonNull(event, "kickMessage", "getKickMessage");
        return describeComponent(value);
    }

    static String describeAddress(InetAddress address) {
        if (address == null) {
            return "unknown";
        }
        return address.getHostAddress();
    }

    static String describeComponent(Object component) {
        if (component == null) {
            return "";
        }
        if (component instanceof String) {
            return (String) component;
        }
        return ServerCompat.describeComponent(component);
    }

    static String describeCommandSender(CommandSender sender) {
        if (sender == null) {
            return "";
        }
        if (sender instanceof Player) {
            return "player:" + ((Player) sender).getName();
        }
        String name = sender.getName();
        return name != null ? name : sender.toString();
    }

    static UUID resolvePlayerUuid(Player player) {
        return player != null ? player.getUniqueId() : null;
    }

    static String resolvePlayerName(Player player) {
        return player != null ? player.getName() : null;
    }

    private static Object firstNonNull(Object target, String... methodNames) {
        for (String methodName : methodNames) {
            Object value = invoke(target, methodName);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Object invoke(Object target, String methodName) {
        if (target == null || methodName == null || methodName.isEmpty()) {
            return null;
        }
        Class<?> type = target.getClass();
        Map<String, Optional<Method>> byName = CACHE.computeIfAbsent(type, key -> new ConcurrentHashMap<>());
        Optional<Method> optional = byName.computeIfAbsent(methodName, key -> resolve(type, key));
        if (!optional.isPresent()) {
            return null;
        }
        Method method = optional.get();
        try {
            return method.invoke(target);
        } catch (Throwable throwable) {
            byName.put(methodName, Optional.empty());
            return null;
        }
    }

    private static Optional<Method> resolve(Class<?> type, String methodName) {
        try {
            Method method = type.getMethod(methodName);
            method.setAccessible(true);
            return Optional.of(method);
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }
}
