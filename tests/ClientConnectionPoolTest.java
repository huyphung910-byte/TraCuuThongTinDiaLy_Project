import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import server.ClientConnectionPool;

public final class ClientConnectionPoolTest {
    public static void main(String[] args) throws Exception {
        try (ClientConnectionPool pool = new ClientConnectionPool(1);
             ServerSocket listener = new ServerSocket(0);
             Socket peer = new Socket("localhost", listener.getLocalPort());
             Socket accepted = listener.accept()) {
            CountDownLatch started = new CountDownLatch(1), finished = new CountDownLatch(1);
            check(pool.submit(accepted, () -> {
                started.countDown();
                try {
                    accepted.getInputStream().read();
                } catch (java.io.IOException expected) {
                    // Closing the pool must unblock the read.
                } finally {
                    finished.countDown();
                }
            }), "First client accepted");
            check(started.await(3, TimeUnit.SECONDS), "Handler started");
            Socket excess = new Socket();
            check(!pool.submit(excess, () -> { throw new AssertionError("Overloaded handler ran"); }),
                    "Pool limit enforced");
            check(excess.isClosed(), "Rejected socket closed");
            pool.close();
            check(accepted.isClosed() && finished.await(3, TimeUnit.SECONDS), "Shutdown unblocks client read");
            Socket late = new Socket();
            check(!pool.submit(late, () -> { throw new AssertionError(); }) && late.isClosed(),
                    "No connections after shutdown");
            pool.close();
        }
        try {
            new ClientConnectionPool(0);
            throw new AssertionError("Invalid limit accepted");
        } catch (IllegalArgumentException expected) { }
        System.out.println("ClientConnectionPoolTest: bounded connections and shutdown passed.");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
