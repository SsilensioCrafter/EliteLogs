package com.comphenix.protocol;

import com.comphenix.protocol.events.PacketListener;

public interface ProtocolManager {
    void addPacketListener(PacketListener listener);

    void removePacketListener(PacketListener listener);
}
