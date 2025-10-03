package com.comphenix.protocol;

import com.comphenix.protocol.events.PacketListener;

public final class ProtocolLibrary {
    private static final ProtocolManager MANAGER = new ProtocolManager() {
        @Override
        public void addPacketListener(PacketListener listener) {
            // no-op stub
        }

        @Override
        public void removePacketListener(PacketListener listener) {
            // no-op stub
        }
    };

    private ProtocolLibrary() {
    }

    public static ProtocolManager getProtocolManager() {
        return MANAGER;
    }
}
