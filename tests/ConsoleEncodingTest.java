package util;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Check real stdout/stderr bytes with legacy Windows JVM code pages, without opening the GUI. */
public final class ConsoleEncodingTest {
    private static final String OUTPUT = "Máy khách đã khởi động. Đang mở cửa sổ đăng nhập...\n";
    private static final String ERROR = "Không tải được địa điểm đã lưu: dữ liệu yêu cầu không hợp lệ.\n";

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            if (args[0].equals("capture")) capturedDestination();
            else {
                if (!args[0].equals("before")) ConsoleEncoding.configureUtf8();
                System.out.print(OUTPUT);
                System.err.print(ERROR);
            }
            return;
        }
        Result before = probe("before", "UTF-8", "IBM437");
        check(!Arrays.equals(before.output, OUTPUT.getBytes(StandardCharsets.UTF_8)),
                "Fixture must reproduce incorrect console bytes despite file.encoding=UTF-8");
        for (String[] encoding : new String[][]{{"UTF-8", "IBM437"}, {"windows-1252", "windows-1252"}, {"UTF-8", "UTF-8"}}) {
            Result actual = probe("after", encoding[0], encoding[1]);
            check(Arrays.equals(actual.output, OUTPUT.getBytes(StandardCharsets.UTF_8)), "stdout must preserve Vietnamese in UTF-8");
            check(Arrays.equals(actual.error, ERROR.getBytes(StandardCharsets.UTF_8)), "stderr must preserve Vietnamese in UTF-8");
        }
        probe("capture", "windows-1252", "IBM437");
        ConsoleEncoding.configureUtf8();
        System.out.println("ĐẠT: tiếng Việt trên stdout/stderr, bảng mã Windows cũ và chuyển hướng đầu ra.");
    }

    private static Result probe(String mode, String fileEncoding, String consoleEncoding) throws Exception {
        String executable = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java";
        String javaPath = Path.of(System.getProperty("java.home"), "bin", executable).toString();
        Process process = new ProcessBuilder(javaPath, "-Dfile.encoding=" + fileEncoding,
                "-Dsun.stdout.encoding=" + consoleEncoding, "-Dsun.stderr.encoding=" + consoleEncoding,
                "-Dstdout.encoding=" + consoleEncoding, "-Dstderr.encoding=" + consoleEncoding,
                "-cp", System.getProperty("java.class.path"), ConsoleEncodingTest.class.getName(), mode).start();
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("Console fixture timed out");
        }
        byte[] output = process.getInputStream().readAllBytes(), error = process.getErrorStream().readAllBytes();
        check(process.exitValue() == 0, "Console fixture failed: " + new String(error, StandardCharsets.UTF_8));
        return new Result(output, error);
    }

    private static void capturedDestination() {
        ByteArrayOutputStream output = new ByteArrayOutputStream(), error = new ByteArrayOutputStream();
        System.setOut(new PrintStream(output, true, Charset.forName("IBM437")));
        System.setErr(new PrintStream(error, true, Charset.forName("IBM437")));
        ConsoleEncoding.configureUtf8();
        PrintStream configuredOutput = System.out, configuredError = System.err;
        ConsoleEncoding.configureUtf8();
        check(System.out == configuredOutput && System.err == configuredError, "Repeated configuration must be harmless");
        System.out.print(OUTPUT);
        System.err.print(ERROR);
        check(Arrays.equals(output.toByteArray(), OUTPUT.getBytes(StandardCharsets.UTF_8)), "Preserve captured stdout destination");
        check(Arrays.equals(error.toByteArray(), ERROR.getBytes(StandardCharsets.UTF_8)), "Preserve captured stderr destination");
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private record Result(byte[] output, byte[] error) { }
}
