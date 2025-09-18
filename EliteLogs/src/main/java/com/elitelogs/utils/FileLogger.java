package com.elitelogs.utils;

import java.io.File;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

public class FileLogger {
    private final File dir;
    public FileLogger(File dir){ this.dir = dir; if (!dir.exists()) dir.mkdirs(); }
    public synchronized void append(String fileName, String line){
        File target = new File(dir, fileName);
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(target, true), StandardCharsets.UTF_8))) {
            writer.write(line);
            writer.newLine();
        } catch (IOException e){ e.printStackTrace(); }
    }
}
