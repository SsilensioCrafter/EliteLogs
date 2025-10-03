package com.comphenix.protocol.events;

import org.bukkit.entity.Player;

public class PacketEvent {
    public boolean isCancelled() {
        return false;
    }

    public Player getPlayer() {
        return null;
    }

    public PacketContainer getPacket() {
        return new PacketContainer();
    }
}
