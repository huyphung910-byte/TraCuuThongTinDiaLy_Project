package network;

import com.google.gson.Gson;
import model.Message;
import security.SecureChannel;
import util.AppConfig;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import java.util.function.Consumer;

public class ClientSocketManager implements AutoCloseable {

    private final Gson gson = new Gson();

    private final BlockingQueue<Object> responses =
            new LinkedBlockingQueue<>(4);

    private final SecureChannel channel;

    private volatile Consumer<String> alertListener;
    private volatile IOException connectionFailure;
    private volatile boolean closed;

    public ClientSocketManager() throws IOException {

        Socket socket = new Socket();

        try {
            AppConfig config = AppConfig.load("client");
            String host = config.required("server.host");
            int port = config.positiveInt("server.port", 65535);

            Path keyPath = resolveServerPublicKey();

            java.security.PublicKey pinnedKey =
                    SecureChannel.readPublicKey(keyPath);

            socket.connect(
                    new InetSocketAddress(host, port),
                    10_000
            );

            channel = SecureChannel.connect(socket, pinnedKey);

        } catch (IOException | RuntimeException e) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }

            throw new IOException(
                    "Không thiết lập được kết nối bảo mật: "
                            + describeConnectionFailure(e),
                    e
            );
        }

        Thread listener = new Thread(
                this::listenForMessages,
                "secure-server-listener"
        );

        listener.setDaemon(true);
        listener.start();
    }

    private static Path resolveServerPublicKey()
            throws IOException {

        // Nếu có cấu hình -D, sử dụng đúng đường dẫn được chỉ định.
        String configured =
                System.getProperty("secure.serverPublicKey");

        if (configured != null && !configured.trim().isEmpty()) {

            Path path = Paths.get(configured)
                    .toAbsolutePath()
                    .normalize();

            if (!Files.isRegularFile(path)) {
                throw new IOException(
                        "Không tìm thấy khóa công khai đã cấu hình: "
                                + path
                );
            }

            return path;
        }

        // Trường hợp chạy từ thư mục gốc project.
        Path fromProjectRoot = Paths.get(
                "client",
                "security",
                "server-public.key"
        ).toAbsolutePath().normalize();

        if (Files.isRegularFile(fromProjectRoot)) {
            return fromProjectRoot;
        }

        // Trường hợp chạy từ thư mục client.
        Path fromClientFolder = Paths.get(
                "security",
                "server-public.key"
        ).toAbsolutePath().normalize();

        if (Files.isRegularFile(fromClientFolder)) {
            return fromClientFolder;
        }

        throw new IOException(
                "Không tìm thấy khóa công khai. Đã kiểm tra: "
                        + fromProjectRoot
                        + " và "
                        + fromClientFolder
        );
    }

    public void setAlertListener(Consumer<String> listener) {
        alertListener = listener;
    }

    private void listenForMessages() {

        IOException failure =
                new IOException("Máy chủ đã ngắt kết nối.");

        try {
            String json;

            while ((json = channel.receive()) != null) {

                Message message =
                        gson.fromJson(json, Message.class);

                if (message == null || message.getAction() == null) {
                    throw new IOException(
                            "Phản hồi của máy chủ không hợp lệ."
                    );
                }

                if ("WEATHER_ALERT".equals(message.getAction())
                        || "WEATHER_ERROR".equals(message.getAction())) {

                    Consumer<String> listener = alertListener;

                    if (listener != null) {
                        String alert =
                                String.valueOf(message.getData());

                        javax.swing.SwingUtilities.invokeLater(
                                () -> {
                                    if (!closed && alertListener == listener) listener.accept(alert);
                                }
                        );
                    }

                } else if (!responses.offer(message)) {
                    throw new IOException(
                            "Máy chủ gửi thêm phản hồi ngoài dự kiến."
                    );
                }
            }

        } catch (Exception e) {
            failure = new IOException(
                    "Kết nối bảo mật đã đóng: "
                            + describeConnectionFailure(e),
                    e
            );

        } finally {
            failConnection(failure);
        }
    }

    public synchronized Message sendRequest(Message request)
            throws IOException {

        // Validate before writing anything. A local input error must not close a healthy socket.
        Message validatedRequest = util.RequestValidation.validate(request);

        if (closed || connectionFailure != null) {
            throw new IOException(
                    "Kết nối đã đóng. Hãy đăng nhập lại.",
                    connectionFailure
            );
        }

        try {
            channel.send(validatedRequest.toJson());

            // Country lookup includes base metadata plus two parallel enrichment APIs.
            boolean countryLookup = "SEARCH_COUNTRY".equals(validatedRequest.getAction());
            Object result = responses.poll(
                    countryLookup ? 15 : 10,
                    TimeUnit.SECONDS
            );

            if (result == null) {
                throw new IOException(
                        "Hết thời gian chờ phản hồi của máy chủ. "
                                + "Hãy đăng nhập lại."
                );
            }

            if (result instanceof IOException) {
                throw (IOException) result;
            }

            if (closed || connectionFailure != null) {
                throw new IOException("Kết nối đã đóng. Hãy đăng nhập lại.", connectionFailure);
            }

            return (Message) result;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            IOException failure = new IOException(
                    "Đã gián đoạn khi chờ phản hồi.",
                    e
            );

            failConnection(failure);
            throw failure;

        } catch (IOException e) {
            failConnection(e);
            throw e;
        }
    }

    private void failConnection(IOException cause) {

        connectionFailure = cause;
        closed = true;
        alertListener = null;

        try {
            channel.close();
        } catch (IOException ignored) {
        }

        responses.clear();
        responses.offer(cause);
    }

    @Override
    public void close() throws IOException {
        failConnection(
                new IOException("Máy khách đã đóng kết nối.")
        );
    }

    private static String describeConnectionFailure(Exception exception) {
        if (exception instanceof java.net.SocketTimeoutException) return "Hết thời gian chờ kết nối với máy chủ.";
        if (exception instanceof java.net.ConnectException) return "Không kết nối được máy chủ. Kiểm tra máy chủ đã chạy chưa.";
        if (exception instanceof java.net.UnknownHostException) return "Không xác định được địa chỉ máy chủ.";
        if (exception instanceof java.net.SocketException || exception instanceof java.io.EOFException) {
            return "Kết nối với máy chủ bị gián đoạn.";
        }
        if (exception instanceof java.nio.file.NoSuchFileException) return "Không tìm thấy tệp khóa công khai của máy chủ.";
        if (exception instanceof NumberFormatException) return "Cổng máy chủ phải là số nguyên hợp lệ.";
        if (exception instanceof java.nio.file.InvalidPathException) return "Đường dẫn khóa công khai không hợp lệ.";
        if (exception.getClass() == IOException.class && exception.getMessage() != null
                && exception.getMessage().matches("(?s).*[^\\x00-\\x7F].*")) return exception.getMessage();
        return "Không xử lý được kết nối hoặc phản hồi của máy chủ.";
    }
}
