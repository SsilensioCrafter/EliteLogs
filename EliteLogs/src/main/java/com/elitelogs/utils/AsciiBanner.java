package com.elitelogs.utils;

import java.util.logging.Logger;

public class AsciiBanner {
    public static void print(Logger log, String version, String style) {
        log.info("");
        log.info("██╗      ██████╗   ██████╗ ███████╗");
        log.info("██║     ██╔═══██╗ ██╔════╝ ██╔════╝");
        log.info("██║     ██║   ██║ ██║  ███╗███████╗");
        log.info("██║     ██║   ██║ ██║   ██║╚════██║");
        log.info("███████╗╚██████╔╝ ╚██████╔╝███████║");
        log.info("╚══════╝ ╚═════╝   ╚═════╝ ╚══════╝");
        log.info("");
        log.info("EliteLogs v" + version + " — logging & monitoring system");
        log.info("");
    }
}
