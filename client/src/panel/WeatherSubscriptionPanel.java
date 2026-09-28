package panel;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import model.Message;
import model.User;
import network.ClientSocketManager;

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
        setLayout(new BorderLayout());

        // Top registration form
        JPanel topPanel = new JPanel(new GridBagLayout());
        topPanel.setBorder(BorderFactory.createTitledBorder("Đăng ký theo dõi thời tiết"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        topPanel.add(new JLabel("Tên thành phố:"), gbc);
        locationField = new JTextField(20);
        gbc.gridx = 1;
        topPanel.add(locationField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        topPanel.add(new JLabel("Điều kiện cảnh báo:"), gbc);
        String[] conditions = {"Mưa lớn & Bão", "Nhiệt độ trên 38 độ C", "Tuyết rơi nặng hạt"};
        conditionBox = new JComboBox<>(conditions);
        gbc.gridx = 1;
        topPanel.add(conditionBox, gbc);

        subscribeButton = new JButton("Đăng ký");
        gbc.gridx = 2; gbc.gridy = 1;
        topPanel.add(subscribeButton, gbc);

        add(topPanel, BorderLayout.NORTH);

        // Center table
        String[] columns = {"ID", "Địa điểm", "Điều kiện"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        subTable = new JTable(tableModel);
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBorder(BorderFactory.createTitledBorder("Danh sách đã đăng ký"));
        centerPanel.add(new JScrollPane(subTable), BorderLayout.CENTER);
        
        JButton refreshBtn = new JButton("Làm mới");
        refreshBtn.addActionListener(e -> loadSubscriptions());
        centerPanel.add(refreshBtn, BorderLayout.SOUTH);
        add(centerPanel, BorderLayout.CENTER);

        // Bottom alert area
        alertArea = new JTextArea(6, 40);
        alertArea.setEditable(false);
        alertArea.setForeground(Color.RED);
        alertArea.setFont(new Font("SansSerif", Font.BOLD, 12));
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createTitledBorder("Cảnh báo từ Server (Trực tiếp)"));
        bottomPanel.add(new JScrollPane(alertArea), BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        subscribeButton.addActionListener(e -> subscribe());
    }

    private void subscribe() {
        String location = locationField.getText().trim();
        String condition = (String) conditionBox.getSelectedItem();
        if (location.isEmpty()) return;

        subscribeButton.setEnabled(false);
        new SwingWorker<Message, Void>() {
            @Override
            protected Message doInBackground() throws Exception {
                JsonObject data = new JsonObject();
                data.addProperty("userId", currentUser.getId());
                data.addProperty("location", location);
                data.addProperty("condition", condition);
                return socketManager.sendRequest(new Message("SUBSCRIBE_WEATHER", data));
            }

            @Override
            protected void done() {
                try {
                    Message response = get();
                    if (response != null && "SUBSCRIBE_WEATHER_RESPONSE".equals(response.getAction()) && (Boolean)response.getData()) {
                        JOptionPane.showMessageDialog(WeatherSubscriptionPanel.this, "Đăng ký thành công!");
                        locationField.setText("");
                        loadSubscriptions();
                    } else {
                        JOptionPane.showMessageDialog(WeatherSubscriptionPanel.this, "Đăng ký thất bại.");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    subscribeButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private void loadSubscriptions() {
        new SwingWorker<Message, Void>() {
            @Override
            protected Message doInBackground() throws Exception {
                JsonObject data = new JsonObject();
                data.addProperty("userId", currentUser.getId());
                return socketManager.sendRequest(new Message("GET_SUBSCRIPTIONS", data));
            }

            @Override
            protected void done() {
                try {
                    Message response = get();
                    if (response != null && "GET_SUBSCRIPTIONS_RESPONSE".equals(response.getAction())) {
                        java.lang.reflect.Type listType = new TypeToken<List<String[]>>(){}.getType();
                        List<String[]> subs = gson.fromJson(gson.toJsonTree(response.getData()), listType);
                        tableModel.setRowCount(0);
                        if (subs != null) {
                            for (String[] sub : subs) {
                                tableModel.addRow(new Object[]{sub[0], sub[2], sub[5]});
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.execute();
    }

    public void addAlert(String msg) {
        SwingUtilities.invokeLater(() -> {
            alertArea.append(msg + "\n");
            alertArea.setCaretPosition(alertArea.getDocument().getLength());
        });
    }
}
