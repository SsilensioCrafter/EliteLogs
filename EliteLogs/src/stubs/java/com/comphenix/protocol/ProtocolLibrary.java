package com.comphenix.protocol;

public final class ProtocolLibrary {
    private static final ProtocolManager MANAGER = new ProtocolManager();

    private ProtocolLibrary() {
    }

    public static ProtocolManager getProtocolManager() {
        return MANAGER;
    }
}
