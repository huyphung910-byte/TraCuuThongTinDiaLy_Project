package server;

import java.io.IOException;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded workers for long-lived connections; overloaded connections are closed immediately. */
public final class ClientConnectionPool implements AutoCloseable {
    private final ThreadPoolExecutor executor;
    private final Set<Socket> connections = ConcurrentHashMap.newKeySet();
    private boolean closed;

    public ClientConnectionPool(int maxClients) {
        if (maxClients < 1) throw new IllegalArgumentException("server.maxClients phải lớn hơn 0.");
        AtomicInteger sequence = new AtomicInteger();
        executor = new ThreadPoolExecutor(0, maxClients, 60, TimeUnit.SECONDS,
                new SynchronousQueue<>(), task -> new Thread(task, "client-worker-" + sequence.incrementAndGet()));
    }

    public synchronized boolean submit(Socket socket, Runnable handler) {
        if (closed) {
            closeSocket(socket);
            return false;
        }
        connections.add(socket);
        try {
            executor.execute(() -> {
                try {
                    handler.run();
                } finally {
                    closeSocket(socket);
                    connections.remove(socket);
                }
            });
            return true;
        } catch (RejectedExecutionException e) {
            connections.remove(socket);
            closeSocket(socket);
            return false;
        }
    }

    @Override public void close() {
        synchronized (this) {
            if (closed) return;
            closed = true;
            executor.shutdown();
            // Interrupting a thread alone does not unblock Socket reads.
            connections.forEach(ClientConnectionPool::closeSocket);
        }
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static void closeSocket(Socket socket) {
        try {
            socket.close();
        } catch (IOException e) {
            System.err.println("Không đóng được kết nối máy khách.");
        }
    }
}
