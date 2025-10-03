package com.elitelogs.listeners;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.elitelogs.compat.ServerCompat;
import com.elitelogs.logging.DisconnectLogEntry;
import com.elitelogs.logging.LogRouter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

class ProtocolLibDisconnectInterceptor extends PacketAdapter implements DisconnectPacketInterceptor {
    private final LogRouter router;

    ProtocolLibDisconnectInterceptor(Plugin plugin, LogRouter router) {
        super(plugin, ListenerPriority.NORMAL, resolveDisconnectPackets());
        this.router = router;
        ProtocolLibrary.getProtocolManager().addPacketListener(this);
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        if (event == null || event.isCancelled()) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        WrappedChatComponent component = extractChatComponent(event.getPacket());
        String reason = component != null ? safeGetJson(component) : null;
        String plain = toPlainText(reason);
        if ((plain == null || plain.isEmpty()) && (reason == null || reason.isEmpty())) {
            return;
        }
        DisconnectLogEntry.Builder builder = DisconnectLogEntry.phase("disconnect-screen");
        applyPlayerMetadata(builder, player);
        if (plain != null && !plain.isEmpty()) {
            builder.attribute("reason", plain);
        }
        if (reason != null && !reason.isEmpty() && !reason.equals(plain)) {
            builder.attribute("raw-json", reason);
        }
        router.disconnect(builder.build());
    }

    private WrappedChatComponent extractChatComponent(Object packetContainer) {
        if (packetContainer == null) {
            return null;
        }

        // Modern ProtocolLib exposes getChatComponents(); older builds may require using the generic modifier API.
        WrappedChatComponent component = tryInvokeChatComponents(packetContainer);
        if (component != null) {
            return component;
        }

        // Fallback to the generic modifier API so we can pull chat components regardless of helper availability.
        return tryModifierLookup(packetContainer);
    }

    private WrappedChatComponent tryInvokeChatComponents(Object packetContainer) {
        try {
            Method accessor = packetContainer.getClass().getMethod("getChatComponents");
            Object modifier = accessor.invoke(packetContainer);
            return readChatComponent(modifier);
        } catch (NoSuchMethodException ignored) {
            // Method removed in newer ProtocolLib builds.
        } catch (Throwable ignored) {
            // Any reflective failure should fall back to alternative strategies.
        }
        return null;
    }

    private WrappedChatComponent tryModifierLookup(Object packetContainer) {
        try {
            Method getModifier = packetContainer.getClass().getMethod("getModifier");
            Object modifier = getModifier.invoke(packetContainer);
            if (modifier == null) {
                return null;
            }
            Method withType = modifier.getClass().getMethod("withType", Class.class);
            Object typed = withType.invoke(modifier, WrappedChatComponent.class);
            return readChatComponent(typed);
        } catch (Throwable ignored) {
            // Nothing else we can do here – fall back to vanilla disconnect logging.
        }
        return null;
    }

    private WrappedChatComponent readChatComponent(Object modifier) {
        if (modifier == null) {
            return null;
        }
        try {
            Method readSafely = modifier.getClass().getMethod("readSafely", int.class);
            Object value = readSafely.invoke(modifier, 0);
            if (value instanceof WrappedChatComponent) {
                return (WrappedChatComponent) value;
            }
        } catch (NoSuchMethodException ignored) {
            // Older StructureModifier instances may only expose read(int).
            try {
                Method read = modifier.getClass().getMethod("read", int.class);
                Object value = read.invoke(modifier, 0);
                if (value instanceof WrappedChatComponent) {
                    return (WrappedChatComponent) value;
                }
            } catch (Throwable ignoredToo) {
                // Continue on to return null.
            }
        } catch (Throwable ignored) {
            // If the modifier API throws, pretend we never saw a chat component.
        }
        return null;
    }

    private String safeGetJson(WrappedChatComponent component) {
        if (component == null) {
            return null;
        }
        try {
            return component.getJson();
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public void shutdown() {
        ProtocolLibrary.getProtocolManager().removePacketListener(this);
    }

    private String toPlainText(String json) {
        if (json == null || json.isEmpty()) {
            return "";
        }
        try {
            Class<?> gsonSerializerClass = Class.forName("net.kyori.adventure.text.serializer.gson.GsonComponentSerializer");
            Method gson = gsonSerializerClass.getMethod("gson");
            Object serializer = gson.invoke(null);
            Method deserialize = serializer.getClass().getMethod("deserialize", String.class);
            Object component = deserialize.invoke(serializer, json);
            return ServerCompat.describeComponent(component);
        } catch (Throwable ignored) {
        }
        return json;
    }

    private static PacketType[] resolveDisconnectPackets() {
        Set<PacketType> packetTypes = new LinkedHashSet<>();
        for (String className : new String[]{
                "com.comphenix.protocol.PacketType$Play$Server",
                "com.comphenix.protocol.PacketType$Login$Server",
                "com.comphenix.protocol.PacketType$Configuration$Server"
        }) {
            try {
                Class<?> holder = Class.forName(className);
                for (Field field : holder.getFields()) {
                    if (!PacketType.class.isAssignableFrom(field.getType())) {
                        continue;
                    }
                    String name = field.getName();
                    if (name == null || !name.toUpperCase(Locale.ROOT).contains("DISCONNECT")) {
                        continue;
                    }
                    PacketType type = (PacketType) field.get(null);
                    if (isRegistered(type)) {
                        packetTypes.add(type);
                    }
                }
            } catch (ClassNotFoundException ignored) {
                // The ProtocolLib build in use doesn't expose this packet group.
            } catch (Throwable ignored) {
                // Any failure to discover a packet type should not prevent fallback behaviour.
            }
        }
        if (packetTypes.isEmpty()) {
            throw new IllegalStateException("No disconnect packets detected in ProtocolLib.");
        }
        return packetTypes.toArray(new PacketType[0]);
    }

    private static boolean isRegistered(PacketType type) {
        if (type == null) {
            return false;
        }
        try {
            Method method = type.getClass().getMethod("isSupported");
            Object result = method.invoke(type);
            if (result instanceof Boolean && Boolean.FALSE.equals(result)) {
                return false;
            }
        } catch (NoSuchMethodException ignored) {
            // Older ProtocolLib builds may not expose isSupported.
        } catch (Throwable ignored) {
            // Any failure to ask ProtocolLib about support should fall back to string heuristics below.
        }
        String description = String.valueOf(type).toLowerCase(Locale.ROOT);
        return !description.contains("unregistered");
    }

    private void applyPlayerMetadata(DisconnectLogEntry.Builder builder, Player player) {
        if (builder == null || player == null) {
            return;
        }

        UUID uuid = safeUniqueId(player);
        String name = safeName(player);

        if (uuid != null && name != null) {
            builder.player(uuid, name);
        } else if (uuid != null) {
            builder.player(uuid);
        } else if (name != null && !name.isEmpty()) {
            builder.playerName(name);
        }
    }

    private UUID safeUniqueId(Player player) {
        try {
            return player.getUniqueId();
        } catch (UnsupportedOperationException ignored) {
            // ProtocolLib temporary players do not expose UUIDs during early handshake.
        } catch (Throwable ignored) {
            // Any unexpected exception should not break disconnect logging.
        }
        return null;
    }

    private String safeName(Player player) {
        try {
            return player.getName();
        } catch (Throwable ignored) {
            return null;
        }
    }

}
