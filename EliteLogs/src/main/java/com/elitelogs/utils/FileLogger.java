package com.elitelogs.utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class FileLogger {
    private final File dir;
    public FileLogger(File dir){ this.dir = dir; if (!dir.exists()) dir.mkdirs(); }
    public synchronized void append(String fileName, String line){
        try (FileWriter fw = new FileWriter(new File(dir, fileName), StandardCharsets.UTF_8, true)) {
            fw.write(line + System.lineSeparator());
        } catch (IOException e){ e.printStackTrace(); }
    }
}
