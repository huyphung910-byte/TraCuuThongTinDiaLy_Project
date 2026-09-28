package panel;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import model.Hotel;
import model.Location;
import model.Message;
import model.User;
import model.WeatherData;
import network.ClientSocketManager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class CitySearchPanel extends JPanel {
    private final User currentUser;
    private final ClientSocketManager socketManager;
    private final Gson gson;
    private JTextField searchField;
    private JButton searchButton;
    private JTable resultTable;
    private DefaultTableModel tableModel;
    private JTextArea detailArea;
    private JButton saveButton;

    public CitySearchPanel(User user, ClientSocketManager socketManager) {
        this.currentUser = user;
        this.socketManager = socketManager;
        this.gson = new Gson();
        buildLayout();
    }

    private void buildLayout() {
        setLayout(new BorderLayout());

        // Top search bar
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchField = new JTextField(25);
        searchButton = new JButton("Tra cứu");
        topPanel.add(new JLabel("Tên thành phố: "));
        topPanel.add(searchField);
        topPanel.add(searchButton);
        add(topPanel, BorderLayout.NORTH);

        // Center result table
        String[] columns = {"ID", "Tên địa điểm", "Vĩ độ", "Kinh độ"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        resultTable = new JTable(tableModel);
        resultTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && resultTable.getSelectedRow() != -1) {
                showDetails(resultTable.getSelectedRow());
            }
        });
        add(new JScrollPane(resultTable), BorderLayout.CENTER);

        // Bottom detail area
        JPanel bottomPanel = new JPanel(new BorderLayout());
        detailArea = new JTextArea(8, 50);
        detailArea.setEditable(false);
        bottomPanel.add(new JScrollPane(detailArea), BorderLayout.CENTER);

        saveButton = new JButton("Lưu địa điểm này");
        saveButton.setEnabled(false);
        saveButton.addActionListener(e -> saveLocation());
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(saveButton);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(bottomPanel, BorderLayout.SOUTH);

        searchButton.addActionListener(e -> search());
    }

    private void search() {
        String keyword = searchField.getText().trim();
        if (keyword.isEmpty()) return;
        
        saveHistory(keyword);

        new SwingWorker<Message, Void>() {
            @Override
            protected Message doInBackground() throws Exception {
                JsonObject data = new JsonObject();
                data.addProperty("keyword", keyword);
                return socketManager.sendRequest(new Message("SEARCH_LOCATION", data));
            }
            @Override
            protected void done() {
                try {
                    Message response = get();
                    if (response != null && "SEARCH_LOCATION_RESPONSE".equals(response.getAction())) {
                        java.lang.reflect.Type listType = new TypeToken<List<Location>>(){}.getType();
                        List<Location> locations = gson.fromJson(gson.toJsonTree(response.getData()), listType);
                        tableModel.setRowCount(0);
                        if (locations != null) {
                            for (Location loc : locations) {
                                tableModel.addRow(new Object[]{loc.getId(), loc.getCityName(), loc.getLatitude(), loc.getLongitude()});
                            }
                        }
                        detailArea.setText("");
                        saveButton.setEnabled(false);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.execute();
    }

    private void saveHistory(String keyword) {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                JsonObject data = new JsonObject();
                data.addProperty("userId", currentUser.getId());
                data.addProperty("keyword", keyword);
                socketManager.sendRequest(new Message("SAVE_HISTORY", data));
                return null;
            }
        }.execute();
    }

    private void showDetails(int row) {
        String cityName = (String) tableModel.getValueAt(row, 1);
        saveButton.setEnabled(true);
        detailArea.setText("Đang tải thông tin thời tiết và khách sạn cho " + cityName + "...");

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                JsonObject weatherReq = new JsonObject();
                weatherReq.addProperty("cityName", cityName);
                Message weatherRes = socketManager.sendRequest(new Message("GET_WEATHER", weatherReq));

                JsonObject hotelReq = new JsonObject();
                hotelReq.addProperty("cityName", cityName);
                Message hotelRes = socketManager.sendRequest(new Message("GET_HOTELS", hotelReq));

                StringBuilder sb = new StringBuilder();
                if (weatherRes != null && "GET_WEATHER_RESPONSE".equals(weatherRes.getAction())) {
                    WeatherData weather = gson.fromJson(gson.toJsonTree(weatherRes.getData()), WeatherData.class);
                    sb.append("--- THỜI TIẾT ---\n");
                    sb.append(String.format("Nhiệt độ: %.1f°C\nĐộ ẩm: %d%%\nGió: %.1fm/s\nMô tả: %s\n\n",
                            weather.getTemp(), weather.getHumidity(), weather.getWindSpeed(), weather.getDescription()));
                }
                
                if (hotelRes != null && "GET_HOTELS_RESPONSE".equals(hotelRes.getAction())) {
                    java.lang.reflect.Type listType = new TypeToken<List<Hotel>>(){}.getType();
                    List<Hotel> hotels = gson.fromJson(gson.toJsonTree(hotelRes.getData()), listType);
                    sb.append("--- GỢI Ý KHÁCH SẠN ---\n");
                    if (hotels != null && !hotels.isEmpty()) {
                        for (Hotel h : hotels) {
                            sb.append(String.format("- %s (%.1f*): %.0f %s\n  %s\n",
                                    h.getTenKhachSan(), h.getDanhGia(), h.getGiaMotDem(), h.getDonViTien(), h.getDiaChi()));
                        }
                    } else {
                        sb.append("Không có khách sạn nào trong CSDL cho thành phố này.\n");
                    }
                }
                return sb.toString();
            }
            @Override
            protected void done() {
                try {
                    detailArea.setText(get());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.execute();
    }

    private void saveLocation() {
        int row = resultTable.getSelectedRow();
        if (row == -1) return;
        
        String name = (String) tableModel.getValueAt(row, 1);
        double lat = (Double) tableModel.getValueAt(row, 2);
        double lng = (Double) tableModel.getValueAt(row, 3);
        String note = JOptionPane.showInputDialog(this, "Nhập ghi chú cho địa điểm này:", "Lưu địa điểm", JOptionPane.PLAIN_MESSAGE);
        
        if (note == null) return; // Cancelled

        new SwingWorker<Message, Void>() {
            @Override
            protected Message doInBackground() throws Exception {
                JsonObject data = new JsonObject();
                data.addProperty("userId", currentUser.getId());
                data.addProperty("name", name);
                data.addProperty("lat", lat);
                data.addProperty("lng", lng);
                data.addProperty("note", note);
                return socketManager.sendRequest(new Message("SAVE_LOCATION", data));
            }
            @Override
            protected void done() {
                try {
                    Message response = get();
                    if (response != null && "SAVE_LOCATION_RESPONSE".equals(response.getAction()) && (Boolean)response.getData()) {
                        JOptionPane.showMessageDialog(CitySearchPanel.this, "Đã lưu địa điểm thành công!");
                    } else {
                        JOptionPane.showMessageDialog(CitySearchPanel.this, "Không thể lưu địa điểm.");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.execute();
    }
}
