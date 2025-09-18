package com.elitelogs.utils;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(out))) {
            for (Path p : toZip) {
                if (!Files.exists(p)) continue;
                Files.walk(p).filter(Files::isRegularFile).forEach(f -> {
                    try {
                        ZipEntry e = new ZipEntry(base.relativize(f).toString().replace("\\","/"));
                        zos.putNextEntry(e);
                        Files.copy(f, zos);
                        zos.closeEntry();
                    } catch (Exception ignored){}
                });
            }
        }
    }
}
