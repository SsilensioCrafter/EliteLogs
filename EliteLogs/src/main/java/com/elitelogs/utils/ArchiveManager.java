package com.elitelogs.utils;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

public class ArchiveManager {
    public static void archiveOldLogs(File dataFolder, int keepDays){
        Path logs = new File(dataFolder, "logs").toPath();
        Path archive = new File(dataFolder, "archive").toPath();
        try {
            if (keepDays <= 0) {
                Logger.getLogger("EliteLogs").fine("Archive skipped because keep-days <= 0");
                return;
            }
            if (!Files.exists(logs)) {
                return;
            }
            Files.createDirectories(archive);
            Instant cutoff = Instant.now().minus(Duration.ofDays(keepDays));
            Files.walk(logs).filter(Files::isRegularFile).forEach(p -> {
                try {
                    File f = p.toFile();
                    if (Instant.ofEpochMilli(f.lastModified()).isBefore(cutoff)){
                        Path rel = logs.relativize(p);
                        Path gz = archive.resolve(rel.toString() + ".gz");
                        Files.createDirectories(gz.getParent());
                        try (GZIPOutputStream g = new GZIPOutputStream(new FileOutputStream(gz.toFile()));
                             FileInputStream in = new FileInputStream(f)) {
                            byte[] buffer = new byte[8192];
                            int read;
                            while ((read = in.read(buffer)) != -1) {
                                g.write(buffer, 0, read);
                            }
                        }
                        // Files.delete(p); // оставить оригиналы по желанию
                    }
                } catch(Exception ignored){}
            });
        } catch(Exception ignored){}
    }
}
