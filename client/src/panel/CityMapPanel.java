package panel;

import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.*;
import java.util.Locale;

/** OSM tiles load off the EDT, only while the map tab is visible. */
public final class CityMapPanel extends JPanel {
    private static final int WIDTH = 768, HEIGHT = 512, TILE = 256;
    private static final long CACHE_MILLIS = 7L * 24 * 60 * 60 * 1000;
    private final JLabel status = new JLabel("Chọn thành phố để xem bản đồ.");
    private final JButton zoomIn = new JButton("+"), zoomOut = new JButton("−");
    private final Canvas canvas = new Canvas();
    private double latitude, longitude;
    private String cityName;
    private int zoom = 11;
    private long version;
    private SwingWorker<BufferedImage, Void> task;
    private BufferedImage map;
    private final Timer resizeTimer = new Timer(250, e -> loadMap());

    public CityMapPanel() {
        setLayout(new BorderLayout());
        resizeTimer.setRepeats(false);
        canvas.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                if (isShowing()) resizeTimer.restart();
            }
        });
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(zoomOut);
        controls.add(zoomIn);
        controls.add(status);
        zoomOut.addActionListener(e -> changeZoom(-1));
        zoomIn.addActionListener(e -> changeZoom(1));
        add(controls, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
        JButton source = new JButton("© OpenStreetMap contributors · Mở bản đồ lớn");
        source.setBorderPainted(false);
        source.addActionListener(e -> {
            if (cityName == null) return;
            try {
                Desktop.getDesktop().browse(mapUri(latitude, longitude, zoom));
            } catch (Exception error) {
                JOptionPane.showMessageDialog(this, "Không mở được trình duyệt: " + error.getMessage());
            }
        });
        add(source, BorderLayout.SOUTH);
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (isShowing()) loadMap();
                else { resizeTimer.stop(); cancelLoad(); }
            }
        });
        updateButtons();
    }

    public void showCity(String name, double lat, double lon) {
        if (!Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180) {
            clear();
            status.setText("Tọa độ thành phố không hợp lệ.");
            return;
        }
        if (name.equals(cityName) && lat == latitude && lon == longitude) return;
        cancelLoad();
        cityName = name;
        latitude = lat;
        longitude = lon;
        zoom = 11;
        map = null;
        status.setText(String.format(Locale.ROOT, "%s · %.5f, %.5f", name, lat, lon));
        updateButtons();
        canvas.repaint();
        if (isShowing()) loadMap();
    }

    public void clear() {
        cancelLoad();
        cityName = null;
        map = null;
        status.setText("Chọn thành phố để xem bản đồ.");
        updateButtons();
        canvas.repaint();
    }

    private void changeZoom(int delta) {
        zoom = Math.max(2, Math.min(18, zoom + delta));
        map = null;
        updateButtons();
        loadMap();
    }

    private void updateButtons() {
        zoomIn.setEnabled(cityName != null && zoom < 18);
        zoomOut.setEnabled(cityName != null && zoom > 2);
    }

    private void cancelLoad() {
        version++;
        if (task != null) task.cancel(true);
        task = null;
    }

    private void loadMap() {
        if (cityName == null || !isShowing()) return;
        final int viewWidth = canvas.getWidth(), viewHeight = canvas.getHeight();
        if (viewWidth <= 0 || viewHeight <= 0) return;
        if (map != null && map.getWidth() == viewWidth && map.getHeight() == viewHeight) return;
        cancelLoad();
        final long expected = version;
        final int level = zoom;
        final double centerX = worldX(longitude, level), centerY = worldY(latitude, level);
        status.setText("Đang tải bản đồ " + cityName + "...");
        task = new SwingWorker<BufferedImage, Void>() {
            @Override protected BufferedImage doInBackground() throws Exception {
                BufferedImage result = new BufferedImage(viewWidth, viewHeight, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = result.createGraphics();
                try {
                    graphics.setColor(new Color(235, 239, 232));
                    graphics.fillRect(0, 0, viewWidth, viewHeight);
                    int left = (int) Math.floor(centerX - viewWidth / 2.0);
                    int top = (int) Math.floor(centerY - viewHeight / 2.0);
                    int count = 1 << level;
                    for (int y = Math.floorDiv(top, TILE); y <= Math.floorDiv(top + viewHeight - 1, TILE); y++) {
                        for (int x = Math.floorDiv(left, TILE); x <= Math.floorDiv(left + viewWidth - 1, TILE); x++) {
                            if (isCancelled()) throw new java.util.concurrent.CancellationException();
                            if (y < 0 || y >= count) continue;
                            BufferedImage tile = readTile(level, Math.floorMod(x, count), y);
                            graphics.drawImage(tile, x * TILE - left, y * TILE - top, null);
                        }
                    }
                } finally { graphics.dispose(); }
                return result;
            }
            @Override protected void done() {
                if (isCancelled() || version != expected || !isShowing()) return;
                try {
                    map = get();
                    status.setText(String.format(Locale.ROOT, "%s · %.5f, %.5f · Zoom %d", cityName, latitude, longitude, zoom));
                } catch (Exception error) {
                    status.setText("Không tải được bản đồ. Kiểm tra Internet hoặc mở bản đồ lớn.");
                }
                canvas.repaint();
            }
        };
        task.execute();
    }

    static double worldX(double longitude, int zoom) {
        return (longitude + 180) / 360 * TILE * (1 << zoom);
    }

    static double worldY(double latitude, int zoom) {
        double radians = Math.toRadians(Math.max(-85.05112878, Math.min(85.05112878, latitude)));
        return (1 - Math.log(Math.tan(radians) + 1 / Math.cos(radians)) / Math.PI) / 2 * TILE * (1 << zoom);
    }

    static URI mapUri(double lat, double lon, int zoom) {
        return URI.create(String.format(Locale.ROOT,
                "https://www.openstreetmap.org/?mlat=%.7f&mlon=%.7f#map=%d/%.7f/%.7f", lat, lon, zoom, lat, lon));
    }

    private static BufferedImage readTile(int zoom, int x, int y) throws Exception {
        Path cache = Path.of("map-cache", zoom + "-" + x + "-" + y + ".png");
        if (Files.isRegularFile(cache) && System.currentTimeMillis() - Files.getLastModifiedTime(cache).toMillis() < CACHE_MILLIS) {
            BufferedImage image = ImageIO.read(cache.toFile());
            if (image != null) return image;
        }
        HttpURLConnection connection = (HttpURLConnection) URI.create(
                "https://tile.openstreetmap.org/" + zoom + "/" + x + "/" + y + ".png").toURL().openConnection();
        connection.setRequestProperty("User-Agent", "TraCuuThongTinDiaLy/1.0 (Java desktop city map)");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        if (Files.isRegularFile(cache)) connection.setIfModifiedSince(Files.getLastModifiedTime(cache).toMillis());
        try {
            if (connection.getResponseCode() == 304 && Files.isRegularFile(cache)) {
                BufferedImage image = ImageIO.read(cache.toFile());
                if (image != null) {
                    Files.setLastModifiedTime(cache, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));
                    return image;
                }
            }
            if (connection.getResponseCode() != 200) throw new java.io.IOException("HTTP " + connection.getResponseCode());
            BufferedImage image;
            try (var input = connection.getInputStream()) { image = ImageIO.read(input); }
            if (image == null || image.getWidth() != TILE || image.getHeight() != TILE)
                throw new java.io.IOException("Tile không hợp lệ.");
            try {
                Files.createDirectories(cache.getParent());
                Path temporary = Files.createTempFile(cache.getParent(), "tile-", ".tmp");
                try {
                    ImageIO.write(image, "png", temporary.toFile());
                    try {
                        Files.move(temporary, cache, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    } catch (AtomicMoveNotSupportedException e) {
                        Files.move(temporary, cache, StandardCopyOption.REPLACE_EXISTING);
                    }
                } finally { Files.deleteIfExists(temporary); }
            } catch (java.io.IOException ignored) { /* Map remains usable if the cache folder is read-only. */ }
            return image;
        } finally { connection.disconnect(); }
    }

    private final class Canvas extends JPanel {
        Canvas() { setPreferredSize(new Dimension(WIDTH, HEIGHT)); }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int width = getWidth(), height = getHeight();
            if (map == null) {
                g.drawString(cityName == null ? "Chọn thành phố để xem vị trí." : "Bản đồ chưa tải xong.", 20, 30);
                return;
            }
            // Cover the viewport while a resized map is loading, preserving aspect ratio.
            double scale = Math.max(width / (double) map.getWidth(), height / (double) map.getHeight());
            int drawnWidth = (int) Math.ceil(map.getWidth() * scale), drawnHeight = (int) Math.ceil(map.getHeight() * scale);
            int left = (width - drawnWidth) / 2, top = (height - drawnHeight) / 2;
            g.drawImage(map, left, top, drawnWidth, drawnHeight, null);
            int centerX = left + drawnWidth / 2, centerY = top + drawnHeight / 2;
            g.setColor(Color.WHITE);
            g.fillOval(centerX - 9, centerY - 9, 18, 18);
            g.setColor(new Color(200, 35, 45));
            g.fillOval(centerX - 6, centerY - 6, 12, 12);
        }
    }
}
