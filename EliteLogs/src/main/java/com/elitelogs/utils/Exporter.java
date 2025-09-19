package com.elitelogs.utils;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.stream.Stream;

public class Exporter {
    public static File exportToday(File dataFolder) throws IOException {
        String name = "EliteLogs_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + "_today.zip";
        File out = new File(new File(dataFolder, "exports"), name);
        zipPaths(out, dataFolder.toPath(),
                dataFolder.toPath().resolve("logs"),
                dataFolder.toPath().resolve("reports"));
        return out;
    }
    public static File exportFull(File dataFolder) throws IOException {
        String name = "EliteLogs_" + new SimpleDateFormat("yyyy-MM-dd_HHmm").format(new Date()) + "_full.zip";
        File out = new File(new File(dataFolder, "exports"), name);
        zipPaths(out, dataFolder.toPath(), dataFolder.toPath());
        return out;
    }
    public static File exportLastCrash(File dataFolder) throws IOException {
        String name = "EliteLogs_last-crash_" + new SimpleDateFormat("yyyy-MM-dd_HHmm").format(new Date()) + ".zip";
        File out = new File(new File(dataFolder, "exports"), name);
        zipPaths(out, dataFolder.toPath(),
                dataFolder.toPath().resolve("exports"));
        return out;
    }
    private static void zipPaths(File out, Path base, Path... toZip) throws IOException {
        out.getParentFile().mkdirs();
        Path outputPath = out.toPath().toAbsolutePath().normalize();
        Path basePath = base.toAbsolutePath().normalize();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(out))) {
            for (Path p : toZip) {
                if (p == null || !Files.exists(p)) continue;
                Path root = p.toAbsolutePath().normalize();
                try (Stream<Path> stream = Files.walk(root)) {
                    stream.filter(Files::isRegularFile).forEach(f -> {
                        Path filePath = f.toAbsolutePath().normalize();
                        if (filePath.equals(outputPath)) {
                            return;
                        }
                        try {
                            Path relative = basePath.relativize(filePath);
                            ZipEntry e = new ZipEntry(relative.toString().replace("\\","/"));
                            zos.putNextEntry(e);
                            Files.copy(filePath, zos);
                            zos.closeEntry();
                        } catch (Exception ignored){}
                    });
                }
            }
        }
    }
}
