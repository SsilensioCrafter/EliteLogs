package com.comphenix.protocol.events;

import com.comphenix.protocol.wrappers.WrappedChatComponent;

public class PacketContainer {
    public ChatComponentModifier getChatComponents() {
        return new ChatComponentModifier();
    }

    public static class ChatComponentModifier {
        public WrappedChatComponent readSafely(int index) {
            return null;
        }
    }
}
