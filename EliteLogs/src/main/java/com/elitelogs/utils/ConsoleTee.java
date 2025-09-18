package com.elitelogs.utils;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public class ConsoleTee {
    private final LogRouter router;
    private PrintStream oldOut;
    private PrintStream oldErr;

    public ConsoleTee(LogRouter router){ this.router = router; }

    public void hook(){
        oldOut = System.out;
        oldErr = System.err;
        System.setOut(new TeePrintStream(oldOut, false));
        System.setErr(new TeePrintStream(oldErr, true));
    }
    public void unhook(){
        if (oldOut != null) System.setOut(oldOut);
        if (oldErr != null) System.setErr(oldErr);
    }

    private class TeePrintStream extends PrintStream {
        private final boolean isErr;
        public TeePrintStream(PrintStream base, boolean isErr){
            super(new TeeOutputStream(base, isErr), true, StandardCharsets.UTF_8);
            this.isErr = isErr;
        }
    }

    private class TeeOutputStream extends OutputStream {
        private final PrintStream base;
        private final boolean isErr;
        private final StringBuilder buf = new StringBuilder();
        TeeOutputStream(PrintStream base, boolean isErr){ this.base = base; this.isErr = isErr; }

        @Override public void write(int b){
            base.write(b);
            if (b == '\n'){ flushBuf(); }
            else buf.append((char)b);
        }
        @Override public void write(byte[] b, int off, int len){
            base.write(b, off, len);
            for (int i=off; i<off+len; i++){
                int ch = b[i];
                if (ch == '\n'){ flushBuf(); }
                else buf.append((char)ch);
            }
        }
        private void flushBuf(){
            String line = buf.toString();
            buf.setLength(0);
            if (line.isEmpty()) return;
            String up = line.toUpperCase();
            if (isErr || up.contains("[ERROR") || up.startsWith("ERROR") || up.contains(" ERROR "))
                router.error(line);
            else if (up.contains("[WARN") || up.startsWith("WARN") || up.contains(" WARN "))
                router.warn(line);
            else
                router.console(line);
        }
    }
}
