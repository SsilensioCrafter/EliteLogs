package com.comphenix.protocol;

public class PacketType {
    public static class Play {
        public static class Server {
            public static final PacketType DISCONNECT = new PacketType();

            private Server() {
            }
        }

        private Play() {
        }
    }
}
