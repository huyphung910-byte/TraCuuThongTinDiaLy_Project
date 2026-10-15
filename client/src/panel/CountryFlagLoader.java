package panel;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Iterator;
import java.util.Locale;

/** Chỉ tải quốc kỳ PNG từ hai máy chủ ảnh được dùng bởi nguồn quốc gia. */
final class CountryFlagLoader {
    static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final byte[] PNG_SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

    private CountryFlagLoader() { }

    static BufferedImage load(String url) throws IOException {
        URI address = trustedAddress(url);
        for (int redirects = 0; redirects <= 3; redirects++) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Đã hủy tải quốc kỳ.");
            HttpURLConnection connection = (HttpURLConnection) address.toURL().openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(7000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "TraCuuThongTinDiaLy/1.0");
            connection.setRequestProperty("Accept", "image/png");
            try {
                int status = connection.getResponseCode();
                if (status >= 300 && status < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || redirects == 3) throw new IOException("Nguồn quốc kỳ chuyển hướng quá nhiều lần.");
                    address = trustedAddress(address.resolve(location).toString());
                    continue;
                }
                if (status != HttpURLConnection.HTTP_OK) throw new IOException("Nguồn quốc kỳ báo lỗi HTTP " + status + ".");
                if (connection.getContentLengthLong() > MAX_BYTES) throw new IOException("Ảnh quốc kỳ vượt quá giới hạn 2 MB.");
                try (InputStream stream = connection.getInputStream()) { return decodePng(readBounded(stream)); }
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException("Không tải được quốc kỳ.");
    }

    static URI trustedAddress(String url) throws IOException {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)
                    || !(host.equals("flagcdn.com") || host.equals("upload.wikimedia.org"))) {
                throw new IOException("Nguồn ảnh quốc kỳ phải dùng HTTPS trên FlagCDN hoặc Wikimedia.");
            }
            return uri;
        } catch (IllegalArgumentException | NullPointerException error) {
            throw new IOException("Đường dẫn ảnh quốc kỳ không hợp lệ.", error);
        }
    }

    static byte[] readBounded(InputStream stream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int count;
        while ((count = stream.read(chunk)) != -1) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Đã hủy tải quốc kỳ.");
            if (buffer.size() + count > MAX_BYTES) throw new IOException("Ảnh quốc kỳ vượt quá giới hạn 2 MB.");
            buffer.write(chunk, 0, count);
        }
        return buffer.toByteArray();
    }

    static BufferedImage decodePng(byte[] data) throws IOException {
        if (data == null || data.length < PNG_SIGNATURE.length) throw new IOException("Nguồn quốc kỳ không trả về ảnh PNG.");
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (data[i] != PNG_SIGNATURE[i]) throw new IOException("Nguồn quốc kỳ không trả về ảnh PNG.");
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Không đọc được ảnh quốc kỳ PNG.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > 4096 || height > 4096 || (long) width * height > 4_000_000) {
                    throw new IOException("Kích thước ảnh quốc kỳ quá lớn.");
                }
                BufferedImage original = reader.read(0);
                double scale = Math.min(1, Math.min(260.0 / width, 160.0 / height));
                int scaledWidth = Math.max(1, (int) Math.round(width * scale));
                int scaledHeight = Math.max(1, (int) Math.round(height * scale));
                BufferedImage scaled = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = scaled.createGraphics();
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    graphics.drawImage(original, 0, 0, scaledWidth, scaledHeight, null);
                } finally {
                    graphics.dispose();
                }
                return scaled;
            } finally {
                reader.dispose();
            }
        }
    }
}
