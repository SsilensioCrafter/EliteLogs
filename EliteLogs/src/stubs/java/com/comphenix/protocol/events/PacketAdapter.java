package com.comphenix.protocol.events;

import com.comphenix.protocol.PacketType;
import org.bukkit.plugin.Plugin;

public class PacketAdapter implements PacketListener {
    public PacketAdapter(Plugin plugin, ListenerPriority priority, PacketType... types) {
        // no-op stub
    }

    public void onPacketSending(PacketEvent event) {
        // no-op stub
    }

    public void onPacketReceiving(PacketEvent event) {
        // no-op stub
    }
}
