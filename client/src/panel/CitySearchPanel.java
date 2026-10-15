package panel;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import model.Location;
import model.Message;
import model.User;
import model.WeatherData;
import network.ClientSocketManager;
import util.AppTheme;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import util.VietnameseText;
import util.InputValidation;

public class CitySearchPanel extends JPanel {
    private final User currentUser;
    private final ClientSocketManager socketManager;
    private final Gson gson = new Gson();
    private final JTextField searchField = new JTextField(25);
    private final JButton searchButton = new JButton("Tra cứu");
    private final JButton saveButton = new JButton("Lưu địa điểm");
    private final JTextArea detailArea = new JTextArea(12, 50);
    private final NearbyPlacesPanel nearbyPlaces = new NearbyPlacesPanel();
    private final CityMapPanel cityMap = new CityMapPanel();
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new String[]{"Tên thành phố", "Quốc gia", "Tỉnh / vùng", "Vĩ độ", "Kinh độ", "Dân số"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable resultTable = new JTable(tableModel);
    private List<Location> results = new ArrayList<>();
    private long selectionVersion;
    private boolean saving;
    private boolean detailLoading;
    private final JButton refreshWeather = new JButton("Cập nhật thời tiết");
    private final JLabel currentTime = new JLabel("Chọn thành phố để xem giờ địa phương");
    private final Timer clockTimer = new Timer(1000, e -> updateClock());
    private final Timer weatherTimer = new Timer(5 * 60 * 1000, e -> {
        if (isShowing() && !detailLoading && searchButton.isEnabled() && selectedCity() != null) showDetails();
    });
    private static final java.time.format.DateTimeFormatter TIME_FORMAT =
            java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy (XXX)");

    public CitySearchPanel(User user, ClientSocketManager socketManager) {
        this.currentUser = user;
        this.socketManager = socketManager;
        setLayout(new BorderLayout(0, 0));
        setBackground(AppTheme.BG_MAIN);

        // ── Header tìm kiếm ──────────────────────────────────────────────
        JPanel headerPanel = AppTheme.headerPanel("< Tra cứu thành phố");
        add(headerPanel, BorderLayout.NORTH);

        // ── Thanh tìm kiếm ────────────────────────────────────────────────
        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        searchBar.setBackground(AppTheme.BG_CARD);
        searchBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, AppTheme.BORDER_COLOR));
        JLabel searchLbl = new JLabel("Tên thành phố:");
        searchLbl.setFont(AppTheme.FONT_BOLD);
        searchLbl.setForeground(AppTheme.TEXT_SECONDARY);
        searchField.setFont(AppTheme.FONT_FIELD);
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppTheme.BORDER_COLOR, 1, true),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        searchField.setPreferredSize(new Dimension(260, 34));
        AppTheme.styleTable(searchButton.getParent() instanceof JPanel ? null : null);
        // style search button
        searchButton.setFont(AppTheme.FONT_BOLD);
        searchButton.setBackground(AppTheme.PRIMARY);
        searchButton.setForeground(AppTheme.TEXT_WHITE);
        searchButton.setFocusPainted(false);
        searchButton.setBorderPainted(false);
        searchButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        searchBar.add(searchLbl);
        searchBar.add(searchField);
        searchBar.add(searchButton);

        // ── Bảng kết quả ─────────────────────────────────────────────────
        resultTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        AppTheme.styleTable(resultTable);
        JScrollPane tableScroll = AppTheme.neatScrollPane(resultTable);

        // ── Khu vực chi tiết ─────────────────────────────────────────────
        detailArea.setEditable(false);
        detailArea.setFont(AppTheme.FONT_BODY);
        detailArea.setBackground(AppTheme.BG_CARD);
        detailArea.setForeground(AppTheme.TEXT_PRIMARY);
        detailArea.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        currentTime.setFont(AppTheme.FONT_SMALL);
        currentTime.setForeground(AppTheme.TEXT_SECONDARY);
        currentTime.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBorder(AppTheme.sectionBorder("Thông tin thành phố, thời tiết và địa điểm liên quan"));
        bottom.setBackground(AppTheme.BG_MAIN);
        bottom.add(currentTime, BorderLayout.NORTH);

        JTabbedPane detailsTabs = new JTabbedPane();
        detailsTabs.setFont(AppTheme.FONT_BOLD);
        detailsTabs.addTab("Thành phố & Thời tiết", AppTheme.neatScrollPane(detailArea));
        JScrollPane placesScroll = AppTheme.neatScrollPane(nearbyPlaces);
        placesScroll.getVerticalScrollBar().setUnitIncrement(16);
        detailsTabs.addTab("Địa điểm lân cận", placesScroll);
        detailsTabs.addTab("Bản đồ", cityMap);
        bottom.add(detailsTabs, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 8));
        buttons.setBackground(AppTheme.BG_CARD);
        buttons.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, AppTheme.BORDER_COLOR));
        saveButton.setEnabled(false);
        saveButton.setFont(AppTheme.FONT_BOLD);
        saveButton.setBackground(AppTheme.ACCENT);
        saveButton.setForeground(AppTheme.TEXT_WHITE);
        saveButton.setFocusPainted(false);
        saveButton.setBorderPainted(false);
        refreshWeather.setEnabled(false);
        refreshWeather.setFont(AppTheme.FONT_BOLD);
        refreshWeather.setForeground(AppTheme.PRIMARY_DARK);
        refreshWeather.addActionListener(e -> { if (!detailLoading && selectedCity() != null) showDetails(); });
        buttons.add(saveButton);
        buttons.add(refreshWeather);
        bottom.add(buttons, BorderLayout.SOUTH);

        JPanel topArea = new JPanel(new BorderLayout());
        topArea.add(searchBar, BorderLayout.NORTH);
        topArea.add(tableScroll, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topArea, bottom);
        split.setResizeWeight(0.4);
        split.setDividerLocation(260);
        split.setContinuousLayout(true);
        split.setBackground(AppTheme.BG_MAIN);
        split.setBorder(BorderFactory.createEmptyBorder());
        add(split, BorderLayout.CENTER);

        searchButton.addActionListener(e -> search());
        searchField.addActionListener(e -> search());
        saveButton.addActionListener(e -> saveLocation());
        resultTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showDetails();
        });
    }

    private void search() {
        if (!searchButton.isEnabled()) return;
        final String keyword;
        try {
            keyword = InputValidation.keyword(searchField.getText(), "Tên thành phố", 2, 200);
        } catch (InputValidation.ValidationException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), "Dữ liệu không hợp lệ", JOptionPane.WARNING_MESSAGE);
            return;
        }
        selectionVersion++;
        cityMap.clear();
        results = new ArrayList<>();
        tableModel.setRowCount(0);
        saveButton.setEnabled(false);
        refreshWeather.setEnabled(false);
        updateClock();
        searchButton.setEnabled(false);
        detailArea.setText("Đang tra cứu thành phố...");
        nearbyPlaces.showMessage("Đang tra cứu thành phố...");
        new SwingWorker<Message, Void>() {
            @Override protected Message doInBackground() throws Exception {
                JsonObject data = new JsonObject();
                data.addProperty("keyword", keyword);
                Message response = socketManager.sendRequest(new Message("SEARCH_LOCATION", data));
                if (response != null && "SEARCH_LOCATION_RESPONSE".equals(response.getAction())) {
                    JsonObject history = new JsonObject();
                    history.addProperty("keyword", keyword);
                    history.addProperty("type", "thanh_pho");
                    try {
                        socketManager.sendRequest(new Message("SAVE_HISTORY", history));
                    } catch (Exception e) {
                        System.err.println("Không lưu được lịch sử thành phố: " + VietnameseText.errorMessage(e));
                    }
                }
                return response;
            }
            @Override protected void done() {
                if (!CitySearchPanel.this.isDisplayable()) return;
                try {
                    Message response = get();
                    requireResponse(response, "SEARCH_LOCATION_RESPONSE");
                    List<Location> found = gson.fromJson(gson.toJsonTree(response.getData()),
                            new TypeToken<List<Location>>(){}.getType());
                    results = found == null ? new ArrayList<>() : found;
                    for (Location city : results) {
                        tableModel.addRow(new Object[]{VietnameseText.city(city.getCityName(), city.getCountryCode()),
                                VietnameseText.country(city.getCountry(), city.getCountryCode()),
                                VietnameseText.city(city.getDescription(), city.getCountryCode()),
                                String.format(VietnameseText.LOCALE, "%.5f°", city.getLatitude()),
                                String.format(VietnameseText.LOCALE, "%.5f°", city.getLongitude()), population(city)});
                    }
                    detailArea.setText(results.isEmpty() ? "Không tìm thấy thành phố phù hợp."
                            : "Chọn một thành phố để xem thời tiết và địa điểm lân cận.");
                    nearbyPlaces.showMessage(results.isEmpty() ? "Không tìm thấy thành phố phù hợp."
                            : "Chọn thành phố để xem địa điểm lân cận.");
                    if (!results.isEmpty()) resultTable.setRowSelectionInterval(0, 0);
                } catch (Exception e) {
                    detailArea.setText("Tra cứu thất bại: " + errorMessage(e));
                    nearbyPlaces.showMessage("Tra cứu thất bại: " + errorMessage(e));
                } finally { searchButton.setEnabled(true); }
            }
        }.execute();
    }

    private Location selectedCity() {
        int row = resultTable.getSelectedRow();
        if (row < 0) return null;
        int index = resultTable.convertRowIndexToModel(row);
        return index < results.size() ? results.get(index) : null;
    }

    private void showDetails() {
        long version = ++selectionVersion;
        Location city = selectedCity();
        saveButton.setEnabled(city != null && !saving);
        refreshWeather.setEnabled(false);
        updateClock();
        detailLoading = city != null;
        if (city == null) { cityMap.clear(); return; }
        cityMap.showCity(VietnameseText.city(city.getCityName(), city.getCountryCode()), city.getLatitude(), city.getLongitude());
        nearbyPlaces.showMessage("Đang tải địa điểm lân cận...");
        String header = String.format(VietnameseText.LOCALE, "Thành phố: %s\nQuốc gia: %s\nTỉnh / vùng: %s\n"
                        + "Tọa độ: %.5f, %.5f\nDân số (GeoNames): %s\n\n",
                VietnameseText.city(city.getCityName(), city.getCountryCode()), VietnameseText.country(city.getCountry(), city.getCountryCode()),
                VietnameseText.city(city.getDescription(), city.getCountryCode()),
                city.getLatitude(), city.getLongitude(), population(city));
        detailArea.setText(header + "Đang tải thời tiết và địa điểm lân cận...");
        new SwingWorker<JsonObject, Void>() {
            @Override protected JsonObject doInBackground() throws Exception {
                JsonObject request = new JsonObject();
                request.addProperty("id", city.getId());
                Message response = socketManager.sendRequest(new Message("GET_CITY_DETAILS", request));
                requireResponse(response, "GET_CITY_DETAILS_RESPONSE");
                return gson.toJsonTree(response.getData()).getAsJsonObject();
            }
            @Override protected void done() {
                if (!CitySearchPanel.this.isDisplayable() || version != selectionVersion) return;
                try {
                JsonObject data = get();
                StringBuilder text = new StringBuilder(header);
                text.append("--- THỜI TIẾT HIỆN TẠI (OpenWeather) ---\n");
                if (data.has("weather")) {
                    WeatherData w = gson.fromJson(data.get("weather"), WeatherData.class);
                    text.append(String.format(VietnameseText.LOCALE, "Nhiệt độ: %.1f°C\nĐộ ẩm: %d%%\nGió: %.1f m/s\n"
                                    + "Mô tả: %s\nThời điểm dữ liệu OpenWeather: %s\n",
                            w.getTemp(), w.getHumidity(), w.getWindSpeed(), VietnameseText.weather(w.getDescription()), weatherTime(w.getUpdatedAt(), city)));
                    text.append("Lần lấy dữ liệu gần nhất: ").append(TIME_FORMAT.format(java.time.ZonedDateTime.now(cityZone(city)))).append('\n');
                    text.append(w.isFromCache() ? "Nguồn: bộ nhớ đệm còn hiệu lực trên máy chủ.\n" : "Nguồn: vừa lấy từ OpenWeather.\n");
                    text.append("Tự cập nhật mỗi 5 phút khi đang xem tab.\n");
                } else { text.append(data.get("weatherError").getAsString()).append('\n'); }
                text.append("\nXem ảnh và thông tin địa điểm ở tab Địa điểm lân cận.");
                detailArea.setText(text.toString());
                nearbyPlaces.showPlaces(data);
                } catch (Exception e) {
                    detailArea.setText(header + "Không tải được chi tiết: " + errorMessage(e));
                    nearbyPlaces.showMessage("Không tải được địa điểm: " + errorMessage(e));
                }
                detailArea.setCaretPosition(0);
                detailLoading = false;
                refreshWeather.setEnabled(selectedCity() != null && searchButton.isEnabled());
            }
        }.execute();
    }

    private void saveLocation() {
        Location city = selectedCity();
        if (city == null || saving) return;
        final String name;
        try {
            InputValidation.coordinates(city.getLatitude(), city.getLongitude());
            String cityName = InputValidation.keyword(city.getCityName(), "Tên thành phố", 1, 255);
            String country = InputValidation.keyword(city.getCountry(), "Tên quốc gia", 1, 255);
            name = InputValidation.keyword(cityName + ", " + country, "Tên địa điểm", 1, 255);
        } catch (InputValidation.ValidationException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), "Dữ liệu địa điểm không hợp lệ", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String enteredNote = JOptionPane.showInputDialog(this, "Nhập ghi chú cho địa điểm này:",
                "Lưu địa điểm", JOptionPane.PLAIN_MESSAGE);
        if (enteredNote == null) return;
        final String note;
        try {
            note = InputValidation.optionalText(enteredNote, "Ghi chú", 2000);
        } catch (InputValidation.ValidationException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), "Ghi chú không hợp lệ", JOptionPane.WARNING_MESSAGE);
            return;
        }
        saving = true;
        saveButton.setEnabled(false);
        new SwingWorker<Message, Void>() {
            @Override protected Message doInBackground() throws Exception {
                JsonObject data = new JsonObject();
                data.addProperty("name", name);
                data.addProperty("lat", city.getLatitude());
                data.addProperty("lng", city.getLongitude());
                data.addProperty("note", note);
                return socketManager.sendRequest(new Message("SAVE_LOCATION", data));
            }
            @Override protected void done() {
                if (!CitySearchPanel.this.isDisplayable()) return;
                try {
                    Message response = get();
                    requireResponse(response, "SAVE_LOCATION_RESPONSE");
                    boolean success = gson.toJsonTree(response.getData()).getAsBoolean();
                    JOptionPane.showMessageDialog(CitySearchPanel.this,
                            success ? "Đã lưu địa điểm thành công!" : "Không thể lưu địa điểm.");
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(CitySearchPanel.this, "Lỗi lưu địa điểm: " + errorMessage(e));
                } finally {
                    saving = false;
                    saveButton.setEnabled(selectedCity() != null);
                }
            }
        }.execute();
    }

    private static String population(Location city) {
        return city.getPopulation() == null ? "Chưa có dữ liệu" : String.format(VietnameseText.LOCALE, "%,d người", city.getPopulation());
    }

    private void updateClock() {
        Location city = selectedCity();
        if (city == null) { currentTime.setText("Chọn thành phố để xem giờ địa phương"); return; }
        boolean known = city.getTimezone() != null && !city.getTimezone().isBlank();
        currentTime.setText((known ? "Giờ hiện tại tại " + VietnameseText.city(city.getCityName(), city.getCountryCode()) : "Giờ hiện tại (UTC; chưa có múi giờ thành phố)")
                + ": " + TIME_FORMAT.format(java.time.ZonedDateTime.now(cityZone(city))));
    }

    private static java.time.ZoneId cityZone(Location city) {
        try { return java.time.ZoneId.of(city.getTimezone()); }
        catch (Exception e) { return java.time.ZoneOffset.UTC; }
    }

    private static String weatherTime(String timestamp, Location city) {
        try { return TIME_FORMAT.format(java.time.Instant.parse(timestamp).atZone(cityZone(city))); }
        catch (Exception e) { return timestamp; }
    }

    @Override public void addNotify() {
        super.addNotify();
        clockTimer.start();
        weatherTimer.start();
    }

    @Override public void removeNotify() {
        selectionVersion++;
        detailLoading = false;
        clockTimer.stop();
        weatherTimer.stop();
        super.removeNotify();
    }

    private static void requireResponse(Message response, String action) throws java.io.IOException {
        if (response == null) throw new java.io.IOException("Máy chủ không trả lời.");
        if (!action.equals(response.getAction())) {
            throw new java.io.IOException("ERROR".equals(response.getAction())
                    ? String.valueOf(response.getData()) : "Phản hồi máy chủ không hợp lệ.");
        }
    }

    private static String errorMessage(Exception error) {
        return VietnameseText.errorMessage(error);
    }
}
