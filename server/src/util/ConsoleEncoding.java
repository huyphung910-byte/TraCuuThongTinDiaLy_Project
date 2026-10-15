package util;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/** Console streams can use a Windows code page even when file.encoding is UTF-8. */
public final class ConsoleEncoding {
    private static boolean configured;

    private ConsoleEncoding() { }

    public static synchronized void configureUtf8() {
        if (configured) return;
        System.out.flush();
        System.err.flush();
        // Keep existing destinations, including terminal pipes, redirected files and IDE captures.
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        configured = true;
    }
}
