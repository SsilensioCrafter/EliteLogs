package com.elitelogs.listeners;

interface DisconnectPacketInterceptor {
    void shutdown();

    DisconnectPacketInterceptor NOOP = () -> {};
}
