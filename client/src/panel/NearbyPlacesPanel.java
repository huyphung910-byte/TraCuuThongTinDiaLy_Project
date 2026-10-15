package panel;

import com.google.gson.*;
import util.VietnameseText;
import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;

/** Wikipedia cards; all image downloads run outside the Swing event thread. */
public final class NearbyPlacesPanel extends JPanel implements Scrollable {
    private final List<SwingWorker<ImageIcon, Void>> imageTasks = new ArrayList<>();
    private long version;

    public NearbyPlacesPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        showMessage("Tra cứu và chọn thành phố để xem địa điểm lân cận.");
    }

    public void showMessage(String message) {
        clearCards();
        add(new JLabel(message));
        revalidate();
        repaint();
    }

    private void clearCards() {
        version++;
        for (SwingWorker<ImageIcon, Void> task : imageTasks) task.cancel(true);
        imageTasks.clear();
        removeAll();
    }

    public void showPlaces(JsonObject data) {
        clearCards();
        if (data.has("placesError")) {
            add(new JLabel(data.get("placesError").getAsString()));
        } else {
            add(new JLabel("Nguồn: Wikipedia · Khoảng cách đường thẳng từ tọa độ thành phố, trong 10 km"));
            if (data.has("placesNotice")) add(new JLabel(data.get("placesNotice").getAsString()));
            if (data.has("placesMetadataError")) add(new JLabel(data.get("placesMetadataError").getAsString()));
            JsonArray places = data.getAsJsonArray("places");
            if (places == null || places.isEmpty()) add(new JLabel("Chưa có địa điểm lân cận trong nguồn dữ liệu."));
            else for (JsonElement element : places) addCard(element.getAsJsonObject());
        }
        revalidate();
        repaint();
    }

    private void addCard(JsonObject place) {
        JPanel card = new JPanel(new BorderLayout(12, 8));
        card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEtchedBorder(),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 190));
        card.setPreferredSize(new Dimension(600, 180));
        card.setMinimumSize(new Dimension(0, 180));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel image = new JLabel("Không có ảnh", SwingConstants.CENTER);
        image.setPreferredSize(new Dimension(150, 110));
        card.add(image, BorderLayout.WEST);
        JPanel content = new JPanel(new BorderLayout(4, 6));
        JTextArea title = new JTextArea(value(place, "name", "Địa điểm"));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        configureText(title);
        content.add(title, BorderLayout.NORTH);
        double distance = place.get("distance").getAsDouble();
        String distanceText = distance < 1000 ? String.format(VietnameseText.LOCALE, "%.0f m", distance)
                : String.format(VietnameseText.LOCALE, "%.2f km", distance / 1000);
        JTextArea summary = new JTextArea("Khoảng cách: " + distanceText + "\n"
                + value(place, "description", "Chưa có mô tả. Xem bài viết trên Wikipedia để biết thêm."));
        configureText(summary);
        summary.setRows(4);
        content.add(summary, BorderLayout.CENTER);
        JButton link = new JButton("Xem trên Wikipedia");
        link.addActionListener(e -> openArticle(value(place, "url", "")));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        actions.add(link);
        content.add(actions, BorderLayout.SOUTH);
        card.add(content, BorderLayout.CENTER);
        add(card);
        add(Box.createVerticalStrut(8));
        String url = value(place, "imageUrl", "");
        if (!url.isEmpty()) loadImage(image, url);
    }

    private void loadImage(JLabel label, String url) {
        long currentVersion = version;
        label.setText("Đang tải ảnh...");
        SwingWorker<ImageIcon, Void> task = new SwingWorker<ImageIcon, Void>() {
            @Override protected ImageIcon doInBackground() throws Exception {
                URI uri = URI.create(url);
                if (!"https".equals(uri.getScheme()) || !"upload.wikimedia.org".equals(uri.getHost())) return null;
                HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(4000);
                connection.setRequestProperty("User-Agent", "TraCuuThongTinDiaLy/1.0 (educational Wikipedia viewer)");
                try {
                    if (connection.getResponseCode() != 200 || isCancelled()) return null;
                    try (var input = connection.getInputStream()) {
                        byte[] bytes = input.readNBytes(2_000_001);
                        if (bytes.length > 2_000_000 || isCancelled()) return null;
                        BufferedImage original = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
                        if (original == null) return null;
                        double scale = Math.min(150.0 / original.getWidth(), 110.0 / original.getHeight());
                        return new ImageIcon(original.getScaledInstance(Math.max(1, (int)(original.getWidth() * scale)),
                                Math.max(1, (int)(original.getHeight() * scale)), Image.SCALE_SMOOTH));
                    }
                } finally { connection.disconnect(); }
            }
            @Override protected void done() {
                if (!label.isDisplayable() || isCancelled() || currentVersion != version) return;
                try {
                    ImageIcon icon = get();
                    label.setIcon(icon);
                    label.setText(icon == null ? "Không tải được ảnh" : "");
                } catch (Exception e) { label.setText("Không tải được ảnh"); }
                imageTasks.remove(this);
            }
        };
        imageTasks.add(task);
        task.execute();
    }

    @Override public void removeNotify() {
        version++;
        for (SwingWorker<ImageIcon, Void> task : imageTasks) task.cancel(true);
        imageTasks.clear();
        super.removeNotify();
    }

    private void openArticle(String url) {
        try {
            URI uri = URI.create(url);
            if (!"https".equals(uri.getScheme()) || !("vi.wikipedia.org".equals(uri.getHost())
                    || "en.wikipedia.org".equals(uri.getHost()))) {
                throw new IllegalArgumentException("Đường dẫn Wikipedia không hợp lệ.");
            }
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                showArticleLink("Sao chép đường dẫn để mở trong trình duyệt:", url);
                return;
            }
            Desktop.getDesktop().browse(uri);
        } catch (Exception e) {
            showArticleLink("Không mở được trình duyệt. Sao chép đường dẫn:", url);
        }
    }

    private void showArticleLink(String message, String url) {
        JOptionPane.showInputDialog(this, message, "Liên kết Wikipedia", JOptionPane.INFORMATION_MESSAGE,
                null, null, url);
    }

    private static void configureText(JTextArea text) {
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setOpaque(false);
    }

    private static String value(JsonObject json, String key, String fallback) {
        if (!json.has(key) || json.get(key).isJsonNull() || json.get(key).getAsString().isBlank()) return fallback;
        return json.get(key).getAsString();
    }

    @Override public Dimension getPreferredScrollableViewportSize() { return new Dimension(650, 300); }
    @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 16; }
    @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) { return Math.max(16, visible.height - 16); }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
}
