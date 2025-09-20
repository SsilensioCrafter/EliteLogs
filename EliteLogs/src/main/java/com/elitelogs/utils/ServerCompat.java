package com.elitelogs.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * Small collection of runtime compatibility helpers so the plugin can operate
 * on legacy (1.8+) and modern (1.21+) servers without sacrificing features.
 */
public final class ServerCompat {
    private static Method inventoryMainHand;
    private static Method inventoryItemInHand;
    private static Method playerItemInHand;
    private static boolean handMethodsResolved;

    private static Method legacyGetOnlinePlayers;
    private static boolean legacyGetOnlinePlayersResolved;

    private static Method inventoryViewTitle;
    private static boolean inventoryViewTitleResolved;
    private static Method inventoryTitle;
    private static boolean inventoryTitleResolved;

    private static Object plainTextSerializer;
    private static Method plainTextSerialize;
    private static boolean plainTextSerializerResolved;

    private ServerCompat() {
    }

    /**
     * Attempts to read the item currently held in the player's main hand while
     * gracefully falling back to the legacy "hand" accessors used before 1.9.
     */
    public static ItemStack getHeldItem(Player player) {
        if (player == null) {
            return null;
        }
        PlayerInventory inventory = player.getInventory();
        resolveHandMethods(inventory != null ? inventory.getClass() : null, player.getClass());
        ItemStack stack = invokeItemStack(inventory, inventoryMainHand);
        if (stack != null) {
            return stack;
        }
        stack = invokeItemStack(inventory, inventoryItemInHand);
        if (stack != null) {
            return stack;
        }
        return invokeItemStack(player, playerItemInHand);
    }

