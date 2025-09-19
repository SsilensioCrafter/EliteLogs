package com.elitelogs.utils;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

public class ArchiveManager {
    public static final class Result {
        private final boolean skipped;
        private final int candidates;
        private final int archived;
        private final int failed;
        private final Exception error;

        private Result(boolean skipped, int candidates, int archived, int failed, Exception error) {
            this.skipped = skipped;
            this.candidates = candidates;
            this.archived = archived;
            this.failed = failed;
            this.error = error;
        }

        public static Result skipped() { return new Result(true, 0, 0, 0, null); }
        public static Result success(int candidates, int archived, int failed) { return new Result(false, candidates, archived, failed, null); }
        public static Result failure(Exception error) { return new Result(false, 0, 0, 0, error); }

        public boolean isSkipped() { return skipped; }
        public int getCandidates() { return candidates; }
        public int getArchived() { return archived; }
        public int getFailed() { return failed; }
        public Exception getError() { return error; }
    }

    public static Result archiveOldLogs(File dataFolder, int keepDays){
        Path logs = new File(dataFolder, "logs").toPath();
        Path archive = new File(dataFolder, "archive").toPath();
        try {
            if (keepDays <= 0) {
                Logger.getLogger("EliteLogs").fine("Archive skipped because keep-days <= 0");
                return Result.skipped();
            }
            if (!Files.exists(logs)) {
                return Result.success(0, 0, 0);
            }
            Files.createDirectories(archive);
            Instant cutoff = Instant.now().minus(Duration.ofDays(keepDays));
            final int[] counters = new int[3]; // 0=candidates,1=archived,2=failed
            try (java.util.stream.Stream<Path> stream = Files.walk(logs)) {
                stream.filter(Files::isRegularFile).forEach(p -> {
                    File f = p.toFile();
                    if (Instant.ofEpochMilli(f.lastModified()).isBefore(cutoff)){
                        counters[0]++;
                        Path rel = logs.relativize(p);
                        Path gz = archive.resolve(rel.toString() + ".gz");
                        try {
                            Path parent = gz.getParent();
                            if (parent != null) {
                                Files.createDirectories(parent);
                            }
                            try (GZIPOutputStream g = new GZIPOutputStream(new FileOutputStream(gz.toFile()));
                                 FileInputStream in = new FileInputStream(f)) {
                                byte[] buffer = new byte[8192];
                                int read;
                                while ((read = in.read(buffer)) != -1) {
                                    g.write(buffer, 0, read);
                                }
                            }
                            counters[1]++;
                        } catch(Exception ex) {
                            counters[2]++;
                            Logger.getLogger("EliteLogs").warning("Archive failed for " + rel + ": " + ex.getMessage());
                        }
                    }
                });
            }
            return Result.success(counters[0], counters[1], counters[2]);
        } catch(Exception ex) {
            Logger.getLogger("EliteLogs").severe("Archive failed: " + ex.getMessage());
            return Result.failure(ex);
        }
    }
}
