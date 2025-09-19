package com.elitelogs.utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class FileLogger {
    private final File dir;
    public FileLogger(File dir){
        this.dir = dir;
        try {
            Files.createDirectories(dir.toPath());
        } catch (IOException ignored) {
            dir.mkdirs();
        }
    }
    public synchronized void append(String fileName, String line){
        File target = new File(dir, fileName);
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            try {
                Files.createDirectories(parent.toPath());
            } catch (IOException ignored) {
                parent.mkdirs();
            }
        }
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(target, true), StandardCharsets.UTF_8))) {
            writer.write(line);
            writer.newLine();
        } catch (IOException e){ e.printStackTrace(); }
    }
}
