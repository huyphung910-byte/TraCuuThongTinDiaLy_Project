package panel;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import model.Message;
import model.User;
import network.ClientSocketManager;
import util.AppTheme;
import util.VietnameseText;
import util.InputValidation;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class WeatherSubscriptionPanel extends JPanel {
    private final User currentUser;
    private final ClientSocketManager socketManager;
    private final Gson gson;
    private JTextField locationField;
    private JComboBox<String> conditionBox;
    private JButton subscribeButton;
    private JButton refreshButton;
    private JButton unsubscribeButton;
    private boolean busy;
    private JTable subTable;
    private DefaultTableModel tableModel;
    private JTextArea alertArea;

    public WeatherSubscriptionPanel(User user, ClientSocketManager socketManager) {
        this.currentUser = user;
        this.socketManager = socketManager;
        this.gson = new Gson();
        buildLayout();
        loadSubscriptions();
    }

    private void buildLayout() {
        setLayout(new BorderLayout(0, 0));
        setBackground(AppTheme.BG_MAIN);

        add(AppTheme.headerPanel("< Đăng ký theo dõi thời tiết"), BorderLayout.NORTH);

        // Form đăng ký
        JPanel topPanel = new JPanel(new GridBagLayout());
        topPanel.setBackground(AppTheme.BG_CARD);
        topPanel.setBorder(BorderFactory.createCompoundBorder(
                AppTheme.sectionBorder("Đăng ký theo dõi"),
                BorderFactory.createEmptyBorder(10, 0, 10, 0)));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        JLabel cityLbl = new JLabel("Tên thành phố:");
        cityLbl.setFont(AppTheme.FONT_BOLD);
        cityLbl.setForeground(AppTheme.TEXT_SECONDARY);
        topPanel.add(cityLbl, gbc);
        locationField = new JTextField(20);
        locationField.setFont(AppTheme.FONT_FIELD);
        locationField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppTheme.BORDER_COLOR, 1, true),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        locationField.setPreferredSize(new Dimension(220, 34));
        gbc.gridx = 1;
        topPanel.add(locationField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        JLabel condLbl = new JLabel("Điều kiện cảnh báo:");
        condLbl.setFont(AppTheme.FONT_BOLD);
        condLbl.setForeground(AppTheme.TEXT_SECONDARY);
        topPanel.add(condLbl, gbc);
        String[] conditions = {"Mưa lớn & Bão", "Nhiệt độ trên 38 độ C", "Tuyết rơi nặng hạt"};
        conditionBox = new JComboBox<>(conditions);
        conditionBox.setFont(AppTheme.FONT_FIELD);
        conditionBox.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                     boolean selected, boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                setText(conditionText(value == null ? null : value.toString()));
                return this;
            }
        });
        gbc.gridx = 1;
        topPanel.add(conditionBox, gbc);

        subscribeButton = new JButton("Đăng ký");
        subscribeButton.setFont(AppTheme.FONT_BOLD);
        subscribeButton.setBackground(AppTheme.PRIMARY);
        subscribeButton.setForeground(AppTheme.TEXT_WHITE);
        subscribeButton.setFocusPainted(false);
        subscribeButton.setBorderPainted(false);
        subscribeButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        gbc.gridx = 2; gbc.gridy = 1;
        topPanel.add(subscribeButton, gbc);
        add(topPanel, BorderLayout.NORTH);

        // Bảng danh sách
        String[] columns = {"Mã đăng ký", "Địa điểm", "Điều kiện cảnh báo"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        subTable = new JTable(tableModel);
        AppTheme.styleTable(subTable);
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBackground(AppTheme.BG_MAIN);
        centerPanel.setBorder(AppTheme.sectionBorder("Danh sách đã đăng ký"));
        centerPanel.add(AppTheme.neatScrollPane(subTable), BorderLayout.CENTER);

        refreshButton = new JButton("Làm mới");
        refreshButton.setFont(AppTheme.FONT_BOLD);
        refreshButton.setBackground(AppTheme.BG_CARD);
        refreshButton.setForeground(AppTheme.PRIMARY_DARK);
        refreshButton.addActionListener(e -> loadSubscriptions());
        unsubscribeButton = new JButton("Hủy theo dõi");
        unsubscribeButton.setFont(AppTheme.FONT_BOLD);
        unsubscribeButton.setBackground(AppTheme.DANGER);
        unsubscribeButton.setForeground(AppTheme.TEXT_WHITE);
        unsubscribeButton.setFocusPainted(false);
        unsubscribeButton.setBorderPainted(false);
        unsubscribeButton.setEnabled(false);
        unsubscribeButton.addActionListener(e -> unsubscribe());
        subTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        subTable.getSelectionModel().addListSelectionListener(e ->
                unsubscribeButton.setEnabled(!busy && subTable.getSelectedRow() >= 0));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 6));
        actions.setBackground(AppTheme.BG_CARD);
        actions.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, AppTheme.BORDER_COLOR));
        actions.add(refreshButton);
        actions.add(unsubscribeButton);
        centerPanel.add(actions, BorderLayout.SOUTH);
        add(centerPanel, BorderLayout.CENTER);

        // Khu vực cảnh báo
        alertArea = new JTextArea(6, 40);
        alertArea.setEditable(false);
        alertArea.setForeground(AppTheme.DANGER);
        alertArea.setBackground(AppTheme.DANGER_LIGHT);
        alertArea.setFont(new Font("Segoe UI", Font.BOLD, 12));
        alertArea.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(AppTheme.DANGER_LIGHT);
        bottomPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(2, 0, 0, 0, AppTheme.DANGER),
                BorderFactory.createEmptyBorder(0, 0, 0, 0)));
        JLabel alertTitle = new JLabel("  CANH BAO THOI TIET XAU");
        alertTitle.setFont(AppTheme.FONT_BOLD);
        alertTitle.setForeground(AppTheme.TEXT_WHITE);
        alertTitle.setBackground(AppTheme.DANGER);
        alertTitle.setOpaque(true);
        alertTitle.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        bottomPanel.add(alertTitle, BorderLayout.NORTH);
        bottomPanel.add(AppTheme.neatScrollPane(alertArea), BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        subscribeButton.addActionListener(e -> subscribe());
        locationField.addActionListener(e -> subscribe());
    }

    private void subscribe() {
        if (busy) return;
        final String location;
        String condition = (String) conditionBox.getSelectedItem();
        try {
            location = InputValidation.keyword(locationField.getText(), "Tên thành phố", 2, 200);
        } catch (InputValidation.ValidationException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), "Dữ liệu không hợp lệ", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (condition == null) {
            JOptionPane.showMessageDialog(this, "Chọn điều kiện cảnh báo thời tiết.", "Thiếu thông tin", JOptionPane.WARNING_MESSAGE);
            return;
        }

        setBusy(true);
        new SwingWorker<Message, Void>() {
            @Override
            protected Message doInBackground() throws Exception {
                JsonObject data = new JsonObject();
                data.addProperty("location", location);
                data.addProperty("condition", condition);
                return socketManager.sendRequest(new Message("SUBSCRIBE_WEATHER", data));
            }

            @Override
            protected void done() {
                if (!WeatherSubscriptionPanel.this.isDisplayable()) return;
                boolean subscribed = false;
                try {
                    Message response = get();
                    requireResponse(response, "SUBSCRIBE_WEATHER_RESPONSE");
                    if (response != null && "SUBSCRIBE_WEATHER_RESPONSE".equals(response.getAction()) && (Boolean)response.getData()) {
                        JOptionPane.showMessageDialog(WeatherSubscriptionPanel.this, "Đã đăng ký theo dõi thời tiết.",
                                "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                        locationField.setText("");
                        subscribed = true;
                    } else {
                        JOptionPane.showMessageDialog(WeatherSubscriptionPanel.this, "Không đăng ký được theo dõi thời tiết.",
                                "Lỗi đăng ký", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(WeatherSubscriptionPanel.this, "Không đăng ký được theo dõi: " + VietnameseText.errorMessage(e),
                            "Lỗi đăng ký", JOptionPane.ERROR_MESSAGE);
                } finally {
                    setBusy(false);
                    if (subscribed) loadSubscriptions();
                }
            }
        }.execute();
    }

    private void unsubscribe() {
        int row = subTable.getSelectedRow();
        if (busy || row < 0) return;
        final int id = Integer.parseInt(tableModel.getValueAt(subTable.convertRowIndexToModel(row), 0).toString());
        setBusy(true);
        new SwingWorker<Message, Void>() {
            @Override protected Message doInBackground() throws Exception {
                JsonObject data = new JsonObject();
                data.addProperty("id", id);
                return socketManager.sendRequest(new Message("UNSUBSCRIBE_WEATHER", data));
            }

            @Override protected void done() {
                if (!WeatherSubscriptionPanel.this.isDisplayable()) return;
                boolean cancelled = false;
                try {
                    Message response = get();
                    requireResponse(response, "UNSUBSCRIBE_WEATHER_RESPONSE");
                    cancelled = Boolean.TRUE.equals(response.getData());
                    if (!cancelled) throw new java.io.IOException("Không hủy được đăng ký theo dõi.");
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(WeatherSubscriptionPanel.this,
                            VietnameseText.errorMessage(e), "Lỗi hủy theo dõi", JOptionPane.ERROR_MESSAGE);
                } finally {
                    setBusy(false);
                    if (cancelled) loadSubscriptions();
                }
            }
        }.execute();
    }

    private void loadSubscriptions() {
        if (busy) return;
        setBusy(true);
        new SwingWorker<Message, Void>() {
            @Override
            protected Message doInBackground() throws Exception {
                JsonObject data = new JsonObject();
                return socketManager.sendRequest(new Message("GET_SUBSCRIPTIONS", data));
            }

            @Override
            protected void done() {
                if (!WeatherSubscriptionPanel.this.isDisplayable()) return;
                try {
                    Message response = get();
                    requireResponse(response, "GET_SUBSCRIPTIONS_RESPONSE");
                    if (response != null && "GET_SUBSCRIPTIONS_RESPONSE".equals(response.getAction())) {
                        java.lang.reflect.Type listType = new TypeToken<List<String[]>>(){}.getType();
                        List<String[]> subs = gson.fromJson(gson.toJsonTree(response.getData()), listType);
                        tableModel.setRowCount(0);
                        if (subs != null) {
                            for (String[] sub : subs) {
                                tableModel.addRow(new Object[]{sub[0], VietnameseText.location(sub[2]), conditionText(sub[5])});
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(WeatherSubscriptionPanel.this, "Không tải được danh sách theo dõi: " + VietnameseText.errorMessage(e),
                            "Lỗi tải dữ liệu", JOptionPane.ERROR_MESSAGE);
                } finally {
                    setBusy(false);
                }
            }
        }.execute();
    }

    private void setBusy(boolean value) {
        busy = value;
        subscribeButton.setEnabled(!value);
        refreshButton.setEnabled(!value);
        unsubscribeButton.setEnabled(!value && subTable.getSelectedRow() >= 0);
        locationField.setEnabled(!value);
        conditionBox.setEnabled(!value);
    }

    private static void requireResponse(Message response, String action) throws java.io.IOException {
        if (response == null) throw new java.io.IOException("Máy chủ không trả lời.");
        if ("ERROR".equals(response.getAction()) || "UNAUTHORIZED".equals(response.getAction())) {
            throw new java.io.IOException("Máy chủ báo lỗi: " + String.valueOf(response.getData()));
        }
        if (!action.equals(response.getAction())) throw new java.io.IOException("Phản hồi của máy chủ không hợp lệ.");
    }

    public void addAlert(String msg) {
        Runnable append = () -> {
            if (!isDisplayable()) return;
            alertArea.append(alertText(msg) + "\n");
            alertArea.setCaretPosition(alertArea.getDocument().getLength());
        };
        if (SwingUtilities.isEventDispatchThread()) append.run();
        else SwingUtilities.invokeLater(append);
    }

    private static String conditionText(String value) {
        if (value == null || value.isBlank()) return VietnameseText.UNKNOWN;
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        switch (normalized) {
            case "MƯA LỚN & BÃO":
            case "MUA LON & BAO":
            case "RAIN_OR_STORM":
            case "RAIN_STORM": return "Có mưa hoặc bão";
            case "RAIN": return "Có mưa";
            case "HEAVY_RAIN": return "Mưa lớn";
            case "NHIỆT ĐỘ TRÊN 38 ĐỘ C":
            case "NHIET DO TREN 38 DO C": return "Nhiệt độ từ 38 °C trở lên";
            case "TEMP_ABOVE": return "Nhiệt độ vượt ngưỡng";
            case "TEMP_BELOW": return "Nhiệt độ dưới ngưỡng";
            case "HUMIDITY_ABOVE": return "Độ ẩm vượt ngưỡng";
            case "WIND_ABOVE":
            case "WIND_SPEED_ABOVE": return "Tốc độ gió vượt ngưỡng";
            case "TUYẾT RƠI NẶNG HẠT":
            case "TUYET ROI NANG HAT":
            case "SNOW": return "Có tuyết rơi";
            case "HEAVY_SNOW": return "Tuyết rơi nhiều";
            case "STORM": return "Có bão";
            case "THUNDERSTORM": return "Có dông";
            default:
                if (normalized.matches("TEMP_ABOVE[_:]\\s*-?\\d+(?:[.,]\\d+)?")) {
                    return "Nhiệt độ trên " + value.trim().substring("TEMP_ABOVE".length() + 1).trim().replace('.', ',') + " °C";
                }
                return value;
        }
    }

    private static String alertText(String message) {
        if (message == null || message.isBlank()) return "Máy chủ chưa gửi nội dung cảnh báo.";
        final String locationMarker = "Thời tiết tại ", weatherMarker = " đang ";
        int start = message.indexOf(locationMarker), weather = message.indexOf(weatherMarker, start);
        if (start >= 0 && weather > start) {
            int description = weather + weatherMarker.length(), end = message.indexOf(" (", description);
            String localLocation = VietnameseText.location(message.substring(start + locationMarker.length(), weather));
            if (end > description) {
                message = message.substring(0, start + locationMarker.length()) + localLocation + weatherMarker
                        + VietnameseText.weather(message.substring(description, end)) + message.substring(end);
            }
        } else {
            int colon = message.indexOf(": ");
            if (colon > 0 && !message.startsWith("CẢNH BÁO")) {
                message = VietnameseText.location(message.substring(0, colon)) + message.substring(colon);
            }
        }
        String conditionMarker = "Khớp với điều kiện: ";
        int condition = message.indexOf(conditionMarker);
        if (condition >= 0) {
            int content = condition + conditionMarker.length();
            message = message.substring(0, content) + conditionText(message.substring(content));
        }
        return message;
    }
}