    private static ItemStack invokeItemStack(Object target, Method method) {
        if (target == null || method == null) {
            return null;
        }
        try {
            Object result = method.invoke(target);
            if (result instanceof ItemStack) {
                return (ItemStack) result;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static synchronized void resolveHandMethods(Class<?> inventoryClass, Class<?> playerClass) {
        if (handMethodsResolved) {
            return;
        }
        if (inventoryClass != null) {
            try {
                inventoryMainHand = inventoryClass.getMethod("getItemInMainHand");
            } catch (NoSuchMethodException ignored) {
            }
            try {
                inventoryItemInHand = inventoryClass.getMethod("getItemInHand");
            } catch (NoSuchMethodException ignored) {
            }
        }
        if (playerClass != null) {
            try {
                playerItemInHand = playerClass.getMethod("getItemInHand");
            } catch (NoSuchMethodException ignored) {
            }
        }
        handMethodsResolved = true;
    }

    /**
     * Attempts to retrieve a title for the provided inventory view or
     * backing inventory without assuming the modern API is available.
     */
    public static String getInventoryTitle(InventoryView view, Inventory inventory) {
        String title = invokeString(view, resolveInventoryViewTitle(view != null ? view.getClass() : null));
        if (title != null && !title.isEmpty()) {
            return title;
        }
        title = invokeString(inventory, resolveInventoryTitle(inventory != null ? inventory.getClass() : null));
        if (title != null && !title.isEmpty()) {
            return title;
        }
        if (inventory != null) {
            try {
                return inventory.getType().name();
            } catch (Throwable ignored) {
            }
        }
        return "unknown";
    }

    private static Method resolveInventoryViewTitle(Class<?> viewClass) {
        if (!inventoryViewTitleResolved && viewClass != null) {
            try {
                inventoryViewTitle = viewClass.getMethod("getTitle");
            } catch (NoSuchMethodException ignored) {
            } finally {
                inventoryViewTitleResolved = true;
            }
        }
        return inventoryViewTitle;
    }

    private static Method resolveInventoryTitle(Class<?> inventoryClass) {
        if (!inventoryTitleResolved && inventoryClass != null) {
            try {
                inventoryTitle = inventoryClass.getMethod("getTitle");
            } catch (NoSuchMethodException ignored) {
            } finally {
                inventoryTitleResolved = true;
            }
        }
        return inventoryTitle;
    }

    private static String invokeString(Object target, Method method) {
        if (target == null || method == null) {
            return null;
        }
        try {
            Object result = method.invoke(target);
            if (result instanceof String) {
                return (String) result;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * Returns a best-effort description of the inventory holder without
     * assuming the method exists on legacy servers.
     */
    public static String describeInventoryHolder(Inventory inventory) {
        if (inventory == null) {
            return "none";
        }
        try {
            Object holder = inventory.getHolder();
            if (holder != null) {
                return holder.getClass().getSimpleName();
            }
        } catch (Throwable ignored) {
        }
        return "none";
    }

    /**
     * Returns a snapshot of online players that works with both modern
     * Collection-based and legacy array-based Bukkit APIs.
     */
    @SuppressWarnings("unchecked")
    public static Iterable<Player> getOnlinePlayers() {
        try {
            Collection<? extends Player> collection = Bukkit.getOnlinePlayers();
            if (collection != null) {
                return (Iterable<Player>) collection;
            }
        } catch (NoSuchMethodError ignored) {
            // Legacy servers use a Player[] signature
        }
        Player[] legacy = getOnlinePlayersLegacy();
        if (legacy != null && legacy.length > 0) {
            return Arrays.asList(legacy);
        }
        return Collections.emptyList();
    }

    /**
     * Resolves the number of online players without assuming a specific return type.
     */
    public static int getOnlinePlayerCount() {
        try {
            Collection<? extends Player> collection = Bukkit.getOnlinePlayers();
            if (collection != null) {
                return collection.size();
            }
        } catch (NoSuchMethodError ignored) {
            // Legacy servers use a Player[] signature
        }
        Player[] legacy = getOnlinePlayersLegacy();
        return legacy != null ? legacy.length : 0;
    }

    private static Player[] getOnlinePlayersLegacy() {
        resolveLegacyOnlinePlayers();
        if (legacyGetOnlinePlayers != null) {
            try {
                Object result = legacyGetOnlinePlayers.invoke(null);
                if (result instanceof Player[]) {
                    return (Player[]) result;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static synchronized void resolveLegacyOnlinePlayers() {
        if (legacyGetOnlinePlayersResolved) {
            return;
        }
        try {
            legacyGetOnlinePlayers = Bukkit.class.getMethod("getOnlinePlayers");
        } catch (NoSuchMethodException ignored) {
        } finally {
            legacyGetOnlinePlayersResolved = true;
        }
    }

    /**
     * Provides a human readable server version, falling back across the
     * Bukkit API variants available on old servers.
     */
    public static String describeServerVersion() {
        try {
            String bukkitVersion = Bukkit.getBukkitVersion();
            if (bukkitVersion != null && !bukkitVersion.isEmpty()) {
                return bukkitVersion;
            }
        } catch (Throwable ignored) {
        }
        try {
            String version = Bukkit.getVersion();
            if (version != null && !version.isEmpty()) {
                return version;
            }
        } catch (Throwable ignored) {
        }
        return "unknown";
    }

    /**
     * Returns a static description of the supported server range so we can
     * surface it in documentation and startup logs.
     */
    public static String describeSupportedRange() {
        return "1.8.x – 1.21.x";
    }

    /**
     * Attempts to normalize adventure components into plain text without
     * introducing a hard dependency on the kyori serializer.
     */
    public static String describeComponent(Object component) {
        if (component == null) {
            return "";
        }
        if (component instanceof String) {
            return (String) component;
        }
        resolvePlainTextSerializer();
        if (plainTextSerializer != null && plainTextSerialize != null) {
            try {
                Object result = plainTextSerialize.invoke(plainTextSerializer, component);
                if (result instanceof String) {
                    return (String) result;
                }
            } catch (Throwable ignored) {
            }
        }
        return component.toString();
    }

    /**
     * Reads the death message across Bukkit API changes (String vs Component).
     */
    public static String describeDeathMessage(PlayerDeathEvent event) {
        if (event == null) {
            return "unknown";
        }
        Object message = invokeDeathMessage(event, "getDeathMessage");
        if (message == null) {
            message = invokeDeathMessage(event, "deathMessage");
        }
        if (message == null) {
            return "unknown";
        }
        if (message instanceof String) {
            return (String) message;
        }
        return describeComponent(message);
    }

    private static Object invokeDeathMessage(PlayerDeathEvent event, String methodName) {
        if (methodName == null || methodName.isEmpty()) {
            return null;
        }
        try {
            Method method = event.getClass().getMethod(methodName);
            return method.invoke(event);
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static synchronized void resolvePlainTextSerializer() {
        if (plainTextSerializerResolved) {
            return;
        }
        plainTextSerializerResolved = true;
        try {
            Class<?> serializerClass = Class.forName("net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer");
            Method plainText = serializerClass.getMethod("plainText");
            Object serializer = plainText.invoke(null);
            if (serializer != null) {
                for (Method method : serializer.getClass().getMethods()) {
                    if ("serialize".equals(method.getName()) && method.getParameterTypes().length == 1) {
                        plainTextSerializer = serializer;
                        plainTextSerialize = method;
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {
            plainTextSerializer = null;
            plainTextSerialize = null;
        }
    }
}
