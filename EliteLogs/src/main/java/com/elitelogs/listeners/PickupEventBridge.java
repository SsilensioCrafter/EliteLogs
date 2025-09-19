package com.elitelogs.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.logging.Level;

final class PickupEventBridge {
    private PickupEventBridge() {
    }

    static void register(Plugin plugin, InventoryListener listener) {
        if (plugin == null || listener == null) {
            return;
        }
        if (tryEntityPickup(plugin, listener)) {
            return;
        }
        if (tryPlayerPickup(plugin, listener)) {
            return;
        }
        plugin.getLogger().warning("[EliteLogs] No compatible item pickup event available; inventory pickups will not be logged.");
    }

    private static boolean tryEntityPickup(Plugin plugin, InventoryListener listener) {
        try {
            Class<?> eventClass = Class.forName("org.bukkit.event.entity.EntityPickupItemEvent");
            Method getEntity = eventClass.getMethod("getEntity");
            Method getItem = eventClass.getMethod("getItem");
            EventExecutor executor = (l, event) -> {
                if (!eventClass.isInstance(event)) {
                    return;
                }
                try {
                    Object entity = getEntity.invoke(event);
                    if (!(entity instanceof Player)) {
                        return;
                    }
                    Player player = (Player) entity;
                    Object itemObj = getItem.invoke(event);
                    Item item = itemObj instanceof Item ? (Item) itemObj : null;
                    ItemStack stack = item != null ? item.getItemStack() : null;
                    Location location = item != null ? item.getLocation() : player.getLocation();
                    listener.handlePickupEvent(player, stack, location);
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.WARNING, "[EliteLogs] Failed to handle EntityPickupItemEvent", t);
                }
            };
            @SuppressWarnings("unchecked")
            Class<? extends Event> bukkitEventClass = (Class<? extends Event>) eventClass;
            Bukkit.getPluginManager().registerEvent(bukkitEventClass, listener,
                    EventPriority.MONITOR, executor, plugin, true);
            plugin.getLogger().info("[EliteLogs] Hooked EntityPickupItemEvent");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "[EliteLogs] Failed to hook EntityPickupItemEvent: " + safeMessage(t), t);
            return false;
        }
    }

    private static boolean tryPlayerPickup(Plugin plugin, InventoryListener listener) {
        try {
            Class<?> eventClass = Class.forName("org.bukkit.event.player.PlayerPickupItemEvent");
            Method getPlayer = eventClass.getMethod("getPlayer");
            Method getItem = eventClass.getMethod("getItem");
            EventExecutor executor = (l, event) -> {
                if (!eventClass.isInstance(event)) {
                    return;
                }
                try {
                    Object playerObj = getPlayer.invoke(event);
                    if (!(playerObj instanceof Player)) {
                        return;
                    }
                    Player player = (Player) playerObj;
                    Object itemObj = getItem.invoke(event);
                    Item item = itemObj instanceof Item ? (Item) itemObj : null;
                    ItemStack stack = item != null ? item.getItemStack() : null;
                    Location location = item != null ? item.getLocation() : player.getLocation();
                    listener.handlePickupEvent(player, stack, location);
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.WARNING, "[EliteLogs] Failed to handle PlayerPickupItemEvent", t);
                }
            };
            @SuppressWarnings("unchecked")
            Class<? extends Event> bukkitEventClass = (Class<? extends Event>) eventClass;
            Bukkit.getPluginManager().registerEvent(bukkitEventClass, listener,
                    EventPriority.MONITOR, executor, plugin, true);
            plugin.getLogger().info("[EliteLogs] Hooked legacy PlayerPickupItemEvent");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "[EliteLogs] Failed to hook PlayerPickupItemEvent: " + safeMessage(t), t);
            return false;
        }
    }

    private static String safeMessage(Throwable t) {
        return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
    }
}
