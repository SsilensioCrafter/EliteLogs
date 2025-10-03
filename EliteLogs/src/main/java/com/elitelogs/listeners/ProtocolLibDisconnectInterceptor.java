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

import java.lang.reflect.Method;

class ProtocolLibDisconnectInterceptor extends PacketAdapter implements DisconnectPacketInterceptor {
    private final LogRouter router;

    ProtocolLibDisconnectInterceptor(Plugin plugin, LogRouter router) {
        super(plugin, ListenerPriority.NORMAL, PacketType.Play.Server.DISCONNECT);
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
        WrappedChatComponent component = event.getPacket().getChatComponents().readSafely(0);
        String reason = component != null ? component.getJson() : null;
        String plain = toPlainText(reason);
        if ((plain == null || plain.isEmpty()) && (reason == null || reason.isEmpty())) {
            return;
        }
        DisconnectLogEntry.Builder builder = DisconnectLogEntry.phase("disconnect-screen")
                .player(player.getUniqueId(), player.getName());
        if (plain != null && !plain.isEmpty()) {
            builder.attribute("reason", plain);
        }
        if (reason != null && !reason.isEmpty() && !reason.equals(plain)) {
            builder.attribute("raw-json", reason);
        }
        router.disconnect(builder.build());
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

}
